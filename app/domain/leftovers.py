"""Raw leftovers reference inventory; cooked leftovers never receive FreshScore."""
from datetime import datetime,timedelta,timezone
from typing import Literal
from fastapi import APIRouter,Request,HTTPException
from pydantic import Field,field_validator,model_validator
from app.storage import store
from app.domain.common import Command,VersionCommand,read,put,listing,execute,new_id,now
from app.domain.recipes import normalize_recipe
router=APIRouter(prefix='/api/v3/leftovers',tags=['Leftovers'])
GUIDANCE='https://www.fsis.usda.gov/food-safety/safe-food-handling-and-preparation/food-safety-basics/leftovers-and-food-safety'
NOTICE='记录筛选不是可食用性鉴定；熟食不使用生鲜 FreshScore。MLX90614 锅面测量不能核验食物内部温度。再加热不能重置原制作与储存时间。'

def stamp(value):
    if value is None:return None
    try: result=datetime.fromisoformat(value.replace('Z','+00:00'))
    except (ValueError,AttributeError): raise ValueError('时间格式无效')
    if result.tzinfo is None: raise ValueError('时间必须带时区')
    return result
class Create(Command):
    kind:Literal['raw','cooked']
    inventory_id:str|None=None
    cooked_type:Literal['rice','noodles','vegetables','meat','other']='other'
    name:str=Field('',max_length=80)
    quantity:float|None=Field(None,gt=0,allow_inf_nan=False,strict=True)
    unit:str|None=Field(None,max_length=16)
    made_at:str|None=None
    stored_at:str|None=None
    expiry_at:str|None=None
    storage_type:Literal['常温','冷藏','冷冻']|None=None
    cold_chain_confirmed:bool=False
    abnormal:bool=False
    notes:str=Field('',max_length=1000)
    @field_validator('name','unit')
    @classmethod
    def trimmed(cls,value):return value.strip() if isinstance(value,str) else value
    @field_validator('made_at','stored_at','expiry_at')
    @classmethod
    def dates(cls,value):stamp(value);return value
    @model_validator(mode='after')
    def valid(self):
        if self.kind=='raw' and not self.inventory_id:raise ValueError('请选择原料库存批次')
        if self.kind=='cooked' and not self.name.strip():raise ValueError('请填写熟食名称')
        made,stored,expiry=map(stamp,(self.made_at,self.stored_at,self.expiry_at));current=datetime.now(timezone.utc)
        if any(d and d>current+timedelta(seconds=60) for d in (made,stored)):raise ValueError('制作或储存时间不能在未来')
        if made and stored and stored<made:raise ValueError('储存时间不能早于制作')
        if made and expiry and expiry<=made:raise ValueError('到期时间必须晚于制作')
        return self
class Event(VersionCommand):
    cold_chain_confirmed:bool=False
    type:Literal['reheated','used','abnormal','discarded','restored']
    quantity:float|None=Field(None,gt=0,allow_inf_nan=False,strict=True)
    notes:str=Field('',max_length=1000)


def raw_batch(user,id):
    from app.main import USER_DATA_BASE
    return next((r for r in store.read(f'{USER_DATA_BASE}/{user}/inventory.json',[]) if str(r['id'])==id),None)

def assessment(row,user):
    reasons=[];current=datetime.now(timezone.utc)
    if row.get('abnormal'):reasons.append('已标记异常，不提供再利用建议')
    if row.get('closed'):reasons.append('此记录已结束')
    fresh=None
    if row['kind']=='raw':
        from app.main import _public_freshfusion_result
        batch=raw_batch(user,row['inventory_id'])
        if not batch or batch.get('quantity',0)<=0:reasons.append('原库存已不存在或已用完')
        else:
            fresh=_public_freshfusion_result(batch,current)
            if fresh['freshness_level'] in ('unknown','high_risk','expired'):reasons.append('原料鲜度信息不足、异常或已过期')
    else:
        made,stored,expiry=map(stamp,(row.get('made_at'),row.get('stored_at'),row.get('expiry_at')))
        if not all((made,stored,expiry,row.get('quantity'),row.get('unit'))):reasons.append('制作、储存、到期或计量信息不完整')
        if row.get('storage_type') not in ('冷藏','冷冻') or not row.get('cold_chain_confirmed'):reasons.append('连续低温储存条件未确认')
        # Conservative one-hour gate covers the USDA high-ambient-temperature limit.
        if made and stored and stored-made>timedelta(hours=1):reasons.append('未满足本版一小时内冷藏/冷冻的保守筛选条件')
        if expiry and expiry<=current:reasons.append('记录的到期时间已过')
        if made and row.get('storage_type')=='冷藏' and current-made>=timedelta(days=4):reasons.append('冷藏熟食已达到四天筛选上限')
    return {'eligible':not reasons,'reasons':reasons,'freshness':fresh,'disclaimer':NOTICE,'guidance_url':GUIDANCE,'evaluated_at':current.isoformat()}

@router.get('')
def get(request:Request):
    with store.transaction():return {'items':[{**r,'assessment':assessment(r,request.state.user_id)} for r in listing('leftover',request.state.user_id)]}

@router.post('')
def create(request:Request,body:Create):
    user=request.state.user_id
    def action():
        values=body.model_dump(exclude={'idempotency_key'})
        if body.kind=='raw':
            batch=raw_batch(user,body.inventory_id)
            if batch is None:raise HTTPException(404,'原料库存不存在')
            if any(r['kind']=='raw' and r['inventory_id']==body.inventory_id and not r.get('closed') for r in listing('leftover',user)):raise HTTPException(409,'该批次已有剩余原料记录')
            values.update(name=batch['name'],original_inventory=dict(batch),quantity=None,unit=None)
        row=put('leftover',new_id(),user,{**values,'created_at':now(),'events':[],'closed':False})
        return {'item':{**row,'assessment':assessment(row,user)}}
    return execute(user,'leftover-create',body,action)

@router.post('/{id}/events')
def event(id:str,request:Request,body:Event):
    user=request.state.user_id
    with store.transaction():
        row=read('leftover',id)
        if row['owner']!=user:raise HTTPException(403,'不能修改其他账号记录')
        def action():
            if row.get('closed'):raise HTTPException(409,'记录已结束')
            if body.type=='abnormal':row['abnormal']=True
            elif body.type=='discarded':row['closed']=True
            elif body.type=='used':
                if row['kind']=='raw':raise HTTPException(422,'原料数量请通过原库存的实际使用核对扣减')
                if body.quantity is None or row.get('quantity') is None or body.quantity>row['quantity']:raise HTTPException(422,'请核对实际使用数量')
                row['quantity']=round(row['quantity']-body.quantity,6);row['closed']=row['quantity']==0
            elif body.type=='reheated':
                if row['kind']!='cooked':raise HTTPException(422,'只有熟食记录再加热事件')
                row['cold_chain_confirmed']=False
            elif body.type=='restored':
                latest=next((e for e in reversed(row['events']) if e['type']=='reheated'),None)
                if row['kind']!='cooked' or not latest or not body.cold_chain_confirmed:raise HTTPException(422,'请确认再加热后的连续低温储存条件')
                if datetime.now(timezone.utc)-stamp(latest['at'])>timedelta(hours=1):raise HTTPException(409,'再加热后已超出本版保守恢复窗口')
                row['cold_chain_confirmed']=True
            row['events'].append({'type':body.type,'at':now(),'quantity':body.quantity,'notes':body.notes})
            updated=put('leftover',id,user,row,body.expected_version)
            return {'item':{**updated,'assessment':assessment(updated,user)}}
        return execute(user,'leftover-event:'+id,body,action)

@router.get('/{id}/ideas')
def ideas(id:str,request:Request):
    user=request.state.user_id
    with store.transaction():
        row=read('leftover',id)
        if row['owner']!=user:raise HTTPException(403,'不能读取其他账号记录')
        result=assessment(row,user)
        if not result['eligible']:return {'ideas':[],'assessment':result}
        if row['kind']=='raw':
            from app.services.recommendation_service import load_recipes
            from app.main import normalize_inventory_name
            candidates=[r for r in load_recipes() if any(normalize_inventory_name(i['name'])==normalize_inventory_name(row['name']) for i in r['ingredients'])]
            from app.services.inventory_transactions import digest
            return {'ideas':[{'type':'standard','id':r['id'],'source_version':digest(r),'name':r['name']} for r in candidates[:5]],'assessment':result}
        variations={
            'rice':('剩饭蔬菜炒饭','另备已核查的新鲜蔬菜；先处理蔬菜，再把本次份量的米饭拨散并均匀炒热。'),
            'noodles':('剩面蔬菜汤面','另备新鲜蔬菜和汤底；先煮蔬菜，再加入本次份量的熟面，充分均匀加热。'),
            'vegetables':('熟蔬菜汤','把本次份量的熟蔬菜加入新汤底，分散块状食材，均匀加热。'),
            'meat':('熟肉蔬菜汤','另备新鲜蔬菜，先煮蔬菜，再加入切小的本次份量熟肉，均匀加热。'),
        }
        selected=variations.get(row.get('cooked_type'))
        if selected:
            return {'ideas':[{'type':'cooked-guidance','name':selected[0],'steps':['先核查原记录与实物；存在异常或不确定储存情况时不要尝试。',selected[1],'用食品温度计核验食物内部达到约 74 ℃，锅面读数不能替代；按个人忌口选择新增食材。','本建议不提供用量换算；记录实际使用量与再加热，原时间保持不变。']}],'assessment':result}
        # Process guidance instead of inventing a full recipe for an arbitrary cooked dish.
        return {'ideas':[{'type':'cooked-guidance','name':'分出本次需要的份量再加热','steps':['先核对连续储存与日期记录，存在疑问或异常时不要尝试。','只取本次需要的份量，按原菜适用的方法均匀再加热。','USDA 指引要求用食品温度计确认内部达到约 74 ℃；锅面读数不能替代。','记录本次再加热和实际使用量，剩余部分仍保留原制作时间。']}],'assessment':result}
