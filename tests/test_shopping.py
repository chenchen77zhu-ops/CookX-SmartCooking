import uuid
from concurrent.futures import ThreadPoolExecutor
from test_sqlite_auth import sqlite_app,signup
from test_households import household,add_member,post,key

def test_quantity_merge_claim_conflict_purchase_separate_and_stock_retry(sqlite_app):
    c,_=sqlite_app;a,ha=signup(c,'shop-a');b,hb=signup(c,'shop-b');f=household(c,ha);add_member(c,ha,hb,f)
    root=f'/households/{f}/shopping'
    row=post(c,ha,root,name='番茄',quantity=2,unit='个').json()['item']
    merged=post(c,hb,root,name='西红柿',quantity=3,unit='个').json()['item'];assert merged['id']==row['id'] and merged['quantity']==5
    separate=post(c,ha,root,name='番茄',quantity=200,unit='克').json()['item'];assert separate['id']!=row['id']
    with ThreadPoolExecutor(max_workers=2) as pool:
        results=list(pool.map(lambda h:post(c,h,root+'/'+row['id']+'/state',expected_version=merged['version'],action='claim'),[ha,hb]))
    assert sorted(r.status_code for r in results)==[200,409]
    winner=next(h for h,r in zip([ha,hb],results) if r.status_code==200)
    claimed=next(r.json()['item'] for r in results if r.status_code==200)
    bought=post(c,winner,root+'/'+row['id']+'/state',expected_version=claimed['version'],action='bought').json()['item']
    assert c.get(f'/api/v3/households/{f}',headers=ha).json()['inventory']==[]
    body={'idempotency_key':key(),'name':'番茄','quantity':4.5,'unit':'个','storage_type':'冷藏','shelf_life':2.5,'expected_version':bought['version']}
    path='/api/v3'+root+'/'+row['id']+'/stock-in'
    success=c.post(path,headers=winner,json=body);assert success.status_code==200,success.text
    assert c.post(path,headers=winner,json=body).json()['replayed']
    assert c.post(path,headers=winner,json={**body,'idempotency_key':key()}).status_code==409
    inventory=c.get(f'/api/v3/households/{f}',headers=hb).json()['inventory'];assert len(inventory)==1 and inventory[0]['quantity']==4.5

def test_recipe_generation_unknown_unit_and_membership(sqlite_app):
    c,_=sqlite_app;a,ha=signup(c,'generate-a');_,hx=signup(c,'generate-x');f=household(c,ha);root=f'/households/{f}/shopping'
    reply=post(c,ha,root+'/generate',source_type='recipe',source_id='recipe_001',multiplier=2)
    assert reply.status_code==200,reply.text
    items=c.get('/api/v3'+root,headers=ha).json()['items'];assert next(r for r in items if r['name']=='鸡蛋')['quantity']==6
    unknown=post(c,ha,root,name='酱料',quantity=None,unit='待确认').json()['item'];assert unknown['quantity'] is None
    assert c.get('/api/v3'+root,headers=hx).status_code==403

def test_stockin_version_failure_rolls_back_created_stock(sqlite_app):
    c,_=sqlite_app;a,ha=signup(c,'rollback-stock');f=household(c,ha);root=f'/households/{f}/shopping'
    row=post(c,ha,root,name='盐',quantity=1,unit='袋').json()['item']
    bought=post(c,ha,root+'/'+row['id']+'/state',expected_version=1,action='bought').json()['item']
    response=post(c,ha,root+'/'+row['id']+'/stock-in',name='盐',quantity=500,unit='克',expected_version=1)
    assert response.status_code==409
    assert c.get(f'/api/v3/households/{f}',headers=ha).json()['inventory']==[]
    assert c.get('/api/v3'+root,headers=ha).json()['items'][0]['state']=='bought'
