from test_sqlite_auth import sqlite_app,signup
from test_households import post
from test_growth import completion

def test_badges_dedupe_relogin_restore_and_private_evidence(sqlite_app):
    c,_=sqlite_app;u,h=signup(c,'badge-one');_,other=signup(c,'badge-other')
    post(c,h,'/challenges/week-three-v1/join')
    first=completion('badge-first');post(c,h,'/growth/completions',**first)
    one=c.get('/api/v3/badges',headers=h).json()['items'][0]['award'];assert one
    post(c,h,'/growth/completions',**first)
    assert c.get('/api/v3/badges',headers=h).json()['items'][0]['award']==one
    for i in range(2):post(c,h,'/growth/completions',**completion('new-'+str(i),recipe={'dish_name':'新菜'+str(i),'steps':['制作']}))
    badges=c.get('/api/v3/badges',headers=h).json()['items'];assert all(b['award'] for b in badges)
    assert all(b['award'] is None for b in c.get('/api/v3/badges',headers=other).json()['items'])
    from app.storage import store
    with store.transaction() as db:assert db.execute("SELECT count(*) FROM entities WHERE kind='badge' AND owner=?",(u['id'],)).fetchone()[0]==3
    c.post('/api/auth/logout',headers=h)
    assert c.get('/api/v3/badges',headers=h).status_code==401
    token=c.post('/api/login',json={'username':'badge-one','password':'test-pass-123'}).json()['access_token']
    assert c.get('/api/v3/badges',headers={'Authorization':'Bearer '+token}).json()['items']==badges
