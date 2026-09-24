from test_sqlite_auth import sqlite_app,signup

def test_database_backup_restore_to_new_path_never_overwrites_live_writes(sqlite_app,tmp_path):
    c,_=sqlite_app;u,h=signup(c,'restore-preserves')
    from app.migration import backup_database,restore_database
    from app.storage import database_path
    import sqlite3,pytest
    backup=tmp_path/'backup.sqlite3';restored=tmp_path/'restored.sqlite3';backup_database(backup)
    signup(c,'new-after-backup')
    result=restore_database(backup,restored);assert result['status']=='restored_to_new_path'
    with sqlite3.connect(restored) as db:assert db.execute('SELECT count(*) FROM accounts').fetchone()[0]==1
    with sqlite3.connect(database_path()) as db:assert db.execute('SELECT count(*) FROM accounts').fetchone()[0]==2
    with pytest.raises(ValueError):restore_database(backup,database_path())
    with pytest.raises(ValueError):restore_database(backup,restored)

def test_production_import_without_cloud_or_vision_still_serves_local_domains(tmp_path):
    import subprocess,os,sys
    script='''
import os
from fastapi.testclient import TestClient
from app.storage import store
store.initialize()
with store.transaction(ready=False) as db:db.execute("INSERT INTO meta VALUES('ready','1')")
from app.models.user import create_user
u=create_user('real-local','','real-local','test-pass-123')
from app.main import app
from app.services.deepseek_service import client
assert client is None
with TestClient(app) as c:
 login=c.post('/api/login',json={'username':'real-local','password':'test-pass-123'}).json()
 h={'Authorization':'Bearer '+login['access_token']}
 assert c.get('/api/v3/planning/catalog',headers=h).status_code==200
 assert c.get('/api/v3/growth',headers=h).json()['confirmed_count']==0
 assert c.get('/api/recommend-recipe',headers=h,params={'user_id':u['id'],'user_prompt':'测试'}).json()['status']=='error'
 print('production-local-domains-without-provider-credentials: passed')
'''
    env={**os.environ,'DEEPSEEK_API_KEY':'','DASHSCOPE_API_KEY':'','PYTHON_DOTENV_DISABLED':'1','COOKX_DATABASE':str(tmp_path/'real-local.sqlite3'),'PYTHONUTF8':'1'}
    run=subprocess.run([sys.executable,'-c',script],env=env,capture_output=True,text=True,encoding='utf8',timeout=60)
    assert run.returncode==0,run.stdout+run.stderr
