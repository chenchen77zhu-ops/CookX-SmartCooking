from datetime import date,timedelta,datetime,timezone
from test_sqlite_auth import sqlite_app,signup
from test_households import post,key
from app.domain.planning_catalog import catalog,key as ingredient_key
from app.domain.planning_solver import solve,audit

def config(**kw):
    return {'start_date':(date.today()+timedelta(days=1)).isoformat(),'meals':[{'day':0,'meal':'lunch','side':False}],'people':1,'portion_factor':1,'budget':None,'max_minutes':120,'max_difficulty':2,'max_repeat':3,'avoid_ingredients':[],'dietary_restrictions':[],'nutrition':{},'locks':[],'family_id':None,'inventory_priority':True,'confirm_estimates':True,'timeout_seconds':5,'seed':77,'utc_offset_hours':8,**kw}
def all_prices():return [{'name':k.split('|')[0],'unit':k.split('|')[1],'price':.1} for k in sorted({ingredient_key(i['name'],i['unit']) for r in catalog() for i in r['ingredients']})]

def test_solver_hard_constraints_data_absence_and_infeasible():
    missing=solve(config(budget=100),[],[]);assert missing['solver_status']=='NEEDS_DATA'
    impossible=solve(config(max_minutes=1),[],[]);assert impossible['solver_status']=='INFEASIBLE'
    timeout=solve(config(timeout_seconds=.0000001),[],[]);assert timeout['solver_status']=='TIMEOUT'
    locked=solve(config(avoid_ingredients=['鸡蛋'],locks=[{'day':0,'meal':'lunch','role':'main','recipe_id':'recipe_001'}]),[],[]);assert not locked['meals'] and locked['solver_status']=='INFEASIBLE'
    ok=solve(config(budget=100,nutrition={'kcal':{'min':200,'max':2000}}),[],all_prices());assert ok['solver_status'] in ('OPTIMAL','FEASIBLE');audit(config(budget=100,nutrition={'kcal':{'min':200,'max':2000}}),ok,[])
    assert ok['total_estimated_cost']<=100 and 200<=ok['nutrition']['kcal']<=2000

def test_stock_not_reused_expired_and_units_are_not_converted():
    now=datetime.now(timezone.utc);inventory=[{'id':'rice','name':'大米','unit':'克','quantity':100,'add_time':now.isoformat(),'shelf_life':20},{'id':'expired','name':'鸡蛋','unit':'个','quantity':5,'expiry_date':(now-timedelta(days=1)).isoformat()},{'id':'unknown-unit','name':'大米','unit':'库存计数','quantity':1000,'add_time':now.isoformat(),'shelf_life':20}]
    cfg=config(meals=[{'day':i,'meal':'lunch','side':False} for i in (0,1)],locks=[{'day':i,'meal':'lunch','role':'staple','recipe_id':'plan-rice'} for i in (0,1)])
    result=solve(cfg,inventory,all_prices());assert result['solver_status'] in ('OPTIMAL','FEASIBLE');audit(cfg,result,inventory)
    assert sum(a['quantity'] for a in result['inventory_allocations'] if a['item_id']=='rice')==100
    assert not any(a['item_id'] in ('expired','unknown-unit') for a in result['inventory_allocations'])
    assert next(i for i in result['shopping_requirements'] if i['name']=='大米')['quantity']==50
    assert result['shopping_estimated_cost']<result['total_estimated_cost']

def test_menu_api_save_no_deduction_price_cas_replan_and_privacy(sqlite_app):
    c,_=sqlite_app;u,h=signup(c,'menu-user');_,other=signup(c,'menu-private')
    prices={'idempotency_key':key(),'expected_version':0,'items':all_prices()};r=c.put('/api/v3/planning/prices',headers=h,json=prices);assert r.status_code==200,r.text
    assert c.put('/api/v3/planning/prices',headers=h,json={**prices,'idempotency_key':key()}).status_code==409
    cfg=config();p=post(c,h,'/planning/preview',**cfg);assert p.status_code==200,p.text;preview=p.json()['preview'];assert preview['result']['meals']
    save=post(c,h,'/planning/menus',preview_id=preview['id'],expected_version=preview['version'],name='我的计划');assert save.status_code==200,save.text
    menu=save.json()['menu'];assert c.get('/api/inventory',headers=h,params={'user_id':u['id']}).json()==[]
    assert c.get('/api/v3/planning/menus/'+menu['id']+'/check',headers=other).status_code==403
    id=menu['id']+'|0|lunch|main';copy=post(c,h,'/recipes/copies',source_type='menu',source_id=id,expected_source_version=str(menu['version']));assert copy.status_code==200,copy.text
    assert copy.json()['copy']['recipe']['inventory_scope']=={'family_id':None}
    chosen=menu['result']['meals'][0]['dishes'][0]['recipe_id'];next_preview=post(c,h,'/planning/preview',**config(locks=[{'day':0,'meal':'lunch','role':'main','recipe_id':chosen}]));assert next_preview.status_code==200
    c.post('/api/add-to-inventory',headers=h,params={'user_id':u['id']},json=[{'name':'大米','quantity':1}])
    draft=next_preview.json()['preview'];assert post(c,h,'/planning/menus',preview_id=draft['id'],expected_version=1,name='过期快照').status_code==409

def test_family_menu_shopping_snapshot_changes_and_removed_member(sqlite_app):
    c,_=sqlite_app;u,h=signup(c,'menu-family');family=post(c,h,'/households',name='规划家庭').json()['household']['id']
    item=post(c,h,'/households/'+family+'/inventory',name='大米',unit='克',quantity=100,shelf_life=30).json()['item']
    draft=post(c,h,'/planning/preview',**config(family_id=family,locks=[{'day':0,'meal':'lunch','role':'staple','recipe_id':'plan-rice'}])).json()['preview']
    menu=post(c,h,'/planning/menus',preview_id=draft['id'],expected_version=1,name='家庭菜单').json()['menu']
    r=post(c,h,'/households/'+family+'/shopping/generate',source_type='menu',source_id=menu['id']);assert r.status_code==200,r.text
    copy=post(c,h,'/recipes/copies',source_type='menu',source_id=menu['id']+'|0|lunch|staple',expected_source_version='1').json()['copy']
    check=c.get('/api/v3/recipes/copies/'+copy['id']+'/check',headers=h).json();assert check['ingredients'][0]['matched_batches']==[item['id']]
    post(c,h,'/households/'+family+'/inventory',name='大米',unit='克',quantity=1,shelf_life=30)
    assert post(c,h,'/households/'+family+'/shopping/generate',source_type='menu',source_id=menu['id']).status_code==409

def test_seven_day_breakfast_side_locked_and_recomputed_constraints():
    meals=[{'day':d,'meal':m,'side':m=='dinner'} for d in range(7) for m in ('breakfast','lunch','dinner')]
    cfg=config(meals=meals,max_repeat=7,locks=[{'day':0,'meal':'breakfast','role':'main','recipe_id':'plan-breakfast-tofu'}],nutrition={'protein_g':{'min':100,'max':2000}},budget=10000,max_minutes=240)
    result=solve(cfg,[],all_prices());assert result['solver_status'] in ('OPTIMAL','FEASIBLE');audit(cfg,result,[])
    assert len(result['meals'])==21 and len(result['meals'][2]['dishes'])==3
    assert result['meals'][0]['dishes'][0]['recipe_id']=='plan-breakfast-tofu'
