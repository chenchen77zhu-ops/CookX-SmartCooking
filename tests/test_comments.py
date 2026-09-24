from test_sqlite_auth import sqlite_app,signup
from test_households import post,key
from app.models import user as accounts
from app.storage import store

def test_single_level_comments_author_admin_and_retry(sqlite_app):
    c,_=sqlite_app;a,ha=signup(c,'comment-a');b,hb=signup(c,'comment-b')
    p=post(c,ha,'/community/posts',text='分享作品',confirmed=True).json()['post'];root=f'/community/posts/{p["id"]}/comments'
    body={'text':'这道菜的步骤很清楚','idempotency_key':key()}
    r=c.post('/api/v3'+root,headers=hb,json=body);assert r.status_code==200
    comment=r.json()['comment'];assert c.post('/api/v3'+root,headers=hb,json=body).json()['replayed']
    assert len(c.get('/api/v3'+root,headers=ha).json()['items'])==1
    assert post(c,ha,root,text='不支持嵌套',parent_id=comment['id']).status_code==422
    command={'expected_version':comment['version'],'idempotency_key':key()}
    assert c.request('DELETE','/api/v3'+root+'/'+comment['id'],headers=ha,json=command).status_code==403
    with store.transaction():
        users=accounts.get_all_users();next(u for u in users if u['id']==a['id'])['role']='admin';store.write(accounts.USERS_FILE,users)
    assert c.request('DELETE','/api/v3'+root+'/'+comment['id'],headers=ha,json=command).status_code==200
    assert c.get('/api/v3'+root,headers=hb).json()['items']==[]
    post(c,ha,'/community/posts/'+p['id']+'/moderation',hidden=True,reason='隐藏后禁止评论',expected_version=1)
    assert post(c,hb,root,text='不能评论隐藏作品').status_code==404
