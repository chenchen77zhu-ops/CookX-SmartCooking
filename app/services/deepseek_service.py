import os
import json
from openai import AsyncOpenAI # 建议使用异步客户端以获得更好性能
from dotenv import load_dotenv

load_dotenv()

# 使用异步客户端
client = AsyncOpenAI(
    api_key=os.getenv("DEEPSEEK_API_KEY"),
    base_url="https://api.deepseek.com"
)

# 之前的翻译映射表保持不变
TRANSLATE_MAP = {
    'beef': '牛肉', 'carrot': '胡萝卜', 'chicken': '鸡肉',
    'chili': '辣椒', 'egg': '鸡蛋', 'garlic': '大蒜',
    'kimchi': '泡菜', 'leek': '大葱', 'onion': '洋葱', 'potato': '土豆'
}

async def get_recipe_suggestion(inventory, user_prompt):
    # 1. 翻译食材
    inventory_cn = [TRANSLATE_MAP.get(i.lower().strip(), i) for i in inventory]
    inventory_str = "、".join(inventory_cn)

    # 2. 系统提示词
    system_role = (
        "你是一位专业的星级大厨和营养师。请根据用户的食材和需求推荐菜谱。"
        "【绝对指令】：你必须返回纯 JSON 格式，包含以下 6 个核心字段，缺一不可：\n"
        "1. 'dish_name': 菜名字符串\n"
        "2. 'ingredients_list': 数组，格式为 [{'item': '食材名', 'amount': '用量'}]\n"
        "3. 'nutrition': 对象，包含 {'calories': '数字', 'protein': '数字', 'fat': '数字', 'carbs': '数字'}\n"
        "4. 'steps': 数组，格式为 [{'text': '详细步骤描述', 'time_estimate': 秒数}]\n"
        "5. 'used_ingredients': 属于库存列表里的原始英文标签数组\n"
        "6. 'missing': 缺少的食材列表数组\n\n"
        "【重要说明】：步骤要极其详细，对厨房纯小白进行教学。禁止在 JSON 外添加任何 Markdown 标识（如 ```json）。"
        "3. time_estimate按动作精准算秒，禁默认60/15；4. missing=[]。\n"
    )

    # 3. 构造请求
    user_content = f"材料：{inventory_str}。需求：{user_prompt}。请用中文教学。"

    try:
        # 关键：使用 await 配合 stream=True
        response = await client.chat.completions.create(
            model="deepseek-chat",
            messages=[
                {"role": "system", "content": system_role},
                {"role": "user", "content": f"库存：{inventory_str}。我想吃：{user_prompt}。"}
            ],
            response_format={'type': 'json_object'},
            stream=True
        )

        async for chunk in response:
            content = chunk.choices[0].delta.content
            if content:
                yield content  # ✅ 逐个字符吐出
    except Exception as e:
        print(f"DeepSeek 流式调用失败: {e}")
        yield json.dumps({"error": str(e)})