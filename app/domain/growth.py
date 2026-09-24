"""Confirmed completion events, separate from recipe generation or inventory writes."""
from datetime import datetime,timezone,timedelta
from collections import Counter
from typing import Literal
from fastapi import APIRouter,Request,HTTPException
from pydantic import Field,field_validator,model_validator
from app.storage import store
from app.domain.common import Command,read,put,listing,execute,now
from app.domain.recipes import normalize_recipe
from app.services.inventory_transactions import digest
router=APIRouter(prefix='/api/v3/growth',tags=['Cooking growth'])
class Complete(Command):
    session_id:str=Field(min_length=1,max_length=128,pattern=r'^[A-Za-z0-9_-]+$')
    recipe:dict
    recipe_version:int=Field(ge=1,strict=True)
    started_at:str
    completed_at:str
    confirmed:Literal[True]
    provenance:Literal['confirmed_session','confirmed_local_import']='confirmed_session'
    @field_validator('started_at','completed_at')
    @classmethod
    def date(cls,value):
        try:d=datetime.fromisoformat(value.replace('Z','+00:00'))
        except ValueError:raise ValueError('完成时间格式无效')
        if d.tzinfo is None or d>datetime.now(timezone.utc)+timedelta(minutes=2):raise ValueError('时间须带时区且不能在未来')
        return value
    @model_validator(mode='after')
    def order(self):
        if datetime.fromisoformat(self.started_at)>datetime.fromisoformat(self.completed_at):raise ValueError('完成不能早于开始')
        return self

def events(user):return sorted(listing('completion',user),key=lambda r:r['completed_at'])

@router.post('/completions')
def complete(request:Request,body:Complete):
    user=request.state.user_id
    def action():
        recipe=normalize_recipe(body.recipe)
        if recipe.get('method') is not None and (not isinstance(recipe['method'],str) or len(recipe['method'])>80):raise HTTPException(422,'烹饪方法格式无效')
        payload=body.model_dump(exclude={'idempotency_key','confirmed'});payload['recipe']=recipe
        fingerprint=digest(payload);id=user+':'+body.session_id
        existing=next((r for r in events(user) if r['id']==id),None)
        if existing:
            if existing['fingerprint']!=fingerprint:raise HTTPException(409,'该会话已记录不同的完成内容，请保留原记录核对')
            return {'completion':existing,'already_recorded':True}
        # A semantic recipe identity ignores UI step IDs and timer edits.
        identity=digest({'name':recipe['dish_name'],'ingredients':recipe.get('ingredients_list',[]),'method':recipe.get('method')})
        row=put('completion',id,user,{**payload,'recipe_identity':identity,'fingerprint':fingerprint,'recorded_at':now()})
        from app.domain.badges import award
        award(user)
        return {'completion':row,'already_recorded':False}
    return execute(user,'cooking-complete',body,action)

@router.get('')
def summary(request:Request):
    with store.transaction():
        rows=events(request.state.user_id)
        methods=Counter(r['recipe'].get('method') or '未标注' for r in rows)
        timeline=Counter(datetime.fromisoformat(r['completed_at']).date().isoformat() for r in rows)
        return {'schema_version':3,'confirmed_count':len(rows),'recipe_count':len({r['recipe_identity'] for r in rows}),'methods':dict(methods),'timeline':dict(sorted(timeline.items())),'history':list(reversed(rows)),'note':'次数来自你确认并由服务端去重的完成记录，不代表专业技能认证；菜谱生成、计时和库存扣减不单独计次。'}
