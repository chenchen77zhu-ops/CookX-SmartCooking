from test_sqlite_auth import sqlite_app,signup
from test_households import post,key

def test_family_consumption_receipt_precision_conflict_rollback_and_identity(sqlite_app):
    c,_=sqlite_app;u,h=signup(c,'consume-family-a');_,other=signup(c,'consume-family-b')
    family=post(c,h,'/households',name='使用核对家庭').json()['household']['id']
    a=post(c,h,'/households/'+family+'/inventory',name='大米',unit='克',quantity=100.5,shelf_life=30).json()['item']
    b=post(c,h,'/households/'+family+'/inventory',name='鸡蛋',unit='个',quantity=3,shelf_life=3).json()['item']
    body={'session_id':'session-a','items':[{'item_id':a['id'],'expected_version':1,'quantity':.5},{'item_id':b['id'],'expected_version':9,'quantity':1}]}
    assert post(c,h,'/households/'+family+'/consumption',**body).status_code==409
    rows=c.get('/api/v3/households/'+family,headers=h).json()['inventory'];assert next(r for r in rows if r['id']==a['id'])['quantity']==100.5
    body['items'][1]['expected_version']=1;tx={'idempotency_key':key(),**body};path='/api/v3/households/'+family+'/consumption'
    result=c.post(path,headers=h,json=tx);assert result.status_code==200,result.text
    assert c.post(path,headers=h,json=tx).json()['replayed']
    assert c.post(path,headers=other,json=tx).status_code==403
    proof=c.get(path+'/session-a',headers=h).json()['receipt'];assert proof['items'][0]['after']==100 and proof['items'][1]['after']==2
    assert post(c,h,'/households/'+family+'/consumption',**body).json()['receipt']['id']==proof['id']
    body['items'][0]['quantity']=1;assert post(c,h,'/households/'+family+'/consumption',**body).status_code==409

def test_preferences_shared_backend_version_and_recommendation_avoidance(sqlite_app):
    c,_=sqlite_app;u,h=signup(c,'preference-cloud-a');_,other=signup(c,'preference-cloud-b')
    initial=c.get('/api/v3/preferences',headers=h).json()['preferences'];assert initial['version']==0
    body={'idempotency_key':key(),'expected_version':0,'values':{'taste':'清淡','dislikedIngredients':'鸡蛋'}}
    r=c.put('/api/v3/preferences',headers=h,json=body);assert r.status_code==200,r.text
    assert c.put('/api/v3/preferences',headers=h,json={**body,'idempotency_key':key()}).status_code==409
    assert c.get('/api/v3/preferences',headers=other).json()['preferences']['values']['dislikedIngredients']==''
    c.post('/api/add-to-inventory',headers=h,params={'user_id':u['id']},json=[{'name':'鸡蛋','quantity':3,'shelf_life':3},{'name':'西红柿','quantity':2,'shelf_life':3}])
    rec=c.post('/api/recommendations',headers=h,json={'user_id':u['id'],'top_k':20,'preferences':{'disliked_ingredients':[]}}).json()
    assert not any('鸡蛋' in r['recipe_name'] for r in rec['recommendations'])
    assert c.get('/api/v3/preferences',headers=h).json()['preferences']['values']['taste']=='清淡'

def test_personal_inventory_revisions_and_insertion_receipts(sqlite_app):
    c,_=sqlite_app;u,h=signup(c,'personal-revisions');params={'user_id':u['id']};headers={**h,'Idempotency-Key':key()};body=[{'name':'鸡蛋','quantity':2,'shelf_life':3}]
    assert c.post('/api/add-to-inventory',headers=headers,params=params,json=body).status_code==200
    assert c.post('/api/add-to-inventory',headers=headers,params=params,json=body).json()['replayed']
    row=c.get('/api/inventory',headers=h,params=params).json()[0];assert row['quantity']==2
    path='/api/inventory/'+row['id']
    assert c.put(path,headers=h,params=params,json={'quantity':3}).status_code==428
    edit_headers={**h,'If-Match':row['_revision'],'Idempotency-Key':key()}
    assert c.put(path,headers=edit_headers,params=params,json={'storage_type':'冷藏'}).status_code==200
    assert c.put(path,headers=edit_headers,params=params,json={'storage_type':'冷藏'}).json()['replayed']
    assert c.put(path,headers={**edit_headers,'Idempotency-Key':key()},params=params,json={'quantity':3}).status_code==409
    old={'user_id':u['id'],'idempotency_key':key(),'items':[{'item_id':row['id'],'quantity':1,'expected_quantity':2,'expected_revision':row['_revision']}]}
    assert c.post('/api/inventory/consume',headers=h,json=old).status_code==409
    assert c.delete(path,headers={**h,'If-Match':row['_revision']},params=params).status_code==409
