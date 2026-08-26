import os
import json
import hashlib
import uuid
from datetime import datetime
from typing import Optional, List
from pydantic import BaseModel

# 用户数据文件
USERS_FILE = "app/users.json"

class User(BaseModel):
    id: str
    username: str
    phone: str
    nickname: str
    password_hash: str
    avatar: Optional[str] = None
    created_at: str
    updated_at: str

def hash_password(password: str) -> str:
    """对密码进行哈希加密"""
    return hashlib.sha256(password.encode()).hexdigest()

def verify_password(password: str, password_hash: str) -> bool:
    """验证密码是否正确"""
    return hash_password(password) == password_hash

def get_all_users() -> List[dict]:
    """获取所有用户"""
    if not os.path.exists(USERS_FILE):
        return []
    try:
        with open(USERS_FILE, "r", encoding="utf-8") as f:
            return json.load(f)
    except:
        return []

def find_user_by_username(username: str) -> Optional[dict]:
    """根据用户名（昵称）查找用户"""
    users = get_all_users()
    for user in users:
        if user["nickname"] == username:  # 使用昵称作为用户名
            return user
    return None

def find_user_by_phone(phone: str) -> Optional[dict]:
    """根据手机号查找用户"""
    users = get_all_users()
    for user in users:
        if user["phone"] == phone:
            return user
    return None

def create_user(username: str, phone: str, nickname: str, password: str) -> dict:
    """创建新用户"""
    users = get_all_users()
    
    # 检查昵称是否已存在
    if find_user_by_username(nickname):
        raise ValueError("昵称已存在")
    
    # 检查手机号是否已被注册（如果有手机号）
    if phone and find_user_by_phone(phone):
        raise ValueError("手机号已被注册")
    
    # 创建用户，使用昵称作为用户名
    now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    user = {
        "id": str(uuid.uuid4())[:8],
        "username": nickname,  # 使用昵称作为用户名
        "phone": phone,
        "nickname": nickname,
        "password_hash": hash_password(password),
        "avatar": None,
        "created_at": now,
        "updated_at": now
    }
    
    users.append(user)
    
    # 保存到文件
    with open(USERS_FILE, "w", encoding="utf-8") as f:
        json.dump(users, f, ensure_ascii=False, indent=2)
    
    return user

def update_user(user_id: str, **kwargs) -> Optional[dict]:
    """更新用户信息"""
    users = get_all_users()
    
    for i, user in enumerate(users):
        if user["id"] == user_id:
            # 更新允许的字段
            allowed_fields = ["nickname", "phone", "avatar"]
            for field in allowed_fields:
                if field in kwargs and kwargs[field] is not None:
                    user[field] = kwargs[field]
            
            user["updated_at"] = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
            users[i] = user
            
            # 保存到文件
            with open(USERS_FILE, "w", encoding="utf-8") as f:
                json.dump(users, f, ensure_ascii=False, indent=2)
            
            return user
    
    return None

def delete_user(user_id: str) -> bool:
    """删除用户（注销）"""
    users = get_all_users()
    
    for i, user in enumerate(users):
        if user["id"] == user_id:
            users.pop(i)
            
            # 保存到文件
            with open(USERS_FILE, "w", encoding="utf-8") as f:
                json.dump(users, f, ensure_ascii=False, indent=2)
            
            return True
    
    return False

def authenticate_user(username: str, password: str) -> Optional[dict]:
    """验证用户登录"""
    user = find_user_by_username(username)
    if not user:
        return None
    
    if not verify_password(password, user["password_hash"]):
        return None
    
    # 返回不包含密码哈希的用户信息
    return {
        "id": user["id"],
        "username": user["username"],
        "phone": user["phone"],
        "nickname": user["nickname"],
        "avatar": user["avatar"],
        "created_at": user["created_at"],
        "updated_at": user["updated_at"]
    }
