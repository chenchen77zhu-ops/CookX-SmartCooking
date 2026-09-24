"""One durable award per account and badge definition, backed by event IDs."""
from fastapi import APIRouter,Request
from app.storage import store
from app.domain.common import listing,put,now
router=APIRouter(prefix='/api/v3/badges',tags=['Cooking badges'])
DEFINITIONS=[{'id':'first-confirmed-v1','name':'第一餐记录','description':'至少一次已确认完成记录'}, {'id':'recipe-explorer-v1','name':'菜谱探索','description':'确认完成三种不同菜谱'}, {'id':'week-challenge-v1','name':'一周坚持','description':'完成已加入的一周三次挑战'}]

def award(user):
    from app.domain.growth import events
    from app.domain.challenges import evaluate
    rows=events(user);challenges=evaluate(user);existing={r['badge_id']:r for r in listing('badge',user)}
    evidence={}
    if rows:evidence['first-confirmed-v1']=[rows[0]['id']]
    distinct={}
    for row in rows:distinct.setdefault(row['recipe_identity'],row['id'])
    if len(distinct)>=3:evidence['recipe-explorer-v1']=list(distinct.values())[:3]
    weekly=next(c for c in challenges if c['id']=='week-three-v1')['participation']
    if weekly and weekly['status']=='completed':evidence['week-challenge-v1']=[weekly['id']]
    for id,ids in evidence.items():
        if id not in existing:existing[id]=put('badge',user+':'+id,user,{'badge_id':id,'awarded_at':now(),'evidence_ids':ids,'rule_version':1})
    return [{**d,'award':existing.get(d['id'])} for d in DEFINITIONS]

@router.get('')
def get(request:Request):
    with store.transaction():return {'items':award(request.state.user_id),'note':'徽章仅记录参与里程碑，不是专业技能认证。每账号每种徽章只发放一次。'}
