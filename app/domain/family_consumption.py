"""Actual shared-batch consumption uses units, optimistic versions and durable receipts."""
from fastapi import APIRouter,Request,HTTPException
from pydantic import BaseModel,ConfigDict,Field,model_validator
from app.storage import store
from app.domain.common import Command,read,put,remove,listing,execute,now
from app.domain.households import membership
from app.services.inventory_transactions import digest
router=APIRouter(prefix='/api/v3/households/{family}/consumption',tags=['Shared consumption'])
class Item(BaseModel):
    model_config=ConfigDict(extra='forbid')
    item_id:str=Field(min_length=1,max_length=128)
    expected_version:int=Field(ge=1,strict=True)
    quantity:float=Field(gt=0,le=1000000,allow_inf_nan=False,strict=True)
class Consume(Command):
    session_id:str=Field(min_length=1,max_length=128,pattern=r'^[A-Za-z0-9_-]+$')
    items:list[Item]=Field(min_length=1,max_length=100)
    @model_validator(mode='after')
    def unique(self):
        if len({i.item_id for i in self.items})!=len(self.items):raise ValueError('同一批次只能出现一次')
        if any(abs(i.quantity-round(i.quantity,6))>1e-9 for i in self.items):raise ValueError('使用量最多六位小数')
        return self
@router.post('')
def consume(family:str,request:Request,body:Consume):
    user=request.state.user_id
    with store.transaction():
        membership(family,user)
        id=family+':'+user+':'+body.session_id
        def action():
            fingerprint=digest([i.model_dump() for i in body.items])
            old=next((r for r in listing('family_consumption',family) if r['id']==id),None)
            if old:
                if old['fingerprint']!=fingerprint:raise HTTPException(409,'该会话已扣减其他内容，请核对原凭证')
                return {'receipt':old}
            changed=[]
            for item in body.items:
                row=read('family_inventory',item.item_id)
                if row['owner']!=family:raise HTTPException(403,'库存不属于这个家庭')
                if row['version']!=item.expected_version or row['quantity']<item.quantity:raise HTTPException(409,'批次或数量已变化，请重新核对')
                after=round(row['quantity']-item.quantity,6)
                if after>0:put('family_inventory',row['id'],family,{**row,'quantity':after},item.expected_version)
                else:remove('family_inventory',row['id'],item.expected_version)
                changed.append({'item_id':row['id'],'name':row['name'],'unit':row['unit'],'quantity':item.quantity,'before':row['quantity'],'after':after,'before_version':item.expected_version})
            return {'receipt':put('family_consumption',id,family,{'session_id':body.session_id,'actor':user,'fingerprint':fingerprint,'items':changed,'recorded_at':now()})}
        return execute(user,'family-consume:'+family,body,action)
@router.get('/{session_id}')
def receipt(family:str,session_id:str,request:Request):
    with store.transaction():
        membership(family,request.state.user_id)
        return {'status':'success','receipt':read('family_consumption',family+':'+request.state.user_id+':'+session_id)}
