from datetime import datetime,timedelta,timezone
from test_sqlite_auth import sqlite_app,signup
from test_households import post
from app.domain.common import put
from app.domain.ranking import ranked

def test_window_distinct_commenters_ties_and_hidden(sqlite_app):
    c,_=sqlite_app;a,ha=signup(c,'rank-a');b,hb=signup(c,'rank-b');reference=datetime.now(timezone.utc)
    ids=[post(c,ha,'/community/posts',text='作品 '+str(i),confirmed=True).json()['post']['id'] for i in range(3)]
    for i,id in enumerate(ids):
        for j,user in enumerate([a['id'],b['id']]):
            put('like',id+':'+user,user,{'post_id':id,'liked':True,'liked_at':(reference-timedelta(days=8 if i==2 else 1)).isoformat()})
    for k in range(3):put('comment','comment-'+str(k),b['id'],{'post_id':ids[0],'text':'评论','created_at':reference.isoformat(),'deleted':False})
    result=ranked(reference)['items'];assert result[0]['id']==ids[0] and result[0]['hot']=={'likes':2,'commenters':1}
    assert result[-1]['id']==ids[2] and result[-1]['hot']['likes']==0
    put('post',ids[0],a['id'],{**result[0],'hidden':True},1)
    assert ids[0] not in [r['id'] for r in ranked(reference)['items']]
    assert c.get('/api/v3/community/hot').status_code==401
