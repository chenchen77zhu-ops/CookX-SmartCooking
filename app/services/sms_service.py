import random
import string
from datetime import datetime

# 模拟短信验证码存储（实际项目中应该使用 Redis 等）
sms_codes = {}

def generate_sms_code(length: int = 6) -> str:
    """生成短信验证码"""
    return ''.join(random.choices(string.digits, k=length))

def send_sms_code(phone: str) -> bool:
    """发送短信验证码（模拟）"""
    code = generate_sms_code()
    # 存储验证码，5 分钟有效
    sms_codes[phone] = {
        "code": code,
        "expires_at": datetime.now().timestamp() + 300
    }
    print(f"【智能厨房】您的验证码是：{code}，5 分钟内有效")
    return True

def verify_sms_code(phone: str, code: str) -> bool:
    """验证短信验证码"""
    if phone not in sms_codes:
        return False
    
    stored = sms_codes[phone]
    
    # 检查是否过期
    if datetime.now().timestamp() > stored["expires_at"]:
        del sms_codes[phone]
        return False
    
    return stored["code"] == code
