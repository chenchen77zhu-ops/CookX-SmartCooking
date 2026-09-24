"""Production SQLite + real sessions. Only external model providers are replaced."""
import hashlib
import json
import sqlite3
import sys
import time
import types
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import pytest
from fastapi.testclient import TestClient
from app.storage import store, database_path
from app.auth import create_invitation, token_hash
from app.migration import inspect_legacy, migrate, backup_database
from app.models import user as accounts

@pytest.fixture
def sqlite_app(monkeypatch,tmp_path):
    monkeypatch.setenv('COOKX_DATABASE',str(tmp_path/'runtime.sqlite3'))
    class FakeYOLO:
        names={}
        def __init__(self,*a,**k): pass
        def __call__(self,*a,**k): return []
    monkeypatch.setitem(sys.modules,'ultralytics',types.SimpleNamespace(YOLO=FakeYOLO))
    sys.modules.pop('app.main',None)
    import app.main as main
    store.initialize(ready=True)
    monkeypatch.setattr(main,'USER_DATA_BASE',str(tmp_path/'virtual'))
    with TestClient(main.app) as client:
        yield client,main

def signup(client,name):
    code=create_invitation('test')
    reply=client.post('/api/register',json={'nickname':name,'password':'test-pass-123','invitation_code':code})
    assert reply.json()['status']=='success',reply.text
    assert 'password_hash' not in reply.text
    user=reply.json()['user']
    login=client.post('/api/login',json={'username':name,'password':'test-pass-123'}).json()
    assert 'password_hash' not in json.dumps(login)
    return user,{'Authorization':'Bearer '+login['access_token']}

def test_real_identity_revocation_and_expiry(sqlite_app):
    c,main=sqlite_app
    a,ha=signup(c,'Alice');b,hb=signup(c,'Bob')
    assert c.get('/api/inventory',params={'user_id':a['id']}).status_code==401
    for path in (f"/api/user/{b['id']}",f"/api/users/{b['id']}/inventory/freshness",f"/api/inventory?user_id={b['id']}"):
        assert c.get(path,headers=ha).status_code==403
    assert c.post('/api/recommendations',headers=ha,json={'user_id':b['id']}).status_code==403
    assert c.get('/api/inventory',params={'user_id':a['id']},headers=ha).json()==[]
    assert c.post('/api/auth/logout',headers=ha).status_code==200
    assert c.get('/api/auth/session',headers=ha).status_code==401
    with store.transaction() as db: db.execute('UPDATE sessions SET expires_at=? WHERE user_id=?',(time.time()-1,b['id']))
    assert c.get('/api/auth/session',headers=hb).status_code==401

def test_invitation_atomic_and_legacy_hash_upgrade(sqlite_app):
    c,_=sqlite_app
    code=create_invitation('test')
    bad=c.post('/api/register',json={'nickname':'short','password':'1','invitation_code':code})
    assert bad.json()['status']=='error'
    body={'nickname':'legacy','password':'test-pass-123','invitation_code':code}
    user=c.post('/api/register',json=body).json()['user']
    assert c.post('/api/register',json={**body,'nickname':'second'}).json()['status']=='error'
    with store.transaction():
        users=accounts.get_all_users();users[0]['password_hash']=hashlib.sha256(b'test-pass-123').hexdigest();store.write(accounts.USERS_FILE,users)
    assert c.post('/api/login',json={'username':'legacy','password':'bad'}).json()['status']=='error'
    assert not accounts.get_all_users()[0]['password_hash'].startswith('$argon2id$')
    assert c.post('/api/login',json={'username':'legacy','password':'test-pass-123'}).json()['status']=='success'
    assert accounts.get_all_users()[0]['password_hash'].startswith('$argon2id$')

def test_real_inventory_freshness_concurrent_receipts_restart(sqlite_app):
    c,main=sqlite_app;a,h=signup(c,'cook');p={'user_id':a['id']}
    assert c.post('/api/add-to-inventory',params=p,headers=h,json=[{'name':'番茄','quantity':5,'shelf_life':2.1},{'name':'鸡蛋','quantity':2,'shelf_life':7}]).json()['status']=='success'
    rows=c.get('/api/inventory',params=p,headers=h).json();tomato=next(r for r in rows if r['name']=='西红柿')
    assert c.get(f"/api/users/{a['id']}/inventory/freshness",headers=h).json()['total_count']==2
    assert c.post('/api/recommendations',headers=h,json={'user_id':a['id']}).json()['recommendations']
    body={'user_id':a['id'],'idempotency_key':'same-completion-001','items':[{'item_id':tomato['id'],'quantity':2,'expected_quantity':5}]}
    with ThreadPoolExecutor(max_workers=4) as pool:
        replies=list(pool.map(lambda _:c.post('/api/inventory/consume',json=body,headers=h),range(4)))
    assert all(r.status_code==200 for r in replies)
    assert sum(not r.json()['replayed'] for r in replies)==1
    assert c.post('/api/inventory/consume',headers=h,json={**body,'idempotency_key':'different-completion'}).status_code==409
    # A fresh app/client reads durable receipt; no original file or dual write.
    assert not Path(main.USER_DATA_BASE).exists()
    with TestClient(main.app) as restart:
        receipt=restart.get('/api/inventory/consumption/same-completion-001',params=p,headers=h)
        assert receipt.json()['changes'][0]['after_quantity']==3
        assert next(r for r in restart.get('/api/inventory',params=p,headers=h).json() if r['id']==tomato['id'])['quantity']==3

def legacy_tree(tmp_path):
    source=tmp_path/'old';(source/'old1').mkdir(parents=True)
    (source/'users.json').write_text(json.dumps([{'id':'old1','nickname':'旧用户','username':'旧用户','phone':'','password_hash':hashlib.sha256(b'old-pass').hexdigest(),'avatar':None}]),encoding='utf-8')
    inventory=[{'id':'legacy-id','name':'番茄','quantity':4,'purchase_date':'2026-08-01 13:02:03','shelf_life':2.3}]
    (source/'old1/inventory.json').write_text(json.dumps(inventory),encoding='utf-8')
    return source,inventory

def test_migration_backup_counts_repeat_dates_and_rollback(monkeypatch,tmp_path):
    source,inventory=legacy_tree(tmp_path);monkeypatch.setenv('COOKX_DATABASE',str(tmp_path/'new.sqlite3'))
    before={p:p.read_bytes() for p in source.rglob('*.json')}
    result=migrate(source,tmp_path/'backups')
    assert result['accounts']==1 and result['documents']==1
    assert store.read(source/'old1/inventory.json')==inventory
    assert all(p.read_bytes()==data for p,data in before.items())
    assert migrate(source,tmp_path/'backups')['status']=='already_migrated'
    saved=tmp_path/'backup.sqlite3';backup_database(saved)
    with sqlite3.connect(saved) as db: assert db.execute('PRAGMA integrity_check').fetchone()[0]=='ok'
    # Invalid source rejected before target activation.
    monkeypatch.setenv('COOKX_DATABASE',str(tmp_path/'failed.sqlite3'))
    (source/'old1/inventory.json').write_text('[{"id":"x","quantity":true}]')
    with pytest.raises(ValueError): migrate(source,tmp_path/'backups')
    assert not database_path().exists()

def test_migration_recovers_prepared_receipt_without_mutating_source(monkeypatch,tmp_path):
    from app.services.inventory_transactions import digest
    source,inventory=legacy_tree(tmp_path);monkeypatch.setenv('COOKX_DATABASE',str(tmp_path/'prepared.sqlite3'))
    after=[{**inventory[0],'quantity':3}]
    record={'k':{'status':'prepared','fingerprint':'preserve','before_digest':digest(inventory),'after_inventory':after,'result':{'status':'success','idempotency_key':'k','changes':[]}}}
    file=source/'old1/inventory-consumption-receipts.json';file.write_text(json.dumps(record),encoding='utf-8');before=file.read_bytes()
    migrate(source,tmp_path/'backups')
    assert store.read(source/'old1/inventory.json')==after
    assert store.receipt(source/'old1/inventory.json','k')['replayed']
    assert file.read_bytes()==before

def test_transaction_failure_leaves_inventory_and_receipt_unchanged(sqlite_app,monkeypatch):
    c,main=sqlite_app;a,h=signup(c,'rollback');path=Path(main.USER_DATA_BASE)/a['id']/'inventory.json'
    store.write(path,[{'id':'batch','name':'番茄','quantity':3}])
    original=store.write
    def fail_journal(path,value):
        if str(path).endswith('inventory-consumption-receipts.json'): raise OSError('injected storage failure')
        return original(path,value)
    monkeypatch.setattr(store,'write',fail_journal)
    body={'user_id':a['id'],'idempotency_key':'rollback-case','items':[{'item_id':'batch','quantity':1,'expected_quantity':3}]}
    assert c.post('/api/inventory/consume',json=body,headers=h).status_code==500
    assert store.read(path)[0]['quantity']==3
    assert store.read(path.with_name('inventory-consumption-receipts.json'),{})=={}

def test_migration_import_failure_never_activates_database(monkeypatch,tmp_path):
    import app.migration as migration
    source,_=legacy_tree(tmp_path);monkeypatch.setenv('COOKX_DATABASE',str(tmp_path/'fail.sqlite3'))
    original=migration.encode
    def injected(value):
        if isinstance(value,list): raise ValueError('injected serialization failure')
        return original(value)
    monkeypatch.setattr(migration,'encode',injected)
    with pytest.raises(ValueError): migrate(source,tmp_path/'backups')
    with sqlite3.connect(database_path()) as db:
        assert db.execute('SELECT COUNT(*) FROM accounts').fetchone()[0]==0
        assert db.execute("SELECT 1 FROM meta WHERE key='ready'").fetchone() is None
    monkeypatch.setattr(migration,'encode',original)
    assert migrate(source,tmp_path/'backups')['status']=='migrated'
