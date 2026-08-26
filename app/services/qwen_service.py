import dashscope
from dashscope import MultiModalConversation
import json
import os
import re

# ✅ 设置你的 API KEY（或者写入 .env 文件中读取）
dashscope.api_key = "sk-cf3dc4c8610f45e9a997a55907a07219"


async def get_ingredients_from_qwen(image_path):
    """
    使用通义千问-VL 精准识别图中所有食材
    """
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
            model='qwen-vl-plus',
            messages=messages
        )

        if response.status_code == 200:
            raw_text = response.output.choices[0].message.content[0]['text']
            print(f"--- 千问原始回复内容 ---\n{raw_text}\n----------------------")

            # ✅ 核心修复 1：更稳健的 JSON 提取（处理 ```json 标签）
            # 寻找字符串中第一个 [ 或 { 和 最后一个 ] 或 }
            json_str_match = re.search(r'(\[.*\]|\{.*\})', raw_text, re.DOTALL)
            if not json_str_match:
                print("❌ 千问回复中未发现 JSON 结构")
                return {"detected": []}

            clean_json_str = json_str_match.group(1)
            try:
                data = json.loads(clean_json_str)
            except Exception as e:
                print(f"❌ JSON 解析失败: {e}")
                return {"detected": []}

            # ✅ 核心修复 2：兼容列表 [] 和 字典 {"detected": []} 两种返回格式
            detected_list = data if isinstance(data, list) else data.get("detected", [])

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

        return {"detected": []}
    except Exception as e:
        print(f"千问模块运行异常: {e}")
        return {"detected": []}