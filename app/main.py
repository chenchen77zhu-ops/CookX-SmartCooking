import os
import re
import io
import json
import shutil
import uuid
from datetime import datetime
from typing import List
from collections import Counter
from fastapi import FastAPI, UploadFile, File, HTTPException, Query
from fastapi import Response
from fastapi import Body
from fastapi.responses import StreamingResponse
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from ultralytics import YOLO
from PIL import Image
from pydantic import BaseModel
from datetime import datetime, timedelta
# 导入你自己的服务模块
from app.services.deepseek_service import get_recipe_suggestion
from app.services.tts_service import generate_voice
from app.services.qwen_service import get_ingredients_from_qwen
from app.models.user import (
    create_user, authenticate_user, update_user, delete_user,
    find_user_by_username, find_user_by_phone
)
from app.services.sms_service import send_sms_code, verify_sms_code
app = FastAPI(title="Smart Cooking API")
USER_DATA_BASE = "app/data/users"
# --- 1. 配置与初始化 ---
UPLOAD_DIR = "app/static/uploads"
AUDIO_DIR = "app/static/audio"
INVENTORY_FILE = "app/inventory.json"
CHAT_HISTORY_FILE = "app/chat_history.json"
for path in [UPLOAD_DIR, AUDIO_DIR]:
    os.makedirs(path, exist_ok=True)

# 初始化库存文件
if not os.path.exists(INVENTORY_FILE):
    with open(INVENTORY_FILE, "w", encoding="utf-8") as f:
        json.dump([], f)

class LoginRequest(BaseModel):
    username: str
    password: str

# 开启跨域
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 预加载模型 (建议放在全局避免重复加载)
model = YOLO('app/models/best.pt')

# 挂载静态资源
app.mount("/static", StaticFiles(directory="app/static"), name="static")
SHELF_LIFE_MAP = {
    'beef': 3,
    'chicken': 2,
    'egg': 15,
    'carrot': 10,
    'potato': 20,
    'onion': 30,
    'chili': 7,
    'garlic': 30,
    'kimchi': 60,
    'leek': 3
}
NAME_MAP = {
    'beef': '牛肉',
    'carrot': '胡萝卜',
    'chicken': '鸡肉',
    'chili': '辣椒',
    'egg': '鸡蛋',
    'garlic': '大蒜',
    'kimchi': '泡菜',
    'leek': '大葱',
    'onion': '洋葱',
    'potato': '土豆'
}
BROADCAST_PATH = "app/data/system_broadcast.json"

def update_expiration_notifications(user_id: str):
    inventory_path = get_user_path(user_id, "inventory.json")
    notify_path = get_user_path(user_id, "notifications.json")

    # 1. 读取该用户现有的通知
    user_notifications = []
    if os.path.exists(notify_path):
        with open(notify_path, "r", encoding="utf-8") as f:
            user_notifications = json.load(f)

    # 获取现有通知的 ID 集合，用于去重
    existing_ids = {n['id'] for n in user_notifications}
    new_found = False

    # 2. 【核心逻辑】：同步全局系统通知
    if os.path.exists(BROADCAST_PATH):
        with open(BROADCAST_PATH, "r", encoding="utf-8") as f:
            broadcasts = json.load(f)

        for b in broadcasts:
            # 如果这条全局公告的 ID 不在用户的个人消息里，就加进去
            if b['id'] not in existing_ids:
                user_notifications.insert(0, {
                    **b,  # 复制公告内容
                    "isRead": False  # 初始状态为未读
                })
                new_found = True

    # 3. 扫描食材过期逻辑 (保持你之前的代码)
    if os.path.exists(inventory_path):
        with open(inventory_path, "r", encoding="utf-8") as f:
            inventory = json.load(f)
        for item in inventory:
            # ... 计算过期天数逻辑 ...
            # title = f"{item['name']}即将过期"
            # if days_left <= 3 and title_not_in_existing:
            #     user_notifications.insert(0, {...})
            #     new_found = True
            pass

    # 4. 如果有新消息，保存回用户的文件夹
    if new_found:
        with open(notify_path, "w", encoding="utf-8") as f:
            json.dump(user_notifications[:50], f, ensure_ascii=False, indent=4)

def get_user_path(user_id: str, filename: str):
    """
    根据用户ID和文件名获取专属存储路径。
    会自动创建用户专属文件夹：app/data/users/{user_id}/
    """
    # 转换为字符串防止 user_id 是数字类型导致报错
    user_id_str = str(user_id)

    # 拼接用户目录：app/data/users/12345
    user_dir = os.path.join(USER_DATA_BASE, user_id_str)

    # 如果该用户的文件夹不存在，则立即创建它
    if not os.path.exists(user_dir):
        os.makedirs(user_dir, exist_ok=True)
        print(f"为用户 {user_id_str} 创建了专属文件夹")

    # 返回文件的完整路径：app/data/users/12345/inventory.json
    return os.path.join(user_dir, filename)

def get_current_inventory_names(user_id: str):
    # 使用之前定义的 get_user_path 函数
    path = get_user_path(user_id, "inventory.json")
    if not os.path.exists(path):
        return []
    try:
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
            # 获取名字列表供 AI 分析
            return list(set([item["name"] for item in data]))
    except:
        return []

def get_user_file_path(user_id: str, file_name: str):
    """为每个用户创建独立文件夹：app/data/users/{user_id}/inventory.json"""
    user_dir = os.path.join(USER_DATA_BASE, user_id)
    os.makedirs(user_dir, exist_ok=True) # 自动创建用户目录
    return os.path.join(user_dir, file_name)

@app.get("/")
async def root():
    return {"message": "SmartCooking API 运行中", "status": "online"}

@app.get("/favicon.ico", include_in_schema=False)
async def favicon():
    # 返回一个空的响应，避免 404 刷屏
    return Response(status_code=204)

@app.post("/api/add-to-inventory")
async def add_to_inventory(items: List[dict], user_id: str = Query(...)):
    """正式入库：按用户隔离，并强制补全日期"""
    try:
        # 1. 获取该用户的专属路径
        path = get_user_path(user_id, "inventory.json")

        # 2. 读取现有数据
        inventory_data = []
        if os.path.exists(path):
            with open(path, "r", encoding="utf-8") as f:
                try:
                    inventory_data = json.load(f)
                except:
                    inventory_data = []

        # 3. 处理新进入的食材
        for incoming in items:
            # 基础数据清洗
            name = incoming.get("name", "未知").strip().lower()
            if not name: continue

            # 核心修正：如果前端没传时间，后端强制生成当前的 ISO 时间
            # 这能彻底解决“保鲜期计算失败 (NaN)”的问题
            curr_time = datetime.now().isoformat()

            inventory_data.append({
                "id": str(uuid.uuid4())[:8],
                "name": name,
                "quantity": int(incoming.get("quantity", 1)),
                "add_time": curr_time,  # ✅ 统一时间格式
                "storage_type": incoming.get("storage_type", "冷藏"),
                "shelf_life": int(incoming.get("shelf_life", 7))
            })

        # 4. 覆盖写入
        with open(path, "w", encoding="utf-8") as f:
            json.dump(inventory_data, f, ensure_ascii=False, indent=4)

        print(f"用户 {user_id} 成功存入 {len(items)} 件食材到 {path}")
        return {"status": "success", "message": "已存入冰箱"}

    except Exception as e:
        print(f"存入失败报错: {e}")
        return {"status": "error", "message": str(e)}


@app.post("/api/analyze-fridge")
async def analyze_fridge(file: UploadFile = File(...)):
    try:
        # 1. 第一步：保存上传的图片
        temp_filename = f"{uuid.uuid4()}.jpg"
        temp_path = os.path.join(UPLOAD_DIR, temp_filename)
        with open(temp_path, "wb") as buffer:
            shutil.copyfileobj(file.file, buffer)

        # 2. 第二步：运行本地 YOLO 模型扫描
        results = model(
            temp_path,
            conf=0.5,  # 设定置信度
            iou=0.3,
            imgsz=640,
            agnostic_nms=True,
        )
        yolo_names = [model.names[int(c)] for r in results for c in r.boxes.cls]

        if len(yolo_names) == 0:
            # 彻底认不出来（yolo_names 为空）才请千问专家
            print("YOLO 未识别到任何食材，正在启动千问视觉引擎...")
            qwen_res = await get_ingredients_from_qwen(temp_path)
            # qwen_res 已经是 [{"name": "大白菜", ...}] 这种中文格式了
            detected_items = qwen_res.get("detected", [])
        else:
            print(f"YOLO 原始识别结果: {yolo_names}")
            detected_items = [
                {"name": NAME_MAP.get(name, name), "quantity": quantity}
                for name, quantity in Counter(yolo_names).items()
            ]
            print(f"YOLO 聚合结果: {detected_items}")
        # -------------------------------------------------------

        # 3. 第三步：统一为识别出的食材添加新鲜度评估（逻辑保持不变）
        for item in detected_items:
            name_hash = sum(ord(c) for c in item["name"])
            item["freshness"] = ['新鲜', '较新鲜', '一般'][name_hash % 3]

        # 4. 第四步：清理临时文件（可选）
        # os.remove(temp_path)

        return {"status": "success", "detected": detected_items}

    except Exception as e:
        print(f"识别接口异常: {e}")
        return {"status": "error", "message": str(e)}


@app.get("/api/recommend-recipe")
async def recommend_recipe(user_prompt: str, user_id: str, save_history: bool = True):
    # --- 1. 变量初始化防御 (防止 referenced before assignment) ---
    full_response = ""
    recipe_data = {}

    try:
        # --- 2. 强制中文指令注入 (解决英文回复问题) ---
        safe_prompt = user_prompt
        if "中文" not in safe_prompt:
            safe_prompt += " (请务必用简体中文详细回复，步骤要极其详细，像教小朋友一样)"

        # --- 3. 记录用户提问 ---
        if save_history:
            save_chat_message(user_id, "user", safe_prompt)

        # --- 4. 获取库存并调用 AI ---
        inventory = get_current_inventory_names(user_id)

        # 逐块接住生成器内容 (后端接完，不流向前端)
        async for chunk in get_recipe_suggestion(inventory, safe_prompt):
            full_response += chunk

        if not full_response:
            raise ValueError("AI 未返回任何内容")

        # --- 5. 强力 JSON 解析 ---
        import re
        # 匹配最外层的 {}
        json_match = re.search(r'(\{.*\})', full_response, re.DOTALL)
        clean_json = json_match.group(1) if json_match else full_response
        recipe_data = json.loads(clean_json)

        # --- 6. 核心补全：确保前端渲染不白屏 ---
        # 补全菜名
        if "dish_name" not in recipe_data:
            recipe_data["dish_name"] = "精选创意菜"

        # 补全营养成分
        if "nutrition" not in recipe_data:
            recipe_data["nutrition"] = {"calories": "200", "protein": "15", "fat": "10", "carbs": "20"}

        # 补全食材清单
        if "ingredients_list" not in recipe_data:
            recipe_data["ingredients_list"] = []

        # 补全缺失和消耗追踪
        if "missing" not in recipe_data:
            recipe_data["missing"] = []
        if "used_ingredients" not in recipe_data:
            recipe_data["used_ingredients"] = []

        # --- 7. 持久化：保存 AI 回答到历史记录 ---
        if save_history:
            ai_display_content = f"为您准备好了：{recipe_data['dish_name']}"
            save_chat_message(
                user_id,
                "assistant",
                ai_display_content,
                recipe=recipe_data,
                missing=recipe_data.get('missing', [])
            )
            print(f"✅ [DEBUG] 用户 {user_id} 的完整对话已持久化到文件")

        # --- 8. 返回给前端 ---
        return {"status": "success", "recipe": recipe_data}

    except Exception as e:
        print(f"❌ [ERROR] 推荐接口崩溃: {str(e)}")
        # 报错时也尝试存一条记录，防止对话流断开
        if save_history:
            save_chat_message(user_id, "assistant", "厨师长刚才走神了，没听清您的要求，能再说一遍吗？")
        return {"status": "error", "message": f"处理失败: {str(e)}"}

@app.post("/api/consume-ingredients")
async def consume_ingredients(used_items: List[str], user_id: str):
    """从库存中扣除已使用的食材"""
    path = get_user_path(user_id, "inventory.json")

    if not os.path.exists(path):
        return {"status": "error", "message": "库存文件不存在"}

    with open(path, "r", encoding="utf-8") as f:
        inventory_data = json.load(f)

    # 执行扣减逻辑
    new_inventory = []
    for item in inventory_data:
        # 匹配名称（统一转小写去空格）
        name_key = item["name"].strip().lower()

        if name_key in [i.strip().lower() for i in used_items]:
            # 如果在消耗名单里，数量减 1
            item["quantity"] = int(item.get("quantity", 1)) - 1
            # 如果减完后数量大于 0，保留；否则不加入 new_inventory (即删除)
            if item["quantity"] > 0:
                new_inventory.append(item)
        else:
            # 不在消耗名单里的，原样保留
            new_inventory.append(item)

    # 保存更新后的库存
    with open(path, "w", encoding="utf-8") as f:
        json.dump(new_inventory, f, ensure_ascii=False, indent=4)

    return {"status": "success", "remaining_count": len(new_inventory)}


@app.get("/api/inventory")
async def get_inventory(user_id: str):  # ✅ 必须有这个参数
    # 核心：去用户专属文件夹找 inventory.json
    path = get_user_path(user_id, "inventory.json")

    if not os.path.exists(path):
        return []

    try:
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
            # 为每项数据注入保质期信息 (保持你之前的逻辑)
            for item in data:
                item["shelf_life"] = SHELF_LIFE_MAP.get(item["name"].lower(), 7)
            return data
    except Exception as e:
        print(f"读取失败: {e}")
        return []


@app.delete("/api/inventory/{item_id}")
async def delete_item(item_id: str, user_id: str):  # ✅ 确保接收这两个参数
    try:
        # 1. 获取专属路径
        path = get_user_path(user_id, "inventory.json")

        if not os.path.exists(path):
            return {"status": "error", "message": "库存文件不存在"}

        # 2. 读取数据
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)

        # 3. 执行过滤（删除匹配 ID 的项）
        # 注意：确保 item["id"] 和传入的 item_id 类型一致（都是字符串）
        original_count = len(data)
        new_data = [item for item in data if str(item["id"]) != str(item_id)]

        # 4. 只有确实删除了才写文件（优化性能）
        if len(new_data) < original_count:
            with open(path, "w", encoding="utf-8") as f:
                json.dump(new_data, f, ensure_ascii=False, indent=4)
            print(f"用户 {user_id} 删除了食材 {item_id}")
            return {"status": "success"}
        else:
            return {"status": "error", "message": "未找到该食材记录"}

    except Exception as e:
        print(f"删除失败: {e}")
        return {"status": "error", "message": str(e)}


@app.put("/api/inventory/{item_id}")
async def update_item(item_id: str, item_data: dict, user_id: str):
    try:
        path = get_user_path(user_id, "inventory.json")
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)

        found = False
        for item in data:
            if str(item["id"]) == str(item_id):
                # 1. 更新名称和数量
                if "name" in item_data: item["name"] = item_data["name"]
                if "quantity" in item_data: item["quantity"] = int(item_data["quantity"])
                if "storage_type" in item_data: item["storage_type"] = item_data["storage_type"]

                # ✅ 核心修复：必须明确包含 shelf_life 字段的写入
                if "shelf_life" in item_data:
                    item["shelf_life"] = int(item_data["shelf_life"])

                found = True
                break

        if not found: return {"status": "error", "message": "未找到记录"}

        # 写入文件
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=4)
        return {"status": "success"}
    except Exception as e:
        return {"status": "error", "message": str(e)}


@app.get("/api/tts")
async def get_tts(text: str = Query(..., min_length=1)):
    """
    语音合成接口
    """
    try:
        # 传入文本生成语音，建议在 tts_service 中处理文件名唯一性
        filename = await generate_voice(text)
        return {"audio_url": f"/static/audio/{filename}"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/api/chat-history")
async def get_chat_history_api(user_id: str): # ✅ 必须接收 user_id
    path = get_user_path(user_id, "chat_history.json")
    if os.path.exists(path):
        with open(path, "r", encoding="utf-8") as f:
            try:
                data = json.load(f)
                return data
            except:
                return []
    return []


# 确保 save_chat_message 接收 user_id 参数
def save_chat_message(user_id: str, role: str, content: str, recipe: dict = None, missing: list = None):
    # ✅ 核心修改：通过 get_user_path 获取该用户的专属 chat_history.json 路径
    path = get_user_path(user_id, "chat_history.json")

    history = []
    if os.path.exists(path):
        try:
            with open(path, "r", encoding="utf-8") as f:
                history = json.load(f)
        except:
            history = []

    # 加入新消息
    history.append({
        "role": role,
        "content": content,
        "recipe": recipe,
        "missing": missing,
        "time": datetime.now().strftime("%H:%M")
    })

    # 仅保留最近 20 条，写入该用户文件夹
    with open(path, "w", encoding="utf-8") as f:
        json.dump(history[-20:], f, ensure_ascii=False, indent=4)

@app.post("/api/clear-chat")
async def clear_chat():
    """清空对话接口"""
    if os.path.exists(CHAT_HISTORY_FILE):
        os.remove(CHAT_HISTORY_FILE)
    return {"status": "success"}

# ==================== 用户认证相关接口 ====================

@app.post("/api/upload-avatar")
async def api_upload_avatar(file: UploadFile = File(...)):
    """上传头像"""
    try:
        # 验证文件类型
        if not file.content_type.startswith('image/'):
            return {"status": "error", "message": "只能上传图片文件"}
        
        # 验证文件大小（不超过 5MB）
        file_size = 0
        content = await file.read()
        file_size = len(content)
        if file_size > 5 * 1024 * 1024:
            return {"status": "error", "message": "图片大小不能超过 5MB"}
        
        # 生成唯一文件名
        file_extension = file.filename.split('.')[-1] if '.' in file.filename else 'jpg'
        filename = f"{uuid.uuid4()}.{file_extension}"
        file_path = f"{UPLOAD_DIR}/avatars/{filename}"
        
        # 确保头像目录存在
        avatar_dir = f"{UPLOAD_DIR}/avatars"
        os.makedirs(avatar_dir, exist_ok=True)
        
        # 保存文件
        with open(file_path, "wb") as f:
            f.write(content)
        
        # 返回可访问的 URL
        avatar_url = f"/static/uploads/avatars/{filename}"
        
        return {
            "status": "success",
            "message": "上传成功",
            "avatar_url": avatar_url
        }
    except Exception as e:
        return {"status": "error", "message": str(e)}

@app.post("/api/send-sms-code")
async def api_send_sms_code(phone: str):
    """发送短信验证码"""
    try:
        # 检查手机号是否已被注册
        if find_user_by_phone(phone):
            return {"status": "error", "message": "该手机号已被注册"}
        
        success = send_sms_code(phone)
        if success:
            return {"status": "success", "message": "验证码已发送"}
        else:
            return {"status": "error", "message": "发送失败"}
    except Exception as e:
        return {"status": "error", "message": str(e)}

class RegisterRequest(BaseModel):
    nickname: str
    phone: str = ""
    password: str = ""
    sms_code: str = ""

@app.post("/api/register")
async def api_register(data: RegisterRequest):
    try:
        # 逻辑中使用 data.nickname, data.phone 等
        user = create_user(data.nickname, data.phone, data.nickname, data.password)
        return {"status": "success", "user": user}
    except Exception as e:
        return {"status": "error", "message": str(e)}


@app.post("/api/login")
async def api_login(data: LoginRequest):  # ✅ 使用模型接收整个 Body
    try:
        # 从 data 对象中获取字段
        user = authenticate_user(data.username, data.password)

        if user:
            return {
                "status": "success",
                "message": "登录成功",
                "user": user
            }
        else:
            return {"status": "error", "message": "用户名或密码错误"}
    except Exception as e:
        return {"status": "error", "message": str(e)}

@app.get("/api/user/{user_id}")
async def api_get_user(user_id: str):
    """获取用户信息"""
    try:
        from app.models.user import get_all_users
        users = get_all_users()
        
        for user in users:
            if user["id"] == user_id:
                return {
                    "status": "success",
                    "user": {
                        "id": user["id"],
                        "username": user["username"],
                        "phone": user["phone"],
                        "nickname": user["nickname"],
                        "avatar": user["avatar"]
                    }
                }
        
        return {"status": "error", "message": "用户不存在"}
    except Exception as e:
        return {"status": "error", "message": str(e)}

@app.put("/api/user/{user_id}")
async def api_update_user(user_id: str, nickname: str = None, phone: str = None, avatar: str = None):
    """更新用户信息"""
    try:
        update_data = {}
        if nickname is not None:
            update_data["nickname"] = nickname
        if phone is not None:
            # 检查手机号是否被其他用户使用
            existing_user = find_user_by_phone(phone)
            if existing_user and existing_user["id"] != user_id:
                return {"status": "error", "message": "该手机号已被其他用户使用"}
            update_data["phone"] = phone
        if avatar is not None:
            update_data["avatar"] = avatar
        
        updated_user = update_user(user_id, **update_data)
        
        if updated_user:
            return {
                "status": "success",
                "message": "更新成功",
                "user": {
                    "id": updated_user["id"],
                    "username": updated_user["username"],
                    "phone": updated_user["phone"],
                    "nickname": updated_user["nickname"],
                    "avatar": updated_user["avatar"]
                }
            }
        else:
            return {"status": "error", "message": "用户不存在"}
    except Exception as e:
        return {"status": "error", "message": str(e)}

@app.delete("/api/user/{user_id}")
async def api_delete_user(user_id: str):
    """注销用户"""
    try:
        success = delete_user(user_id)
        
        if success:
            return {"status": "success", "message": "账户已注销"}
        else:
            return {"status": "error", "message": "用户不存在"}
    except Exception as e:
        return {"status": "error", "message": str(e)}


@app.get("/api/notifications")
async def get_notifications(user_id: str):
    # 每次获取前先扫描一遍库存
    update_expiration_notifications(user_id)

    path = get_user_path(user_id, "notifications.json")
    if os.path.exists(path):
        with open(path, "r", encoding="utf-8") as f:
            return json.load(f)
    return []


@app.post("/api/notifications/read")
async def mark_as_read(user_id: str, msg_id: str = None):
    path = get_user_path(user_id, "notifications.json")
    if not os.path.exists(path): return {"status": "error"}

    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)

    for m in data:
        if msg_id is None or m['id'] == msg_id:
            m['is_read'] = True

    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=4)
    return {"status": "success"}

if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8000)
