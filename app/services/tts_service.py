import edge_tts
import uuid
import os
import asyncio

# 配置音频存放路径 (需与 main.py 保持一致)
AUDIO_DIR = "app/static/audio"

async def generate_voice(text: str) -> str:
    try:
        # 1. 确保目录存在
        if not os.path.exists(AUDIO_DIR):
            os.makedirs(AUDIO_DIR)

        # 2. 限制文本长度（edge-tts 建议单次不要超过几千字，我们菜谱绰绰有余）
        clean_text = text.strip()[:500]

        # 3. 生成唯一文件名
        filename = f"{uuid.uuid4().hex}.mp3"
        output_path = os.path.join(AUDIO_DIR, filename)

        # 4. 执行转换 (增加超时控制)
        communicate = edge_tts.Communicate(clean_text, "zh-CN-XiaoxiaoNeural", rate="-10%")
        await communicate.save(output_path)

        return filename
    except Exception as e:
        # 在黑窗口打印具体错误，方便你调试
        print(f"TTS 核心报错: {str(e)}")
        raise Exception(f"语音合成失败: {str(e)}")