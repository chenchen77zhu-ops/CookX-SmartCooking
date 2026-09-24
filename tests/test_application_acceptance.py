"""Real route/persistence integration with isolated files; only cloud/YOLO are substituted."""
import io
import json
import sys
import types
from pathlib import Path

import pytest
from PIL import Image
from fastapi.testclient import TestClient


@pytest.fixture
def application(monkeypatch, tmp_path):
    class FakeYOLO:
        names = {}
        def __init__(self, *args, **kwargs): pass
        def __call__(self, *args, **kwargs): return []
    monkeypatch.setitem(sys.modules, "ultralytics", types.SimpleNamespace(YOLO=FakeYOLO))
    sys.modules.pop("app.main", None)
    import app.main as main
    from legacy_storage_fixture import install_legacy_storage
    install_legacy_storage(monkeypatch, main)
    import app.models.user as users
    monkeypatch.setattr(users, "USERS_FILE", str(tmp_path / "users.json"))
    monkeypatch.setattr(main, "USER_DATA_BASE", str(tmp_path / "data"))
    upload = tmp_path / "uploads"
    upload.mkdir()
    monkeypatch.setattr(main, "UPLOAD_DIR", str(upload))
    with TestClient(main.app) as client:
        yield client, main


def register(client, nickname):
    response = client.post("/api/register", json={"nickname": nickname, "phone": "", "password": "test-pass-123"})
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "success"
    return body["user"]["id"]


def test_login_inventory_recommendation_freshness_and_consumption(application):
    client, main = application
    uid, other = register(client, "验收用户"), register(client, "独立用户")
    assert client.post("/api/login", json={"username":"验收用户", "password":"wrong"}).json()["status"] == "error"
    assert client.post("/api/login", json={"username":"验收用户", "password":"test-pass-123"}).json()["user"]["id"] == uid
    params = {"user_id":uid}
    assert client.get("/api/inventory", params=params).json() == []
    items = [{"name":"番茄", "quantity":3, "shelf_life":4, "storage_type":"冷藏"},
             {"name":"鸡蛋", "quantity":2, "shelf_life":7, "storage_type":"冷藏"}]
    assert client.post("/api/add-to-inventory", params=params, json=items).json()["status"] == "success"
    assert client.get("/api/inventory", params={"user_id":other}).json() == []
    inventory = client.get("/api/inventory", params=params).json()
    assert len(inventory) == 2
    tomato = next(item for item in inventory if item["name"] == main.normalize_inventory_name("番茄"))
    assert client.put(f"/api/inventory/{tomato['id']}", params=params, json={"quantity":4,"shelf_life":5}).json()["status"] == "success"
    assert client.post("/api/consume-ingredients", params=params, json=["番茄"]).json()["status"] == "success"
    tomato = next(item for item in client.get("/api/inventory", params=params).json() if item["name"] == main.normalize_inventory_name("番茄"))
    assert tomato["quantity"] == 3 and tomato["shelf_life"] == 5
    recommendation = client.post("/api/recommendations", json={"user_id":uid,"top_k":3})
    assert recommendation.status_code == 200
    assert recommendation.json()["recommendations"]
    freshness = client.get(f"/api/users/{uid}/inventory/freshness")
    assert freshness.status_code == 200
    assert freshness.json()["total_count"] == 2
    assert freshness.json()["evaluable_count"] == 2
    assert client.delete(f"/api/inventory/{tomato['id']}", params=params).json()["status"] == "success"
    assert len(client.get("/api/inventory", params=params).json()) == 1


def test_recipe_stream_history_and_provider_failure(application, monkeypatch):
    client, main = application
    uid = register(client, "菜谱用户")
    async def recipe(*args):
        yield '{"dish_name":"番茄炒蛋",'
        yield '"steps":[{"text":"热锅","time_estimate":30,"temperature":"160–180 ℃"}]}'
    monkeypatch.setattr(main, "get_recipe_suggestion", recipe)
    params = {"user_id":uid, "user_prompt":"做番茄炒蛋"}
    result = client.get("/api/recommend-recipe", params=params).json()
    assert result["status"] == "success" and len(result["recipe"]["steps"]) == 1
    history = client.get("/api/chat-history", params={"user_id":uid}).json()
    assert [message["role"] for message in history] == ["user", "assistant"]
    assert history[-1]["recipe"]["dish_name"] == "番茄炒蛋"
    async def unavailable(*args):
        raise RuntimeError("test provider unavailable")
        yield ""
    monkeypatch.setattr(main, "get_recipe_suggestion", unavailable)
    assert client.get("/api/recommend-recipe", params=params).json()["status"] == "error"
    assert len(client.get("/api/chat-history", params={"user_id":uid}).json()) == 4


def test_recognition_contract_preserves_unknown_freshness(application, monkeypatch):
    client, main = application
    async def detect(*args): return {"detected":[{"name":"tomato","quantity":2}]}
    monkeypatch.setattr(main, "get_ingredients_from_qwen", detect)
    data=io.BytesIO(); Image.new("RGB", (32,32)).save(data, format="PNG")
    result=client.post("/api/analyze-fridge", files={"file":("test.png",data.getvalue(),"image/png")}).json()
    assert result["status"] == "success" and result["detected"][0]["quantity"] == 2
    assert result["detected"][0]["freshness_detail"]["fresh_score"] is None
    malformed=client.post("/api/analyze-fridge", files={"file":("bad.png",b"not image","image/png")}).json()
    assert malformed["status"] == "error"


@pytest.mark.parametrize("fail", [False, True])
def test_tts_success_and_failure_contract(application, monkeypatch, fail):
    client, main = application
    async def voice(text):
        if fail: raise RuntimeError("test speech unavailable")
        return "test.wav"
    monkeypatch.setattr(main, "generate_voice", voice)
    result=client.get("/api/tts",params={"text":"热锅"})
    assert result.status_code == (500 if fail else 200)
    if not fail: assert result.json()["audio_url"] == "/static/audio/test.wav"
    assert client.get("/api/tts",params={"text":""}).status_code == 422

def test_missing_generated_nutrition_is_not_fabricated(application, monkeypatch):
    client, main = application
    uid = register(client, 'no-nutrition')
    async def recipe(*args):
        yield '{"dish_name":"测试", "steps":["检查"]}'
    monkeypatch.setattr(main, 'get_recipe_suggestion', recipe)
    result = client.get('/api/recommend-recipe',params={'user_id':uid,'user_prompt':'测试'}).json()
    assert result['recipe']['nutrition'] is None
