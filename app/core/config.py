from pydantic_settings import BaseSettings
import os

class Settings(BaseSettings):
    DEEPSEEK_API_KEY: str = os.getenv("DEEPSEEK_API_KEY", "")
    DEEPSEEK_BASE_URL: str = os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com/v1")
    YOLO_MODEL_PATH: str = os.getenv("YOLO_MODEL_PATH", "app/models/best.pt")
settings = Settings()