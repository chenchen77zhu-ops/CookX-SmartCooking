from test_sqlite_auth import sqlite_app,signup
from test_households import post,key

def test_one_vote_replays_toggle_and_stale_state(sqlite_app):
    c,_=sqlite_app;a,ha=signup(c,'voter-a');b,hb=signup(c,'voter-b')
    p=post(c,ha,'/community/posts',text='测试点赞',confirmed=True).json()['post'];path=f'/api/v3/community/posts/{p["id"]}/likes'
    body={'liked':True,'expected_version':0,'idempotency_key':key()}
    first=c.put(path,headers=ha,json=body);assert first.status_code==200 and first.json()['count']==1
    assert c.put(path,headers=ha,json=body).json()['replayed']
    assert c.put(path,headers=ha,json={**body,'idempotency_key':key()}).status_code==409
    assert c.put(path,headers=hb,json=body).json()['count']==2
    assert c.put(path,headers=ha,json={'liked':False,'expected_version':1,'idempotency_key':key()}).json()['count']==1
    assert c.get(path,headers=ha).json()=={'count':1,'liked':False,'version':2}
    assert c.get(path,headers=hb).json()['liked']
    assert c.get(path).status_code==401
