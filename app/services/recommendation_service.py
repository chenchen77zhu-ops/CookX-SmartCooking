"""Deterministic, local multi-objective recipe recommendation service."""

from __future__ import annotations

import json
import math
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional, Tuple


ALGORITHM_VERSION = "multi_objective_v1"
DEFAULT_WEIGHTS = {"I": 0.35, "F": 0.25, "P": 0.20, "W": 0.20}
DEFAULT_MISSING_PENALTY = 0.15
POSITIVE_COMPONENTS = ("I", "F", "P", "W")
BASIC_SEASONINGS = {
    "盐", "食用油", "油", "水", "清水", "生抽", "老抽", "酱油", "醋", "白糖", "糖",
    "料酒", "淀粉", "胡椒粉", "花椒", "葱", "大葱", "小葱", "姜", "生姜", "蒜", "大蒜",
}

INGREDIENT_ALIASES = {
    "西红柿": "番茄", "小番茄": "番茄", "圣女果": "番茄", "tomato": "番茄",
    "马铃薯": "土豆", "洋芋": "土豆", "potato": "土豆",
    "蛋": "鸡蛋", "egg": "鸡蛋",
    "瘦肉": "猪肉", "猪瘦肉": "猪肉", "pork": "猪肉",
    "鸡脯肉": "鸡胸肉", "鸡胸": "鸡胸肉", "chicken breast": "鸡胸肉",
    "鸡肉": "鸡肉", "chicken": "鸡肉",
    "牛腩肉": "牛腩", "beef brisket": "牛腩",
    "胡萝卜": "胡萝卜", "carrot": "胡萝卜",
    "洋葱": "洋葱", "onion": "洋葱",
    "蒜": "大蒜", "garlic": "大蒜",
    "姜": "生姜", "ginger": "生姜",
    "青葱": "小葱", "香葱": "小葱", "葱花": "小葱",
    "豆腐": "豆腐", "tofu": "豆腐",
    "青椒": "青椒", "柿子椒": "青椒", "甜椒": "青椒",
    "西蓝花": "西兰花", "broccoli": "西兰花",
    "包菜": "圆白菜", "卷心菜": "圆白菜", "甘蓝": "圆白菜",
    "娃娃菜": "白菜", "大白菜": "白菜",
    "香菇": "香菇", "蘑菇": "蘑菇",
}


def clamp(value: float) -> float:
    return max(0.0, min(1.0, float(value)))


def normalize_ingredient(name: Any) -> str:
    normalized = str(name or "").strip().lower().replace(" ", "")
    return INGREDIENT_ALIASES.get(normalized, normalized)


def load_recipes(path: Optional[Path] = None) -> List[Dict[str, Any]]:
    recipe_path = path or Path(__file__).resolve().parents[1] / "data" / "recipes" / "recipes.json"
    with recipe_path.open("r", encoding="utf-8") as handle:
        recipes = json.load(handle)
    if not isinstance(recipes, list):
        raise ValueError("recipes.json must contain a JSON array")
    return recipes


def _number(value: Any) -> Optional[float]:
    if isinstance(value, bool):
        return None
    try:
        result = float(value)
    except (TypeError, ValueError):
        return None
    return result if math.isfinite(result) and result >= 0 else None


def _parse_datetime(value: Any) -> Optional[datetime]:
    if not value:
        return None
    text = str(value).strip().replace("Z", "+00:00")
    try:
        parsed = datetime.fromisoformat(text)
    except ValueError:
        return None
    if parsed.tzinfo is not None:
        parsed = parsed.astimezone(timezone.utc).replace(tzinfo=None)
    return parsed


def _expiry(item: Dict[str, Any]) -> Optional[Tuple[datetime, datetime]]:
    explicit = _parse_datetime(item.get("expiry_date"))
    base = _parse_datetime(item.get("purchase_time") or item.get("add_time"))
    if explicit:
        return (base or explicit, explicit)
    shelf_life = _number(item.get("shelf_life"))
    if base and shelf_life is not None:
        return base, base + timedelta(days=shelf_life)
    return None


def _inventory_index(inventory: Iterable[Dict[str, Any]], now: datetime) -> Dict[str, List[Dict[str, Any]]]:
    indexed: Dict[str, List[Dict[str, Any]]] = {}
    for original in inventory:
        item = dict(original)
        name = normalize_ingredient(item.get("name"))
        if not name:
            continue
        time_range = _expiry(item)
        item["_normalized_name"] = name
        item["_time_range"] = time_range
        item["_expired"] = bool(time_range and time_range[1] < now)
        indexed.setdefault(name, []).append(item)
    return indexed


def _recipe_ingredients(recipe: Dict[str, Any]) -> Tuple[List[Dict[str, Any]], List[Dict[str, Any]]]:
    required, optional = [], []
    for source in recipe.get("ingredients", []):
        target = dict(source)
        target["_name"] = normalize_ingredient(target.get("name"))
        (required if target.get("required", True) else optional).append(target)
    for source in recipe.get("optional_ingredients", []):
        target = dict(source)
        target["required"] = False
        target["_name"] = normalize_ingredient(target.get("name"))
        optional.append(target)
    return required, optional


def _available_batches(indexed: Dict[str, List[Dict[str, Any]]], name: str) -> List[Dict[str, Any]]:
    return [item for item in indexed.get(name, []) if not item["_expired"]]


def _quantity_satisfaction(ingredient: Dict[str, Any], batches: List[Dict[str, Any]]) -> Optional[float]:
    needed = _number(ingredient.get("amount"))
    unit = str(ingredient.get("unit") or "").strip().lower()
    if needed is None or needed == 0 or not unit:
        return None
    total = 0.0
    comparable = False
    for item in batches:
        inventory_unit = str(item.get("amount_unit") or item.get("unit") or "").strip().lower()
        amount = _number(item.get("amount"))
        if amount is not None and inventory_unit == unit:
            total += amount
            comparable = True
        elif unit in {"个", "颗", "根", "棵", "片", "块", "瓣"}:
            quantity = _number(item.get("quantity"))
            if quantity is not None:
                total += quantity
                comparable = True
    return clamp(total / needed) if comparable else None


def _preference_tokens(preferences: Dict[str, Any], keys: Iterable[str]) -> List[str]:
    result: List[str] = []
    for key in keys:
        value = preferences.get(key)
        if isinstance(value, str):
            result.extend(part.strip() for part in value.replace("，", ",").split(",") if part.strip())
        elif isinstance(value, list):
            result.extend(str(part).strip() for part in value if str(part).strip())
    return result


def validate_weights(weights: Optional[Dict[str, Any]]) -> Tuple[Dict[str, float], float]:
    supplied = weights or {}
    unknown = set(supplied) - {*POSITIVE_COMPONENTS, "lambda"}
    if unknown:
        raise ValueError(f"unknown weights: {', '.join(sorted(unknown))}")
    resolved = dict(DEFAULT_WEIGHTS)
    penalty = DEFAULT_MISSING_PENALTY
    for key, value in supplied.items():
        number = _number(value)
        if number is None or number > 1:
            raise ValueError(f"weight {key} must be between 0 and 1")
        if key == "lambda":
            penalty = number
        else:
            resolved[key] = number
    if sum(resolved.values()) <= 0:
        raise ValueError("at least one positive weight must be greater than zero")
    return resolved, penalty


def _blocked(recipe: Dict[str, Any], preferences: Dict[str, Any]) -> Optional[str]:
    blocked = {
        normalize_ingredient(item) for item in _preference_tokens(
            preferences,
            ("allergens", "allergies", "avoid_ingredients", "disliked_ingredients", "dislikedIngredients"),
        )
    }
    restrictions = {item.lower() for item in _preference_tokens(preferences, ("dietary_restrictions", "restrictions"))}
    names = {normalize_ingredient(item.get("name")) for item in recipe.get("ingredients", [])}
    names.update(normalize_ingredient(item.get("name")) for item in recipe.get("optional_ingredients", []))
    conflict = sorted(blocked & names)
    if conflict:
        return f"含明确过敏原或忌口食材：{', '.join(conflict)}"
    tags = {str(tag).strip().lower() for tag in recipe.get("tags", [])}
    if "素食" in restrictions or "vegetarian" in restrictions:
        if "素食" not in tags:
            return "不符合素食限制"
    if "纯素" in restrictions or "vegan" in restrictions:
        if "纯素" not in tags:
            return "不符合纯素限制"
    return None


def safe_inventory_names(
    inventory: List[Dict[str, Any]],
    now: Optional[datetime] = None,
) -> set:
    """Return normalized names for inventory batches that are not expired."""
    indexed = _inventory_index(inventory, now or datetime.now())
    return {
        name
        for name, batches in indexed.items()
        if any(not item["_expired"] for item in batches)
    }


def filter_eligible_recipes(
    inventory: List[Dict[str, Any]],
    recipes: Optional[List[Dict[str, Any]]] = None,
    preferences: Optional[Dict[str, Any]] = None,
    now: Optional[datetime] = None,
) -> Tuple[List[Dict[str, Any]], int]:
    """Filter before scoring: require one safe, non-seasoning required ingredient."""
    source = list(recipes if recipes is not None else load_recipes())
    safe_names = safe_inventory_names(inventory, now)
    eligible = []
    for recipe in source:
        if _blocked(recipe, preferences or {}):
            continue
        required, _ = _recipe_ingredients(recipe)
        key_required_names = {
            item["_name"] for item in required
            if item["_name"] not in BASIC_SEASONINGS
        }
        if key_required_names & safe_names:
            eligible.append(recipe)
    return eligible, len(source) - len(eligible)

def score_recipe(
    recipe: Dict[str, Any],
    inventory: List[Dict[str, Any]],
    preferences: Optional[Dict[str, Any]] = None,
    weights: Optional[Dict[str, Any]] = None,
    now: Optional[datetime] = None,
) -> Optional[Dict[str, Any]]:
    preferences = preferences or {}
    blocked_reason = _blocked(recipe, preferences)
    if blocked_reason:
        return None
    configured_weights, penalty_weight = validate_weights(weights)
    current_time = now or datetime.now()
    indexed = _inventory_index(inventory, current_time)
    required, optional = _recipe_ingredients(recipe)
    all_ingredients = required + optional

    matched: List[str] = []
    missing: List[str] = []
    unsafe: List[str] = []
    quantity_scores: List[float] = []
    for ingredient in all_ingredients:
        name = ingredient["_name"]
        batches = _available_batches(indexed, name)
        if batches:
            matched.append(name)
            quantity_score = _quantity_satisfaction(ingredient, batches)
            if quantity_score is not None:
                quantity_scores.append(quantity_score)
        elif any(item["_expired"] for item in indexed.get(name, [])):
            unsafe.append(name)
            if ingredient.get("required", True) and name not in BASIC_SEASONINGS:
                missing.append(name)
        elif ingredient.get("required", True) and name not in BASIC_SEASONINGS:
            missing.append(name)

    relevant_required = [item for item in required if item["_name"] not in BASIC_SEASONINGS]
    required_coverage = 1.0 if not relevant_required else 1 - len(set(missing)) / len(relevant_required)
    optional_coverage = None if not optional else sum(
        1 for item in optional if _available_batches(indexed, item["_name"])
    ) / len(optional)
    i_parts = [(0.70, required_coverage)]
    if optional_coverage is not None:
        i_parts.append((0.15, optional_coverage))
    if quantity_scores:
        i_parts.append((0.15, sum(quantity_scores) / len(quantity_scores)))
    i_score = clamp(sum(weight * score for weight, score in i_parts) / sum(weight for weight, _ in i_parts))

    timed_urgencies: List[Tuple[str, float]] = []
    expiring_names: List[str] = []
    for name in sorted(set(matched)):
        for item in _available_batches(indexed, name):
            time_range = item["_time_range"]
            if not time_range:
                continue
            start, expiry = time_range
            lifetime = max((expiry - start).total_seconds(), 1)
            remaining = max((expiry - current_time).total_seconds(), 0)
            urgency = clamp(1 - remaining / lifetime)
            timed_urgencies.append((name, urgency))
            if remaining <= 3 * 86400:
                expiring_names.append(name)
    f_score = None if not timed_urgencies else clamp(
        sum(value for _, value in timed_urgencies) / len(timed_urgencies)
    )

    preference_checks: List[float] = []
    matched_preferences: List[str] = []
    recipe_tags = {str(tag).strip().lower() for tag in recipe.get("tags", [])}
    recipe_names = {item["_name"] for item in all_ingredients}
    liked_tags = {str(x).strip().lower() for x in _preference_tokens(preferences, ("tags", "preferred_tags", "taste", "spice"))}
    liked_ingredients = {normalize_ingredient(x) for x in _preference_tokens(preferences, ("ingredients", "preferred_ingredients"))}
    if liked_tags:
        matched_tags = sorted(liked_tags & recipe_tags)
        preference_checks.append(len(matched_tags) / len(liked_tags))
        matched_preferences.extend(matched_tags)
    if liked_ingredients:
        matched_preferred_ingredients = sorted(liked_ingredients & recipe_names)
        preference_checks.append(len(matched_preferred_ingredients) / len(liked_ingredients))
        matched_preferences.extend(matched_preferred_ingredients)
    duration = preferences.get("duration")
    if duration in {"under_30", "30_to_60", "any"}:
        minutes = _number(recipe.get("cooking_time")) or 0
        duration_matches = duration == "any" or (duration == "under_30" and minutes <= 30) or (duration == "30_to_60" and 30 < minutes <= 60)
        preference_checks.append(1.0 if duration_matches else 0.0)
        if duration_matches:
            matched_preferences.append({"under_30": "30分钟内", "30_to_60": "30至60分钟", "any": "不限时长"}[duration])
    p_score = None if not preference_checks else clamp(sum(preference_checks) / len(preference_checks))

    safe_inventory_names = {name for name, batches in indexed.items() if any(not item["_expired"] for item in batches)}
    used_safe_names = set(matched) & safe_inventory_names
    if safe_inventory_names:
        overall_use = len(used_safe_names) / len(safe_inventory_names)
        timed_safe_names = {name for name, _ in timed_urgencies}
        expiring_available = {
            name for name in timed_safe_names
            if any(value >= 0.5 for item_name, value in timed_urgencies if item_name == name)
        }
        if expiring_available:
            expiring_use = len(used_safe_names & expiring_available) / len(expiring_available)
            w_score = clamp(0.70 * overall_use + 0.30 * expiring_use)
        else:
            w_score = clamp(overall_use)
    else:
        w_score = None

    m_score = clamp(len(set(missing)) / len(relevant_required)) if relevant_required else 0.0
    components = {"I": i_score, "F": f_score, "P": p_score, "W": w_score, "M": m_score, "B": None, "D": None, "N": None}
    available_positive = [key for key in POSITIVE_COMPONENTS if components[key] is not None and configured_weights[key] > 0]
    denominator = sum(configured_weights[key] for key in available_positive)
    if not denominator:
        raise ValueError("no positive component is available for this recipe")
    effective = {}
    for key in available_positive[:-1]:
        effective[key] = round(configured_weights[key] / denominator, 6)
    last_key = available_positive[-1]
    effective[last_key] = round(1.0 - sum(effective.values()), 6)

    # Use the exact exposed values so clients can reproduce total_score.
    public_components = {
        key: (None if value is None else round(clamp(value), 6))
        for key, value in components.items()
    }
    positive_score = sum(effective[key] * public_components[key] for key in available_positive)
    penalty = penalty_weight * public_components["M"]
    total = max(0.0, min(100.0, 100 * positive_score - 100 * penalty))
    unavailable = [key for key, value in components.items() if value is None]
    data_notes = []
    if not quantity_scores:
        data_notes.append("库存与菜谱缺少可比单位，I中的数量满足度未参与内部加权")
    if f_score is None:
        data_notes.append("无可用的真实入库/购买/到期时间，F不可用")
    if p_score is None:
        data_notes.append("未提供可评分偏好，P不可用")
    data_notes.append("B、D、N第一阶段不参与总分；缺少可靠数据时保持unavailable")
    reasons = []
    if i_score >= 0.6:
        reasons.append(f"库存食材匹配度较高（I={public_components['I']:.2f}）")
    if matched:
        reasons.append(f"匹配库存食材：{', '.join(sorted(set(matched)))}")
    if f_score is not None and f_score >= 0.5 and expiring_names:
        reasons.append(f"可使用临期但未过期食材：{', '.join(sorted(set(expiring_names)))}")
    if p_score is not None and p_score >= 0.5 and matched_preferences:
        reasons.append(f"符合明确偏好：{', '.join(dict.fromkeys(matched_preferences))}")
    if w_score is not None and w_score >= 0.5:
        reasons.append(f"可利用现有库存并减少浪费（W={public_components['W']:.2f}）")
    if m_score == 0:
        reasons.append("无需补充关键食材")
    elif missing:
        reasons.append(f"仍缺少关键食材：{', '.join(sorted(set(missing)))}")
    unavailable_positive = [key for key in POSITIVE_COMPONENTS if components[key] is None]
    if unavailable_positive:
        reasons.append(f"因数据不足未参与评分：{', '.join(unavailable_positive)}")
    if not reasons:
        reasons.append(f"必需食材覆盖率{required_coverage * 100:.0f}%")

    return {
        "recipe_id": recipe["id"], "recipe_name": recipe["name"], "total_score": round(total, 2),
        "component_scores": public_components,
        "effective_weights": effective,
        "missing_penalty_weight": penalty_weight,
        "unavailable_components": unavailable,
        "matched_ingredients": sorted(set(matched)),
        "missing_required_ingredients": sorted(set(missing)),
        "expiring_ingredients_used": sorted(set(expiring_names)),
        "unsafe_or_expired_ingredients": sorted(set(unsafe)),
        "data_quality_notes": data_notes,
        "reasons": reasons,
    }


def recommend_recipes(
    inventory: List[Dict[str, Any]], top_k: int = 5,
    preferences: Optional[Dict[str, Any]] = None,
    weights: Optional[Dict[str, Any]] = None,
    recipes: Optional[List[Dict[str, Any]]] = None,
    now: Optional[datetime] = None,
) -> List[Dict[str, Any]]:
    validate_weights(weights)
    eligible, _ = filter_eligible_recipes(inventory, recipes, preferences, now)
    scored = [
        result for recipe in eligible
        if (result := score_recipe(recipe, inventory, preferences, weights, now)) is not None
    ]
    scored.sort(key=lambda item: (-item["total_score"], item["recipe_id"]))
    for rank, item in enumerate(scored[:top_k], 1):
        item["rank"] = rank
    return scored[:top_k]
