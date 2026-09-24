"""Shared procurement; purchase markers never write inventory."""
from typing import Literal
from fastapi import APIRouter, Request, HTTPException
from pydantic import Field
from app.domain.common import Command,VersionCommand,read,put,listing,new_id,now
from app.domain.households import family_action,membership,Stock,stock_payload
from app.storage import store

router=APIRouter(prefix='/api/v3/households/{family}/shopping',tags=['Shopping'])
class Demand(Command):
    name:str=Field(min_length=1,max_length=80)
    quantity:float|None=Field(None,gt=0,allow_inf_nan=False)
    unit:str=Field(min_length=1,max_length=16)
class Generate(Command):
    source_type:Literal['recipe','menu']
    source_id:str=Field(min_length=1,max_length=128)
    multiplier:float=Field(1,gt=0,le=100,allow_inf_nan=False)
class Change(VersionCommand): action:Literal['claim','release','bought','cancel']
class StockIn(Stock): expected_version:int=Field(ge=1,strict=True)

def demand(family,user,name,quantity,unit,source):
    from app.main import normalize_inventory_name
    name=normalize_inventory_name(name)
    if not name: raise HTTPException(422,'食材名称不能为空')
    unit=unit.strip()
    if not unit: raise HTTPException(422,'单位不能为空，无法判断时请填写待确认')
    similar=next((r for r in listing('shopping',family) if r['state']=='open' and not r.get('claimed_by') and r['name']==name and r['unit']==unit and r['quantity'] is not None and quantity is not None),None)
    event={'type':'added','actor':user,'at':now(),'quantity':quantity,'source':source}
    if similar:
        value={**similar,'quantity':round(similar['quantity']+quantity,6),'history':[*similar['history'],event]}
        return put('shopping',similar['id'],family,value,similar['version'])
    return put('shopping',new_id(),family,{'name':name,'quantity':quantity,'unit':unit,'state':'open','claimed_by':None,'history':[event]})

@router.get('')
def items(family:str,request:Request):
    with store.transaction():
        membership(family,request.state.user_id)
        return {'items':listing('shopping',family)}

@router.post('')
def add(family:str,request:Request,body:Demand):
    return family_action(family,request.state.user_id,body,'shopping-add:'+family,lambda:{'item':demand(family,request.state.user_id,body.name,body.quantity,body.unit,'manual')})

@router.post('/generate')
def generate(family:str,request:Request,body:Generate):
    user=request.state.user_id
    def action():
        if body.source_type=='recipe':
            from app.services.recommendation_service import load_recipes
            recipe=next((r for r in load_recipes() if r['id']==body.source_id),None)
            if not recipe: raise HTTPException(404,'菜谱不存在')
            requirements=recipe.get('ingredients',[])
        else:
            menu=read('menu',body.source_id)
            if menu['owner']!=user: raise HTTPException(403,'不能读取其他账号的菜单')
            requirements=menu.get('shopping_requirements')
            if requirements is None: raise HTTPException(409,'菜单没有经过核对的采购需求，请重新规划')
        added=[]
        for r in requirements:
            quantity=r.get('amount',r.get('quantity'))
            if not isinstance(quantity,(int,float)) or isinstance(quantity,bool) or quantity<=0: quantity=None
            else: quantity=round(quantity*body.multiplier,6)
            added.append(demand(family,user,r['name'],quantity,r.get('unit') or '待确认',{'type':body.source_type,'id':body.source_id,'multiplier':body.multiplier}))
        return {'items':added,'note':'菜谱按原配方倍数生成；未自动假定份量，也未扣除无法换算的库存。菜单使用已核对的补购需求。'}
    return family_action(family,user,body,'shopping-generate:'+family,action)

@router.post('/{id}/state')
def state(family:str,id:str,request:Request,body:Change):
    user=request.state.user_id
    def action():
        row=read('shopping',id)
        if row['owner']!=family: raise HTTPException(404,'采购项不存在')
        if row['state']!='open': raise HTTPException(409,'该项目已结束或已购买，不能重复操作')
        if body.action=='claim':
            if row.get('claimed_by'): raise HTTPException(409,'已被认领，请与认领成员核对')
            row['claimed_by']=user
        else:
            if row.get('claimed_by') not in (None,user): raise HTTPException(409,'请由认领成员操作')
            if body.action=='release': row['claimed_by']=None
            elif body.action=='bought': row['state']='bought';row['bought_by']=user
            elif body.action=='cancel': row['state']='cancelled'
        row['history'].append({'type':body.action,'actor':user,'at':now()})
        return {'item':put('shopping',id,family,row,body.expected_version)}
    return family_action(family,user,body,'shopping-state:'+id,action)

@router.post('/{id}/stock-in')
def stock_in(family:str,id:str,request:Request,body:StockIn):
    user=request.state.user_id
    def action():
        row=read('shopping',id)
        if row['owner']!=family: raise HTTPException(404,'采购项不存在')
        if row['state']!='bought': raise HTTPException(409,'请先确认已购买，已入库的项目不能重复入库')
        item=put('family_inventory',new_id(),family,{**stock_payload(body),'procurement_id':id})
        row['state']='stocked';row['inventory_id']=item['id'];row['history'].append({'type':'stocked','actor':user,'at':now(),'quantity':body.quantity,'unit':body.unit})
        return {'item':put('shopping',id,family,row,body.expected_version),'inventory':item}
    return family_action(family,user,body,'shopping-stock:'+id,action)

@router.get('/sources')
def sources(family:str,request:Request):
    with store.transaction():
        membership(family,request.state.user_id)
        from app.services.recommendation_service import load_recipes
        return {'recipes':[{'id':r['id'],'name':r['name']} for r in load_recipes()], 'menus':[{'id':r['id'],'name':r.get('name','七日菜单')} for r in listing('menu',request.state.user_id)]}
