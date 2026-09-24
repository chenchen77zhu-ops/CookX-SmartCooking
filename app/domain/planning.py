"""Versioned menu previews, user prices and immutable saved plan snapshots."""
from datetime import date,timedelta,datetime,timezone
from typing import Literal
from fastapi import APIRouter,Request,HTTPException
from pydantic import BaseModel,ConfigDict,Field,model_validator,field_validator
from app.storage import store
from app.domain.common import Command,VersionCommand,read,put,listing,execute,new_id,now
from app.domain.planning_catalog import catalog,nutrition,key
from app.domain.planning_solver import solve
from app.services.inventory_transactions import digest
from app.domain.households import membership,personal_snapshot
router=APIRouter(prefix='/api/v3/planning',tags=['Seven day menus'])
class Strict(BaseModel):model_config=ConfigDict(extra='forbid')
class Slot(Strict):
    day:int=Field(ge=0,le=6,strict=True)
    meal:Literal['breakfast','lunch','dinner']
    side:bool=False
class Lock(Slot):
    role:Literal['main','staple','side']
    recipe_id:str=Field(min_length=1,max_length=80)
class Bounds(Strict):
    min:float|None=Field(None,ge=0,le=1000000,allow_inf_nan=False,strict=True)
    max:float|None=Field(None,ge=0,le=1000000,allow_inf_nan=False,strict=True)
    @model_validator(mode='after')
    def valid(self):
        if self.min is None and self.max is None:raise ValueError('请填写营养上下限之一')
        if self.min is not None and self.max is not None and self.min>self.max:raise ValueError('营养下限不能超过上限')
        return self
class Plan(Command):
    start_date:date
    meals:list[Slot]=Field(min_length=1,max_length=21)
    people:int=Field(1,ge=1,le=20,strict=True)
    portion_factor:float=Field(1,ge=.25,le=3,allow_inf_nan=False,strict=True)
    budget:float|None=Field(None,ge=0,le=100000,allow_inf_nan=False,strict=True)
    max_minutes:int=Field(120,ge=1,le=600,strict=True)
    max_difficulty:int=Field(2,ge=1,le=3,strict=True)
    max_repeat:int=Field(3,ge=1,le=21,strict=True)
    avoid_ingredients:list[str]=Field(default_factory=list,max_length=100)
    dietary_restrictions:list[Literal['素食','纯素']]=Field(default_factory=list,max_length=2)
    nutrition:dict[Literal['kcal','protein_g','fat_g','carb_g'],Bounds]=Field(default_factory=dict)
    locks:list[Lock]=Field(default_factory=list,max_length=63)
    family_id:str|None=None
    inventory_priority:bool=True
    confirm_estimates:Literal[True]
    timeout_seconds:float=Field(10,gt=0,le=20,allow_inf_nan=False,strict=True)
    seed:int=Field(77,ge=0,le=2147483647,strict=True)
    utc_offset_hours:float=Field(8,ge=-12,le=14,allow_inf_nan=False,strict=True)
    @model_validator(mode='after')
    def valid(self):
        if self.start_date<date.today() or self.start_date>date.today()+timedelta(days=365):raise ValueError('请选择从今天起一年内的起始日期')
        slots={(s.day,s.meal) for s in self.meals}
        if len(slots)!=len(self.meals):raise ValueError('餐次不能重复')
        locks={(l.day,l.meal,l.role) for l in self.locks}
        if len(locks)!=len(self.locks):raise ValueError('同一餐次角色不能重复锁定')
        for l in self.locks:
            if (l.day,l.meal) not in slots or (l.role=='side' and not next(s.side for s in self.meals if s.day==l.day and s.meal==l.meal)):raise ValueError('锁定菜不在所选餐次中')
        return self
class Price(Strict):
    name:str=Field(min_length=1,max_length=80)
    unit:str=Field(min_length=1,max_length=16)
    price:float=Field(ge=0,le=10000,allow_inf_nan=False,strict=True)
    @model_validator(mode='after')
    def valid(self):
        if abs(self.price-round(self.price,4))>1e-9:raise ValueError('单价最多四位小数')
        self.name=self.name.strip();self.unit=self.unit.strip()
        if not self.name or not self.unit:raise ValueError('名称与单位不能为空')
        return self
class Prices(Command):
    expected_version:int=Field(ge=0,strict=True)
    items:list[Price]=Field(max_length=200)
class Save(Command):
    preview_id:str=Field(min_length=1,max_length=128)
    expected_version:int=Field(ge=1,strict=True)
    name:str=Field(min_length=1,max_length=80)

def price_settings(user):
    return next(iter(listing('planning_prices',user)),{'items':[],'version':0})
def inventory(user,family):
    if family:
        membership(family,user);items=listing('family_inventory',family)
        return items,digest(items)
    snap=personal_snapshot(user);return snap['items'],str(snap['version'])

@router.get('/catalog')
def get_catalog(request:Request):
    with store.transaction():return {'recipes':[{**r,'planning_nutrition':nutrition(r)} for r in catalog()],'prices':price_settings(request.state.user_id),'currency':'CNY','note':'营养为模板原料估算；人数份量及通用食材映射需用户确认，不用于自动换算库存。'}
@router.put('/prices')
def set_prices(request:Request,body:Prices):
    user=request.state.user_id
    def action():
        current=price_settings(user)
        if current['version']!=body.expected_version:raise HTTPException(409,'价格已变化，请刷新')
        if len({key(p.name,p.unit) for p in body.items})!=len(body.items):raise HTTPException(422,'同食材同单位价格不能重复')
        row=put('planning_prices',user,user,{'items':[p.model_dump() for p in body.items],'updated_at':now()},body.expected_version or None)
        return {'prices':row}
    return execute(user,'planning-prices',body,action)
@router.post('/preview')
def preview(request:Request,body:Plan):
    user=request.state.user_id
    # Read a consistent snapshot, run bounded solver outside the write lock, recheck before saving.
    with store.transaction():
        items,version=inventory(user,body.family_id);prices=price_settings(user)
    config=body.model_dump(mode='json',exclude={'idempotency_key'})
    result=solve(config,items,prices['items'])
    with store.transaction():
        current,current_version=inventory(user,body.family_id)
        if version!=current_version or prices['version']!=price_settings(user)['version']:raise HTTPException(409,'规划期间库存或价格已变化，请重新核对')
        def action():
            row=put('menu_preview',new_id(),user,{'config':config,'result':result,'inventory_version':version,'price_version':prices['version'],'created_at':now(),'model_version':'cpsat-menu-v1'})
            return {'preview':row}
        return execute(user,'menu-preview',body,action)
@router.post('/menus')
def save(request:Request,body:Save):
    user=request.state.user_id
    with store.transaction():
        draft=read('menu_preview',body.preview_id)
        if draft['owner']!=user:raise HTTPException(403,'不能保存其他账号菜单')
        if draft['version']!=body.expected_version:raise HTTPException(409,'预览已变化')
        _,version=inventory(user,draft['config']['family_id'])
        def action():
            if draft['result']['solver_status'] not in ('OPTIMAL','FEASIBLE'):raise HTTPException(422,'预览没有可保存的菜单')
            if version!=draft['inventory_version'] or price_settings(user)['version']!=draft['price_version']:raise HTTPException(409,'库存或价格已变化，请重算后保存')
            existing=next((m for m in listing('menu',user) if m.get('preview_id')==draft['id']),None)
            if existing:return {'menu':existing}
            return {'menu':put('menu',new_id(),user,{**draft,'name':body.name,'preview_id':draft['id'],'shopping_requirements':draft['result']['shopping_requirements'],'saved_at':now()})}
        return execute(user,'menu-save',body,action)
@router.get('/menus')
def menus(request:Request):
    with store.transaction():
        values=[]
        for row in listing('menu',request.state.user_id):
            try:inventory(request.state.user_id,row['config']['family_id'])
            except HTTPException:continue
            values.append(row)
        return {'items':values}
@router.get('/menus/{id}/check')
def check(id:str,request:Request):
    with store.transaction():
        row=read('menu',id)
        if row['owner']!=request.state.user_id:raise HTTPException(403,'不能读取其他账号菜单')
        items,version=inventory(request.state.user_id,row['config']['family_id'])
        from app.main import _public_freshfusion_result
        return {'menu':row,'inventory_changed':version!=row['inventory_version'],'price_changed':price_settings(request.state.user_id)['version']!=row['price_version'],'inventory':[{'item':b,'freshness':_public_freshfusion_result(b,datetime.now(timezone.utc))} for b in items],'note':'请核查当前库存、份量与鲜度。保存菜单不预留或扣减库存，其他人可能已使用；采购前库存变化须重新规划。'}
