import json
import pytest
from fastapi import HTTPException
from test_sqlite_auth import sqlite_app,signup
from test_households import post,key
from app.domain.recipes import normalize_recipe
from app.domain.common import put
from app.storage import store

def test_validated_independent_copy_favorite_and_no_deduction(sqlite_app):
    c,main=sqlite_app;a,ha=signup(c,'clone-a');b,hb=signup(c,'clone-b')
    source=c.get('/api/v3/recipes/sources',headers=ha).json()['items'][0]
    command={'source_type':'standard','source_id':source['id'],'expected_source_version':source['source_version']}
    copied=post(c,ha,'/recipes/copies',**command).json()['copy']
    assert copied['recipe']['steps'][0]['time_estimate'] is None
    checked=c.get('/api/v3/recipes/copies/'+copied['id']+'/check',headers=ha).json()
    assert checked['ingredients'] and all(i['status']=='missing' for i in checked['ingredients'])
    assert c.get('/api/v3/recipes/copies/'+copied['id']+'/check',headers=hb).status_code==403
    favorite=post(c,ha,'/recipes/favorites',**command).json()['favorite']
    assert c.get('/api/v3/recipes/sources?kind=favorite',headers=hb).json()['items']==[]
    recopy=post(c,ha,'/recipes/copies',source_type='favorite',source_id=favorite['id'],expected_source_version=str(favorite['version'])).json()['copy']
    assert recopy['id']!=copied['id'] and recopy['recipe']==copied['recipe']
    assert c.get('/api/inventory',params={'user_id':a['id']},headers=ha).json()==[]
    assert post(c,ha,'/recipes/copies',**{**command,'expected_source_version':'stale'}).status_code==409

def test_history_and_invalid_community_recipe(sqlite_app):
    c,main=sqlite_app;a,ha=signup(c,'history-a')
    store.write(f"{main.USER_DATA_BASE}/{a['id']}/chat_history.json",[{'role':'assistant','recipe':{'dish_name':'历史菜','steps':['检查食材']}}])
    source=c.get('/api/v3/recipes/sources?kind=history',headers=ha).json()['items'][0]
    assert post(c,ha,'/recipes/copies',source_type='history',source_id=source['id'],expected_source_version=source['source_version']).status_code==200
    put('post','invalid-post',a['id'],{'text':'只有图片文字','recipe':None})
    assert post(c,ha,'/recipes/copies',source_type='community',source_id='invalid-post',expected_source_version='1').status_code==422
    put('post','hidden-post',a['id'],{'hidden':True,'recipe':{'dish_name':'菜','steps':['步骤']}})
    assert post(c,ha,'/recipes/copies',source_type='community',source_id='hidden-post',expected_source_version='1').status_code==422

@pytest.mark.parametrize('recipe',[None,'{bad',{}, {'dish_name':'菜','steps':[]},{'dish_name':'菜','steps':[{}]},{'dish_name':'菜','steps':[{'text':'煮','time_estimate':-1}]},{'dish_name':'菜','steps':[{'text':'煮','time_estimate':True}]}])
def test_invalid_recipe_never_becomes_executable(recipe):
    with pytest.raises(HTTPException): normalize_recipe(recipe)
