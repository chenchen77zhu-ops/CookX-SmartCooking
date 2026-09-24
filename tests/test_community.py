import io
from PIL import Image
from test_sqlite_auth import sqlite_app,signup
from test_households import post,key
from app.models import user as accounts
from app.storage import store

def picture():
    output=io.BytesIO();Image.new('RGB',(30,20),'green').save(output,format='PNG');return output.getvalue()
def test_private_images_explicit_publish_edit_delete_and_ownership(sqlite_app):
    c,_=sqlite_app;a,ha=signup(c,'poster');b,hb=signup(c,'reader')
    upload=c.post('/api/v3/community/media',headers=ha,files={'file':('dish.png',picture(),'image/png')});assert upload.status_code==200
    id=upload.json()['id'];image='/api/v3/community/media/'+id
    assert c.get(image).status_code==401 and c.get(image,headers=hb).status_code==403
    assert c.post('/api/v3/community/media',headers=ha,files={'file':('fake.png',b'<svg/>','image/png')}).status_code==422
    assert post(c,ha,'/community/posts',text='缺少确认',image_ids=[id]).status_code==422
    assert post(c,hb,'/community/posts',text='不能偷用图片',image_ids=[id],confirmed=True).status_code==403
    body={'idempotency_key':key(),'text':'今天试做青菜','image_ids':[id],'confirmed':True}
    p=c.post('/api/v3/community/posts',headers=ha,json=body).json()['post']
    assert c.post('/api/v3/community/posts',headers=ha,json=body).json()['replayed']
    img=c.get(image,headers=hb);assert img.status_code==200 and img.headers['content-type']=='image/jpeg' and img.headers['cache-control']=='private, no-store'
    assert c.get('/api/v3/community/posts',headers=hb).json()['items'][0]['text']=='今天试做青菜'
    assert c.request('DELETE','/api/v3/community/posts/'+p['id'],headers=hb,json={'idempotency_key':key(),'expected_version':1}).status_code==403
    assert c.request('DELETE','/api/v3/community/posts/'+p['id'],headers=ha,json={'idempotency_key':key(),'expected_version':1}).status_code==200
    assert c.get('/api/v3/community/posts',headers=hb).json()['items']==[] and c.get(image,headers=hb).status_code==403

def test_report_admin_hide_and_recipe_snapshot_excludes_private_fields(sqlite_app):
    c,_=sqlite_app;a,ha=signup(c,'post-a');b,hb=signup(c,'post-admin')
    with store.transaction():
        users=accounts.get_all_users();next(u for u in users if u['id']==b['id'])['role']='admin';store.write(accounts.USERS_FILE,users)
    from app.domain.common import put
    recipe={'dish_name':'青菜','steps':['检查食材'],'temperature_logs':[999],'family_id':'private','used_ingredients':['我的私有库存']}
    copy=put('recipe_copy','my-copy',a['id'],{'recipe':recipe})
    p=post(c,ha,'/community/posts',text='我的菜',recipe_copy_id=copy['id'],confirmed=True).json()['post']
    assert 'temperature_logs' not in p['recipe'] and 'family_id' not in p['recipe'] and 'used_ingredients' not in p['recipe']
    assert post(c,ha,'/community/posts/'+p['id']+'/reports',reason='请求检查内容').status_code==200
    assert c.get('/api/v3/community/moderation',headers=ha).status_code==403
    assert len(c.get('/api/v3/community/moderation',headers=hb).json()['items'])==1
    assert post(c,hb,'/community/posts/'+p['id']+'/moderation',hidden=True,reason='核查中暂时隐藏',expected_version=1).status_code==200
    assert c.get('/api/v3/community/posts',headers=ha).json()['items']==[]
    assert c.get('/api/v3/community/posts?mine=true',headers=ha).json()['items'][0]['hidden']
    assert post(c,ha,'/recipes/copies',source_type='community',source_id=p['id'],expected_source_version='2').status_code==422
