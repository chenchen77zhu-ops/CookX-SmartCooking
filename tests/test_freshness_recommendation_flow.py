import hashlib
import io
import json
import sys
import types
from datetime import datetime, timedelta, timezone
from pathlib import Path

import pytest
from fastapi.testclient import TestClient
from PIL import Image


@pytest.fixture
def flow_api(monkeypatch, tmp_path):
    class FakeYOLO:
        names = {}

        def __init__(self, *args, **kwargs):
            pass

        def __call__(self, *args, **kwargs):
            return []

    monkeypatch.setitem(sys.modules, "ultralytics", types.SimpleNamespace(YOLO=FakeYOLO))
    sys.modules.pop("app.main", None)
    import app.main as main
    from legacy_storage_fixture import install_legacy_storage
    install_legacy_storage(monkeypatch, main)

    user_id = "flow-test-user"
    data_root = tmp_path / "users"
    upload_root = tmp_path / "uploads"
    upload_root.mkdir()
    monkeypatch.setattr(main, "USER_DATA_BASE", str(data_root))
    monkeypatch.setattr(main, "UPLOAD_DIR", str(upload_root))
    monkeypatch.setattr(main, "get_all_users", lambda: [{"id": user_id}])

    def forbidden(*args, **kwargs):
        raise AssertionError("real cloud, speech, or hardware integration must not be called")

    monkeypatch.setattr(main, "get_recipe_suggestion", forbidden)
    monkeypatch.setattr(main, "generate_voice", forbidden)
    with TestClient(main.app, raise_server_exceptions=False) as client:
        yield client, main, user_id, data_root, forbidden


def _iso(delta: timedelta) -> str:
    return (datetime.now(timezone.utc) + delta).isoformat().replace("+00:00", "Z")


def _confirm(client, user_id, items, confirmed=True):
    return client.post("/api/inventory/confirm-recognition", json={
        "user_id": user_id,
        "confirmed": confirmed,
        "items": items,
    })


def _png_bytes():
    output = io.BytesIO()
    Image.new("RGB", (16, 16), color="white").save(output, format="PNG")
    return output.getvalue()


def _production_inventory_hashes():
    root = Path(__file__).resolve().parents[1] / "app" / "data" / "users"
    return {
        str(path.relative_to(root)): hashlib.sha256(path.read_bytes()).hexdigest()
        for path in sorted(root.glob("*/inventory.json"))
    }


def test_recognition_candidates_do_not_write_inventory_or_fabricate_data(flow_api, monkeypatch):
    client, main, user_id, data_root, _ = flow_api

    async def fake_qwen(*args, **kwargs):
        return {"detected": [{"name": "番茄", "quantity": 2, "category": "vegetable"}]}

    monkeypatch.setattr(main, "get_ingredients_from_qwen", fake_qwen)
    response = client.post(
        "/api/analyze-fridge",
        files={"file": ("fridge.png", _png_bytes(), "image/png")},
    )
    assert response.status_code == 200
    candidate = response.json()["detected"][0]
    assert candidate["name"] == "番茄"
    assert not {"purchase_time", "add_time", "shelf_life", "expiry_date", "storage_type"} & candidate.keys()
    assert candidate["freshness_detail"]["fresh_score"] is None
    assert candidate["freshness_detail"]["component_scores"]["V"] is None
    assert not (data_root / user_id / "inventory.json").exists()


def test_empty_recognition_does_not_write_inventory(flow_api, monkeypatch):
    client, main, user_id, data_root, _ = flow_api

    async def empty_qwen(*args, **kwargs):
        return {"detected": []}

    monkeypatch.setattr(main, "get_ingredients_from_qwen", empty_qwen)
    response = client.post(
        "/api/analyze-fridge",
        files={"file": ("empty.png", _png_bytes(), "image/png")},
    )
    assert response.status_code == 200
    assert response.json()["detected"] == []
    assert not (data_root / user_id / "inventory.json").exists()


def test_unconfirmed_or_empty_confirmation_is_rejected_without_write(flow_api):
    client, _, user_id, data_root, _ = flow_api
    unconfirmed = _confirm(client, user_id, [{"name": "番茄", "quantity": 1}], confirmed=False)
    empty = _confirm(client, user_id, [])
    assert unconfirmed.status_code == 422
    assert empty.status_code == 422
    assert not (data_root / user_id).exists()


def test_confirm_writes_fields_and_returns_aligned_freshfusion(flow_api):
    client, _, user_id, _, _ = flow_api
    item = {
        "name": "番茄",
        "quantity": 2,
        "purchase_time": _iso(timedelta(days=-2)),
        "add_time": _iso(timedelta(days=-1)),
        "shelf_life": 5,
        "expiry_date": _iso(timedelta(days=3)),
        "storage_type": "常温",
    }
    response = _confirm(client, user_id, [item])
    assert response.status_code == 200
    body = response.json()
    result = body["confirmed_items"][0]
    assert result["item_id"] == result["inventory_item"]["id"]
    assert result["ingredient_name"] == result["inventory_item"]["name"]
    assert result["evaluated_at"] == body["evaluated_at"]
    assert result["algorithm_version"] == "freshfusion_v1"
    assert result["fresh_score"] is not None
    assert result["component_scores"]["V"] is None
    assert result["component_scores"]["H"] is None
    inventory = client.get("/api/inventory", params={"user_id": user_id}).json()
    assert inventory == [result["inventory_item"]]
    for field in ("purchase_time", "add_time", "shelf_life", "expiry_date", "storage_type"):
        assert inventory[0][field] == item[field]


def test_batch_confirmation_uses_one_evaluated_at_and_merges_duplicates(flow_api):
    client, _, user_id, _, _ = flow_api
    common = {
        "name": "鸡蛋",
        "quantity": 1,
        "add_time": _iso(timedelta(days=-1)),
        "shelf_life": 7,
        "storage_type": "冷藏",
    }
    response = _confirm(client, user_id, [common, common])
    assert response.status_code == 200
    body = response.json()
    assert body["confirmed_count"] == 1
    assert {item["evaluated_at"] for item in body["confirmed_items"]} == {body["evaluated_at"]}
    assert body["confirmed_items"][0]["inventory_item"]["quantity"] == 2


@pytest.mark.parametrize("invalid", [
    {"name": "", "quantity": 1},
    {"name": "番茄", "quantity": 0},
    {"name": "番茄", "quantity": "乱码"},
    {"name": "番茄", "add_time": "bad-time"},
    {"name": "番茄", "purchase_time": "bad-time"},
    {"name": "番茄", "shelf_life": 0},
    {"name": "番茄", "shelf_life": "NaN"},
    {"name": "番茄", "add_time": "2026-09-20T00:00:00Z", "expiry_date": "2026-09-19T00:00:00Z"},
    {"name": "番茄", "storage_type": "未知储存"},
])
def test_invalid_confirmation_is_atomic(flow_api, invalid):
    client, _, user_id, data_root, _ = flow_api
    response = _confirm(client, user_id, [
        {"name": "鸡蛋", "quantity": 1},
        invalid,
    ])
    assert response.status_code == 422
    assert not (data_root / user_id / "inventory.json").exists()


def test_unknown_user_does_not_create_orphan_directory(flow_api, monkeypatch):
    client, main, _, data_root, _ = flow_api
    monkeypatch.setattr(main, "get_all_users", lambda: [])
    response = _confirm(client, "missing", [{"name": "番茄", "quantity": 1}])
    assert response.status_code == 404
    assert not (data_root / "missing").exists()


def test_freshfusion_failure_does_not_write_inventory(flow_api, monkeypatch):
    client, main, user_id, data_root, _ = flow_api

    def fail_evaluation(*args, **kwargs):
        raise RuntimeError("test freshness failure")

    monkeypatch.setattr(main, "_public_freshfusion_result", fail_evaluation)
    response = _confirm(client, user_id, [{"name": "番茄", "quantity": 1}])
    assert response.status_code == 500
    assert response.json()["detail"] == "鲜度评估失败，库存未写入"
    assert not (data_root / user_id / "inventory.json").exists()


def test_recommendation_refreshes_after_safe_confirmation(flow_api):
    client, _, user_id, _, _ = flow_api
    before = client.post("/api/recommendations", json={"user_id": user_id, "top_k": 20}).json()
    assert before["status"] == "inventory_required"
    confirmation = _confirm(client, user_id, [{
        "name": "番茄",
        "quantity": 2,
        "add_time": _iso(timedelta(days=-1)),
        "shelf_life": 10,
        "storage_type": "常温",
    }])
    assert confirmation.status_code == 200
    after = client.post("/api/recommendations", json={"user_id": user_id, "top_k": 20}).json()
    assert after["status"] == "success"
    assert after["eligible_recipe_count"] > before["eligible_recipe_count"]
    assert any("番茄" in item["matched_ingredients"] for item in after["recommendations"])


def test_expiring_item_uses_real_f_and_w_and_reason(flow_api):
    client, _, user_id, _, _ = flow_api
    assert _confirm(client, user_id, [{
        "name": "番茄",
        "quantity": 2,
        "add_time": _iso(timedelta(days=-9)),
        "shelf_life": 10,
        "storage_type": "常温",
    }]).status_code == 200
    response = client.post("/api/recommendations", json={"user_id": user_id, "top_k": 20}).json()
    tomato_recipe = next(item for item in response["recommendations"] if "番茄" in item["matched_ingredients"])
    assert tomato_recipe["component_scores"]["F"] > 0.8
    assert tomato_recipe["component_scores"]["W"] is not None
    assert tomato_recipe["expiring_ingredients_used"] == ["番茄"]
    assert "临期但未过期" in "；".join(tomato_recipe["reasons"])


def test_expired_item_cannot_create_recommendation_candidate(flow_api):
    client, main, user_id, _, _ = flow_api
    assert _confirm(client, user_id, [{
        "name": "番茄",
        "quantity": 2,
        "add_time": _iso(timedelta(days=-11)),
        "shelf_life": 10,
        "storage_type": "常温",
    }]).status_code == 200
    response = client.post("/api/recommendations", json={"user_id": user_id, "top_k": 20}).json()
    assert response["status"] == "inventory_required"
    recipe = next(recipe for recipe in main.load_recipes() if recipe["name"] == "番茄炒鸡蛋")
    inventory = client.get("/api/inventory", params={"user_id": user_id}).json()
    scored = main.recommend_recipes(inventory, recipes=[recipe], top_k=1)
    assert scored == []


def test_missing_time_keeps_f_unavailable_without_breaking_recommendation(flow_api):
    client, _, user_id, _, _ = flow_api
    confirmation = _confirm(client, user_id, [{"name": "番茄", "quantity": 2, "storage_type": "常温"}])
    assert confirmation.status_code == 200
    result = confirmation.json()["confirmed_items"][0]
    assert result["freshness_level"] == "unknown" or result["component_scores"]["T"] is None
    response = client.post("/api/recommendations", json={"user_id": user_id, "top_k": 20}).json()
    tomato_recipe = next(item for item in response["recommendations"] if "番茄" in item["matched_ingredients"])
    assert tomato_recipe["component_scores"]["F"] is None
    assert "F" in tomato_recipe["unavailable_components"]
    assert tomato_recipe["effective_weights"]


def test_basic_seasoning_only_has_no_eligible_recommendation(flow_api):
    client, _, user_id, _, _ = flow_api
    assert _confirm(client, user_id, [{"name": "盐", "quantity": 1}]).status_code == 200
    response = client.post("/api/recommendations", json={"user_id": user_id, "top_k": 20}).json()
    assert response["status"] == "no_eligible_recipes"
    assert response["recommendations"] == []


def test_recommendation_failure_does_not_remove_saved_inventory(flow_api, monkeypatch):
    client, main, user_id, _, _ = flow_api
    assert _confirm(client, user_id, [{"name": "番茄", "quantity": 1}]).status_code == 200

    def fail_recommendation(*args, **kwargs):
        raise ValueError("test recommendation failure")

    monkeypatch.setattr(main, "recommend_recipes", fail_recommendation)
    response = client.post("/api/recommendations", json={"user_id": user_id})
    assert response.status_code == 422
    assert len(client.get("/api/inventory", params={"user_id": user_id}).json()) == 1


def test_inventory_and_freshness_gets_do_not_write(flow_api):
    client, _, user_id, data_root, _ = flow_api
    assert _confirm(client, user_id, [{"name": "番茄", "quantity": 1}]).status_code == 200
    path = data_root / user_id / "inventory.json"
    before = path.read_bytes()
    assert client.get("/api/inventory", params={"user_id": user_id}).status_code == 200
    assert client.get(f"/api/users/{user_id}/inventory/freshness").status_code == 200
    assert path.read_bytes() == before


def test_same_inventory_produces_stable_recommendation_payload(flow_api):
    client, _, user_id, _, _ = flow_api
    assert _confirm(client, user_id, [{
        "name": "番茄", "quantity": 2,
        "add_time": _iso(timedelta(days=-5)), "shelf_life": 10,
    }]).status_code == 200
    first = client.post("/api/recommendations", json={"user_id": user_id, "top_k": 20}).json()
    second = client.post("/api/recommendations", json={"user_id": user_id, "top_k": 20}).json()
    assert first["recommendations"] == second["recommendations"]


def test_flow_routes_and_legacy_routes_coexist(flow_api):
    _, main, _, _, _ = flow_api
    paths = {
        (route.path, tuple(getattr(route, "methods", None) or []))
        for route in main.app.routes
    }
    assert ("/api/inventory/confirm-recognition", ("POST",)) in paths
    assert ("/api/add-to-inventory", ("POST",)) in paths
    assert ("/api/recommendations", ("POST",)) in paths
    assert ("/api/recommend-recipe", ("GET",)) in paths


def test_flow_does_not_modify_production_inventory(flow_api):
    before = _production_inventory_hashes()
    client, _, user_id, _, _ = flow_api
    assert _confirm(client, user_id, [{"name": "番茄", "quantity": 1}]).status_code == 200
    assert _production_inventory_hashes() == before
