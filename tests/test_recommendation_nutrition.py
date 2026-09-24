import json
import math
import sys
import types
from datetime import datetime, timedelta

import pytest
from fastapi.testclient import TestClient

from app.services import recommendation_service as service


NOW = datetime(2026, 9, 23, 12, 0, 0)
DISCLAIMER = "营养评分仅基于现有结构化数据和用户目标进行辅助比较，不能替代营养师或医疗建议。"


def ingredient(name="番茄", amount=1, unit="个", required=True):
    return {"name": name, "amount": amount, "unit": unit, "required": required}


def recipe(recipe_id, nutrition=None, ingredients=None, difficulty="简单", estimated_cost=None):
    return {
        "id": recipe_id,
        "name": f"{recipe_id}营养测试夹具",
        "ingredients": ingredients or [ingredient()],
        "optional_ingredients": [],
        "cooking_time": 20,
        "difficulty": difficulty,
        "estimated_cost": estimated_cost,
        "method": "炒",
        "tags": [],
        "nutrition": nutrition,
        "steps": ["仅为隔离测试夹具"],
    }


def per_serving(**values):
    return {"basis": "per_serving", **values}


def inventory_item(name="番茄", age_days=None, shelf_life=None):
    item = {"name": name, "quantity": 2}
    if age_days is not None:
        item["add_time"] = (NOW - timedelta(days=age_days)).isoformat()
    if shelf_life is not None:
        item["shelf_life"] = shelf_life
    return item


@pytest.mark.parametrize(
    "value,limit,expected",
    [(600, 600, 1.0), (400, 600, 1.0), (800, 600, 0.75), (0, 600, 1.0)],
)
def test_max_nutrition_formula(value, limit, expected):
    assert service.score_max_nutrition(value, limit) == expected


@pytest.mark.parametrize(
    "value,goal,expected",
    [(25, 25, 1.0), (10, 25, 0.4), (40, 25, 1.0), (0, 25, 0.0)],
)
def test_min_nutrition_formula(value, goal, expected):
    assert service.score_min_nutrition(value, goal) == expected


@pytest.mark.parametrize(
    "metric,target_key,value,target,expected",
    [
        ("fat_g", "max_fat_g", 30, 20, 2 / 3),
        ("carbohydrates_g", "max_carbohydrates_g", 100, 80, 0.8),
        ("calories_kcal", "max_calories_kcal", 500, 600, 1.0),
        ("protein_g", "min_protein_g", 20, 25, 0.8),
    ],
)
def test_single_metric_match(metric, target_key, value, target, expected):
    score, components = service.calculate_nutrition_match(
        per_serving(**{metric: value}), {target_key: target},
    )
    assert score == pytest.approx(expected)
    assert components == {target_key: pytest.approx(expected)}


def test_multiple_targets_average_only_comparable_metrics():
    score, components = service.calculate_nutrition_match(
        per_serving(calories_kcal=800, protein_g=10),
        {"max_calories_kcal": 600, "min_protein_g": 20, "max_fat_g": 10},
    )
    assert components == {"max_calories_kcal": 0.75, "min_protein_g": 0.5}
    assert score == pytest.approx(0.625)


def test_no_comparable_metric_returns_none_not_zero():
    score, components = service.calculate_nutrition_match(
        per_serving(calories_kcal=500), {"min_protein_g": 20},
    )
    assert score is None
    assert components == {}


def test_nutrition_match_is_deterministic_bounded_and_reproducible():
    nutrition = per_serving(calories_kcal=700, protein_g=15, fat_g=10, carbohydrates_g=90)
    target = {
        "max_calories_kcal": 600, "min_protein_g": 25,
        "max_fat_g": 20, "max_carbohydrates_g": 80,
    }
    first = service.calculate_nutrition_match(nutrition, target)
    second = service.calculate_nutrition_match(nutrition, target)
    assert first == second
    assert 0 <= first[0] <= 1
    assert first[0] == pytest.approx(sum(first[1].values()) / len(first[1]))
    assert all(0 <= value <= 1 for value in first[1].values())


@pytest.mark.parametrize("nutrition", [None, {}, [], "nutrition", True])
def test_missing_or_non_object_nutrition_is_unavailable(nutrition):
    result = service.score_recipe(
        recipe("a", nutrition), [inventory_item()],
        preferences={"nutrition_target": {"max_calories_kcal": 600}}, now=NOW,
    )
    assert result["component_scores"]["N"] is None
    assert "N" in result["unavailable_components"]
    assert "N" not in result["effective_weights"]
    assert result["nutrition_status"] in {"recipe_nutrition_unavailable", "basis_unavailable"}


@pytest.mark.parametrize("basis", [None, "per_100g", "per_recipe", "unknown", 1])
def test_non_per_serving_basis_is_unavailable(basis):
    nutrition = {"calories_kcal": 500}
    if basis is not None:
        nutrition["basis"] = basis
    result = service.score_recipe(
        recipe("a", nutrition), [inventory_item()],
        preferences={"nutrition_target": {"max_calories_kcal": 600}}, now=NOW,
    )
    assert result["component_scores"]["N"] is None
    assert result["nutrition_status"] == "basis_unavailable"
    assert result["recipe_nutrition"] is None


@pytest.mark.parametrize("invalid", [-1, float("nan"), float("inf"), True, "500"])
def test_invalid_recipe_metric_is_excluded_not_treated_as_zero(invalid):
    validated = service.validate_recipe_nutrition(
        per_serving(calories_kcal=invalid, protein_g=20),
    )
    assert "calories_kcal" not in validated
    assert validated["protein_g"] == 20
    score, components = service.calculate_nutrition_match(
        per_serving(calories_kcal=invalid, protein_g=20),
        {"max_calories_kcal": 600, "min_protein_g": 40},
    )
    assert components == {"min_protein_g": 0.5}
    assert score == 0.5


def test_target_without_corresponding_valid_metric_has_no_comparable_status():
    result = service.score_recipe(
        recipe("a", per_serving(calories_kcal="500")), [inventory_item()],
        preferences={"nutrition_target": {"max_calories_kcal": 600}}, now=NOW,
    )
    assert result["component_scores"]["N"] is None
    assert result["nutrition_status"] == "no_comparable_metrics"
    assert result["nutrition_component_scores"] == {}


def test_missing_target_keeps_n_unavailable_without_inference():
    result = service.score_recipe(
        recipe("a", per_serving(calories_kcal=500)), [inventory_item()], now=NOW,
    )
    assert result["component_scores"]["N"] is None
    assert result["nutrition_target"] is None
    assert result["nutrition_status"] == "target_unavailable"
    assert "N" not in result["effective_weights"]


def test_available_n_enters_weights_and_total_is_reproducible():
    result = service.score_recipe(
        recipe("a", per_serving(calories_kcal=800, protein_g=10)), [inventory_item()],
        preferences={"nutrition_target": {"max_calories_kcal": 600, "min_protein_g": 20}}, now=NOW,
    )
    assert result["component_scores"]["N"] == 0.625
    assert result["nutrition_component_scores"] == {"max_calories_kcal": 0.75, "min_protein_g": 0.5}
    assert result["nutrition_status"] == "available"
    assert "N" in result["effective_weights"]
    assert sum(result["effective_weights"].values()) == pytest.approx(1.0, abs=0.000001)
    positive = 100 * sum(
        weight * result["component_scores"][key]
        for key, weight in result["effective_weights"].items()
    )
    penalty = 100 * result["missing_penalty_weight"] * result["component_scores"]["M"]
    assert result["total_score"] == round(max(0, min(100, positive - penalty)), 2)
    assert result["nutrition_disclaimer"] == DISCLAIMER


def test_zero_n_weight_is_disabled_and_does_not_trigger_v4():
    result = service.score_recipe(
        recipe("a", per_serving(calories_kcal=500)), [inventory_item()],
        preferences={"nutrition_target": {"max_calories_kcal": 600}},
        weights={"N": 0}, now=NOW,
    )
    assert result["component_scores"]["N"] == 1.0
    assert result["nutrition_status"] == "disabled_by_weight"
    assert "N" not in result["effective_weights"]
    assert service.algorithm_version_for_recommendations([result]) == "multi_objective_v1"


def test_each_recipe_normalizes_against_its_available_components():
    results = service.recommend_recipes(
        [inventory_item()],
        preferences={"nutrition_target": {"max_calories_kcal": 600}},
        recipes=[recipe("known", per_serving(calories_kcal=500)), recipe("missing", None)], now=NOW,
    )
    by_id = {item["recipe_id"]: item for item in results}
    assert "N" in by_id["known"]["effective_weights"]
    assert "N" not in by_id["missing"]["effective_weights"]
    assert all(sum(item["effective_weights"].values()) == pytest.approx(1.0, abs=0.000001) for item in results)


def test_no_nutrition_target_preserves_a6_ranking_scores_weights_and_version():
    recipes = [
        recipe("b", per_serving(calories_kcal=900)),
        recipe("a", per_serving(calories_kcal=400)),
    ]
    current = service.recommend_recipes([inventory_item()], recipes=recipes, now=NOW)
    a6_weights = {"I": 0.35, "F": 0.25, "P": 0.20, "W": 0.20, "B": 0.15, "D": 0.10}
    baseline = service.recommend_recipes([inventory_item()], recipes=recipes, weights=a6_weights, now=NOW)
    comparable = lambda items: [
        (item["recipe_id"], item["total_score"], item["effective_weights"])
        for item in items
    ]
    assert comparable(current) == comparable(baseline)
    assert service.algorithm_version_for_recommendations(current) == "multi_objective_v1"


def test_n_changes_only_eligible_candidate_ranking_and_ties_stay_deterministic():
    recipes = [
        recipe("b", per_serving(calories_kcal=800)),
        recipe("a", per_serving(calories_kcal=400)),
    ]
    results = service.recommend_recipes(
        [inventory_item()], preferences={"nutrition_target": {"max_calories_kcal": 600}},
        weights={"I": 0, "F": 0, "P": 0, "W": 0, "B": 0, "D": 0, "N": 1},
        recipes=recipes, now=NOW,
    )
    assert [item["recipe_id"] for item in results] == ["a", "b"]
    tied = service.recommend_recipes(
        [inventory_item()], preferences={"nutrition_target": {"max_calories_kcal": 600}},
        weights={"I": 0, "F": 0, "P": 0, "W": 0, "B": 0, "D": 0, "N": 1},
        recipes=[recipe("b", per_serving(calories_kcal=500)), recipe("a", per_serving(calories_kcal=500))], now=NOW,
    )
    assert [item["recipe_id"] for item in tied] == ["a", "b"]


def test_n_cannot_make_zero_match_or_expired_recipe_eligible():
    target = {"nutrition_target": {"max_calories_kcal": 600}}
    zero_match = service.recommend_recipes(
        [inventory_item("番茄")], preferences=target,
        recipes=[recipe("potato", per_serving(calories_kcal=500), [ingredient("土豆")])], now=NOW,
    )
    expired = service.recommend_recipes(
        [inventory_item(age_days=11, shelf_life=10)], preferences=target,
        recipes=[recipe("expired", per_serving(calories_kcal=500))], now=NOW,
    )
    assert zero_match == []
    assert expired == []


def test_b_and_d_formulas_and_versions_remain_unchanged():
    b_result = service.score_recipe(
        recipe("b", None, estimated_cost=20), [inventory_item()],
        preferences={"budget": 50}, now=NOW,
    )
    d_result = service.score_recipe(
        recipe("d", None, difficulty="中等"), [inventory_item()],
        preferences={"difficulty_target": "easy"}, now=NOW,
    )
    assert b_result["component_scores"]["B"] == 0.6
    assert service.algorithm_version_for_recommendations([b_result]) == "multi_objective_v2"
    assert d_result["component_scores"]["D"] == 0.5
    assert service.algorithm_version_for_recommendations([d_result]) == "multi_objective_v3"


def test_b_d_n_together_return_v4():
    result = service.score_recipe(
        recipe("all", per_serving(calories_kcal=500), difficulty="简单", estimated_cost=20),
        [inventory_item()],
        preferences={
            "budget": 50, "difficulty_target": "easy",
            "nutrition_target": {"max_calories_kcal": 600},
        }, now=NOW,
    )
    assert {"B", "D", "N"} <= set(result["effective_weights"])
    assert service.algorithm_version_for_recommendations([result]) == "multi_objective_v4"


def test_production_recipes_remain_unpriced_and_without_nutrition():
    recipes = service.load_recipes()
    assert len(recipes) == 22
    assert all(item["nutrition"] is None for item in recipes)
    assert all(item["estimated_cost"] is None for item in recipes)
    assert all("营养测试夹具" not in item["name"] for item in recipes)


def _import_main_without_real_yolo(monkeypatch):
    class FakeYOLO:
        def __init__(self, *args, **kwargs):
            self.names = {}

    monkeypatch.setitem(sys.modules, "ultralytics", types.SimpleNamespace(YOLO=FakeYOLO))
    sys.modules.pop("app.main", None)
    import app.main as main
    from legacy_storage_fixture import install_legacy_storage
    install_legacy_storage(monkeypatch, main)
    return main


def _http_client(monkeypatch, tmp_path, recipes):
    main = _import_main_without_real_yolo(monkeypatch)
    user_id = "nutrition-api-user"
    monkeypatch.setattr(main, "get_all_users", lambda: [{"id": user_id}])
    monkeypatch.setattr(main, "USER_DATA_BASE", str(tmp_path))
    monkeypatch.setattr(main, "load_recipes", lambda: recipes)
    user_dir = tmp_path / user_id
    user_dir.mkdir(exist_ok=True)
    (user_dir / "inventory.json").write_text(json.dumps([inventory_item()], ensure_ascii=False), encoding="utf-8")
    return TestClient(main.app), main, user_id


def test_openapi_exposes_strict_nutrition_target_units_and_routes(monkeypatch):
    main = _import_main_without_real_yolo(monkeypatch)
    openapi = main.app.openapi()
    request_property = openapi["components"]["schemas"]["RecommendationRequest"]["properties"]["nutrition_target"]
    target_ref = request_property["anyOf"][0]["$ref"].split("/")[-1]
    target_schema = openapi["components"]["schemas"][target_ref]
    assert set(target_schema["properties"]) == set(service.NUTRITION_TARGETS)
    assert target_schema["additionalProperties"] is False
    assert "kcal" in target_schema["properties"]["max_calories_kcal"]["description"]
    assert all("g" in target_schema["properties"][key]["description"] for key in ("min_protein_g", "max_fat_g", "max_carbohydrates_g"))
    assert "post" in openapi["paths"]["/api/recommendations"]
    assert "get" in openapi["paths"]["/api/recommend-recipe"]


@pytest.mark.parametrize(
    "target",
    [
        {"max_calories_kcal": 600},
        {"min_protein_g": 25},
        {"max_fat_g": 20},
        {"max_carbohydrates_g": 80},
        {"max_calories_kcal": 600, "min_protein_g": 25, "max_fat_g": 20, "max_carbohydrates_g": 80},
    ],
)
def test_real_post_accepts_valid_single_and_multiple_targets(monkeypatch, tmp_path, target):
    nutrition = per_serving(calories_kcal=500, protein_g=30, fat_g=18, carbohydrates_g=65)
    client, _, user_id = _http_client(monkeypatch, tmp_path, [recipe("a", nutrition)])
    response = client.post("/api/recommendations", json={"user_id": user_id, "nutrition_target": target})
    assert response.status_code == 200
    assert response.json()["algorithm_version"] == "multi_objective_v4"


@pytest.mark.parametrize("target", [{}, [], 1, True, "target", None])
def test_real_post_rejects_empty_or_non_object_target(monkeypatch, tmp_path, target):
    client, _, user_id = _http_client(monkeypatch, tmp_path, [recipe("a", per_serving(calories_kcal=500))])
    response = client.post("/api/recommendations", json={"user_id": user_id, "nutrition_target": target})
    assert response.status_code == 422


@pytest.mark.parametrize("invalid", [0, -1, "500", True, float("nan"), float("inf")])
def test_real_post_rejects_invalid_target_values(monkeypatch, tmp_path, invalid):
    client, _, user_id = _http_client(monkeypatch, tmp_path, [recipe("a", per_serving(calories_kcal=500))])
    payload = {"user_id": user_id, "nutrition_target": {"max_calories_kcal": invalid}}
    if isinstance(invalid, float) and not math.isfinite(invalid):
        response = client.post(
            "/api/recommendations", content=json.dumps(payload, allow_nan=True),
            headers={"content-type": "application/json"},
        )
    else:
        response = client.post("/api/recommendations", json=payload)
    assert response.status_code == 422


def test_real_post_rejects_unknown_target_field(monkeypatch, tmp_path):
    client, _, user_id = _http_client(monkeypatch, tmp_path, [recipe("a", per_serving(calories_kcal=500))])
    response = client.post(
        "/api/recommendations", json={"user_id": user_id, "nutrition_target": {"calories": 500}},
    )
    assert response.status_code == 422


@pytest.mark.parametrize("weight", [-0.1, 1.1, "0.1", True, float("nan"), float("inf")])
def test_real_post_rejects_invalid_n_weight(monkeypatch, tmp_path, weight):
    client, _, user_id = _http_client(monkeypatch, tmp_path, [recipe("a", per_serving(calories_kcal=500))])
    payload = {"user_id": user_id, "nutrition_target": {"max_calories_kcal": 600}, "weights": {"N": weight}}
    if isinstance(weight, float) and not math.isfinite(weight):
        response = client.post(
            "/api/recommendations", content=json.dumps(payload, allow_nan=True),
            headers={"content-type": "application/json"},
        )
    else:
        response = client.post("/api/recommendations", json=payload)
    assert response.status_code == 422


def test_real_post_serializes_nutrition_fields_and_reaches_service(monkeypatch, tmp_path):
    nutrition = per_serving(calories_kcal=800, protein_g=10)
    client, _, user_id = _http_client(monkeypatch, tmp_path, [recipe("a", nutrition)])
    target = {"max_calories_kcal": 600, "min_protein_g": 20}
    response = client.post("/api/recommendations", json={"user_id": user_id, "nutrition_target": target})
    assert response.status_code == 200
    body = response.json()
    item = body["recommendations"][0]
    assert body["algorithm_version"] == "multi_objective_v4"
    assert item["component_scores"]["N"] == 0.625
    assert item["nutrition_target"] == target
    assert item["recipe_nutrition"] == nutrition
    assert item["nutrition_basis"] == "per_serving"
    assert item["nutrition_status"] == "available"
    assert item["nutrition_component_scores"] == {"max_calories_kcal": 0.75, "min_protein_g": 0.5}
    assert item["nutrition_disclaimer"] == DISCLAIMER


def test_preferences_target_does_not_activate_n_and_old_post_stays_v1(monkeypatch, tmp_path):
    client, _, user_id = _http_client(monkeypatch, tmp_path, [recipe("a", per_serving(calories_kcal=500))])
    nested = client.post(
        "/api/recommendations",
        json={"user_id": user_id, "preferences": {"nutrition_target": {"max_calories_kcal": 600}}},
    )
    assert nested.status_code == 200
    assert nested.json()["algorithm_version"] == "multi_objective_v1"
    item = nested.json()["recommendations"][0]
    assert item["component_scores"]["N"] is None
    assert item["nutrition_status"] == "target_unavailable"


def test_real_production_recipes_keep_n_unavailable_with_target(monkeypatch, tmp_path):
    main = _import_main_without_real_yolo(monkeypatch)
    production_recipes = main.load_recipes()
    client, _, user_id = _http_client(monkeypatch, tmp_path, production_recipes)
    response = client.post(
        "/api/recommendations",
        json={"user_id": user_id, "nutrition_target": {"max_calories_kcal": 600}},
    )
    assert response.status_code == 200
    body = response.json()
    assert body["algorithm_version"] == "multi_objective_v1"
    assert all(item["component_scores"]["N"] is None for item in body["recommendations"])
    assert all(item["nutrition_status"] == "recipe_nutrition_unavailable" for item in body["recommendations"])
