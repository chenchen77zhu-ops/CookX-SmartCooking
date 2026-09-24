"""SQLite is the production store. FileStore is an explicit migration/test adapter."""
from contextlib import contextmanager
from contextvars import ContextVar
from pathlib import Path
import json
import os
import sqlite3
from fastapi import HTTPException
from app.services.inventory_transactions import atomic_json, read_json, inventory_session, digest

SCHEMA = '''
CREATE TABLE IF NOT EXISTS meta(key TEXT PRIMARY KEY, value TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS accounts(id TEXT PRIMARY KEY, username TEXT UNIQUE NOT NULL, payload TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS documents(owner TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE, kind TEXT NOT NULL, payload TEXT NOT NULL, version INTEGER NOT NULL DEFAULT 1, PRIMARY KEY(owner,kind));
CREATE TABLE IF NOT EXISTS sessions(token_hash TEXT PRIMARY KEY, user_id TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE, expires_at REAL NOT NULL);
CREATE TABLE IF NOT EXISTS invitations(code_hash TEXT PRIMARY KEY, created_by TEXT NOT NULL, remaining INTEGER NOT NULL CHECK(remaining>=0), expires_at REAL NOT NULL);
CREATE TABLE IF NOT EXISTS entities(kind TEXT NOT NULL, id TEXT NOT NULL, owner TEXT NOT NULL, payload TEXT NOT NULL, version INTEGER NOT NULL DEFAULT 1, updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY(kind,id));
CREATE INDEX IF NOT EXISTS entities_owner ON entities(kind,owner);
CREATE TABLE IF NOT EXISTS receipts(scope TEXT NOT NULL, key TEXT NOT NULL, fingerprint TEXT NOT NULL, payload TEXT NOT NULL, PRIMARY KEY(scope,key));
CREATE TABLE IF NOT EXISTS media(id TEXT PRIMARY KEY, owner TEXT NOT NULL, mime TEXT NOT NULL, content BLOB NOT NULL);
'''
_active = ContextVar('cookx_sqlite_transaction', default=None)

def database_path():
    return Path(os.environ.get('COOKX_DATABASE', Path(__file__).parent/'data'/'cookx.sqlite3')).resolve()

def encode(value):
    return json.dumps(value, ensure_ascii=False, allow_nan=False, separators=(',', ':'))

class SqliteStore:
    is_sqlite = True
    @contextmanager
    def transaction(self, ready=True):
        path = database_path()
        current = _active.get()
        if current and current[0] == path:
            yield current[1]
            return
        if not path.exists():
            raise HTTPException(503, '数据库未初始化，请运行 scripts/manage.py init 或 migrate')
        connection = sqlite3.connect(path, timeout=10)
        connection.row_factory = sqlite3.Row
        connection.execute('PRAGMA foreign_keys=ON')
        token = None
        try:
            connection.execute('BEGIN IMMEDIATE')
            if ready and not connection.execute("SELECT 1 FROM meta WHERE key='ready' AND value='1'").fetchone():
                raise HTTPException(503, '数据迁移尚未完成')
            token = _active.set((path, connection))
            yield connection
            connection.commit()
        except BaseException:
            connection.rollback()
            raise
        finally:
            if token is not None: _active.reset(token)
            connection.close()

    def initialize(self, ready=False):
        path=database_path();path.parent.mkdir(parents=True,exist_ok=True)
        with sqlite3.connect(path) as db:
            db.execute('PRAGMA journal_mode=WAL')
            db.executescript(SCHEMA)
            db.execute("INSERT OR IGNORE INTO meta VALUES('schema_version','1')")
            if ready: db.execute("INSERT OR IGNORE INTO meta VALUES('ready','1')")

    @staticmethod
    def identity(path):
        p=Path(path)
        return p.parent.name,p.name

    def read(self,path,default=None):
        with self.transaction() as db:
            owner,kind=self.identity(path)
            if kind=='users.json': return [json.loads(row[0]) for row in db.execute('SELECT payload FROM accounts ORDER BY rowid')]
            row=db.execute('SELECT payload FROM documents WHERE owner=? AND kind=?',(owner,kind)).fetchone()
            return json.loads(row[0]) if row else default

    def exists(self,path):
        if Path(path).name=='users.json': return True
        with self.transaction() as db:
            return db.execute('SELECT 1 FROM documents WHERE owner=? AND kind=?',self.identity(path)).fetchone() is not None

    def write(self,path,data):
        with self.transaction() as db:
            owner,kind=self.identity(path)
            if kind=='users.json':
                keep={row['id'] for row in data}
                for row in db.execute('SELECT id FROM accounts').fetchall():
                    if row[0] not in keep:
                        db.execute('DELETE FROM documents WHERE owner=?',(row[0],))
                        db.execute('DELETE FROM entities WHERE owner=?',(row[0],))
                        db.execute('DELETE FROM receipts WHERE scope=?',(row[0],))
                        db.execute('DELETE FROM media WHERE owner=?',(row[0],))
                        db.execute('DELETE FROM invitations WHERE created_by=?',(row[0],))
                        db.execute('DELETE FROM accounts WHERE id=?',(row[0],))
                for row in data:
                    db.execute('INSERT INTO accounts VALUES(?,?,?) ON CONFLICT(id) DO UPDATE SET username=excluded.username,payload=excluded.payload',(row['id'],row.get('nickname') or row['username'],encode(row)))
            else:
                db.execute('INSERT INTO documents(owner,kind,payload) VALUES(?,?,?) ON CONFLICT(owner,kind) DO UPDATE SET payload=excluded.payload,version=documents.version+1',(owner,kind,encode(data)))

    def session(self,path): return self.transaction()

    def consume(self,path,key,items,writer=None):
        with self.transaction():
            journal_path=Path(path).with_name('inventory-consumption-receipts.json')
            journal=self.read(journal_path,{})
            fingerprint=digest(sorted(items,key=lambda row:row['item_id']))
            if key in journal:
                record=journal[key]
                if record['fingerprint']!=fingerprint: raise HTTPException(409,'同一幂等键不能用于不同清单')
                return {**record['result'],'replayed':True}
            rows=self.read(path,[]);by_id={str(row['id']):row for row in rows}
            if len(by_id)!=len(rows): raise HTTPException(409,'库存 ID 重复')
            changes=[]
            for item in items:
                row=by_id.get(item['item_id'])
                if row is None or type(row.get('quantity')) is not int or row['quantity']!=item['expected_quantity'] or row['quantity']<item['quantity']:
                    raise HTTPException(409,'库存已变化，请重新读取实际清单')
                if not item.get('expected_revision'):raise HTTPException(428,'请使用支持批次版本核对的客户端')
                if item['expected_revision']!=digest(row):raise HTTPException(409,'批次已被修改，请刷新后重新核对')
                changes.append({'item_id':row['id'],'name':row['name'],'before_quantity':row['quantity'],'consumed_quantity':item['quantity'],'after_quantity':row['quantity']-item['quantity']})
            for change in changes: by_id[str(change['item_id'])]['quantity']=change['after_quantity']
            ids={str(c['item_id']) for c in changes}
            after=[row for row in rows if str(row['id']) not in ids or row['quantity']>0]
            result={'status':'success','schema_version':2,'idempotency_key':key,'changes':changes,'replayed':False}
            journal[key]={'status':'committed','fingerprint':fingerprint,'result':result}
            self.write(path,after);self.write(journal_path,journal)
            return result

    def receipt(self,path,key):
        value=self.read(Path(path).with_name('inventory-consumption-receipts.json'),{}).get(key)
        if value is None: raise HTTPException(404,'未找到原扣减凭证')
        return {**value['result'],'replayed':True}

class FileStore:
    """Only selected by explicit dependency injection in legacy compatibility tests."""
    is_sqlite=False
    def read(self,path,default=None): return read_json(path,default)
    def exists(self,path): return Path(path).exists()
    def write(self,path,data): atomic_json(path,data)
    def session(self,path): return inventory_session(path)
    @contextmanager
    def transaction(self): yield None

store=SqliteStore()
