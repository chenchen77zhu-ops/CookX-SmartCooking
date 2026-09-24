from datetime import datetime,timedelta,timezone
from test_sqlite_auth import sqlite_app,signup
from test_households import post,key

def cooked(**overrides):
    now=datetime.now(timezone.utc)
    return {'kind':'cooked','name':'剩余炖菜','quantity':2,'unit':'份','made_at':(now-timedelta(hours=3)).isoformat(),'stored_at':(now-timedelta(hours=2,minutes=30)).isoformat(),'expiry_at':(now+timedelta(days=1)).isoformat(),'storage_type':'冷藏','cold_chain_confirmed':True,**overrides}

def test_unknown_abnormal_expired_and_overdue_leftovers_never_suggest(sqlite_app):
    c,_=sqlite_app;_,h=signup(c,'leftover-rejection')
    cases=[{'kind':'cooked','name':'不明剩菜'},cooked(abnormal=True),cooked(expiry_at=(datetime.now(timezone.utc)-timedelta(hours=1)).isoformat()),cooked(made_at=(datetime.now(timezone.utc)-timedelta(days=5)).isoformat(),stored_at=(datetime.now(timezone.utc)-timedelta(days=5)+timedelta(minutes=20)).isoformat())]
    for body in cases:
        r=post(c,h,'/leftovers',**body);assert r.status_code==200,r.text
        item=r.json()['item'];assert not item['assessment']['eligible'] and item['assessment']['freshness'] is None
        assert c.get('/api/v3/leftovers/'+item['id']+'/ideas',headers=h).json()['ideas']==[]

def test_reheat_preserves_original_time_requires_cold_chain_and_usage_idempotent(sqlite_app):
    c,_=sqlite_app;_,h=signup(c,'leftover-reheat');_,other=signup(c,'leftover-private')
    item=post(c,h,'/leftovers',**cooked()).json()['item'];assert item['assessment']['eligible']
    path='/leftovers/'+item['id']+'/events'
    reheated=post(c,h,path,type='reheated',expected_version=1).json()['item']
    assert reheated['made_at']==item['made_at'] and reheated['stored_at']==item['stored_at']
    assert not reheated['assessment']['eligible']
    restored=post(c,h,path,type='restored',expected_version=2,cold_chain_confirmed=True).json()['item'];assert restored['assessment']['eligible']
    body={'idempotency_key':key(),'type':'used','quantity':1,'expected_version':3}
    first=c.post('/api/v3'+path,headers=h,json=body);assert first.json()['item']['quantity']==1
    assert c.post('/api/v3'+path,headers=h,json=body).json()['replayed']
    assert c.get('/api/v3/leftovers/'+item['id']+'/ideas',headers=other).status_code==403

def test_raw_leftover_keeps_inventory_dates_and_rechecks_risk(sqlite_app):
    c,main=sqlite_app;u,h=signup(c,'raw-leftover')
    c.post('/api/add-to-inventory',headers=h,params={'user_id':u['id']},json=[{'name':'鸡蛋','quantity':2,'shelf_life':3}])
    original=c.get('/api/inventory',headers=h,params={'user_id':u['id']}).json()[0]
    item=post(c,h,'/leftovers',kind='raw',inventory_id=original['id']).json()['item']
    assert item['original_inventory']['add_time']==original['add_time']
    ideas=c.get('/api/v3/leftovers/'+item['id']+'/ideas',headers=h).json();assert ideas['ideas']
    c.delete('/api/inventory/'+original['id'],headers=h,params={'user_id':u['id']})
    assert c.get('/api/v3/leftovers/'+item['id']+'/ideas',headers=h).json()['ideas']==[]
