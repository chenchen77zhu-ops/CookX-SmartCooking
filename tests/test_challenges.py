from datetime import datetime,timedelta,timezone
from test_sqlite_auth import sqlite_app,signup
from test_households import post
from test_growth import completion

def test_challenge_opt_in_progress_unique_events_imports_and_expiry(sqlite_app):
    c,_=sqlite_app;u,h=signup(c,'challenge-user');_,other=signup(c,'challenge-other')
    from app.domain.challenges import evaluate
    from app.storage import store
    assert all(r['participation'] is None for r in c.get('/api/v3/challenges',headers=h).json()['items'])
    post(c,h,'/growth/completions',**completion('before-join'))
    for id in ('first-meal-v1','week-three-v1','three-recipes-v1'):
        assert post(c,h,'/challenges/'+id+'/join').status_code==200
        assert post(c,h,'/challenges/'+id+'/join').json()['already_joined']
    post(c,h,'/growth/completions',**completion('old-import',provenance='confirmed_local_import'))
    assert c.get('/api/v3/challenges',headers=h).json()['items'][0]['participation']['progress']==0
    one=completion('one');post(c,h,'/growth/completions',**one);post(c,h,'/growth/completions',**one)
    rows=c.get('/api/v3/challenges',headers=h).json()['items'];assert rows[0]['participation']['status']=='completed' and rows[2]['participation']['progress']==1
    for id in ('two','three'):post(c,h,'/growth/completions',**completion(id))
    rows=c.get('/api/v3/challenges',headers=h).json()['items'];assert rows[1]['participation']['progress']==1 and rows[2]['participation']['status']=='completed'
    assert all(r['participation'] is None for r in c.get('/api/v3/challenges',headers=other).json()['items'])
    v,vh=signup(c,'challenge-expired');post(c,vh,'/challenges/week-three-v1/join')
    with store.transaction():expired=evaluate(v['id'],datetime.now(timezone.utc)+timedelta(days=8))
    assert expired[2]['participation']['status']=='ended'
    assert post(c,h,'/challenges/my-own/join').status_code==404
