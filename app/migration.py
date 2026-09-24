"""Offline, backed-up and transactional import. Never modifies the JSON source."""
import copy
import hashlib
import json
import re
import shutil
import sqlite3
from datetime import datetime, timezone
from pathlib import Path
from app.storage import store, database_path, encode
from app.services.inventory_transactions import digest

SAFE_ID=re.compile(r'^[A-Za-z0-9_-]{1,128}$')
def inspect_legacy(source):
    source=Path(source).resolve()
    account_path=source/'users.json'
    if not account_path.is_file(): raise ValueError('找不到 users.json；请明确指定原账号目录')
    files={p.relative_to(source).as_posix():p.read_bytes() for p in source.rglob('*.json')}
    fingerprint=hashlib.sha256(b''.join(k.encode()+b'\0'+files[k]+b'\0' for k in sorted(files))).hexdigest()
    accounts=json.loads(files['users.json'].decode('utf-8-sig'))
    if not isinstance(accounts,list): raise ValueError('账号必须是数组')
    ids=set();names=set();phones=set()
    for row in accounts:
        if not isinstance(row,dict) or not SAFE_ID.fullmatch(str(row.get('id',''))): raise ValueError('账号 ID 非法')
        if row['id'] in ids or not row.get('nickname') or row['nickname'] in names: raise ValueError('账号 ID 或昵称重复/缺失')
        if row.get('phone') and row['phone'] in phones: raise ValueError('手机号重复')
        if not isinstance(row.get('password_hash'),str): raise ValueError('密码凭证缺失')
        ids.add(row['id']);names.add(row['nickname']);phones.add(row.get('phone'))
    documents={}
    for relative,raw in files.items():
        if relative=='users.json': continue
        parts=Path(relative).parts
        if len(parts)!=2 or parts[0] not in ids: raise ValueError(f'存在未知归属数据: {relative}')
        documents[(parts[0],parts[1])]=json.loads(raw.decode('utf-8-sig'))
    for uid in ids:
        inventory=documents.get((uid,'inventory.json'),[])
        if not isinstance(inventory,list): raise ValueError('库存必须是数组')
        batch_ids=set()
        for item in inventory:
            if not isinstance(item,dict) or not item.get('id') or str(item['id']) in batch_ids: raise ValueError('库存 ID 缺失或重复')
            if type(item.get('quantity')) is not int or item['quantity']<0: raise ValueError('旧库存计数无效')
            batch_ids.add(str(item['id']))
        journal=documents.get((uid,'inventory-consumption-receipts.json'),{})
        if not isinstance(journal,dict): raise ValueError('扣减凭证必须是对象')
        for entry in journal.values():
            if not isinstance(entry,dict) or entry.get('status') not in ('committed','prepared') or not all(k in entry for k in ('fingerprint','result')): raise ValueError('扣减凭证损坏')
            if entry['status']=='prepared':
                after=entry.get('after_inventory')
                if not isinstance(after,list): raise ValueError('待恢复凭证缺少库存')
                if digest(inventory)==entry.get('before_digest'): inventory=copy.deepcopy(after)
                elif digest(inventory)!=digest(after): raise ValueError('待恢复库存不一致，需要人工核对')
                entry['status']='committed';entry.pop('after_inventory',None)
                documents[(uid,'inventory.json')]=inventory
    return {'accounts':accounts,'documents':documents,'files':files,'fingerprint':fingerprint}

def migrate(source, backup_directory):
    snapshot=inspect_legacy(source)
    path=database_path()
    if path.exists():
        with sqlite3.connect(path) as db:
            ready=db.execute("SELECT value FROM meta WHERE key='ready'").fetchone()
            previous=db.execute("SELECT value FROM meta WHERE key='migration_fingerprint'").fetchone()
            if ready:
                if previous and previous[0]==snapshot['fingerprint']: return {'status':'already_migrated','fingerprint':previous[0]}
                raise ValueError('目标数据库已有数据，禁止覆盖或二次导入')
    backup=Path(backup_directory)/('json-'+datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ'))
    backup.mkdir(parents=True,exist_ok=False)
    for relative,data in snapshot['files'].items():
        destination=backup/relative;destination.parent.mkdir(parents=True,exist_ok=True);destination.write_bytes(data)
    if inspect_legacy(source)['fingerprint']!=snapshot['fingerprint']: raise ValueError('源文件在预检查后变化，请停止旧服务后重试')
    store.initialize()
    with store.transaction(ready=False) as db:
        if db.execute('SELECT COUNT(*) FROM accounts').fetchone()[0]: raise ValueError('目标账号表非空')
        for row in snapshot['accounts']:
            db.execute('INSERT INTO accounts VALUES(?,?,?)',(row['id'],row['nickname'],encode(row)))
        for (owner,kind),payload in snapshot['documents'].items():
            db.execute('INSERT INTO documents(owner,kind,payload) VALUES(?,?,?)',(owner,kind,encode(payload)))
        if db.execute('SELECT COUNT(*) FROM accounts').fetchone()[0]!=len(snapshot['accounts']): raise ValueError('账号数量核对失败')
        if db.execute('SELECT COUNT(*) FROM documents').fetchone()[0]!=len(snapshot['documents']): raise ValueError('文档数量核对失败')
        for key,value in [('ready','1'),('schema_version','1'),('migration_fingerprint',snapshot['fingerprint'])]:
            db.execute('INSERT OR REPLACE INTO meta VALUES(?,?)',(key,value))
    report={'status':'migrated','accounts':len(snapshot['accounts']),'documents':len(snapshot['documents']), 'fingerprint':snapshot['fingerprint'],'backup':str(backup)}
    (backup/'migration-report.txt').write_text(encode(report),encoding='utf-8')
    return report

def backup_database(destination):
    destination=Path(destination)
    if destination.exists(): raise ValueError('备份目标已存在')
    destination.parent.mkdir(parents=True,exist_ok=True)
    with sqlite3.connect(database_path()) as src, sqlite3.connect(destination) as target:
        src.backup(target)
        if target.execute('PRAGMA integrity_check').fetchone()[0]!='ok': raise ValueError('备份完整性检查失败')
    return {'status':'success','backup':str(destination)}
