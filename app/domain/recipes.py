"""Validated, independent recipe snapshots with explicit provenance."""
import json
import math
from typing import Literal
from fastapi import APIRouter,Request,HTTPException
from pydantic import Field
from app.storage import store
from app.domain.common import Command,VersionCommand,read,put,listing,remove,execute,new_id,now
from app.services.inventory_transactions import digest

router=APIRouter(prefix='/api/v3/recipes',tags=['Recipe reuse'])
class Source(Command):
    source_type:Literal['standard','history','favorite','copy','community']
    source_id:str=Field(min_length=1,max_length=128)
    expected_source_version:str=Field(min_length=1,max_length=128)

def normalize_recipe(value):
    if isinstance(value,str):
        try: value=json.loads(value)
        except (ValueError,TypeError): raise HTTPException(422,'菜谱 JSON 无法解析')
    if not isinstance(value,dict): raise HTTPException(422,'菜谱不是有效对象')
    name=value.get('dish_name',value.get('name'))
    steps=value.get('steps')
    if not isinstance(name,str) or not name.strip() or len(name)>120: raise HTTPException(422,'菜谱缺少有效菜名')
    if not isinstance(steps,list) or not 1<=len(steps)<=100: raise HTTPException(422,'菜谱没有有效步骤')
    normalized=[]
    for index,step in enumerate(steps):
        if isinstance(step,str): step={'text':step}
        if not isinstance(step,dict): raise HTTPException(422,'菜谱步骤结构无效')
        text=step.get('text',step.get('content'))
        duration=step.get('time_estimate',step.get('duration'))
        if not isinstance(text,str) or not text.strip() or len(text)>4000: raise HTTPException(422,'菜谱步骤内容无效')
        if duration not in (None,''):
            try: numeric=float(duration)
            except (TypeError,ValueError): raise HTTPException(422,'菜谱时长无效')
            if isinstance(duration,bool) or not math.isfinite(numeric) or not 0<=numeric<=86400: raise HTTPException(422,'菜谱时长无效')
            duration=numeric
        else: duration=None
        normalized.append({**step,'id':f'step-{index+1}','text':text.strip(),'time_estimate':duration})
    ingredients=value.get('ingredients_list')
    if not isinstance(ingredients,list): ingredients=[{'item':r['name'],'amount':r.get('amount'),'unit':r.get('unit')} for r in (value.get('ingredients') if isinstance(value.get('ingredients'),list) else []) if isinstance(r,dict) and isinstance(r.get('name'),str)]
    ingredients=[r for r in ingredients if isinstance(r,dict) and isinstance(r.get('item'),str)]
    result={**value,'schemaVersion':1,'dish_name':name.strip(),'steps':normalized,'ingredients_list':ingredients}
    if len(json.dumps(result,ensure_ascii=False))>100000: raise HTTPException(422,'菜谱内容过长')
    return result

def history(user):
    from app.main import USER_DATA_BASE
    rows=store.read(f'{USER_DATA_BASE}/{user}/chat_history.json',[])
    values=[]
    for row in rows:
        if not row.get('recipe'): continue
        try: recipe=normalize_recipe(row['recipe'])
        except HTTPException: continue
        values.append({'id':'chat-'+digest(recipe),'recipe':recipe,'source_version':digest(recipe)})
    for row in listing('completion',user):
        try: recipe=normalize_recipe(row['recipe'])
        except (HTTPException,KeyError): continue
        values.append({'id':row['id'],'recipe':recipe,'source_version':digest(recipe)})
    return list({r['id']:r for r in values}.values())

def sources(user,kind):
    if kind=='standard':
        from app.services.recommendation_service import load_recipes
        return [{'id':r['id'],'recipe':normalize_recipe(r),'source_version':digest(r)} for r in load_recipes()]
    if kind=='history': return history(user)
    if kind in ('favorite','copy'):
        return [{**r,'source_version':str(r['version'])} for r in listing('recipe_'+kind,user)]
    raise HTTPException(422,'来源类型不支持')

def source(user,kind,id):
    if kind=='community':
        post=read('post',id)
        if post.get('hidden') or post.get('deleted') or not post.get('recipe'): raise HTTPException(422,'帖子没有可复刻的有效菜谱')
        return {'id':id,'recipe':normalize_recipe(post['recipe']),'source_version':str(post['version'])}
    row=next((r for r in sources(user,kind) if r['id']==id),None)
    if row is None: raise HTTPException(404,'来源菜谱不存在或无权读取')
    return row

@router.get('/sources')
def catalog(request:Request,kind:Literal['standard','history','favorite','copy']='standard'):
    return {'items':sources(request.state.user_id,kind)}

def create_snapshot(user,body,kind):
    row=source(user,body.source_type,body.source_id)
    if row['source_version']!=body.expected_source_version: raise HTTPException(409,'来源菜谱已变化，请重新核对')
    recipe=normalize_recipe(row['recipe'])
    # Copies intentionally detach every nested value and retain the original source/version.
    snapshot=json.loads(json.dumps(recipe,ensure_ascii=False,allow_nan=False))
    return put('recipe_'+kind,new_id(),user,{'recipe':snapshot,'source':{'type':body.source_type,'id':body.source_id,'version':row['source_version']},'created_at':now()})

@router.post('/copies')
def clone(request:Request,body:Source):
    return execute(request.state.user_id,'recipe-clone',body,lambda:{'copy':create_snapshot(request.state.user_id,body,'copy')})

@router.post('/favorites')
def favorite(request:Request,body:Source):
    return execute(request.state.user_id,'recipe-favorite',body,lambda:{'favorite':create_snapshot(request.state.user_id,body,'favorite')})

@router.delete('/favorites/{id}')
def unfavorite(id:str,request:Request,body:VersionCommand):
    with store.transaction():
        row=read('recipe_favorite',id)
        if row['owner']!=request.state.user_id: raise HTTPException(403,'不能修改他人收藏')
        return execute(request.state.user_id,'recipe-unfavorite:'+id,body,lambda:(remove('recipe_favorite',id,body.expected_version) or {}))

@router.get('/copies/{id}/check')
def check(id:str,request:Request):
    with store.transaction():
        copy=read('recipe_copy',id)
        if copy['owner']!=request.state.user_id: raise HTTPException(403,'不能读取他人复刻')
        from app.main import USER_DATA_BASE,normalize_inventory_name,_public_freshfusion_result
        from datetime import datetime,timezone
        rows=store.read(f'{USER_DATA_BASE}/{request.state.user_id}/inventory.json',[]);evaluated=datetime.now(timezone.utc)
        assessed=[(row,_public_freshfusion_result(row,evaluated)) for row in rows if row.get('quantity',0)>0]
        required=[]
        for ingredient in copy['recipe']['ingredients_list']:
            name=ingredient['item'];matching=[(row,f) for row,f in assessed if normalize_inventory_name(row['name'])==normalize_inventory_name(name)]
            usable=[row for row,f in matching if not f['expired'] and f['fresh_score'] is not None]
            required.append({'name':name,'required_amount':ingredient.get('amount'),'unit':ingredient.get('unit'),'matched_batches':[row['id'] for row in usable], 'status':'needs_quantity_check' if usable else 'needs_freshness_check' if matching else 'missing'})
        return {'copy':copy,'ingredients':required,'evaluated_at':evaluated.isoformat(),'note':'现有计数与菜谱用量未自动换算；请逐项核对份量、日期和实物状态，再自行启动指导。'}
