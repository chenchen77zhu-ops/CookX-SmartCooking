"""Account operations; legacy hashes upgrade only after a successful login."""
import hashlib
import hmac
import uuid
from datetime import datetime, timezone
from pathlib import Path
from functools import wraps
from argon2 import PasswordHasher
from argon2.exceptions import VerificationError, InvalidHashError
from app.storage import store as storage

USERS_FILE = str(Path(__file__).resolve().parents[1] / 'data/users/users.json')
_hasher = PasswordHasher(time_cost=2, memory_cost=19456, parallelism=1)

def atomic(function):
    @wraps(function)
    def wrapped(*args, **kwargs):
        with storage.transaction(): return function(*args, **kwargs)
    return wrapped

def hash_password(password): return _hasher.hash(password)

def verify_password(password, password_hash):
    if password_hash.startswith('$argon2id$'):
        try: return _hasher.verify(password_hash, password)
        except (VerificationError, InvalidHashError): return False
    return hmac.compare_digest(hashlib.sha256(password.encode()).hexdigest(), password_hash)

def public_user(user): return {k:v for k,v in user.items() if k != 'password_hash'}
def get_all_users(): return storage.read(USERS_FILE, [])
def find_user_by_username(username): return next((u for u in get_all_users() if u['nickname']==username),None)
def find_user_by_phone(phone): return next((u for u in get_all_users() if u['phone']==phone),None)

@atomic
def create_user(username, phone, nickname, password):
    if not nickname.strip() or len(nickname)>64: raise ValueError('昵称应为 1–64 个字符')
    if len(password)<8 or len(password)>256: raise ValueError('密码应为 8–256 个字符')
    users=get_all_users()
    if find_user_by_username(nickname): raise ValueError('昵称已存在')
    if phone and find_user_by_phone(phone): raise ValueError('手机号已被注册')
    now=datetime.now(timezone.utc).isoformat()
    user=dict(id=str(uuid.uuid4()), username=nickname, nickname=nickname, phone=phone,
              password_hash=hash_password(password), avatar=None, created_at=now, updated_at=now)
    storage.write(USERS_FILE,[*users,user])
    return public_user(user)

@atomic
def update_user(user_id, **kwargs):
    users=get_all_users()
    for user in users:
        if user['id']!=user_id: continue
        nickname=kwargs.get('nickname')
        phone=kwargs.get('phone')
        if nickname is not None:
            if not nickname.strip() or len(nickname)>64: raise ValueError('昵称无效')
            if any(u['id']!=user_id and u['nickname']==nickname for u in users): raise ValueError('昵称已存在')
            user['username']=nickname
        if phone and any(u['id']!=user_id and u['phone']==phone for u in users): raise ValueError('手机号已被注册')
        for key in ('nickname','phone','avatar'):
            if kwargs.get(key) is not None: user[key]=kwargs[key]
        user['updated_at']=datetime.now(timezone.utc).isoformat()
        storage.write(USERS_FILE,users)
        return public_user(user)

@atomic
def delete_user(user_id):
    users=get_all_users()
    if storage.is_sqlite:
        from app.domain.common import listing,put
        members=[m for m in listing('membership') if m['user_id']==user_id and m['active']]
        if any(m['role']=='admin' for m in members): raise ValueError('请先移交家庭管理员，再注销账户')
        for member in members: put('membership',member['id'],member['owner'],{**member,'active':False},member['version'])
    remaining=[u for u in users if u['id']!=user_id]
    if len(remaining)==len(users): return False
    storage.write(USERS_FILE,remaining)
    return True

@atomic
def authenticate_user(username,password):
    users=get_all_users()
    user=next((u for u in users if u['nickname']==username),None)
    if not user or not verify_password(password,user['password_hash']): return None
    if not user['password_hash'].startswith('$argon2id$') or _hasher.check_needs_rehash(user['password_hash']):
        user['password_hash']=hash_password(password)
        storage.write(USERS_FILE,users)
    return public_user(user)
