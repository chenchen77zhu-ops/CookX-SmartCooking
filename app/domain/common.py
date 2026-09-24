"""Transactional building blocks for versioned multiuser business records."""
import json
import uuid
from datetime import datetime, timezone
from typing import Annotated
from fastapi import HTTPException
from pydantic import BaseModel, ConfigDict, Field
from app.storage import store, encode
from app.services.inventory_transactions import digest

Key=Annotated[str,Field(min_length=8,max_length=128,pattern=r'^[A-Za-z0-9_-]+$')]
class Command(BaseModel):
    model_config=ConfigDict(extra='forbid')
    idempotency_key:Key
class VersionCommand(Command):
    expected_version:int=Field(ge=1,strict=True)

def now(): return datetime.now(timezone.utc).isoformat()
def new_id(): return str(uuid.uuid4())
def read(kind,id):
    with store.transaction() as db:
        row=db.execute('SELECT * FROM entities WHERE kind=? AND id=?',(kind,id)).fetchone()
        if not row: raise HTTPException(404,'记录不存在')
        return {**json.loads(row['payload']),'id':row['id'],'version':row['version'],'owner':row['owner']}

def listing(kind,owner=None):
    with store.transaction() as db:
        query='SELECT id FROM entities WHERE kind=?';args=[kind]
        if owner is not None: query+=' AND owner=?';args.append(owner)
        return [read(kind,row['id']) for row in db.execute(query,args).fetchall()]

def put(kind,id,owner,payload,expected=None):
    payload={k:v for k,v in payload.items() if k not in ('id','version','owner')}
    with store.transaction() as db:
        if expected is None:
            db.execute('INSERT INTO entities(kind,id,owner,payload) VALUES(?,?,?,?)',(kind,id,owner,encode(payload)))
        else:
            changed=db.execute('UPDATE entities SET payload=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE kind=? AND id=? AND owner=? AND version=?',(encode(payload),kind,id,owner,expected)).rowcount
            if changed!=1: raise HTTPException(409,'数据已被修改，请刷新后重新确认')
        return read(kind,id)

def remove(kind,id,expected):
    with store.transaction() as db:
        if db.execute('DELETE FROM entities WHERE kind=? AND id=? AND version=?',(kind,id,expected)).rowcount!=1: raise HTTPException(409,'数据已被修改，请刷新')

def execute(actor,operation,command,action):
    """Authorization belongs inside action AND before execute, including replays."""
    body=command.model_dump(mode='json');key=body.pop('idempotency_key');fingerprint=digest({'operation':operation,'body':body})
    with store.transaction() as db:
        prior=db.execute('SELECT fingerprint,payload FROM receipts WHERE scope=? AND key=?',(actor,key)).fetchone()
        if prior:
            if prior['fingerprint']!=fingerprint: raise HTTPException(409,'该操作凭证已用于其他内容')
            return {**json.loads(prior['payload']),'replayed':True}
        result={'status':'success','schema_version':3,**action(),'replayed':False}
        db.execute('INSERT INTO receipts VALUES(?,?,?,?)',(actor,key,fingerprint,encode(result)))
        return result
