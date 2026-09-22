import json
import sys
import types
from datetime import datetime, timedelta, timezone
from pathlib import Path

import pytest
from fastapi.testclient import TestClient


@pytest.fixture
def inventory_api(monkeypatch, tmp_path):
    class FakeYOLO:
        def __init__(self, *args, **kwargs):
            self.names = {}

    monkeypatch.setitem(sys.modules, "ultralytics", types.SimpleNamespace(YOLO=FakeYOLO))
    sys.modules.pop("app.main", None)
    import app.main as main

    user_id = "inventory-test-user"
    monkeypatch.setattr(main, "USER_DATA_BASE", str(tmp_path))
    monkeypatch.setattr(main, "get_all_users", lambda: [{"id": user_id}])
    with TestClient(main.app) as client:
        yield client, main, user_id, tmp_path


def _iso(delta: timedelta) -> str:
    return (datetime.now(timezone.utc) + delta).isoformat().replace("+00:00", "Z")


def _add(client, user_id, payload):
    return client.post("/api/add-to-inventory", params={"user_id": user_id}, json=[payload])


def test_add_complete_inventory_fields_and_read_them_unchanged(inventory_api):
    client, _, user_id, _ = inventory_api
    payload = {
        "name": "番茄",
        "quantity": 2,
        "purchase_time": _iso(timedelta(days=-2)),
        "add_time": _iso(timedelta(days=-1)),
        "shelf_life": 5.5,
        "expiry_date": _iso(timedelta(days=3)),
        "storage_type": "冰箱冷藏",
    }
    assert _add(client, user_id, payload).status_code == 200
    saved = client.get("/api/inventory", params={"user_id": user_id}).json()[0]
    for field in ("purchase_time", "add_time", "shelf_life", "expiry_date", "storage_type"):
        assert saved[field] == payload[field]


def test_add_legacy_fields_generates_utc_add_time_without_default_shelf_life(inventory_api):
    client, _, user_id, _ = inventory_api
    response = _add(client, user_id, {"name": "鸡蛋", "quantity": 3})
    assert response.status_code == 200
    saved = client.get("/api/inventory", params={"user_id": user_id}).json()[0]
    assert saved["quantity"] == 3
    assert saved["add_time"].endswith("Z")
    assert "shelf_life" not in saved
    assert "purchase_time" not in saved


@pytest.mark.parametrize(
    "field,value",
    [
        ("purchase_time", lambda: _iso(timedelta(days=-3))),
        ("add_time", lambda: _iso(timedelta(days=-2))),
        ("shelf_life", lambda: 9.5),
        ("expiry_date", lambda: _iso(timedelta(days=2))),
        ("storage_type", lambda: "室温"),
    ],
)
def test_update_each_freshness_field(inventory_api, field, value):
    client, _, user_id, _ = inventory_api
    add_time = _iso(timedelta(days=-5))
    assert _add(client, user_id, {
        "name": "土豆", "quantity": 1, "add_time": add_time,
        "shelf_life": 10, "storage_type": "阴凉干燥",
    }).status_code == 200
    item = client.get("/api/inventory", params={"user_id": user_id}).json()[0]
    updated = value()
    response = client.put(
        f"/api/inventory/{item['id']}",
        params={"user_id": user_id},
        json={field: updated},
    )
    assert response.status_code == 200
    saved = client.get("/api/inventory", params={"user_id": user_id}).json()[0]
    assert saved[field] == updated


def test_update_changes_batch_freshness_and_expired_overrides_storage(inventory_api):
    client, _, user_id, _ = inventory_api
    assert _add(client, user_id, {
        "name": "番茄", "quantity": 1,
        "add_time": _iso(timedelta(days=-10)),
        "expiry_date": _iso(timedelta(days=2)),
        "storage_type": "常温",
    }).status_code == 200
    item = client.get("/api/inventory", params={"user_id": user_id}).json()[0]
    before = client.get(f"/api/users/{user_id}/inventory/freshness").json()["items"][0]
    response = client.put(
        f"/api/inventory/{item['id']}",
        params={"user_id": user_id},
        json={"expiry_date": _iso(timedelta(days=-1))},
    )
    assert response.status_code == 200
    after = client.get(f"/api/users/{user_id}/inventory/freshness").json()["items"][0]
    assert before["expired"] is False
    assert after["expired"] is True
    assert after["freshness_level"] == "expired"
    assert after["storage_details"]["score"] == 1.0


@pytest.mark.parametrize(
    "payload,fragment",
    [
        ({"add_time": "not-a-time"}, "add_time"),
        ({"purchase_time": lambda: _iso(timedelta(days=2))}, "purchase_time"),
        ({"add_time": "2026-09-20T00:00:00Z", "expiry_date": "2026-09-19T00:00:00Z"}, "add_time"),
        ({"purchase_time": "2026-09-20T00:00:00Z", "expiry_date": "2026-09-19T00:00:00Z"}, "purchase_time"),
        ({"shelf_life": 0}, "shelf_life"),
        ({"shelf_life": -1}, "shelf_life"),
        ({"shelf_life": "乱码"}, "shelf_life"),
        ({"shelf_life": "NaN"}, "shelf_life"),
        ({"shelf_life": "Infinity"}, "shelf_life"),
        ({"storage_type": "火星储存"}, "storage_type"),
        ({"quantity": 0}, "quantity"),
        ({"quantity": -1}, "quantity"),
        ({"quantity": "一份"}, "quantity"),
    ],
)
def test_invalid_add_fields_return_422(inventory_api, payload, fragment):
    client, _, user_id, _ = inventory_api
    resolved = {key: value() if callable(value) else value for key, value in payload.items()}
    response = _add(client, user_id, {"name": "番茄", "quantity": 1, **resolved})
    assert response.status_code == 422
    assert fragment in response.text


def test_update_rejects_expiry_before_existing_add_time(inventory_api):
    client, _, user_id, _ = inventory_api
    assert _add(client, user_id, {
        "name": "番茄", "quantity": 1, "add_time": _iso(timedelta(days=-2)),
    }).status_code == 200
    item = client.get("/api/inventory", params={"user_id": user_id}).json()[0]
    response = client.put(
        f"/api/inventory/{item['id']}", params={"user_id": user_id},
        json={"expiry_date": _iso(timedelta(days=-3))},
    )
    assert response.status_code == 422
    assert "expiry_date必须晚于add_time" in response.text


@pytest.mark.parametrize("method,path,json_body", [
    ("get", "/api/inventory", None),
    ("post", "/api/add-to-inventory", [{"name": "番茄", "quantity": 1}]),
    ("put", "/api/inventory/missing", {"quantity": 1}),
    ("delete", "/api/inventory/missing", None),
])
def test_inventory_crud_unknown_user_returns_404(inventory_api, monkeypatch, method, path, json_body):
    client, main, _, _ = inventory_api
    monkeypatch.setattr(main, "get_all_users", lambda: [])
    kwargs = {"params": {"user_id": "missing"}}
    if json_body is not None:
        kwargs["json"] = json_body
    response = getattr(client, method)(path, **kwargs)
    assert response.status_code == 404
    assert response.json()["detail"] == "用户不存在"


@pytest.mark.parametrize("method", ["put", "delete"])
def test_missing_inventory_item_returns_404(inventory_api, method):
    client, _, user_id, tmp_path = inventory_api
    user_dir = tmp_path / user_id
    user_dir.mkdir()
    (user_dir / "inventory.json").write_text("[]", encoding="utf-8")
    kwargs = {"params": {"user_id": user_id}}
    if method == "put":
        kwargs["json"] = {"quantity": 1}
    response = getattr(client, method)("/api/inventory/missing", **kwargs)
    assert response.status_code == 404
    assert response.json()["detail"] == "库存项目不存在"


def test_get_legacy_inventory_missing_new_fields_without_rewriting(inventory_api):
    client, _, user_id, tmp_path = inventory_api
    user_dir = tmp_path / user_id
    user_dir.mkdir()
    path = user_dir / "inventory.json"
    original = '[{"id":"legacy","name":"鸡蛋","quantity":1}]'
    path.write_text(original, encoding="utf-8")
    assert client.get("/api/inventory", params={"user_id": user_id}).json()[0]["id"] == "legacy"
    assert path.read_text(encoding="utf-8") == original


def test_inventory_tests_do_not_modify_production_inventory(inventory_api):
    _, _, _, tmp_path = inventory_api
    production = Path(__file__).resolve().parents[1] / "app" / "data" / "users"
    before = {path: path.read_bytes() for path in production.glob("*/inventory.json")}
    assert not str(tmp_path).startswith(str(production))
    after = {path: path.read_bytes() for path in production.glob("*/inventory.json")}
    assert after == before
