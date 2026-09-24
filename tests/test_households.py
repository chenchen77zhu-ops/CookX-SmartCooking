from concurrent.futures import ThreadPoolExecutor
import uuid
from test_sqlite_auth import sqlite_app,signup
from app.domain.common import listing
from app.storage import store

def key(): return str(uuid.uuid4())
def post(c,h,path,**body): return c.post('/api/v3'+path,headers=h,json={'idempotency_key':key(),**body})
def household(c,h): return post(c,h,'/households',name='测试家庭').json()['household']['id']
def add_member(c,ha,hb,family):
    code=post(c,ha,f'/households/{family}/invite').json()['code']
    assert post(c,hb,'/households/join',code=code).status_code==200

def test_private_transfer_replay_and_revocation(sqlite_app):
    c,main=sqlite_app;a,ha=signup(c,'family-a');b,hb=signup(c,'family-b');_,hc=signup(c,'outsider')
    family=household(c,ha);add_member(c,ha,hb,family)
    assert c.get(f'/api/v3/households/{family}',headers=hc).status_code==403
    c.post('/api/add-to-inventory',headers=ha,params={'user_id':a['id']},json=[{'name':'番茄','quantity':3,'shelf_life':2.3}])
    assert c.get(f'/api/v3/households/{family}',headers=hb).json()['inventory']==[]
    personal=c.get('/api/v3/personal-inventory',headers=ha).json();item=personal['items'][0]
    body={'idempotency_key':key(),'item_id':item['id'],'expected_inventory_version':personal['version'],'confirmed':True}
    first=c.post(f'/api/v3/households/{family}/transfer',headers=ha,json=body)
    assert first.status_code==200,first.text
    assert c.post(f'/api/v3/households/{family}/transfer',headers=ha,json=body).json()['replayed']
    assert c.get('/api/v3/personal-inventory',headers=ha).json()['items']==[]
    result=c.get(f'/api/v3/households/{family}',headers=hb).json();assert len(result['inventory'])==1
    shared=result['inventory'][0];assert shared['unit']=='库存计数' and shared['add_time']==item['add_time'] and shared['shelf_life']==2.3
    member=next(m for m in result['members'] if m['user_id']==b['id'])
    assert c.request('DELETE',f'/api/v3/households/{family}/members/{b["id"]}',headers=ha,json={'idempotency_key':key(),'expected_version':member['version']}).status_code==200
    assert c.get(f'/api/v3/households/{family}',headers=hb).status_code==403
    assert post(c,hb,f'/households/{family}/inventory',name='鸡蛋',quantity=2,unit='个').status_code==403

def test_invitation_revoke_and_single_join(sqlite_app):
    c,_=sqlite_app;a,ha=signup(c,'invite-a');b,hb=signup(c,'invite-b');family=household(c,ha)
    invite=post(c,ha,f'/households/{family}/invite').json()
    path=f'/api/v3/households/{family}/invitations/'+invite['invitation_id']
    assert c.request('DELETE',path,headers=ha,json={'idempotency_key':key(),'expected_version':1}).status_code==200
    assert post(c,hb,'/households/join',code=invite['code']).status_code==409

def test_concurrent_family_edits_and_stale_transfer(sqlite_app):
    c,_=sqlite_app;a,ha=signup(c,'concurrent-a');b,hb=signup(c,'concurrent-b');family=household(c,ha);add_member(c,ha,hb,family)
    created=post(c,ha,f'/households/{family}/inventory',name='面粉',quantity=1.5,unit='千克',storage_type='常温').json()['item']
    body={'idempotency_key':key(),'name':'面粉','quantity':1.25,'unit':'千克','expected_version':1}
    path=f'/api/v3/households/{family}/inventory/'+created['id']
    with ThreadPoolExecutor(max_workers=2) as pool:
        result=list(pool.map(lambda h:c.put(path,headers=h,json=body),[ha,hb]))
    assert sorted(r.status_code for r in result)==[200,409]
    winner=next(h for h,r in zip([ha,hb],result) if r.status_code==200)
    assert c.put(path,headers=winner,json=body).json()['replayed']
    assert c.get(f'/api/v3/households/{family}',headers=ha).json()['inventory'][0]['quantity']==1.25
    c.post('/api/add-to-inventory',headers=ha,params={'user_id':a['id']},json=[{'name':'鸡蛋','quantity':2}])
    snapshot=c.get('/api/v3/personal-inventory',headers=ha).json()
    c.post('/api/add-to-inventory',headers=ha,params={'user_id':a['id']},json=[{'name':'土豆','quantity':1}])
    assert post(c,ha,f'/households/{family}/transfer',item_id=snapshot['items'][0]['id'],expected_inventory_version=snapshot['version'],confirmed=True).status_code==409
    assert len(c.get('/api/v3/personal-inventory',headers=ha).json()['items'])==2

def test_transfer_administrator_and_account_deletion(sqlite_app):
    c,_=sqlite_app;a,ha=signup(c,'admin-a');b,hb=signup(c,'admin-b');family=household(c,ha);add_member(c,ha,hb,family)
    assert c.delete('/api/user/'+a['id'],headers=ha).json()['status']=='error'
    detail=c.get(f'/api/v3/households/{family}',headers=ha).json();member=next(m for m in detail['members'] if m['user_id']==b['id'])
    assert post(c,ha,f'/households/{family}/administrator',member_id=b['id'],expected_member_version=member['version'],expected_version=detail['membership']['version']).status_code==200
    assert c.delete('/api/user/'+a['id'],headers=ha).json()['status']=='success'
    assert c.get(f'/api/v3/households/{family}',headers=hb).json()['membership']['role']=='admin'
