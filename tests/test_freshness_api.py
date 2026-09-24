import asyncio
import json
import sys
import types
from datetime import datetime, timezone
from pathlib import Path
from types import SimpleNamespace

import pytest
from fastapi import HTTPException
from fastapi.testclient import TestClient


def import_main(monkeypatch):
    class FakeYOLO:
        def __init__(self, *args, **kwargs):
            self.names = {}
    monkeypatch.setitem(sys.modules, "ultralytics", types.SimpleNamespace(YOLO=FakeYOLO))
    sys.modules.pop("app.main", None)
    import app.main as main
    from legacy_storage_fixture import install_legacy_storage
    install_legacy_storage(monkeypatch, main)
    return main


def test_single_evaluation_api_success(monkeypatch):
    main = import_main(monkeypatch)
    request = main.FreshnessEvaluationRequest(
        ingredient_name="番茄", add_time="2026-09-16T12:00:00Z", shelf_life=4,
        storage_type="常温",
    )
    result = asyncio.run(main.evaluate_freshness(request))
    assert result["algorithm_version"] == "freshfusion_v1"
    assert result["fresh_score"] is not None
    assert result["evaluated_at"].endswith("Z")
    assert result["component_scores"]["V"] is None
    assert "V" in result["unavailable_components"]
    assert "当前尚无服务端可信视觉鲜度结果，V未参与融合。" in result["data_quality_notes"]


def test_single_api_rejects_client_reference_time(monkeypatch):
    main = import_main(monkeypatch)
    response = TestClient(main.app).post(
        "/api/freshness/evaluate",
        json={"ingredient_name": "番茄", "reference_time": "2020-01-01T00:00:00Z"},
    )
    assert response.status_code == 422


def test_client_cannot_roll_back_time_to_revive_expired_item(monkeypatch):
    main = import_main(monkeypatch)
    response = TestClient(main.app).post(
        "/api/freshness/evaluate",
        json={
            "ingredient_name": "番茄", "add_time": "2020-01-01T00:00:00Z",
            "shelf_life": 1, "reference_time": "2020-01-01T01:00:00Z",
        },
    )
    assert response.status_code == 422


def test_single_api_rejects_client_visual_freshness(monkeypatch):
    main = import_main(monkeypatch)
    response = TestClient(main.app).post(
        "/api/freshness/evaluate",
        json={
            "ingredient_name": "番茄",
            "visual_freshness": {
                "score": 1, "confidence": 1, "source": "qwen_visual",
                "observed_at": datetime.now(timezone.utc).isoformat(),
                "evidence": ["客户端声称非常新鲜"], "model_version": "forged",
            },
        },
    )
    assert response.status_code == 422


def test_unknown_user_returns_404(monkeypatch):
    main = import_main(monkeypatch)
    monkeypatch.setattr(main, "get_all_users", lambda: [])
    with pytest.raises(HTTPException) as error:
        asyncio.run(main.evaluate_inventory_freshness("missing", SimpleNamespace(query_params={})))
    assert error.value.status_code == 404


def test_empty_inventory_returns_empty_statistics(monkeypatch, tmp_path):
    main = import_main(monkeypatch)
    monkeypatch.setattr(main, "get_all_users", lambda: [{"id": "u"}])
    monkeypatch.setattr(main, "USER_DATA_BASE", str(tmp_path))
    result = asyncio.run(main.evaluate_inventory_freshness("u", SimpleNamespace(query_params={})))
    assert result["total_count"] == result["evaluable_count"] == result["unknown_count"] == 0
    assert result["items"] == []


def test_batch_statistics_order_and_no_inventory_write(monkeypatch, tmp_path):
    main = import_main(monkeypatch)
    monkeypatch.setattr(main, "get_all_users", lambda: [{"id": "u"}])
    monkeypatch.setattr(main, "USER_DATA_BASE", str(tmp_path))
    directory = tmp_path / "u"; directory.mkdir()
    path = directory / "inventory.json"
    inventory = [
        {"id": "1", "name": "番茄", "add_time": "2020-01-01", "shelf_life": 1, "storage_type": "常温"},
        {"id": "2", "name": "未知食材"},
    ]
    original = json.dumps(inventory, ensure_ascii=False)
    path.write_text(original, encoding="utf-8")
    result = asyncio.run(main.evaluate_inventory_freshness("u", SimpleNamespace(query_params={})))
    assert [row["item_id"] for row in result["items"]] == ["1", "2"]
    assert result["total_count"] == 2 and result["expired_count"] == 1 and result["unknown_count"] == 1
    assert path.read_text(encoding="utf-8") == original


def test_batch_api_never_calls_cloud_models(monkeypatch, tmp_path):
    main = import_main(monkeypatch)
    monkeypatch.setattr(main, "get_all_users", lambda: [{"id": "u"}])
    monkeypatch.setattr(main, "USER_DATA_BASE", str(tmp_path))
    def forbidden(*args, **kwargs):
        raise AssertionError("cloud model must not be called")
    monkeypatch.setattr(main, "get_recipe_suggestion", forbidden)
    monkeypatch.setattr(main, "get_ingredients_from_qwen", forbidden)
    result = asyncio.run(main.evaluate_inventory_freshness("u", SimpleNamespace(query_params={})))
    assert result["items"] == []


def test_batch_uses_one_server_utc_time_and_strips_visual_input(monkeypatch, tmp_path):
    main = import_main(monkeypatch)
    fixed = datetime(2026, 9, 18, 12, 0, tzinfo=timezone.utc)

    class FixedDateTime(datetime):
        @classmethod
        def now(cls, tz=None):
            return fixed if tz is not None else fixed.replace(tzinfo=None)

    monkeypatch.setattr(main, "datetime", FixedDateTime)
    monkeypatch.setattr(main, "get_all_users", lambda: [{"id": "u"}])
    monkeypatch.setattr(main, "USER_DATA_BASE", str(tmp_path))
    directory = tmp_path / "u"; directory.mkdir()
    (directory / "inventory.json").write_text(json.dumps([{
        "id": "1", "name": "番茄", "add_time": "2026-09-16T12:00:00Z",
        "shelf_life": 4, "storage_type": "常温",
        "visual_freshness": {
            "score": 1, "confidence": 1, "source": "qwen_visual",
            "observed_at": fixed.isoformat(), "evidence": ["伪造证据"],
            "model_version": "forged",
        },
    }], ensure_ascii=False), encoding="utf-8")
    result = asyncio.run(main.evaluate_inventory_freshness("u", SimpleNamespace(query_params={})))
    assert result["evaluated_at"] == "2026-09-18T12:00:00Z"
    assert result["items"][0]["component_scores"]["V"] is None
    assert "V" in result["items"][0]["unavailable_components"]


@pytest.mark.parametrize("parameter", ["reference_time", "visual_freshness"])
def test_batch_rejects_client_trust_boundary_parameters(monkeypatch, parameter):
    main = import_main(monkeypatch)
    with pytest.raises(HTTPException) as error:
        asyncio.run(main.evaluate_inventory_freshness(
            "u", SimpleNamespace(query_params={parameter: "forged"}),
        ))
    assert error.value.status_code == 422


def test_public_openapi_excludes_internal_freshness_fields(monkeypatch):
    main = import_main(monkeypatch)
    schema = main.app.openapi()["components"]["schemas"]["FreshnessEvaluationRequest"]
    assert schema["additionalProperties"] is False
    assert "reference_time" not in schema["properties"]
    assert "visual_freshness" not in schema["properties"]


def test_legacy_recognition_freshness_is_freshfusion_compatible(monkeypatch):
    main = import_main(monkeypatch)
    detail = main.calculate_freshfusion({"ingredient_name": "番茄"})
    assert detail["freshness_label"] == "数据不足"
    assert detail["fresh_score"] is None


def test_freshness_routes_exist(monkeypatch):
    main = import_main(monkeypatch)
    paths = {(route.path, tuple(getattr(route, "methods", None) or [])) for route in main.app.routes}
    assert ("/api/freshness/evaluate", ("POST",)) in paths
    assert ("/api/users/{user_id}/inventory/freshness", ("GET",)) in paths


def test_production_source_has_no_legacy_name_hash():
    source = (Path(__file__).resolve().parents[1] / "app" / "main.py").read_text(encoding="utf-8")
    assert "name_hash" not in source
    assert "sum(ord" not in source
    assert "freshness_detail" in source
