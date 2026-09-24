"""Opt-in, predefined challenges driven by deduplicated server completions."""
from datetime import datetime,timedelta,timezone
from fastapi import APIRouter,Request,HTTPException
from app.storage import store
from app.domain.common import Command,put,listing,execute,now
from app.domain.growth import events
router=APIRouter(prefix='/api/v3/challenges',tags=['Cooking challenges'])
PRESETS=[{'id':'first-meal-v1','name':'起步一餐','description':'加入后确认完成一次烹饪','target':1,'metric':'count','days':None}, {'id':'three-recipes-v1','name':'尝试三种菜谱','description':'加入后确认完成三种不同菜谱','target':3,'metric':'recipes','days':None}, {'id':'week-three-v1','name':'一周三次','description':'加入后的七天内确认完成三次烹饪','target':3,'metric':'count','days':7}]

def evaluate(user,reference=None):
    current=reference or datetime.now(timezone.utc);rows=[]
    completions=events(user)
    for preset in PRESETS:
        joined=next((r for r in listing('challenge',user) if r['preset_id']==preset['id']),None)
        if joined and joined['status']=='active':
            start=datetime.fromisoformat(joined['joined_at']);end=datetime.fromisoformat(joined['ends_at']) if joined['ends_at'] else None
            relevant=[e for e in completions if e['provenance']=='confirmed_session' and start<=datetime.fromisoformat(e['completed_at'])<=current and start<=datetime.fromisoformat(e['recorded_at'])<=current and (not end or (datetime.fromisoformat(e['completed_at'])<end and datetime.fromisoformat(e['recorded_at'])<end))]
            progress=len({e['recipe_identity'] for e in relevant}) if preset['metric']=='recipes' else len(relevant)
            status='completed' if progress>=preset['target'] else 'ended' if end and current>=end else 'active'
            if progress!=joined['progress'] or status!=joined['status']:
                joined=put('challenge',joined['id'],user,{**joined,'progress':progress,'status':status,'completed_at':current.isoformat() if status=='completed' else None},joined['version'])
        rows.append({**preset,'participation':joined})
    return rows

@router.get('')
def get(request:Request):
    with store.transaction():return {'items':evaluate(request.state.user_id),'note':'仅加入后的服务端确认完成事件计入挑战；本机旧记录导入不计入。次数不是技能等级。'}

@router.post('/{id}/join')
def join(id:str,request:Request,body:Command):
    user=request.state.user_id;preset=next((p for p in PRESETS if p['id']==id),None)
    if not preset:raise HTTPException(404,'系统挑战不存在')
    def action():
        existing=next((r for r in listing('challenge',user) if r['preset_id']==id),None)
        if existing:return {'participation':existing,'already_joined':True}
        joined=datetime.now(timezone.utc)
        row=put('challenge',user+':'+id,user,{'preset_id':id,'joined_at':joined.isoformat(),'ends_at':(joined+timedelta(days=preset['days'])).isoformat() if preset['days'] else None,'progress':0,'status':'active','completed_at':None})
        return {'participation':row,'already_joined':False}
    return execute(user,'challenge-join:'+id,body,action)
