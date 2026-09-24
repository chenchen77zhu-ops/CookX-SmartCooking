import asyncio
import inspect
import json
import math
import sys
import types
from datetime import datetime, timedelta
from pathlib import Path

import pytest
from fastapi import HTTPException
from fastapi.testclient import TestClient
from pydantic import ValidationError

from app.services import recommendation_service as service


NOW = datetime(2026, 9, 17, 12, 0, 0)
ROOT = Path(__file__).resolve().parents[1]


def ingredient(name, amount=1, unit="个", required=True):
    return {"name": name, "amount": amount, "unit": unit, "required": required}


def recipe(recipe_id, name, required, tags=None, optional=None, estimated_cost=None):
    return {
        "id": recipe_id, "name": name, "ingredients": required,
        "optional_ingredients": optional or [], "cooking_time": 20,
        "difficulty": "简单", "estimated_cost": estimated_cost, "method": "炒",
        "tags": tags or [], "nutrition": None, "steps": ["完成烹饪"],
    }


def inventory_item(name, quantity=1, age_days=None, shelf_life=None):
    item = {"name": name, "quantity": quantity}
    if age_days is not None:
        item["add_time"] = (NOW - timedelta(days=age_days)).isoformat()
    if shelf_life is not None:
        item["shelf_life"] = shelf_life
    return item


def test_recipe_dataset_has_at_least_20_unique_complete_records():
    recipes = service.load_recipes()
    assert len(recipes) >= 20
    assert len({item["id"] for item in recipes}) == len(recipes)
    required_fields = {"id", "name", "ingredients", "optional_ingredients", "cooking_time", "difficulty", "estimated_cost", "method", "tags", "nutrition", "steps"}
    assert all(required_fields <= item.keys() for item in recipes)
    assert all({"name", "amount", "unit", "required"} <= ing.keys() for item in recipes for ing in item["ingredients"])


@pytest.mark.parametrize("source,target", [("西红柿", "番茄"), ("马铃薯", "土豆"), ("洋芋", "土豆"), ("蛋", "鸡蛋"), ("瘦肉", "猪肉"), ("鸡脯肉", "鸡胸肉"), ("鸡胸", "鸡胸肉")])
def test_ingredient_synonyms(source, target):
    assert service.normalize_ingredient(source) == target


def test_complete_match_scores_higher_and_missing_is_penalized():
    target = recipe("a", "双拼", [ingredient("番茄"), ingredient("鸡蛋")])
    complete = service.score_recipe(target, [inventory_item("番茄", 2), inventory_item("鸡蛋", 3)], now=NOW)
    partial = service.score_recipe(target, [inventory_item("番茄", 2)], now=NOW)
    assert complete["total_score"] > partial["total_score"]
    assert partial["component_scores"]["M"] == 0.5
    assert partial["missing_required_ingredients"] == ["鸡蛋"]


def test_expiring_real_time_increases_f_score():
    target = recipe("a", "番茄菜", [ingredient("番茄")])
    fresh = service.score_recipe(target, [inventory_item("番茄", age_days=1, shelf_life=10)], now=NOW)
    expiring = service.score_recipe(target, [inventory_item("番茄", age_days=9, shelf_life=10)], now=NOW)
    assert expiring["component_scores"]["F"] > fresh["component_scores"]["F"]
    assert expiring["expiring_ingredients_used"] == ["番茄"]


def test_expired_food_is_unsafe_and_not_matched_or_prioritized():
    target = recipe("a", "番茄菜", [ingredient("番茄")])
    result = service.score_recipe(target, [inventory_item("番茄", age_days=11, shelf_life=10)], now=NOW)
    assert result["unsafe_or_expired_ingredients"] == ["番茄"]
    assert result["matched_ingredients"] == []
    assert result["expiring_ingredients_used"] == []
    assert result["component_scores"]["F"] is None


def test_preferences_change_ranking():
    recipes = [
        recipe("a", "清淡番茄", [ingredient("番茄")], ["清淡"]),
        recipe("b", "酸甜番茄", [ingredient("番茄")], ["酸甜"]),
    ]
    results = service.recommend_recipes([inventory_item("番茄")], preferences={"taste": "酸甜"}, recipes=recipes, now=NOW)
    assert results[0]["recipe_id"] == "b"


def test_allergen_or_taboo_hard_filters_recipe():
    recipes = [recipe("a", "花生菜", [ingredient("花生")]), recipe("b", "番茄菜", [ingredient("番茄")])]
    results = service.recommend_recipes([inventory_item("花生"), inventory_item("番茄")], preferences={"allergens": ["花生"]}, recipes=recipes, now=NOW)
    assert [item["recipe_id"] for item in results] == ["b"]


def test_missing_components_are_unavailable_and_weights_renormalize():
    target = recipe("a", "番茄菜", [ingredient("番茄")])
    result = service.score_recipe(target, [inventory_item("番茄")], now=NOW)
    assert {"F", "P", "B", "D", "N"} <= set(result["unavailable_components"])
    assert set(result["effective_weights"]) == {"I", "W"}
    assert sum(result["effective_weights"].values()) == pytest.approx(1, abs=0.000001)


def test_explicit_test_cost_and_budget_enable_b_without_inference():
    target = recipe("a", "测试菜谱", [ingredient("番茄")], estimated_cost=20)
    result = service.score_recipe(target, [inventory_item("番茄")], preferences={"budget": 50}, now=NOW)
    assert result["component_scores"]["B"] == 0.6
    assert "B" in result["effective_weights"]
    assert "B" not in result["unavailable_components"]
    assert result["estimated_cost"] == 20
    assert result["budget"] == 50
    assert result["currency"] == "CNY"
    assert result["cost_status"] == "available"
    assert result["cost_data_notes"]


@pytest.mark.parametrize("estimated_cost", [None, "", -1, float("nan"), True])
def test_missing_or_invalid_recipe_cost_keeps_b_unavailable(estimated_cost):
    target = recipe("a", "测试菜谱", [ingredient("番茄")], estimated_cost=estimated_cost)
    result = service.score_recipe(target, [inventory_item("番茄")], preferences={"budget": 50}, now=NOW)
    assert result["component_scores"]["B"] is None
    assert "B" in result["unavailable_components"]
    assert "B" not in result["effective_weights"]


@pytest.mark.parametrize("budget", [None, 0, -1, float("nan"), True])
def test_missing_or_invalid_budget_keeps_b_unavailable(budget):
    target = recipe("a", "测试菜谱", [ingredient("番茄")], estimated_cost=20)
    preferences = {} if budget is None else {"budget": budget}
    result = service.score_recipe(target, [inventory_item("番茄")], preferences=preferences, now=NOW)
    assert result["component_scores"]["B"] is None
    assert "B" in result["unavailable_components"]


def test_production_recipes_do_not_contain_fabricated_costs():
    assert all(item["estimated_cost"] is None for item in service.load_recipes())


def test_cost_weight_changes_ranking_only_for_explicit_test_prices():
    recipes = [
        recipe("expensive", "高成本测试菜", [ingredient("番茄")], estimated_cost=40),
        recipe("affordable", "低成本测试菜", [ingredient("番茄")], estimated_cost=10),
    ]
    results = service.recommend_recipes(
        [inventory_item("番茄")], preferences={"budget": 50},
        weights={"I": 0, "F": 0, "P": 0, "W": 0, "B": 1},
        recipes=recipes, now=NOW,
    )
    assert [item["recipe_id"] for item in results] == ["affordable", "expensive"]


def test_no_budget_preserves_a4_scores_and_ranking():
    priced = [
        recipe("b", "高成本测试菜", [ingredient("番茄")], estimated_cost=40),
        recipe("a", "低成本测试菜", [ingredient("番茄")], estimated_cost=10),
    ]
    unpriced = [{**item, "estimated_cost": None} for item in priced]
    priced_results = service.recommend_recipes([inventory_item("番茄")], recipes=priced, now=NOW)
    baseline_results = service.recommend_recipes([inventory_item("番茄")], recipes=unpriced, now=NOW)
    comparable = lambda results: [
        (item["recipe_id"], item["total_score"], item["effective_weights"])
        for item in results
    ]
    assert comparable(priced_results) == comparable(baseline_results)
    assert all(item["component_scores"]["B"] is None for item in priced_results)


def test_cost_version_helper_switches_only_when_b_participates():
    unavailable = service.score_recipe(recipe("a", "无价格", [ingredient("番茄")]), [inventory_item("番茄")], now=NOW)
    available = service.score_recipe(
        recipe("a", "有价格", [ingredient("番茄")], estimated_cost=20),
        [inventory_item("番茄")], preferences={"budget": 50}, now=NOW,
    )
    assert service.algorithm_version_for_recommendations([unavailable]) == "multi_objective_v1"
    assert service.algorithm_version_for_recommendations([available]) == "multi_objective_v2"


def _http_recommendation_client(monkeypatch, tmp_path, recipes):
    main = _import_main_without_loading_real_yolo(monkeypatch)
    user_id = "cost-api-user"
    monkeypatch.setattr(main, "get_all_users", lambda: [{"id": user_id}])
    monkeypatch.setattr(main, "USER_DATA_BASE", str(tmp_path))
    monkeypatch.setattr(main, "load_recipes", lambda: recipes)
    user_dir = tmp_path / user_id
    user_dir.mkdir(exist_ok=True)
    (user_dir / "inventory.json").write_text(
        json.dumps([inventory_item("番茄")], ensure_ascii=False), encoding="utf-8",
    )
    return TestClient(main.app), main, user_id


def test_openapi_formally_exposes_strict_positive_finite_budget(monkeypatch):
    main = _import_main_without_loading_real_yolo(monkeypatch)
    schema = main.app.openapi()["components"]["schemas"]["RecommendationRequest"]
    budget_schema = schema["properties"]["budget"]
    assert budget_schema["anyOf"][0]["type"] == "number"
    assert budget_schema["anyOf"][0]["exclusiveMinimum"] == 0
    assert schema["additionalProperties"] is False


def test_real_post_budget_reaches_service_and_serializes_cost(monkeypatch, tmp_path):
    priced_recipe = recipe("priced", "有价格测试菜", [ingredient("番茄")], estimated_cost=20)
    client, _, user_id = _http_recommendation_client(monkeypatch, tmp_path, [priced_recipe])
    response = client.post("/api/recommendations", json={"user_id": user_id, "budget": 50})
    assert response.status_code == 200
    body = response.json()
    assert body["algorithm_version"] == "multi_objective_v2"
    item = body["recommendations"][0]
    assert item["component_scores"]["B"] == 0.6
    assert item["effective_weights"]["B"] > 0
    assert item["estimated_cost"] == 20
    assert item["budget"] == 50
    assert item["currency"] == "CNY"
    assert item["cost_status"] == "available"
    assert item["cost_data_notes"]
    positive = 100 * sum(
        weight * item["component_scores"][key]
        for key, weight in item["effective_weights"].items()
    )
    penalty = 100 * item["missing_penalty_weight"] * item["component_scores"]["M"]
    assert item["total_score"] == round(max(0, min(100, positive - penalty)), 2)


@pytest.mark.parametrize("budget", [0, -1, "50", "NaN", "Infinity", float("nan"), float("inf")])
def test_real_post_rejects_invalid_budget(monkeypatch, tmp_path, budget):
    client, _, user_id = _http_recommendation_client(
        monkeypatch, tmp_path, [recipe("a", "测试菜", [ingredient("番茄")], estimated_cost=20)],
    )
    payload = {"user_id": user_id, "budget": budget}
    if isinstance(budget, float) and not math.isfinite(budget):
        response = client.post(
            "/api/recommendations",
            content=json.dumps(payload, allow_nan=True),
            headers={"content-type": "application/json"},
        )
    else:
        response = client.post("/api/recommendations", json=payload)
    assert response.status_code == 422


def test_old_real_post_without_budget_remains_v1_and_cost_unavailable(monkeypatch, tmp_path):
    priced_recipe = recipe("priced", "有价格测试菜", [ingredient("番茄")], estimated_cost=20)
    client, _, user_id = _http_recommendation_client(monkeypatch, tmp_path, [priced_recipe])
    response = client.post("/api/recommendations", json={"user_id": user_id})
    assert response.status_code == 200
    body = response.json()
    assert body["algorithm_version"] == "multi_objective_v1"
    item = body["recommendations"][0]
    assert item["component_scores"]["B"] is None
    assert "B" in item["unavailable_components"]
    assert "B" not in item["effective_weights"]
    assert item["estimated_cost"] == 20
    assert item["budget"] is None
    assert item["cost_status"] == "budget_unavailable"


def test_undeclared_or_nested_budget_cannot_accidentally_activate_b(monkeypatch, tmp_path):
    priced_recipe = recipe("priced", "有价格测试菜", [ingredient("番茄")], estimated_cost=20)
    client, _, user_id = _http_recommendation_client(monkeypatch, tmp_path, [priced_recipe])
    nested = client.post("/api/recommendations", json={"user_id": user_id, "preferences": {"budget": 50}})
    assert nested.status_code == 200
    assert nested.json()["algorithm_version"] == "multi_objective_v1"
    extra = client.post("/api/recommendations", json={"user_id": user_id, "budget_cny": 50})
    assert extra.status_code == 422


def test_custom_weights_change_ranking():
    recipes = [
        recipe("a", "库存匹配", [ingredient("番茄")], ["清淡"]),
        recipe("b", "偏好匹配", [ingredient("土豆"), ingredient("鸡蛋")], ["酸甜"]),
    ]
    stock = [inventory_item("番茄"), inventory_item("土豆")]
    prefs = {"taste": "酸甜"}
    ingredient_first = service.recommend_recipes(stock, preferences=prefs, weights={"I": 0.9, "P": 0.1, "F": 0, "W": 0}, recipes=recipes, now=NOW)
    preference_first = service.recommend_recipes(stock, preferences=prefs, weights={"I": 0.1, "P": 0.9, "F": 0, "W": 0}, recipes=recipes, now=NOW)
    assert ingredient_first[0]["recipe_id"] == "a"
    assert preference_first[0]["recipe_id"] == "b"


def test_same_input_is_deterministic_and_ties_use_recipe_id():
    recipes = [recipe("b", "二", [ingredient("番茄")]), recipe("a", "一", [ingredient("番茄")])]
    first = service.recommend_recipes([inventory_item("番茄")], recipes=recipes, now=NOW)
    second = service.recommend_recipes([inventory_item("番茄")], recipes=recipes, now=NOW)
    assert first == second
    assert [item["recipe_id"] for item in first] == ["a", "b"]


@pytest.mark.parametrize("weights", [{"I": -0.1}, {"F": 1.1}, {"X": 0.2}, {"I": 0, "F": 0, "P": 0, "W": 0}])
def test_invalid_weights_rejected(weights):
    with pytest.raises(ValueError):
        service.validate_weights(weights)


def _import_main_without_loading_real_yolo(monkeypatch):
    class FakeYOLO:
        def __init__(self, *args, **kwargs):
            self.names = {}

    monkeypatch.setitem(sys.modules, "ultralytics", types.SimpleNamespace(YOLO=FakeYOLO))
    sys.modules.pop("app.main", None)
    import app.main as main
    from legacy_storage_fixture import install_legacy_storage
    install_legacy_storage(monkeypatch, main)
    return main


def test_invalid_top_k_is_rejected_by_api_schema(monkeypatch):
    main = _import_main_without_loading_real_yolo(monkeypatch)
    with pytest.raises(ValidationError):
        main.RecommendationRequest(user_id="u", top_k=0)
    with pytest.raises(ValidationError):
        main.RecommendationRequest(user_id="u", top_k=21)


def test_unknown_user_returns_404(monkeypatch):
    main = _import_main_without_loading_real_yolo(monkeypatch)
    monkeypatch.setattr(main, "get_all_users", lambda: [])
    request = main.RecommendationRequest(user_id="missing")
    with pytest.raises(HTTPException) as error:
        asyncio.run(main.multi_objective_recommendations(request))
    assert error.value.status_code == 404


def test_api_rejects_invalid_weights(monkeypatch):
    main = _import_main_without_loading_real_yolo(monkeypatch)
    monkeypatch.setattr(main, "get_all_users", lambda: [{"id": "u"}])
    request = main.RecommendationRequest(user_id="u", weights={"I": 0, "F": 0, "P": 0, "W": 0})
    with pytest.raises(HTTPException) as error:
        asyncio.run(main.multi_objective_recommendations(request))
    assert error.value.status_code == 422


def test_new_api_does_not_call_deepseek_or_qwen(monkeypatch, tmp_path):
    main = _import_main_without_loading_real_yolo(monkeypatch)
    monkeypatch.setattr(main, "get_all_users", lambda: [{"id": "api-test-user"}])
    monkeypatch.setattr(main, "USER_DATA_BASE", str(tmp_path))
    user_dir = tmp_path / "api-test-user"
    user_dir.mkdir()
    (user_dir / "inventory.json").write_text(json.dumps([inventory_item("番茄")], ensure_ascii=False), encoding="utf-8")

    def forbidden(*args, **kwargs):
        raise AssertionError("cloud model must not be called")

    monkeypatch.setattr(main, "get_recipe_suggestion", forbidden)
    monkeypatch.setattr(main, "get_ingredients_from_qwen", forbidden)
    response = asyncio.run(main.multi_objective_recommendations(main.RecommendationRequest(user_id="api-test-user", top_k=2)))
    assert response["algorithm_version"] == service.ALGORITHM_VERSION
    assert len(response["recommendations"]) == 2


def test_new_algorithm_does_not_use_legacy_hash_freshness():
    source = inspect.getsource(service)
    assert "name_hash" not in source
    assert "sum(ord" not in source
    assert "random" not in source


def test_legacy_route_exists_and_main_imports(monkeypatch):
    main = _import_main_without_loading_real_yolo(monkeypatch)
    paths = {(route.path, tuple(getattr(route, "methods", None) or [])) for route in main.app.routes}
    assert any(path == "/api/recommend-recipe" and "GET" in methods for path, methods in paths)
    assert any(path == "/api/recommendations" and "POST" in methods for path, methods in paths)


def test_all_recommendation_scores_are_in_range():
    stock = [inventory_item("番茄", 3, 2, 7), inventory_item("鸡蛋", 4, 1, 15), inventory_item("土豆", 5)]
    results = service.recommend_recipes(stock, top_k=20, preferences={"taste": "酸甜"}, now=NOW)
    assert results
    assert all(0 <= item["total_score"] <= 100 for item in results)
    assert all(value is None or 0 <= value <= 1 for item in results for value in item["component_scores"].values())


def test_model_script_is_preserved():
    assert (ROOT / "test_model.py").is_file()


def test_effective_weights_are_not_empty():
    target = recipe("a", "番茄菜", [ingredient("番茄")])
    result = service.score_recipe(target, [inventory_item("番茄")], now=NOW)
    assert result["effective_weights"]


def test_effective_weights_sum_to_one():
    target = recipe("a", "番茄菜", [ingredient("番茄")], ["清淡"])
    result = service.score_recipe(target, [inventory_item("番茄")], preferences={"taste": "清淡"}, now=NOW)
    assert sum(result["effective_weights"].values()) == pytest.approx(1.0, abs=0.000001)


def test_unavailable_components_are_absent_from_effective_weights():
    target = recipe("a", "番茄菜", [ingredient("番茄")])
    result = service.score_recipe(target, [inventory_item("番茄")], now=NOW)
    assert set(result["effective_weights"]).isdisjoint(result["unavailable_components"])


def test_effective_weights_match_participating_components():
    target = recipe("a", "番茄菜", [ingredient("番茄")], ["清淡"])
    result = service.score_recipe(target, [inventory_item("番茄", age_days=5, shelf_life=10)], preferences={"taste": "清淡"}, now=NOW)
    expected = {key for key in service.POSITIVE_COMPONENTS if result["component_scores"][key] is not None}
    assert set(result["effective_weights"]) == expected


def test_every_normal_recommendation_has_a_reason():
    results = service.recommend_recipes([inventory_item("番茄")], top_k=20, now=NOW)
    assert results
    assert all(item["reasons"] for item in results)


def test_reasons_agree_with_matched_missing_and_expiring_lists():
    target = recipe("a", "番茄鸡蛋", [ingredient("番茄"), ingredient("鸡蛋")])
    result = service.score_recipe(
        target,
        [inventory_item("番茄", age_days=9, shelf_life=10)],
        now=NOW,
    )
    joined = "；".join(result["reasons"])
    assert "番茄" in joined
    assert "鸡蛋" in joined
    assert "临期但未过期" in joined
    assert "已过期" not in joined


def test_response_values_exactly_reproduce_total_score():
    target = recipe("a", "番茄菜", [ingredient("番茄")], ["清淡"])
    result = service.score_recipe(
        target,
        [inventory_item("番茄", age_days=7, shelf_life=10)],
        preferences={"taste": "清淡"},
        weights={"I": 0.4, "F": 0.25, "P": 0.2, "W": 0.15, "lambda": 0.15},
        now=NOW,
    )
    positive = 100 * sum(
        weight * result["component_scores"][key]
        for key, weight in result["effective_weights"].items()
    )
    penalty = 100 * result["missing_penalty_weight"] * result["component_scores"]["M"]
    assert result["total_score"] == round(max(0, min(100, positive - penalty)), 2)


def test_f_and_p_unavailable_still_normalize_remaining_weights():
    target = recipe("a", "番茄菜", [ingredient("番茄")])
    result = service.score_recipe(target, [inventory_item("番茄")], now=NOW)
    assert result["component_scores"]["F"] is None
    assert result["component_scores"]["P"] is None
    assert set(result["effective_weights"]) == {"I", "W"}
    assert sum(result["effective_weights"].values()) == pytest.approx(1.0, abs=0.000001)


def test_recipes_with_different_availability_get_distinct_weights():
    recipes = [
        recipe("a", "有时间数据", [ingredient("番茄")]),
        recipe("b", "无匹配时间数据", [ingredient("土豆")]),
    ]
    results = service.recommend_recipes(
        [inventory_item("番茄", age_days=5, shelf_life=10), inventory_item("土豆")],
        weights={"I": 0.5, "F": 0.5, "P": 0, "W": 0},
        recipes=recipes,
        now=NOW,
    )
    by_id = {item["recipe_id"]: item for item in results}
    assert set(by_id["a"]["effective_weights"]) == {"I", "F"}
    assert by_id["b"]["effective_weights"] == {"I": 1.0}


def test_api_serialization_preserves_weights_and_reasons(monkeypatch, tmp_path):
    main = _import_main_without_loading_real_yolo(monkeypatch)
    monkeypatch.setattr(main, "get_all_users", lambda: [{"id": "serialization-user"}])
    monkeypatch.setattr(main, "USER_DATA_BASE", str(tmp_path))
    user_dir = tmp_path / "serialization-user"
    user_dir.mkdir()
    (user_dir / "inventory.json").write_text(json.dumps([inventory_item("番茄")], ensure_ascii=False), encoding="utf-8")
    request = main.RecommendationRequest(
        user_id="serialization-user",
        top_k=2,
        preferences={"taste": "清淡", "duration": "under_30"},
    )
    response = asyncio.run(main.multi_objective_recommendations(request))
    serialized = json.loads(json.dumps(response, ensure_ascii=False))
    assert serialized["recommendations"]
    assert all(item["effective_weights"] for item in serialized["recommendations"])
    assert all(item["reasons"] for item in serialized["recommendations"])

def _call_api_with_inventory(monkeypatch, tmp_path, inventory, preferences=None):
    main = _import_main_without_loading_real_yolo(monkeypatch)
    user_id = "candidate-test-user"
    monkeypatch.setattr(main, "get_all_users", lambda: [{"id": user_id}])
    monkeypatch.setattr(main, "USER_DATA_BASE", str(tmp_path))
    user_dir = tmp_path / user_id
    user_dir.mkdir(exist_ok=True)
    (user_dir / "inventory.json").write_text(
        json.dumps(inventory, ensure_ascii=False),
        encoding="utf-8",
    )
    request = main.RecommendationRequest(
        user_id=user_id,
        top_k=20,
        preferences=preferences,
    )
    return asyncio.run(main.multi_objective_recommendations(request))


def test_empty_inventory_returns_no_recommendations(monkeypatch, tmp_path):
    response = _call_api_with_inventory(monkeypatch, tmp_path, [])
    assert response["recommendations"] == []


def test_empty_inventory_returns_inventory_required(monkeypatch, tmp_path):
    response = _call_api_with_inventory(monkeypatch, tmp_path, [])
    assert response["status"] == "inventory_required"
    assert response["message"] == "当前没有可用库存食材，请先添加或识别食材后再获取推荐。"


def test_empty_inventory_cannot_recommend_from_preferences_alone(monkeypatch, tmp_path):
    response = _call_api_with_inventory(
        monkeypatch,
        tmp_path,
        [],
        {"taste": "清淡", "preferred_ingredients": ["番茄"]},
    )
    assert response["recommendations"] == []
    assert response["eligible_recipe_count"] == 0


def test_zero_key_ingredient_match_is_filtered():
    recipes = [
        recipe("eligible", "番茄菜", [ingredient("番茄")]),
        recipe("ineligible", "土豆菜", [ingredient("土豆")], ["酸甜"]),
    ]
    results = service.recommend_recipes(
        [inventory_item("番茄")],
        preferences={"taste": "酸甜"},
        recipes=recipes,
        now=NOW,
    )
    assert [item["recipe_id"] for item in results] == ["eligible"]


def test_basic_seasoning_only_does_not_qualify_recipe():
    target = recipe("a", "盐拌番茄", [ingredient("盐"), ingredient("番茄")])
    eligible, filtered = service.filter_eligible_recipes(
        [inventory_item("盐")],
        [target],
        now=NOW,
    )
    assert eligible == []
    assert filtered == 1


def test_expired_required_ingredient_does_not_qualify_recipe():
    target = recipe("a", "番茄菜", [ingredient("番茄")])
    eligible, filtered = service.filter_eligible_recipes(
        [inventory_item("番茄", age_days=11, shelf_life=10)],
        [target],
        now=NOW,
    )
    assert eligible == []
    assert filtered == 1


def test_one_safe_required_ingredient_qualifies_recipe():
    target = recipe("a", "番茄鸡蛋", [ingredient("番茄"), ingredient("鸡蛋")])
    eligible, filtered = service.filter_eligible_recipes(
        [inventory_item("番茄", age_days=1, shelf_life=10)],
        [target],
        now=NOW,
    )
    assert [item["id"] for item in eligible] == ["a"]
    assert filtered == 0


def test_all_candidates_filtered_returns_no_eligible_status(monkeypatch, tmp_path):
    response = _call_api_with_inventory(
        monkeypatch,
        tmp_path,
        [inventory_item("香蕉")],
    )
    assert response["status"] == "no_eligible_recipes"
    assert response["recommendations"] == []


def test_normal_candidate_response_returns_success(monkeypatch, tmp_path):
    response = _call_api_with_inventory(
        monkeypatch,
        tmp_path,
        [inventory_item("番茄")],
    )
    assert response["status"] == "success"
    assert response["recommendations"]


def test_normal_response_includes_candidate_counts(monkeypatch, tmp_path):
    response = _call_api_with_inventory(
        monkeypatch,
        tmp_path,
        [inventory_item("番茄")],
    )
    assert response["eligible_recipe_count"] > 0
    assert response["filtered_recipe_count"] > 0
    assert response["eligible_recipe_count"] + response["filtered_recipe_count"] == len(service.load_recipes())


def test_filtered_candidate_score_remains_exactly_reproducible():
    target = recipe("a", "番茄鸡蛋", [ingredient("番茄"), ingredient("鸡蛋")], ["清淡"])
    result = service.recommend_recipes(
        [inventory_item("番茄", age_days=7, shelf_life=10)],
        preferences={"taste": "清淡"},
        weights={"I": 0.4, "F": 0.25, "P": 0.2, "W": 0.15, "lambda": 0.15},
        recipes=[target],
        now=NOW,
    )[0]
    positive = 100 * sum(
        weight * result["component_scores"][key]
        for key, weight in result["effective_weights"].items()
    )
    penalty = 100 * result["missing_penalty_weight"] * result["component_scores"]["M"]
    assert result["total_score"] == round(max(0, min(100, positive - penalty)), 2)


def test_preferences_only_rank_eligible_candidates():
    recipes = [
        recipe("a", "番茄清淡菜", [ingredient("番茄")], ["清淡"]),
        recipe("b", "番茄酸甜菜", [ingredient("番茄")], ["酸甜"]),
        recipe("c", "土豆酸甜菜", [ingredient("土豆")], ["酸甜"]),
    ]
    results = service.recommend_recipes(
        [inventory_item("番茄")],
        preferences={"taste": "酸甜"},
        recipes=recipes,
        now=NOW,
    )
    assert results[0]["recipe_id"] == "b"
    assert {item["recipe_id"] for item in results} == {"a", "b"}
