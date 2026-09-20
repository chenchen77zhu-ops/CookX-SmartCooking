from datetime import datetime, timezone

import pytest

from app.services.freshness_service import (
    COOKING_TEMPERATURE_NOTE,
    calculate_freshfusion,
    calculate_storage_suitability,
    calculate_time_freshness,
    classify_freshness_level,
    normalize_storage_type,
    validate_visual_freshness_input,
)


NOW = datetime(2026, 9, 18, 12, 0, tzinfo=timezone.utc)


def test_service_layer_still_accepts_fixed_reference_time():
    result = calculate_freshfusion(
        {"name": "番茄", "add_time": "2026-09-16T12:00:00Z", "shelf_life": 4},
        reference_time=NOW,
    )
    assert result["component_scores"]["T"] == 0.5


def item(**values):
    return {"name": "番茄", **values}


def visual(**values):
    return {
        "score": 0.8, "source": "qwen_visual", "observed_at": "2026-09-18T08:00:00Z",
        "confidence": 0.9, "evidence": ["颜色正常"], "model_version": "qwen3-vl-plus",
        "ingredient_name": "番茄", **values,
    }


def test_add_time_and_shelf_life_calculate_t():
    result = calculate_time_freshness(item(add_time="2026-09-13T12:00:00Z", shelf_life=10), NOW)
    assert result["score"] == 0.5


def test_purchase_time_has_priority_over_add_time():
    result = calculate_time_freshness(item(purchase_time="2026-09-16T12:00:00Z", add_time="2026-09-17T12:00:00Z", shelf_life=4), NOW)
    assert result["score"] == 0.5


def test_expiry_date_calculation():
    result = calculate_time_freshness(item(add_time="2026-09-10T12:00:00Z", expiry_date="2026-09-20T12:00:00Z"), NOW)
    assert result["score"] == 0.2


def test_expired_is_available_zero():
    result = calculate_time_freshness(item(add_time="2026-09-01", shelf_life=5), NOW)
    assert result["score"] == 0 and result["expired"] is True


def test_exact_expiry_is_critical_not_unavailable():
    result = calculate_time_freshness(item(add_time="2026-09-17T12:00:00Z", expiry_date=NOW.isoformat()), NOW)
    assert result["score"] == 0 and result["critical"] is True and result["expired"] is False


@pytest.mark.parametrize("values", [
    {"add_time": "2026-09-19", "shelf_life": 2},
    {"add_time": "2026-09-10", "shelf_life": 0},
    {"add_time": "bad", "shelf_life": 5},
    {"add_time": "2026-09-10"},
    {},
])
def test_invalid_or_missing_time_is_unavailable(values):
    assert calculate_time_freshness(item(**values), NOW)["score"] is None


def test_no_default_seven_day_lifetime():
    result = calculate_freshfusion(item(add_time="2026-09-10"), NOW)
    assert result["component_scores"]["T"] is None


@pytest.mark.parametrize("source,target", [
    ("冷藏", "refrigerated"), ("fridge", "refrigerated"),
    ("冷冻", "frozen"), ("room", "room_temperature"), ("阴凉干燥", "cool_dry"),
])
def test_storage_normalization(source, target):
    assert normalize_storage_type(source) == target


def test_recommended_storage_scores_one():
    assert calculate_storage_suitability("菠菜", "冷藏")["score"] == 1.0


def test_acceptable_storage_scores_point_seven():
    assert calculate_storage_suitability("土豆", "常温")["score"] == 0.7


def test_unsuitable_storage_adds_risk():
    result = calculate_freshfusion(item(storage_type="冷冻"), NOW)
    assert result["component_scores"]["S"] == 0.3
    dangerous = calculate_freshfusion({"name": "豆腐", "storage_type": "常温"}, NOW)
    assert "storage_unsuitable" in dangerous["risk_flags"]


@pytest.mark.parametrize("name,storage", [("未知食材", "冷藏"), ("番茄", "月球仓")])
def test_unknown_food_or_storage_is_unavailable(name, storage):
    assert calculate_storage_suitability(name, storage)["score"] is None


def test_valid_visual_input_participates():
    result = calculate_freshfusion(item(add_time="2026-09-16T12:00:00Z", shelf_life=4, storage_type="常温", visual_freshness=visual()), NOW)
    assert result["component_scores"]["V"] == 0.8
    assert "V" in result["effective_weights"]


@pytest.mark.parametrize("change", [{"score": 1.1}, {"confidence": -0.1}, {"observed_at": "bad"}, {"evidence": []}, {"model_version": ""}])
def test_invalid_visual_input_is_rejected(change):
    payload = visual(**change)
    valid, _ = validate_visual_freshness_input(payload, "番茄", NOW)
    assert valid is None


def test_missing_visual_renormalizes_t_and_s():
    result = calculate_freshfusion(item(add_time="2026-09-16T12:00:00Z", shelf_life=4, storage_type="常温"), NOW)
    assert result["effective_weights"] == {"T": 0.733333, "S": 0.266667}
    assert sum(result["effective_weights"].values()) == pytest.approx(1)


def test_h_is_always_unavailable_and_explained():
    result = calculate_freshfusion({"name": "番茄", "temperature": 4, "cooking_temperature": 180}, NOW)
    assert result["component_scores"]["H"] is None and "H" in result["unavailable_components"]
    assert COOKING_TEMPERATURE_NOTE in result["data_quality_notes"]


def test_all_components_missing_returns_null_score():
    result = calculate_freshfusion({"name": "未知食材"}, NOW)
    assert result["fresh_score"] is None and result["freshness_level"] == "unknown"


def test_response_values_reproduce_score():
    result = calculate_freshfusion(item(add_time="2026-09-16T12:00:00Z", shelf_life=4, storage_type="常温"), NOW)
    reproduced = round(100 * sum(result["effective_weights"][k] * result["component_scores"][k] for k in result["effective_weights"]), 2)
    assert reproduced == result["fresh_score"]


def test_expired_level_overrides_storage_and_visual():
    result = calculate_freshfusion(item(add_time="2026-09-01", shelf_life=2, storage_type="常温", visual_freshness=visual()), NOW)
    assert result["freshness_level"] == "expired" and result["expired"] is True


@pytest.mark.parametrize("score,level", [(80, "fresh"), (79.99, "good"), (60, "good"), (59.99, "consume_soon"), (40, "consume_soon"), (39.99, "high_risk"), (0, "high_risk"), (None, "unknown")])
def test_level_boundaries(score, level):
    assert classify_freshness_level(score)[0] == level


def test_confidence_is_not_fresh_score_and_only_t_not_high():
    result = calculate_freshfusion(item(add_time="2026-09-16", shelf_life=10), NOW)
    assert result["confidence_score"] != result["fresh_score"]
    assert result["confidence_level"] == "medium"


def test_confidence_increases_with_sources():
    only_t = calculate_freshfusion(item(add_time="2026-09-16", shelf_life=10), NOW)
    time_storage = calculate_freshfusion(item(add_time="2026-09-16", shelf_life=10, storage_type="常温"), NOW)
    all_sources = calculate_freshfusion(item(add_time="2026-09-16", shelf_life=10, storage_type="常温", visual_freshness=visual()), NOW)
    assert only_t["confidence_score"] < time_storage["confidence_score"] < all_sources["confidence_score"]
    assert all_sources["confidence_level"] == "high"


def test_explicit_visual_spoilage_evidence_sets_flag():
    result = calculate_freshfusion(item(visual_freshness=visual(evidence=["发现霉变"])), NOW)
    assert "visual_spoilage_indicators" in result["risk_flags"]
