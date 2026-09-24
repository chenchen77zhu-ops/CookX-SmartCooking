"""Bearer sessions and invitation-only registration for the local multiuser server."""
import hashlib
import json
import secrets
import time
from fastapi import APIRouter, HTTPException, Request
from app.storage import store

PUBLIC_PATHS={'/','/favicon.ico','/api/login','/api/register'}
router=APIRouter(prefix='/api/auth',tags=['Authentication'])
def token_hash(value): return hashlib.sha256(value.encode()).hexdigest()

def issue_session(user_id):
    token=secrets.token_urlsafe(32); expiry=time.time()+7*86400
    with store.transaction() as db:
        db.execute('DELETE FROM sessions WHERE expires_at<=?',(time.time(),))
        db.execute('INSERT INTO sessions VALUES(?,?,?)',(token_hash(token),user_id,expiry))
    return {'access_token':token,'token_type':'bearer','expires_at':expiry}

async def require_auth(request: Request):
    if request.url.path in PUBLIC_PATHS or request.method=='OPTIONS': return
    value=request.headers.get('authorization','')
    if not value.startswith('Bearer ') or len(value)>512: raise HTTPException(401,'请登录后继续')
    with store.transaction() as db:
        row=db.execute('SELECT s.user_id,a.payload FROM sessions s JOIN accounts a ON a.id=s.user_id WHERE token_hash=? AND expires_at>?', (token_hash(value[7:]),time.time())).fetchone()
    if row is None: raise HTTPException(401,'登录已失效，请重新登录')
    request.state.user_id=row['user_id'];request.state.user=json.loads(row['payload'])
    requested=[*request.query_params.getlist('user_id')]
    if 'user_id' in request.path_params: requested.append(request.path_params['user_id'])
    if request.headers.get('content-type','').split(';')[0]=='application/json':
        try: body=await request.json()
        except (ValueError,UnicodeError): body=None
        if isinstance(body,dict) and 'user_id' in body: requested.append(body['user_id'])
    if any(str(uid)!=row['user_id'] for uid in requested): raise HTTPException(403,'不能访问其他账号的数据')

def consume_invitation(code):
    with store.transaction() as db:
        changed=db.execute('UPDATE invitations SET remaining=remaining-1 WHERE code_hash=? AND remaining>0 AND expires_at>?',(token_hash(code),time.time())).rowcount
        if changed!=1: raise HTTPException(403,'邀请码无效、已使用或已过期')

def create_invitation(created_by, uses=1, days=7):
    code=secrets.token_urlsafe(18)
    with store.transaction() as db:
        db.execute('INSERT INTO invitations VALUES(?,?,?,?)',(token_hash(code),created_by,uses,time.time()+days*86400))
    return code

@router.get('/session')
def session(request:Request):
    return {'user':{k:v for k,v in request.state.user.items() if k!='password_hash'}}

@router.post('/logout')
def logout(request:Request):
    with store.transaction() as db:
        db.execute('DELETE FROM sessions WHERE token_hash=?',(token_hash(request.headers['authorization'][7:]),))
    return {'status':'success'}

@router.post('/invitations')
def invite(request:Request):
    if request.state.user.get('role')!='admin': raise HTTPException(403,'仅管理员可创建内测邀请码')
    return {'code':create_invitation(request.state.user_id),'expires_in_days':7}
