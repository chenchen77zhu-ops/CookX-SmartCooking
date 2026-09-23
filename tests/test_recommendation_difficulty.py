import json
import math
import sys
import types
from datetime import datetime, timedelta
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from app.services import recommendation_service as service


NOW = datetime(2026, 9, 23, 12, 0, 0)


def ingredient(name, amount=1, unit="个", required=True):
    return {"name": name, "amount": amount, "unit": unit, "required": required}


def recipe(recipe_id, difficulty="简单", ingredients=None, estimated_cost=None):
    result = {
        "id": recipe_id,
        "name": f"{recipe_id}测试菜",
        "ingredients": ingredients or [ingredient("番茄")],
        "optional_ingredients": [],
        "cooking_time": 20,
        "estimated_cost": estimated_cost,
        "method": "炒",
        "tags": [],
        "nutrition": None,
        "steps": ["仅为测试夹具"],
    }
    if difficulty is not None:
        result["difficulty"] = difficulty
    return result


def inventory_item(name="番茄", age_days=None, shelf_life=None):
    result = {"name": name, "quantity": 2}
    if age_days is not None:
        result["add_time"] = (NOW - timedelta(days=age_days)).isoformat()
    if shelf_life is not None:
        result["shelf_life"] = shelf_life
    return result


@pytest.mark.parametrize(
    "target,recipe_level,expected",
    [
        ("easy", "easy", 1.0), ("easy", "medium", 0.5), ("easy", "hard", 0.0),
        ("medium", "easy", 0.5), ("medium", "medium", 1.0), ("medium", "hard", 0.5),
        ("hard", "easy", 0.0), ("hard", "medium", 0.5), ("hard", "hard", 1.0),
    ],
)
def test_difficulty_match_matrix(target, recipe_level, expected):
    assert service.calculate_difficulty_match(recipe_level, target) == expected


@pytest.mark.parametrize(
    "source,expected",
    [
        ("easy", "easy"), ("简单", "easy"), ("容易", "easy"),
        ("medium", "medium"), ("中等", "medium"), ("一般", "medium"),
        ("hard", "hard"), ("困难", "hard"), ("较难", "hard"),
    ],
)
def test_normalize_only_explicit_difficulty_aliases(source, expected):
    assert service.normalize_recipe_difficulty(source) == expected


@pytest.mark.parametrize("source", [None, "", "复杂", "30分钟", 1, True, [], {}])
def test_unknown_difficulty_is_not_inferred(source):
    assert service.normalize_recipe_difficulty(source) is None


def test_difficulty_score_is_bounded_and_deterministic():
    first = [
        service.calculate_difficulty_match(recipe_level, target)
        for recipe_level in service.DIFFICULTY_LEVELS
        for target in service.DIFFICULTY_LEVELS
    ]
    second = [
        service.calculate_difficulty_match(recipe_level, target)
        for recipe_level in service.DIFFICULTY_LEVELS
        for target in service.DIFFICULTY_LEVELS
    ]
    assert first == second
    assert all(0 <= value <= 1 for value in first)


def test_missing_target_keeps_d_unavailable_without_treating_recipe_as_easy():
    result = service.score_recipe(recipe("a", "简单"), [inventory_item()], now=NOW)
    assert result["component_scores"]["D"] is None
    assert "D" in result["unavailable_components"]
    assert "D" not in result["effective_weights"]
    assert result["recipe_difficulty"] == "easy"
    assert result["difficulty_target"] is None
    assert result["difficulty_status"] == "target_unavailable"


@pytest.mark.parametrize("difficulty", [None, "未知", 1, True])
def test_missing_or_unknown_recipe_difficulty_keeps_d_unavailable(difficulty):
    result = service.score_recipe(
        recipe("a", difficulty), [inventory_item()],
        preferences={"difficulty_target": "easy"}, now=NOW,
    )
    assert result["component_scores"]["D"] is None
    assert "D" in result["unavailable_components"]
    assert "D" not in result["effective_weights"]
    assert result["recipe_difficulty"] is None
    assert result["difficulty_status"] == "recipe_difficulty_unavailable"


def test_available_d_enters_weights_and_total_is_reproducible():
    result = service.score_recipe(
        recipe("a", "中等"), [inventory_item()],
        preferences={"difficulty_target": "easy"}, now=NOW,
    )
    assert result["component_scores"]["D"] == 0.5
    assert result["difficulty_status"] == "available"
    assert "D" in result["effective_weights"]
    assert sum(result["effective_weights"].values()) == pytest.approx(1.0, abs=0.000001)
    positive = 100 * sum(
        weight * result["component_scores"][key]
        for key, weight in result["effective_weights"].items()
    )
    penalty = 100 * result["missing_penalty_weight"] * result["component_scores"]["M"]
    assert result["total_score"] == round(max(0, min(100, positive - penalty)), 2)


def test_zero_d_weight_reports_disabled_and_does_not_participate():
    result = service.score_recipe(
        recipe("a", "简单"), [inventory_item()],
        preferences={"difficulty_target": "easy"}, weights={"D": 0}, now=NOW,
    )
    assert result["component_scores"]["D"] == 1.0
    assert result["difficulty_status"] == "disabled_by_weight"
    assert "D" not in result["effective_weights"]
    assert service.algorithm_version_for_recommendations([result]) == "multi_objective_v1"


def test_each_recipe_normalizes_according_to_its_available_components():
    results = service.recommend_recipes(
        [inventory_item()], preferences={"difficulty_target": "easy"},
        recipes=[recipe("known", "简单"), recipe("unknown", None)], now=NOW,
    )
    by_id = {item["recipe_id"]: item for item in results}
    assert "D" in by_id["known"]["effective_weights"]
    assert "D" not in by_id["unknown"]["effective_weights"]
    assert all(sum(item["effective_weights"].values()) == pytest.approx(1.0, abs=0.000001) for item in results)


def test_no_target_preserves_a5_scores_ranking_and_weights():
    recipes = [recipe("b", "中等"), recipe("a", "简单")]
    current = service.recommend_recipes([inventory_item()], recipes=recipes, now=NOW)
    a5_weights = {"I": 0.35, "F": 0.25, "P": 0.20, "W": 0.20, "B": 0.15}
    explicit_a5 = service.recommend_recipes(
        [inventory_item()], recipes=recipes, weights=a5_weights, now=NOW,
    )
    comparable = lambda items: [
        (item["recipe_id"], item["total_score"], item["effective_weights"])
        for item in items
    ]
    assert comparable(current) == comparable(explicit_a5)
    assert [item["recipe_id"] for item in current] == ["a", "b"]


def test_target_changes_only_eligible_candidate_ranking():
    recipes = [recipe("hard", "困难"), recipe("easy", "简单")]
    results = service.recommend_recipes(
        [inventory_item()], preferences={"difficulty_target": "hard"},
        weights={"I": 0, "F": 0, "P": 0, "W": 0, "B": 0, "D": 1},
        recipes=recipes, now=NOW,
    )
    assert [item["recipe_id"] for item in results] == ["hard", "easy"]


def test_difficulty_cannot_make_zero_inventory_match_eligible():
    results = service.recommend_recipes(
        [inventory_item("番茄")], preferences={"difficulty_target": "easy"},
        recipes=[recipe("potato", "简单", [ingredient("土豆")])], now=NOW,
    )
    assert results == []


def test_difficulty_cannot_make_expired_inventory_eligible():
    results = service.recommend_recipes(
        [inventory_item(age_days=11, shelf_life=10)],
        preferences={"difficulty_target": "easy"},
        recipes=[recipe("a", "简单")], now=NOW,
    )
    assert results == []


def test_b_formula_and_version_remain_unchanged_when_d_is_absent():
    result = service.score_recipe(
        recipe("a", "简单", estimated_cost=20), [inventory_item()],
        preferences={"budget": 50}, now=NOW,
    )
    assert result["component_scores"]["B"] == 0.6
    assert result["component_scores"]["D"] is None
    assert service.algorithm_version_for_recommendations([result]) == "multi_objective_v2"


def test_d_participation_has_version_precedence_over_b():
    result = service.score_recipe(
        recipe("a", "简单", estimated_cost=20), [inventory_item()],
        preferences={"budget": 50, "difficulty_target": "easy"}, now=NOW,
    )
    assert {"B", "D"} <= set(result["effective_weights"])
    assert service.algorithm_version_for_recommendations([result]) == "multi_objective_v3"


def test_production_recipes_have_only_explicit_supported_difficulty_and_no_test_pollution():
    recipes = service.load_recipes()
    assert len(recipes) == 22
    assert {item["difficulty"] for item in recipes} == {"简单", "中等"}
    assert all(service.normalize_recipe_difficulty(item["difficulty"]) in {"easy", "medium"} for item in recipes)
    assert all("测试" not in item["name"] for item in recipes)
    assert all(item["estimated_cost"] is None for item in recipes)


def _import_main_without_real_yolo(monkeypatch):
    class FakeYOLO:
        def __init__(self, *args, **kwargs):
            self.names = {}

    monkeypatch.setitem(sys.modules, "ultralytics", types.SimpleNamespace(YOLO=FakeYOLO))
    sys.modules.pop("app.main", None)
    import app.main as main
    return main


def _http_client(monkeypatch, tmp_path, recipes):
    main = _import_main_without_real_yolo(monkeypatch)
    user_id = "difficulty-api-user"
    monkeypatch.setattr(main, "get_all_users", lambda: [{"id": user_id}])
    monkeypatch.setattr(main, "USER_DATA_BASE", str(tmp_path))
    monkeypatch.setattr(main, "load_recipes", lambda: recipes)
    user_dir = tmp_path / user_id
    user_dir.mkdir(exist_ok=True)
    (user_dir / "inventory.json").write_text(
        json.dumps([inventory_item()], ensure_ascii=False), encoding="utf-8",
    )
    return TestClient(main.app), main, user_id


def test_openapi_exposes_difficulty_target_and_preserves_routes(monkeypatch):
    main = _import_main_without_real_yolo(monkeypatch)
    openapi = main.app.openapi()
    target = openapi["components"]["schemas"]["RecommendationRequest"]["properties"]["difficulty_target"]
    assert set(target["anyOf"][0]["enum"]) == {"easy", "medium", "hard"}
    assert "用户期望" in target["description"]
    paths = openapi["paths"]
    assert "post" in paths["/api/recommendations"]
    assert "get" in paths["/api/recommend-recipe"]


@pytest.mark.parametrize("target", ["easy", "medium", "hard"])
def test_real_post_accepts_each_explicit_target(monkeypatch, tmp_path, target):
    client, _, user_id = _http_client(monkeypatch, tmp_path, [recipe("a", "简单")])
    response = client.post("/api/recommendations", json={"user_id": user_id, "difficulty_target": target})
    assert response.status_code == 200
    assert response.json()["recommendations"][0]["difficulty_target"] == target


@pytest.mark.parametrize("target", ["Easy", "简单", "unknown", 1, True, [], {}])
def test_real_post_rejects_invalid_difficulty_target(monkeypatch, tmp_path, target):
    client, _, user_id = _http_client(monkeypatch, tmp_path, [recipe("a", "简单")])
    response = client.post("/api/recommendations", json={"user_id": user_id, "difficulty_target": target})
    assert response.status_code == 422


@pytest.mark.parametrize("weight", [-0.1, 1.1, "0.1", True, float("nan"), float("inf")])
def test_real_post_rejects_invalid_d_weight(monkeypatch, tmp_path, weight):
    client, _, user_id = _http_client(monkeypatch, tmp_path, [recipe("a", "简单")])
    payload = {"user_id": user_id, "difficulty_target": "easy", "weights": {"D": weight}}
    if isinstance(weight, float) and not math.isfinite(weight):
        response = client.post(
            "/api/recommendations", content=json.dumps(payload, allow_nan=True),
            headers={"content-type": "application/json"},
        )
    else:
        response = client.post("/api/recommendations", json=payload)
    assert response.status_code == 422


def test_real_post_serializes_available_d_and_returns_v3(monkeypatch, tmp_path):
    client, _, user_id = _http_client(monkeypatch, tmp_path, [recipe("a", "中等")])
    response = client.post(
        "/api/recommendations", json={"user_id": user_id, "difficulty_target": "easy"},
    )
    assert response.status_code == 200
    body = response.json()
    assert body["algorithm_version"] == "multi_objective_v3"
    item = body["recommendations"][0]
    assert item["component_scores"]["D"] == 0.5
    assert item["recipe_difficulty"] == "medium"
    assert item["difficulty_target"] == "easy"
    assert item["difficulty_status"] == "available"
    assert item["difficulty_data_notes"]


def test_old_real_post_remains_successful_v1(monkeypatch, tmp_path):
    client, _, user_id = _http_client(monkeypatch, tmp_path, [recipe("a", "简单")])
    response = client.post("/api/recommendations", json={"user_id": user_id})
    assert response.status_code == 200
    body = response.json()
    assert body["algorithm_version"] == "multi_objective_v1"
    item = body["recommendations"][0]
    assert item["component_scores"]["D"] is None
    assert item["difficulty_status"] == "target_unavailable"


def test_nested_or_undeclared_target_cannot_accidentally_activate_d(monkeypatch, tmp_path):
    client, _, user_id = _http_client(monkeypatch, tmp_path, [recipe("a", "简单")])
    nested = client.post(
        "/api/recommendations",
        json={"user_id": user_id, "preferences": {"difficulty_target": "easy"}},
    )
    assert nested.status_code == 200
    assert nested.json()["algorithm_version"] == "multi_objective_v1"
    extra = client.post("/api/recommendations", json={"user_id": user_id, "difficulty": "easy"})
    assert extra.status_code == 422
