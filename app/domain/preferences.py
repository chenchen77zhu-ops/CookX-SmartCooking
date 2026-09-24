"""Account-owned preferences with versioned updates; no global preference import."""
from typing import Literal
from fastapi import APIRouter,Request,HTTPException
from pydantic import BaseModel,ConfigDict,Field
from app.domain.common import Command,listing,put,execute,now
router=APIRouter(prefix='/api/v3/preferences',tags=['User preferences'])
class Values(BaseModel):
    model_config=ConfigDict(extra='forbid')
    taste:Literal['','清淡','咸香','酸甜','鲜香']=''
    spice:Literal['','不辣','微辣','中辣','较辣']=''
    duration:Literal['','under_30','30_to_60','any']=''
    dislikedIngredients:str=Field('',max_length=200)
class Criteria(BaseModel):
    model_config=ConfigDict(extra='forbid')
    budget:float|None=Field(None,gt=0,allow_inf_nan=False)
    difficulty_target:Literal['easy','medium','hard']|None=None
    max_calories_kcal:float|None=Field(None,gt=0,allow_inf_nan=False)
    min_protein_g:float|None=Field(None,gt=0,allow_inf_nan=False)
    max_fat_g:float|None=Field(None,gt=0,allow_inf_nan=False)
    max_carbohydrates_g:float|None=Field(None,gt=0,allow_inf_nan=False)
class Update(Command):
    expected_version:int=Field(ge=0,strict=True)
    values:Values|None=None
    recommendation:Criteria|None=None

def current(user):return next(iter(listing('user_preferences',user)),{'version':0,'values':Values().model_dump(),'recommendation':Criteria().model_dump()})
@router.get('')
def get(request:Request):return {'preferences':current(request.state.user_id)}
@router.put('')
def update(request:Request,body:Update):
    user=request.state.user_id
    def action():
        old=current(user)
        if old['version']!=body.expected_version:raise HTTPException(409,'偏好已在其他设备修改，请刷新后核对')
        return {'preferences':put('user_preferences',user,user,{'values':body.values.model_dump() if body.values is not None else old['values'],'recommendation':body.recommendation.model_dump() if body.recommendation is not None else old['recommendation'],'updated_at':now()},body.expected_version or None)}
    return execute(user,'preferences-save',body,action)

def scoring_preferences(user):
    import re
    values=current(user)['values']
    return {**{k:values[k] for k in ('taste','spice','duration') if values[k]},'disliked_ingredients':[v.strip() for v in re.split('[,，、\n]',values['dislikedIngredients']) if v.strip()]}
