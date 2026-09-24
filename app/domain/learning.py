"""Opt-in explicit feedback and isolated reranking; original candidate eligibility is retained."""
from typing import Literal
from fastapi import APIRouter,Request,HTTPException
from pydantic import Field
from app.storage import store
from app.domain.common import Command,listing,put,read,execute,now,remove
from app.domain.learning_model import train,tokens,prediction
from app.services.recommendation_service import load_recipes,score_recipe
router=APIRouter(prefix='/api/v3/learning',tags=['Personalized learning'])
class Toggle(Command):
    enabled:bool=Field(strict=True)
    expected_version:int=Field(ge=0,strict=True)
class Feedback(Command):
    liked:bool=Field(strict=True)
    expected_version:int=Field(ge=0,strict=True)
class Reset(Command): action:Literal['reset_model','clear_feedback']

def settings(user):return next(iter(listing('learning_settings',user)),{'enabled':False,'version':0})
def feedback(user):return listing('feedback',user)
def model(user):return next(iter(listing('learning_model',user)),None)
def require_enabled(user):
    if not settings(user)['enabled']:raise HTTPException(409,'请先主动开启偏好学习')
def auxiliary(user,kind,id,recipe):
    if not settings(user)['enabled']:return
    unique=user+':'+kind+':'+id
    if any(r['id']==unique for r in listing('learning_activity',user)):return
    put('learning_activity',unique,user,{'type':kind,'reference_id':id,'features':tokens(recipe),'created_at':now(),'label':None,'note':'行为单独记录，不作为喜欢/不喜欢训练标签'})

@router.get('')
def get(request:Request):
    user=request.state.user_id
    with store.transaction():
        current=model(user)
        return {'settings':settings(user),'feedback':feedback(user),'model':{k:v for k,v in current.items() if k not in ('weights','vocabulary')} if current else None,'activity_count':len(listing('learning_activity',user)),'note':'只学习明确的喜欢/不喜欢。收藏、复刻、完成另记行为；不将浏览或未点击当负反馈。模型分值不是准确率，也不替代忌口或食材风险过滤。'}
@router.put('/settings')
def toggle(request:Request,body:Toggle):
    user=request.state.user_id
    def action():
        row=settings(user)
        if row['version']!=body.expected_version:raise HTTPException(409,'学习设置已变化，请刷新')
        return {'settings':put('learning_settings',user,user,{'enabled':body.enabled,'updated_at':now()},body.expected_version or None)}
    return execute(user,'learning-toggle',body,action)
@router.put('/feedback/{recipe_id}')
def respond(recipe_id:str,request:Request,body:Feedback):
    user=request.state.user_id
    with store.transaction():
        require_enabled(user)
        recipe=next((r for r in load_recipes() if r['id']==recipe_id),None)
        if recipe is None:raise HTTPException(404,'此菜谱不在学习候选库中')
        def action():
            prior=next((r for r in feedback(user) if r['recipe_id']==recipe_id),None)
            if (prior['version'] if prior else 0)!=body.expected_version:raise HTTPException(409,'这道菜的反馈已变化，请刷新')
            if prior and prior['liked']==body.liked:return {'feedback':prior}
            from app.domain.households import personal_snapshot
            score=score_recipe(recipe,personal_snapshot(user)['items'])
            row=put('feedback',user+':'+recipe_id,user,{'recipe_id':recipe_id,'recipe_name':recipe['name'],'liked':body.liked,'features':tokens(recipe),'created_at':now(),'original_score':score['total_score'] if score else 0,'provenance':'explicit_user'},prior['version'] if prior else None)
            return {'feedback':row}
        return execute(user,'feedback:'+recipe_id,body,action)
@router.post('/train')
def train_model(request:Request,body:Command):
    user=request.state.user_id
    with store.transaction():
        require_enabled(user)
        def action():
            result=train(feedback(user));prior=model(user)
            row=put('learning_model',user,user,{**result,'trained_at':now()},prior['version'] if prior else None)
            return {'model':{k:v for k,v in row.items() if k not in ('weights','vocabulary')}}
        return execute(user,'learning-train',body,action)
@router.post('/reset')
def reset(request:Request,body:Reset):
    user=request.state.user_id
    def action():
        current=model(user)
        if current:remove('learning_model',current['id'],current['version'])
        if body.action=='clear_feedback':
            for kind in ('feedback','learning_activity'):
                for row in listing(kind,user):remove(kind,row['id'],row['version'])
        if body.action=='clear_feedback':
            import json
            from app.storage import encode
            with store.transaction() as db:
                for receipt in db.execute('SELECT key,payload FROM receipts WHERE scope=?',(user,)).fetchall():
                    payload=json.loads(receipt['payload'])
                    if 'feedback' in payload or 'model' in payload:db.execute('UPDATE receipts SET payload=? WHERE scope=? AND key=?',(encode({'status':'success','cleared':True,'note':'原反馈或模型已被用户清除，旧操作不会重新写入'}),user,receipt['key']))
        return {'cleared':body.action,'note':'模型已重置，当前回退原排序'}
    return execute(user,'learning-reset',body,action)

def rerank(user,candidates):
    """Caller supplies only already eligible candidates; no new recipe may be introduced."""
    original=list(candidates)
    try:
        with store.transaction():
            enabled=settings(user)['enabled'];current=model(user) if enabled else None
            if not enabled:return original,{'enabled':False,'applied':False,'reason':'偏好学习未开启'}
            if not current or not current.get('accepted'):return original,{'enabled':True,'applied':False,'reason':current.get('reason') if current else '尚无通过对照评估的模型，使用原排序'}
            if current.get('trained_through')!=max((r['created_at'] for r in feedback(user)),default=None):return original,{'enabled':True,'applied':False,'reason':'反馈已更新，请重新评估模型；当前使用原排序'}
        recipes={r['id']:r for r in load_recipes()};ranked=[]
        for row in original:
            value,reasons=prediction(current,recipes[row['recipe_id']]);ranked.append({**row,'original_rank':row['rank'],'personalization':{'preference_value':round(value,6),'reasons':reasons,'model_version':current['model_version']}})
        ranked.sort(key=lambda r:(-r['personalization']['preference_value'],r['original_rank']))
        for index,row in enumerate(ranked,1):row['rank']=index
        return ranked,{'enabled':True,'applied':True,'model_version':current['model_version'],'reason':'在既有合格候选内按已评估的标签偏好排序，原评分各分量保持不变'}
    except Exception:
        return original,{'enabled':True,'applied':False,'reason':'学习模型暂不可用，已回退原排序'}
