import dashscope
from dashscope import MultiModalConversation
import json
import os
import re
from dotenv import load_dotenv

load_dotenv()

# DashScope 密钥只从运行环境读取，不在源码中保存。
dashscope.api_key = os.getenv("DASHSCOPE_API_KEY")
QWEN_VL_MODEL = os.getenv("QWEN_VL_MODEL", "qwen3-vl-plus")


async def get_ingredients_from_qwen(image_path):
    """
    使用通义千问-VL 精准识别图中所有食材
    """
    if not dashscope.api_key:
        print("[QWEN] DASHSCOPE_API_KEY is not configured")
        return {"detected": [], "_qwen_error": "DASHSCOPE_API_KEY is not configured"}

    # 绝对路径转为符合 DashScope 要求的 file:// 格式
    absolute_path = os.path.abspath(image_path)
    file_url = f"file://{absolute_path}"

    # 构造 Prompt：引导 AI 返回我们需要的数据结构
    prompt = (
        "你是一个极其专业的生鲜食材识别专家。"
        "【任务】：识别图中所有食材。\n"
        "【要求】：\n"
        "1. name: 必须是该食材的【简体中文】常用名。\n"
        "2. category: 根据食材类型，必须从以下选项中选一个: ['meat', 'vegetable', 'fruit', 'other']。\n"
        "3. quantity: 识别数量。\n"
        "4. 忽略包装和容器，只返回 JSON 数组格式。\n"
        "示例：{\"detected\": [{\"name\": \"大白菜\", \"quantity\": 1, \"category\": \"vegetable\"}]}"
    )

    messages = [
        {
            "role": "user",
            "content": [
                {"image": file_url},
                {"text": prompt}
            ]
        }
    ]

    try:
        response = MultiModalConversation.call(
            model=QWEN_VL_MODEL,
            messages=messages,
            enable_thinking=False,
        )

        status_code = getattr(response, "status_code", None)
        request_id = getattr(response, "request_id", None)
        response_code = getattr(response, "code", None)
        response_message = getattr(response, "message", None)
        print(
            "[QWEN] "
            f"status_code={status_code} request_id={request_id or '-'} "
            f"code={response_code or '-'} message={response_message or '-'}"
        )

        if status_code == 200:
            raw_text = response.output.choices[0].message.content[0]['text']

            # ✅ 核心修复 1：更稳健的 JSON 提取（处理 ```json 标签）
            # 寻找字符串中第一个 [ 或 { 和 最后一个 ] 或 }
            json_str_match = re.search(r'(\[.*\]|\{.*\})', raw_text, re.DOTALL)
            if not json_str_match:
                error = "response does not contain a JSON object or array"
                print(f"[QWEN] JSON extraction failed: {error}")
                return {"detected": [], "_qwen_error": error}

            clean_json_str = json_str_match.group(1)
            try:
                data = json.loads(clean_json_str)
            except json.JSONDecodeError as e:
                error = f"JSON decode failed at position {e.pos}: {e.msg}"
                print(f"[QWEN] {error}")
                return {"detected": [], "_qwen_error": error}

            # ✅ 核心修复 2：兼容列表 [] 和 字典 {"detected": []} 两种返回格式
            if isinstance(data, list):
                detected_list = data
            elif isinstance(data, dict):
                detected_list = data.get("detected", [])
            else:
                error = f"unexpected JSON root type: {type(data).__name__}"
                print(f"[QWEN] JSON structure failed: {error}")
                return {"detected": [], "_qwen_error": error}

            if not isinstance(detected_list, list):
                error = "detected field is not a list"
                print(f"[QWEN] JSON structure failed: {error}")
                return {"detected": [], "_qwen_error": error}

            # ✅ 核心修复 3：名称标准化 (解决 napa_cabbage 等命名差异)
            for item in detected_list:
                name = item.get("name", "").lower()
                # 模糊匹配：只要名字里有 cabbage，就统一存为 cabbage，方便前端对上图标
                if "cabbage" in name:
                    item["name"] = "cabbage"
                # 可以在此增加更多常见差异映射
                elif "eggplant" in name or "egg_plant" in name:
                    item["name"] = "eggplant"

            return {"detected": detected_list}

        error = (
            f"DashScope request failed (status_code={status_code}, "
            f"code={response_code or 'unknown'}, request_id={request_id or 'unknown'})"
        )
        print(f"[QWEN] request failed: {error}")
        return {"detected": [], "_qwen_error": "Qwen request failed"}
    except Exception as e:
        error = f"{type(e).__name__}: {e}"
        print(f"[QWEN] exception type={type(e).__name__} message={e}")
        return {"detected": [], "_qwen_error": error}
