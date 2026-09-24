from datetime import datetime,timedelta,timezone
from test_sqlite_auth import sqlite_app,signup
from test_households import post,key

def completion(session='session-one',**extra):
    t=datetime.now(timezone.utc)
    return {'session_id':session,'recipe':{'dish_name':'番茄炒蛋','method':'炒','steps':['准备','翻炒']},'recipe_version':1,'started_at':(t-timedelta(minutes=10)).isoformat(),'completed_at':t.isoformat(),'confirmed':True,**extra}

def test_completed_session_deduplication_conflict_isolation_and_history(sqlite_app):
    c,_=sqlite_app;_,h=signup(c,'growth-a');_,other=signup(c,'growth-b')
    body=completion();r=post(c,h,'/growth/completions',**body);assert r.status_code==200,r.text
    assert post(c,h,'/growth/completions',**body).json()['already_recorded']
    changed={**body,'recipe':{**body['recipe'],'dish_name':'不同内容'}}
    assert post(c,h,'/growth/completions',**changed).status_code==409
    result=c.get('/api/v3/growth',headers=h).json();assert result['confirmed_count']==1 and result['recipe_count']==1 and result['methods']=={'炒':1}
    assert c.get('/api/v3/growth',headers=other).json()['confirmed_count']==0
    history=c.get('/api/v3/recipes/sources?kind=history',headers=h).json()['items'];assert len(history)==1
    recipe=post(c,h,'/recipes/copies',source_type='history',source_id=history[0]['id'],expected_source_version=history[0]['source_version']);assert recipe.status_code==200

def test_completion_requires_confirmation_valid_recipe_and_dates(sqlite_app):
    c,_=sqlite_app;_,h=signup(c,'growth-invalid')
    for overrides in ({'confirmed':False},{'recipe':{'dish_name':'缺步骤'}},{'completed_at':'2100-01-01T00:00:00Z'},{'started_at':'2026-01-01'}):
        r=post(c,h,'/growth/completions',**completion(**overrides));assert r.status_code==422,r.text
    assert c.get('/api/v3/growth',headers=h).json()['confirmed_count']==0
