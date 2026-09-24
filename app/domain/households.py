"""Households keep personal inventory private; access is rechecked transactionally."""
import hashlib
import secrets
import time
from typing import Literal
from fastapi import APIRouter, Request, HTTPException
from pydantic import Field, field_validator
from app.storage import store
from app.domain.common import Command,VersionCommand,execute,read,put,listing,remove,new_id,now

router=APIRouter(prefix='/api/v3',tags=['Households'])
class FamilyCreate(Command): name:str=Field(min_length=1,max_length=80)
class Join(Command): code:str=Field(min_length=8,max_length=80)
class Transfer(Command):
    item_id:str=Field(min_length=1,max_length=128)
    expected_inventory_version:int=Field(ge=1,strict=True)
    confirmed:Literal[True]
class Stock(Command):
    name:str=Field(min_length=1,max_length=80)
    quantity:float=Field(gt=0,allow_inf_nan=False,strict=True)
    unit:Literal['库存计数','克','千克','毫升','升','个','根','份','瓣','片','袋','包']
    storage_type:Literal['常温','冷藏','冷冻']|None=None
    purchase_time:str|None=None
    add_time:str|None=None
    expiry_date:str|None=None
    shelf_life:float|None=Field(None,gt=0,allow_inf_nan=False)
    expected_version:int|None=Field(None,ge=1,strict=True)
    @field_validator('name')
    @classmethod
    def nonblank(cls,value):
        if not value.strip(): raise ValueError('名称不能为空')
        return value.strip()

def membership(family,user,admin=False):
    try: member=read('membership',family+':'+user)
    except HTTPException: raise HTTPException(403,'你已不在这个家庭中')
    if not member.get('active') or (admin and member['role']!='admin'): raise HTTPException(403,'没有此家庭操作权限')
    read('household',family)
    return member

def family_action(family,user,command,operation,action,admin=False):
    with store.transaction():
        membership(family,user,admin)
        return execute(user,operation,command,action)

def personal_snapshot(user):
    from app.main import USER_DATA_BASE
    path=f'{USER_DATA_BASE}/{user}/inventory.json'
    with store.transaction() as db:
        row=db.execute("SELECT version FROM documents WHERE owner=? AND kind='inventory.json'",(user,)).fetchone()
        return {'items':store.read(path,[]),'version':row[0] if row else 0}

@router.get('/personal-inventory')
def personal(request:Request): return personal_snapshot(request.state.user_id)

@router.get('/households')
def families(request:Request):
    with store.transaction():
        memberships=[m for m in listing('membership') if m['user_id']==request.state.user_id and m['active']]
        return {'items':[read('household',m['owner']) for m in memberships]}

@router.post('/households')
def create(request:Request,body:FamilyCreate):
    user=request.state.user_id
    def action():
        id=new_id();family=put('household',id,id,{'name':body.name,'created_at':now()})
        put('membership',id+':'+user,id,{'user_id':user,'role':'admin','active':True})
        return {'household':family}
    return execute(user,'family-create',body,action)

@router.get('/households/{family}')
def detail(family:str,request:Request):
    with store.transaction():
        me=membership(family,request.state.user_id)
        from app.main import _public_freshfusion_result
        from datetime import datetime, timezone
        evaluated=datetime.now(timezone.utc)
        inventory=[{**row,'freshness':{**_public_freshfusion_result(row,evaluated),'evaluated_at':evaluated.isoformat()}} for row in listing('family_inventory',family)]
        from app.models.user import get_all_users
        names={u['id']:u['nickname'] for u in get_all_users()}
        return {'household':read('household',family),'membership':me,'members':[{**m,'display_name':names.get(m['user_id'],'已注销用户')} for m in listing('membership',family) if m['active']],'inventory':inventory, 'invitations':listing('family_invite',family) if me['role']=='admin' else []}

@router.post('/households/{family}/invite')
def invite(family:str,request:Request,body:Command):
    def action():
        code=secrets.token_urlsafe(24)
        invitation=put('family_invite',hashlib.sha256(code.encode()).hexdigest(),family,{'expires_at':time.time()+7*86400,'active':True})
        return {'code':code,'invitation_id':invitation['id']}
    return family_action(family,request.state.user_id,body,'invite:'+family,action,True)

@router.delete('/households/{family}/invitations/{id}')
def revoke(family:str,id:str,request:Request,body:VersionCommand):
    def action():
        invitation=read('family_invite',id)
        if invitation['owner']!=family: raise HTTPException(404,'邀请不存在')
        put('family_invite',id,family,{**invitation,'active':False},body.expected_version)
        return {}
    return family_action(family,request.state.user_id,body,'revoke-invite:'+id,action,True)

@router.post('/households/join')
def join(request:Request,body:Join):
    def action():
        invitation=read('family_invite',hashlib.sha256(body.code.encode()).hexdigest())
        if not invitation['active'] or invitation['expires_at']<time.time(): raise HTTPException(409,'邀请已失效')
        family=invitation['owner'];read('household',family);user=request.state.user_id
        existing=next((m for m in listing('membership',family) if m['user_id']==user),None)
        if existing and existing['active']: raise HTTPException(409,'已加入这个家庭')
        put('membership',family+':'+user,family,{'user_id':user,'role':'member','active':True},existing['version'] if existing else None)
        put('family_invite',invitation['id'],family,{**invitation,'active':False},invitation['version'])
        return {'household':read('household',family)}
    with store.transaction():
        result=execute(request.state.user_id,'join',body,action)
        membership(result['household']['id'],request.state.user_id)
        return result

@router.delete('/households/{family}/members/{member_id}')
def leave(family:str,member_id:str,request:Request,body:VersionCommand):
    user=request.state.user_id
    def action():
        member=read('membership',family+':'+member_id)
        if member['role']=='admin': raise HTTPException(409,'管理员需保留家庭管理权；不能移除管理员')
        put('membership',member['id'],family,{**member,'active':False},body.expected_version)
        return {}
    return family_action(family,user,body,'remove-member:'+family+':'+member_id,action,user!=member_id)

@router.post('/households/{family}/transfer')
def transfer(family:str,request:Request,body:Transfer):
    user=request.state.user_id
    def action():
        from app.main import USER_DATA_BASE
        snapshot=personal_snapshot(user)
        if snapshot['version']!=body.expected_inventory_version: raise HTTPException(409,'个人库存已变化，请重新确认转入批次')
        item=next((i for i in snapshot['items'] if str(i['id'])==body.item_id),None)
        if item is None: raise HTTPException(404,'批次不存在')
        new=new_id()
        result=put('family_inventory',new,family,{**item,'unit':'库存计数','personal_source_id':item['id'],'transferred_by':user,'transferred_at':now()})
        store.write(f'{USER_DATA_BASE}/{user}/inventory.json',[i for i in snapshot['items'] if str(i['id'])!=body.item_id])
        return {'item':result}
    return family_action(family,user,body,'transfer:'+family,action)

def stock_payload(body):
    from app.main import InventoryCreateItem
    # Reuse original time/storage validation without changing the FreshFusion formula.
    value=body.model_dump(exclude={'idempotency_key','expected_version'},exclude_none=True)
    value.setdefault('add_time',now())
    InventoryCreateItem.model_validate({**value,'quantity':1})
    value['measurement_note']='数量与单位由用户确认；库存计数不能自动换算成重量。'
    return value

@router.post('/households/{family}/inventory')
def add_stock(family:str,request:Request,body:Stock):
    def action(): return {'item':put('family_inventory',new_id(),family,stock_payload(body))}
    return family_action(family,request.state.user_id,body,'family-stock-add:'+family,action)

@router.put('/households/{family}/inventory/{id}')
def update_stock(family:str,id:str,request:Request,body:Stock):
    def action():
        item=read('family_inventory',id)
        if item['owner']!=family: raise HTTPException(404,'批次不存在')
        if body.expected_version is None: raise HTTPException(422,'需要库存版本')
        value=stock_payload(body)
        if body.add_time is None: value['add_time']=item.get('add_time')
        return {'item':put('family_inventory',id,family,value,body.expected_version)}
    return family_action(family,request.state.user_id,body,'family-stock-update:'+id,action)

@router.delete('/households/{family}/inventory/{id}')
def delete_stock(family:str,id:str,request:Request,body:VersionCommand):
    def action():
        item=read('family_inventory',id)
        if item['owner']!=family: raise HTTPException(404,'批次不存在')
        remove('family_inventory',id,body.expected_version);return {}
    return family_action(family,request.state.user_id,body,'family-stock-delete:'+id,action)

class AdminTransfer(VersionCommand):
    member_id:str
    expected_member_version:int=Field(ge=1,strict=True)

@router.post('/households/{family}/administrator')
def administrator(family:str,request:Request,body:AdminTransfer):
    user=request.state.user_id
    def action():
        me=membership(family,user,True);target=membership(family,body.member_id)
        if target['user_id']==user: raise HTTPException(409,'请选择另一位成员')
        put('membership',target['id'],family,{**target,'role':'admin'},body.expected_member_version)
        put('membership',me['id'],family,{**me,'role':'member'},body.expected_version)
        return {}
    return family_action(family,user,body,'administrator:'+family,action,True)
