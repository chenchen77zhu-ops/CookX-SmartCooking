"""Loopback-only acceptance backend. Temporary state; cloud/YOLO are test doubles."""
import json
import os
import sys
import tempfile
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
os.environ.setdefault("DEEPSEEK_API_KEY", "test-placeholder-not-a-real-key")
os.environ.setdefault("DASHSCOPE_API_KEY", "test-placeholder-not-a-real-key")
import pytest
import uvicorn
from test_application_acceptance import application, register

if __name__ == "__main__":
    with tempfile.TemporaryDirectory(prefix="cookx-acceptance-") as directory, pytest.MonkeyPatch.context() as patch:
        fixture=application.__wrapped__(patch, Path(directory))
        client, main=next(fixture)
        try:
            from starlette.responses import JSONResponse
            async def legacy_domain_boundary(scope,receive,send):
                if scope['type']=='http' and scope['path'].startswith('/api/v3/'):
                    response=JSONResponse({'detail':'New domain services unavailable in legacy compatibility fixture; validated on the SQLite server'},status_code=503)
                    await response(scope,receive,send)
                else:await main.app(scope,receive,send)
            uid=register(client,"acceptance")
            # A legacy row deliberately lacks dates and shelf life.
            inventory=Path(main.get_user_path(uid,"inventory.json"))
            inventory.write_text(json.dumps([{"id":"legacy","name":"胡萝卜","quantity":1}],ensure_ascii=False),encoding="utf-8")
            async def recipe(*args):
                yield json.dumps({"dish_name":"测试番茄炒蛋","steps":[{"text":"热锅","temperature":"160–180 ℃","time_estimate":30}]},ensure_ascii=False)
            async def speech(*args): raise RuntimeError("Acceptance server: speech provider deliberately unavailable")
            patch.setattr(main,"get_recipe_suggestion",recipe)
            patch.setattr(main,"generate_voice",speech)
            uvicorn.run(legacy_domain_boundary,host="127.0.0.1",port=8000,log_level="warning")
        finally:
            fixture.close()
