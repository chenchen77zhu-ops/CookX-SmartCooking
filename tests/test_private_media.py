from test_sqlite_auth import sqlite_app,signup
from test_community import picture
from app.storage import store
from app.models import user as accounts
from pathlib import Path
import uuid

def test_avatar_private_validation_and_legacy_owner(sqlite_app):
    c,_=sqlite_app;a,ha=signup(c,'avatar-a');b,hb=signup(c,'avatar-b')
    assert c.post('/api/upload-avatar',headers=ha,files={'file':('fake.png',b'<svg/>','image/png')}).status_code==422
    result=c.post('/api/upload-avatar',headers=ha,files={'file':('avatar.png',picture(),'image/png')});assert result.status_code==200,result.text
    url=result.json()['avatar_url'];assert c.get(url).status_code==401
    assert c.get(url,headers=hb).status_code==403
    assert c.get(url,headers=ha).headers['cache-control']=='private, no-store'
    assert c.put('/api/user/'+a['id'],headers=ha,params={'avatar':url}).json()['status']=='success'
    assert c.put('/api/user/'+b['id'],headers=hb,params={'avatar':url}).status_code==403
    assert c.put('/api/user/'+a['id'],headers=ha,params={'avatar':'https://unrelated.example/photo.png'}).status_code==422
    path=Path('app/static/uploads/avatars')/(uuid.uuid4().hex+'.png');path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(picture())
    try:
        legacy='/static/uploads/avatars/'+path.name
        accounts.update_user(a['id'],avatar=legacy)
        assert c.get(legacy).status_code==404 and c.get(legacy,headers=ha).status_code==404
        assert c.get('/api/v3/profile/avatar/legacy',headers=ha).status_code==200
        assert c.get('/api/v3/profile/avatar/legacy',headers=hb).status_code==404
    finally:path.unlink()
    assert c.delete('/api/user/'+a['id'],headers=ha).json()['status']=='success'
    with store.transaction() as db:assert db.execute('SELECT COUNT(*) FROM media WHERE owner=?',(a['id'],)).fetchone()[0]==0

def test_tts_private_audio_cannot_be_used_as_post_image(sqlite_app,monkeypatch):
    c,main=sqlite_app;a,ha=signup(c,'audio-a');_,hb=signup(c,'audio-b')
    filename=uuid.uuid4().hex+'.mp3';path=Path(main.AUDIO_DIR)/filename
    async def generated(text):path.write_bytes(b'ID3-test-audio');return filename
    monkeypatch.setattr(main,'generate_voice',generated)
    result=c.get('/api/tts',headers=ha,params={'text':'个人菜谱示例'});assert result.status_code==200,result.text
    url=result.json()['audio_url'];assert c.get(url,headers=ha).content==b'ID3-test-audio'
    assert c.get(url,headers=hb).status_code==403 and c.get(url).status_code==401
    assert not path.exists() and c.get('/static/audio/'+filename,headers=ha).status_code==404
    from test_households import post
    assert post(c,ha,'/community/posts',text='不是图片',image_ids=[url.rsplit('/',1)[1]],confirmed=True).status_code==403
