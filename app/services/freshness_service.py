"""Deterministic FreshFusion v1 freshness assessment.

The engine only fuses traceable data supplied by callers.  It does not call
cloud models, read cooking-temperature streams, or invent missing dates.
"""

from __future__ import annotations

import json
import math
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional, Tuple


ALGORITHM_VERSION = "freshfusion_v1"
DISCLAIMER = "结果为基于现有数据的辅助判断，不能替代专业食品安全检测。"
COOKING_TEMPERATURE_NOTE = (
    "当前温度传感器用于烹饪锅温感知，不代表食材储存环境温度，因此未参与鲜度融合。"
)
DEFAULT_WEIGHTS = {"T": 0.55, "V": 0.25, "S": 0.20, "H": 0.0}
COMPONENTS = ("T", "V", "S", "H")
STATUS_SCORES = {
    "recommended": 1.0,
    "acceptable": 0.7,
    "suboptimal": 0.3,
    "unsuitable": 0.0,
}
STATUS_LABELS = {
    "recommended": "推荐储存方式",
    "acceptable": "可接受的储存方式",
    "suboptimal": "储存方式不够理想",
    "unsuitable": "储存方式明显不适宜",
    "unknown": "缺少可用储存规则",
}
LEVEL_LABELS = {
    "fresh": "新鲜",
    "good": "状态良好",
    "consume_soon": "建议尽快食用",
    "high_risk": "鲜度风险较高",
    "expired": "已过期",
    "unknown": "数据不足",
}
INGREDIENT_ALIASES = {
    "tomato": "番茄", "西红柿": "番茄", "小番茄": "番茄", "圣女果": "番茄",
    "potato": "土豆", "马铃薯": "土豆", "洋芋": "土豆",
    "carrot": "胡萝卜", "egg": "鸡蛋", "蛋": "鸡蛋",
    "beef": "牛肉", "牛腩肉": "牛腩", "beefbrisket": "牛腩",
    "pork": "猪肉", "瘦肉": "猪肉", "猪瘦肉": "猪肉",
    "chicken": "鸡肉", "chickenbreast": "鸡胸肉", "鸡胸": "鸡胸肉", "鸡脯肉": "鸡胸肉",
    "tofu": "豆腐", "onion": "洋葱", "garlic": "大蒜", "蒜": "大蒜",
    "ginger": "生姜", "姜": "生姜", "cucumber": "黄瓜",
    "broccoli": "西兰花", "西蓝花": "西兰花", "cabbage": "圆白菜", "包菜": "圆白菜", "卷心菜": "圆白菜",
    "lettuce": "生菜", "spinach": "菠菜", "cilantro": "香菜", "leek": "大葱",
}
VISUAL_MAX_AGE = timedelta(hours=24)


def _load_storage_rules() -> Dict[str, Any]:
    path = Path(__file__).resolve().parents[1] / "data" / "freshness" / "storage_rules.json"
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


STORAGE_RULES = _load_storage_rules()


def clamp(value: float) -> float:
    return max(0.0, min(1.0, float(value)))


def _finite_number(value: Any) -> Optional[float]:
    if isinstance(value, bool):
        return None
    try:
        result = float(value)
    except (TypeError, ValueError):
        return None
    return result if math.isfinite(result) else None


def normalize_ingredient_name(name: Any) -> str:
    normalized = str(name or "").strip().lower().replace(" ", "")
    return INGREDIENT_ALIASES.get(normalized, normalized)


def normalize_storage_type(value: Any) -> Optional[str]:
    normalized = str(value or "").strip().lower().replace("-", "_").replace(" ", "_")
    if not normalized:
        return None
    return STORAGE_RULES["storage_aliases"].get(normalized)


def parse_datetime_safely(value: Any) -> Optional[datetime]:
    if isinstance(value, datetime):
        parsed = value
    elif isinstance(value, date):
        parsed = datetime.combine(value, datetime.min.time())
    elif value:
        text = str(value).strip().replace("Z", "+00:00")
        try:
            parsed = datetime.fromisoformat(text)
        except (TypeError, ValueError):
            return None
    else:
        return None
    if parsed.tzinfo is None:
        return parsed.replace(tzinfo=timezone.utc)
    return parsed.astimezone(timezone.utc)


def calculate_time_freshness(
    item: Dict[str, Any], reference_time: Optional[Any] = None,
) -> Dict[str, Any]:
    reference = parse_datetime_safely(reference_time) if reference_time is not None else datetime.now(timezone.utc)
    if reference is None:
        return {"score": None, "status": "unknown", "expired": False, "critical": False,
                "expiring_soon": False, "start_time": None, "expiry_time": None,
                "notes": ["参考时间格式无效"]}

    purchase = parse_datetime_safely(item.get("purchase_time") or item.get("purchase_date"))
    added = parse_datetime_safely(item.get("add_time"))
    start = purchase or added
    expiry = parse_datetime_safely(item.get("expiry_date"))
    notes: List[str] = []

    if item.get("expiry_date") and expiry is None:
        notes.append("到期时间格式无效")
    if not expiry:
        shelf_life = _finite_number(item.get("shelf_life"))
        if shelf_life is None:
            notes.append("缺少合法到期时间或保质期")
        elif shelf_life <= 0:
            notes.append("保质期必须大于0")
        elif start:
            expiry = start + timedelta(days=shelf_life)
    if not start:
        notes.append("缺少合法购买时间或入库时间")
    if start and start > reference:
        notes.append("购买或入库时间晚于参考时间")
    if start and expiry and expiry <= start:
        notes.append("到期时间必须晚于购买或入库时间")

    if not start or not expiry or start > reference or expiry <= start:
        return {"score": None, "status": "unknown", "expired": False, "critical": False,
                "expiring_soon": False,
                "start_time": start.isoformat() if start else None,
                "expiry_time": expiry.isoformat() if expiry else None, "notes": list(dict.fromkeys(notes))}

    total_seconds = (expiry - start).total_seconds()
    remaining_seconds = (expiry - reference).total_seconds()
    score = clamp(remaining_seconds / total_seconds)
    expired = reference > expiry
    critical = not expired and reference.date() == expiry.date()
    expiring_soon = not expired and (critical or score <= 0.30)
    status = "expired" if expired else "critical" if critical else "expiring_soon" if expiring_soon else "valid"
    return {
        "score": round(score, 6), "status": status, "expired": expired,
        "critical": critical, "expiring_soon": expiring_soon,
        "start_time": start.isoformat(), "expiry_time": expiry.isoformat(), "notes": notes,
    }


def calculate_storage_suitability(ingredient_name: Any, storage_type: Any) -> Dict[str, Any]:
    normalized_storage = normalize_storage_type(storage_type)
    name = normalize_ingredient_name(ingredient_name)
    category = STORAGE_RULES["ingredient_categories"].get(name)
    if not normalized_storage:
        return {"score": None, "status": "unknown", "storage_type": None,
                "category": category, "match_type": None, "reason": "缺少或无法识别储存方式"}
    if not category:
        return {"score": None, "status": "unknown", "storage_type": normalized_storage,
                "category": None, "match_type": None, "reason": "当前规则表未覆盖该食材"}
    status = STORAGE_RULES["category_rules"].get(category, {}).get(normalized_storage)
    if not status:
        return {"score": None, "status": "unknown", "storage_type": normalized_storage,
                "category": category, "match_type": None, "reason": "该食材与储存方式组合暂无规则"}
    return {
        "score": STATUS_SCORES[status], "status": status, "storage_type": normalized_storage,
        "category": category, "match_type": "category_rule",
        "reason": f"命中{category}类别的{normalized_storage}储存规则：{STATUS_LABELS[status]}",
    }


def validate_visual_freshness_input(
    visual: Any, ingredient_name: Any, reference_time: Optional[Any] = None,
) -> Tuple[Optional[Dict[str, Any]], str]:
    if visual is None:
        return None, "暂无可靠视觉鲜度输入"
    if not isinstance(visual, dict):
        return None, "视觉鲜度输入必须是结构化对象"
    score = _finite_number(visual.get("score"))
    confidence = _finite_number(visual.get("confidence"))
    observed = parse_datetime_safely(visual.get("observed_at"))
    reference = parse_datetime_safely(reference_time) if reference_time is not None else datetime.now(timezone.utc)
    source = str(visual.get("source") or "").strip()
    model_version = str(visual.get("model_version") or "").strip()
    evidence = visual.get("evidence")
    linked_name = visual.get("ingredient_name")
    if score is None or not 0 <= score <= 1:
        return None, "视觉鲜度score必须位于0到1"
    if confidence is None or not 0 <= confidence <= 1:
        return None, "视觉鲜度confidence必须位于0到1"
    if not source or not model_version or observed is None:
        return None, "视觉鲜度缺少来源、模型版本或合法观测时间"
    if not isinstance(evidence, list) or not evidence or not all(str(x).strip() for x in evidence):
        return None, "视觉鲜度缺少可追溯证据"
    if reference is None or observed > reference or reference - observed > VISUAL_MAX_AGE:
        return None, "视觉鲜度结果已过时或观测时间异常"
    if linked_name and normalize_ingredient_name(linked_name) != normalize_ingredient_name(ingredient_name):
        return None, "视觉鲜度结果与当前食材不匹配"
    return {
        "score": round(score, 6), "confidence": round(confidence, 6), "source": source,
        "observed_at": observed.isoformat(), "evidence": [str(x).strip() for x in evidence],
        "model_version": model_version,
    }, "视觉鲜度输入有效"


def normalize_available_weights(
    component_scores: Dict[str, Optional[float]], weights: Optional[Dict[str, Any]] = None,
) -> Dict[str, float]:
    configured = dict(DEFAULT_WEIGHTS)
    if weights:
        unknown = set(weights) - set(COMPONENTS)
        if unknown:
            raise ValueError(f"未知鲜度权重：{', '.join(sorted(unknown))}")
        for key, value in weights.items():
            number = _finite_number(value)
            if number is None or not 0 <= number <= 1:
                raise ValueError(f"权重{key}必须位于0到1")
            configured[key] = number
    available = [key for key in COMPONENTS if component_scores.get(key) is not None and configured[key] > 0]
    denominator = sum(configured[key] for key in available)
    if not available or denominator <= 0:
        return {}
    effective: Dict[str, float] = {}
    for key in available[:-1]:
        effective[key] = round(configured[key] / denominator, 6)
    effective[available[-1]] = round(1.0 - sum(effective.values()), 6)
    return effective


def classify_freshness_level(fresh_score: Optional[float], expired: bool = False) -> Tuple[str, str]:
    if expired:
        return "expired", LEVEL_LABELS["expired"]
    if fresh_score is None:
        return "unknown", LEVEL_LABELS["unknown"]
    if fresh_score >= 80:
        level = "fresh"
    elif fresh_score >= 60:
        level = "good"
    elif fresh_score >= 40:
        level = "consume_soon"
    else:
        level = "high_risk"
    return level, LEVEL_LABELS[level]


def calculate_confidence(
    time_result: Dict[str, Any], storage_result: Dict[str, Any], visual_result: Optional[Dict[str, Any]],
) -> Tuple[float, str, List[str]]:
    score = 0.0
    reasons: List[str] = []
    if time_result.get("score") is not None:
        score += 0.50
        reasons.append("时间字段完整且可计算")
    else:
        reasons.append("时间字段不足，降低可信度")
    if storage_result.get("score") is not None:
        score += 0.20 if storage_result.get("match_type") == "category_rule" else 0.15
        reasons.append("储存方式命中维护规则")
    else:
        reasons.append("储存适配度不可用")
    if visual_result:
        score += 0.30 * visual_result["confidence"]
        reasons.append("包含有来源和时效校验的视觉输入")
    else:
        reasons.append("未包含可靠视觉输入")
    score = round(clamp(score), 6)
    level = "high" if score >= 0.75 else "medium" if score >= 0.40 else "low"
    return score, level, reasons


def build_freshness_reasons(
    time_result: Dict[str, Any], storage_result: Dict[str, Any], visual_result: Optional[Dict[str, Any]],
) -> List[str]:
    reasons: List[str] = []
    if time_result.get("score") is not None:
        if time_result["expired"]:
            reasons.append("根据有效时间字段，该食材已超过到期时间")
        elif time_result["critical"]:
            reasons.append("根据有效时间字段，该食材在参考时间当日到期")
        elif time_result["expiring_soon"]:
            reasons.append("剩余生命周期比例较低，建议尽快处理")
        else:
            reasons.append(f"时间鲜度T={time_result['score']:.2f}")
    else:
        reasons.append("缺少可验证的购买/入库与到期信息，时间鲜度不可用")
    reasons.append(storage_result["reason"])
    if visual_result:
        reasons.append(f"视觉鲜度来自{visual_result['source']}，证据：{'、'.join(visual_result['evidence'])}")
    else:
        reasons.append("暂无可靠视觉鲜度输入")
    return reasons


def calculate_freshfusion(
    item: Dict[str, Any], reference_time: Optional[Any] = None,
    weights: Optional[Dict[str, Any]] = None,
) -> Dict[str, Any]:
    time_result = calculate_time_freshness(item, reference_time)
    storage_result = calculate_storage_suitability(
        item.get("ingredient_name") or item.get("name"),
        item.get("storage_type") or item.get("storage_method") or item.get("storage"),
    )
    visual_result, visual_note = validate_visual_freshness_input(
        item.get("visual_freshness"), item.get("ingredient_name") or item.get("name"), reference_time,
    )
    components = {
        "T": time_result["score"], "V": visual_result["score"] if visual_result else None,
        "S": storage_result["score"], "H": None,
    }
    effective = normalize_available_weights(components, weights)
    fresh_score = None
    if effective:
        fresh_score = round(100 * sum(effective[key] * components[key] for key in effective), 2)
    level, label = classify_freshness_level(fresh_score, time_result["expired"])
    confidence_score, confidence_level, confidence_reasons = calculate_confidence(
        time_result, storage_result, visual_result,
    )
    risk_flags: List[str] = []
    if time_result["expired"]:
        risk_flags.append("expired")
    elif time_result["critical"]:
        risk_flags.append("critical")
    elif time_result["expiring_soon"]:
        risk_flags.append("expiring_soon")
    if storage_result["status"] == "unsuitable":
        risk_flags.append("storage_unsuitable")
    if visual_result and any(token in evidence for evidence in visual_result["evidence"] for token in ("霉", "腐败", "异味", "腐烂")):
        risk_flags.append("visual_spoilage_indicators")
    unavailable = [key for key, value in components.items() if value is None]
    notes = list(time_result["notes"])
    if storage_result["score"] is None:
        notes.append(storage_result["reason"])
    if visual_result is None:
        notes.append(visual_note)
    notes.append(COOKING_TEMPERATURE_NOTE)
    return {
        "algorithm_version": ALGORITHM_VERSION,
        "fresh_score": fresh_score,
        "freshness_level": level,
        "freshness_label": label,
        "component_scores": components,
        "effective_weights": effective,
        "unavailable_components": unavailable,
        "confidence_score": confidence_score,
        "confidence_level": confidence_level,
        "confidence_reasons": confidence_reasons,
        "expired": time_result["expired"],
        "critical": time_result["critical"],
        "expiring_soon": time_result["expiring_soon"],
        "risk_flags": risk_flags,
        "reasons": build_freshness_reasons(time_result, storage_result, visual_result),
        "data_quality_notes": list(dict.fromkeys(notes)),
        "time_details": {key: time_result[key] for key in ("status", "start_time", "expiry_time")},
        "storage_details": storage_result,
        "visual_details": visual_result,
        "disclaimer": DISCLAIMER,
    }
