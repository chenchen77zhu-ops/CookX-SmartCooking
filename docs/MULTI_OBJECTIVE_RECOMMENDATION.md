# 多目标推荐与成本评分口径

当前成本能力准确命名为 **cost-score framework based on explicit recipe `estimated_cost`**。它只使用菜谱中显式存在的整道菜估算成本，不生成、补全或推测生产价格。

## 评分公式

可用的正向分量为 I、F、P、W、B、D、N。仅将非 `null` 且配置权重大于 0 的分量纳入归一化：

- I：既有库存食材匹配度。
- F：既有真实时间鲜度所形成的使用紧迫度。
- P：既有明确用户偏好匹配度。
- W：既有库存利用与减少浪费评分。
- B：本次新增的显式整道菜成本相对预算评分。
- D：菜谱显式难度与用户明确目标之间的适配度；不是菜谱原始难度，也不是“越难分越高”。
- N：可靠的每份营养数据与用户明确数值目标之间的适配度；不是医学或健康评分。
- M：既有必需食材缺失比例，始终作为独立惩罚项。

默认配置权重为 I=0.35、F=0.25、P=0.20、W=0.20、B=0.15、D=0.10、N=0.10，默认缺失惩罚系数 `lambda=0.15`。D、N 均使用较低默认权重，且只在对应目标和数据可靠时参与；未提供 `nutrition_target` 的旧请求与 A6 的分数、有效权重、排名和版本行为一致。

```text
effective_weight[k] = configured_weight[k] / sum(configured_weight[available positive components])
positive_score = sum(effective_weight[k] * component_score[k])
total_score = clamp(100 * positive_score - 100 * lambda * M, 0, 100)
```

M 仍是独立缺失惩罚项，不进入正向权重分母。I、F、P、W、M 的既有定义未改变。

成本分量的精确公式是：

```text
B = clamp(1 - estimated_cost / budget, 0, 1)
```

`estimated_cost` 和 `budget` 的单位均为人民币元，`currency` 固定返回 `CNY`。B 仅在菜谱存在非负、有限的显式 `estimated_cost`，且请求顶层提供正数、有限的 `budget` 时可用。否则 B 为 `null`、列入 `unavailable_components`，且不进入 `effective_weights`。

## 难度适配分量 D

菜谱显式难度按以下确定性别名规范化：

| 规范等级 | 数值 | 允许的显式值 |
| --- | ---: | --- |
| `easy` | 0.0 | `easy`、`简单`、`容易` |
| `medium` | 0.5 | `medium`、`中等`、`一般` |
| `hard` | 1.0 | `hard`、`困难`、`较难` |

用户目标记为 `t`，菜谱规范难度记为 `r`：

```text
D = clamp(1 - abs(r - t), 0, 1)
```

因此 easy 对 easy 为 1，easy 对 medium 为 0.5，easy 对 hard 为 0。hard 本身的数值 1 不表示对所有用户更好。

`POST /api/recommendations` 顶层可选字段 `difficulty_target` 严格只接受 `easy`、`medium`、`hard`，区分大小写。非法字符串、数字、布尔、数组和对象返回 422。自由结构的 `preferences` 中同名字段不会激活 D，也不会从历史、年龄或其他偏好推测目标。

D 仅在以下条件同时满足时参与总分：

- 请求显式提供合法 `difficulty_target`；
- 菜谱有可识别的显式 `difficulty`；
- D 的配置权重大于 0。

未提供目标时 `difficulty_status=target_unavailable`；菜谱难度缺失或未知时为 `recipe_difficulty_unavailable`；权重为 0 时为 `disabled_by_weight`，D 可计算但不进入 `effective_weights`，也不触发 v3。响应同时返回 `recipe_difficulty`、`difficulty_target`、`difficulty_status` 和 `difficulty_data_notes`。

## 营养目标适配分量 N

N 只比较明确的每份结构化数据。菜谱 `nutrition.basis` 必须严格为 `per_serving`，不会在每100克、整道菜和每份之间换算。营养值必须是有限非负数，布尔值和数字字符串无效。

顶层 `nutrition_target` 是禁止额外字段的正式请求模型，至少需要一个有限且大于 0 的真实数值：

| 目标字段 | 含义与单位 | 对应菜谱字段 |
| --- | --- | --- |
| `max_calories_kcal` | 每份热量上限，kcal | `calories_kcal` |
| `min_protein_g` | 每份蛋白质下限，g | `protein_g` |
| `max_fat_g` | 每份脂肪上限，g | `fat_g` |
| `max_carbohydrates_g` | 每份碳水化合物上限，g | `carbohydrates_g` |

上限型目标：

```text
score_max(x, L) = clamp(min(1, L / x), 0, 1)
```

当 `x=0` 且 `L>0` 时得分为 1。蛋白质下限型目标：

```text
score_min(x, G) = clamp(x / G, 0, 1)
```

只对“用户设置目标且菜谱有可靠对应值”的项目求平均：

```text
N = sum(comparable nutrition component scores) / comparable metric count
```

没有可比较项目时 N 为 `null`。单项缺失或非法只排除该单项，不会把它当作 0 或已满足。N 权重为 0 时状态为 `disabled_by_weight`，不进入 `effective_weights`，也不触发 v4。

响应返回 `nutrition_target`、`recipe_nutrition`、`nutrition_basis`、`nutrition_status`、`nutrition_component_scores`、`nutrition_data_notes` 和固定免责声明。状态包括 `available`、`target_unavailable`、`recipe_nutrition_unavailable`、`basis_unavailable`、`no_comparable_metrics`、`disabled_by_weight`。

> 营养评分仅基于现有结构化数据和用户目标进行辅助比较，不能替代营养师或医疗建议。

## 数据真实性与当前边界

- `estimated_cost` 表示整道菜的估算成本，不是库存抵扣后的新增采购成本。
- 生产 `app/data/recipes/recipes.json` 中的 `estimated_cost` 当前全部为 `null`，所以当前生产 B 仍为 unavailable。
- 测试价格只存在于测试夹具，没有写入生产菜谱或库存。
- 不得将 `estimated_cost` 描述为精确或实时市场价格。
- 当前未实现食材级价格目录、单位换算、库存数量抵扣或 `cost_breakdown`。
- 生产菜谱当前 22 道均有显式 `difficulty`：16 道“简单”、6 道“中等”；没有显式“困难”菜谱。本轮没有修改或补造这些值。
- 当前未实现从烹饪时间、步骤文本、技法或工具推导难度，也不会调用大模型、菜名、ID、哈希或随机数猜测难度。
- 生产菜谱 22 道的 `nutrition` 当前全部为 `null`，没有单位、份数或 `basis`；因此生产 N 仍为 unavailable。
- 不调用大模型或互联网估算营养，不从菜名、食材、图片、ID 或哈希推测营养，也不提供减肥、疾病治疗或营养诊断方案。
- 当前不进行每100克、整道菜与每份之间的换算；测试营养值只存在于隔离测试夹具。

## API 与版本规则

`POST /api/recommendations` 的顶层可选字段 `budget` 是严格数值类型，必须大于 0 且有限；字符串、0、负数、NaN 和 Infinity 均返回 422。请求模型禁止未声明的顶层字段。旧请求不提供 `budget` 时继续正常执行。

- 所有返回推荐的 B 均未参与时：`algorithm_version = multi_objective_v1`。
- 至少一条返回推荐的 B 实际参与且 D 均未参与时：`algorithm_version = multi_objective_v2`。
- 至少一条返回推荐的 D 实际进入 `effective_weights` 时：`algorithm_version = multi_objective_v3`。D 的版本优先级高于 B。
- 至少一条返回推荐的 N 实际进入 `effective_weights` 时：`algorithm_version = multi_objective_v4`。N 的版本优先级高于 B、D。

每条推荐明确返回 `component_scores.B`、`effective_weights`、`estimated_cost`、`budget`、`currency`、`cost_status` 和 `cost_data_notes`。缺少预算或显式成本时不会用 0 元代替缺失数据。

### 难度请求与响应示例

以下请求使用顶层难度目标；`weights.D` 可省略并使用默认值 0.10：

```json
{
  "user_id": "example-user",
  "top_k": 1,
  "difficulty_target": "easy",
  "weights": {"D": 0.1}
}
```

以下完整响应示例使用隔离测试夹具中的“中等”菜谱，不代表生产价格或生产难度数据；`generated_at` 仅为示例时间：

```json
{
  "algorithm_version": "multi_objective_v3",
  "user_id": "example-user",
  "generated_at": "2026-09-23T12:00:00+08:00",
  "status": "success",
  "message": "已根据当前安全库存生成推荐。",
  "eligible_recipe_count": 1,
  "filtered_recipe_count": 0,
  "recommendations": [
    {
      "recipe_id": "difficulty_fixture",
      "recipe_name": "难度测试夹具菜谱",
      "total_score": 92.31,
      "component_scores": {
        "I": 1.0,
        "F": null,
        "P": null,
        "W": 1.0,
        "M": 0.0,
        "B": null,
        "D": 0.5,
        "N": null
      },
      "effective_weights": {"I": 0.538462, "W": 0.307692, "D": 0.153846},
      "missing_penalty_weight": 0.15,
      "unavailable_components": ["F", "P", "B", "N"],
      "matched_ingredients": ["番茄"],
      "missing_required_ingredients": [],
      "expiring_ingredients_used": [],
      "unsafe_or_expired_ingredients": [],
      "estimated_cost": null,
      "budget": null,
      "currency": "CNY",
      "cost_status": "estimated_cost_unavailable",
      "cost_data_notes": ["菜谱未提供可靠estimated_cost，未推测价格"],
      "recipe_difficulty": "medium",
      "difficulty_target": "easy",
      "difficulty_status": "available",
      "difficulty_data_notes": ["基于菜谱显式difficulty与用户明确目标计算难度适配度"],
      "nutrition_target": null,
      "recipe_nutrition": null,
      "nutrition_basis": null,
      "nutrition_status": "target_unavailable",
      "nutrition_component_scores": {},
      "nutrition_data_notes": ["未提供明确数值nutrition_target，N不参与评分"],
      "nutrition_disclaimer": "营养评分仅基于现有结构化数据和用户目标进行辅助比较，不能替代营养师或医疗建议。",
      "data_quality_notes": [
        "无可用的真实入库/购买/到期时间，F不可用",
        "未提供可评分偏好，P不可用",
        "缺少可靠菜谱成本或有效预算，B不可用；未推测价格",
        "缺少明确营养目标或可靠每份营养数据，N不可用；未推测营养值"
      ],
      "reasons": [
        "库存食材匹配度较高（I=1.00）",
        "匹配库存食材：番茄",
        "可利用现有库存并减少浪费（W=1.00）",
        "菜谱难度中等与期望难度简单存在差异",
        "无需补充关键食材",
        "因数据不足未参与评分：F, P, B"
      ],
      "rank": 1
    }
  ]
}
```

### 营养请求与完整响应示例

以下请求按每份设置两个明确目标：

```json
{
  "user_id": "example-user",
  "top_k": 1,
  "nutrition_target": {
    "max_calories_kcal": 600,
    "min_protein_g": 20
  }
}
```

以下完整响应使用隔离测试夹具中的每份营养数据，不是生产数据：

```json
{
  "algorithm_version": "multi_objective_v4",
  "user_id": "example-user",
  "generated_at": "2026-09-23T12:00:00+08:00",
  "status": "success",
  "message": "已根据当前安全库存生成推荐。",
  "eligible_recipe_count": 1,
  "filtered_recipe_count": 0,
  "recommendations": [
    {
      "recipe_id": "nutrition_fixture",
      "recipe_name": "营养测试夹具菜谱",
      "total_score": 94.23,
      "component_scores": {"I": 1.0, "F": null, "P": null, "W": 1.0, "M": 0.0, "B": null, "D": null, "N": 0.625},
      "effective_weights": {"I": 0.538462, "W": 0.307692, "N": 0.153846},
      "missing_penalty_weight": 0.15,
      "unavailable_components": ["F", "P", "B", "D"],
      "matched_ingredients": ["番茄"],
      "missing_required_ingredients": [],
      "expiring_ingredients_used": [],
      "unsafe_or_expired_ingredients": [],
      "estimated_cost": null,
      "budget": null,
      "currency": "CNY",
      "cost_status": "estimated_cost_unavailable",
      "cost_data_notes": ["菜谱未提供可靠estimated_cost，未推测价格"],
      "recipe_difficulty": "easy",
      "difficulty_target": null,
      "difficulty_status": "target_unavailable",
      "difficulty_data_notes": ["未提供明确difficulty_target，D不参与评分"],
      "nutrition_target": {"max_calories_kcal": 600.0, "min_protein_g": 20.0},
      "recipe_nutrition": {"basis": "per_serving", "calories_kcal": 800.0, "protein_g": 10.0},
      "nutrition_basis": "per_serving",
      "nutrition_status": "available",
      "nutrition_component_scores": {"max_calories_kcal": 0.75, "min_protein_g": 0.5},
      "nutrition_data_notes": ["仅按每份结构化营养数据与用户明确数值目标计算；不构成医疗建议"],
      "nutrition_disclaimer": "营养评分仅基于现有结构化数据和用户目标进行辅助比较，不能替代营养师或医疗建议。",
      "data_quality_notes": [
        "无可用的真实入库/购买/到期时间，F不可用",
        "未提供可评分偏好，P不可用",
        "缺少可靠菜谱成本或有效预算，B不可用；未推测价格",
        "缺少明确难度目标或可靠菜谱难度，D不可用；未推测难度"
      ],
      "reasons": [
        "库存食材匹配度较高（I=1.00）",
        "匹配库存食材：番茄",
        "可利用现有库存并减少浪费（W=1.00）",
        "以下营养目标存在差距：热量上限, 蛋白质下限",
        "无需补充关键食材",
        "因数据不足未参与评分：F, P, B, D"
      ],
      "rank": 1
    }
  ]
}
```

生产菜谱即使收到合法目标，也因 `nutrition=null` 如实返回：

```json
{
  "component_scores": {"N": null},
  "nutrition_target": {"max_calories_kcal": 600.0},
  "recipe_nutrition": null,
  "nutrition_basis": null,
  "nutrition_status": "recipe_nutrition_unavailable",
  "nutrition_component_scores": {},
  "nutrition_data_notes": ["菜谱缺少可靠结构化营养数据，未推测营养值"],
  "nutrition_disclaimer": "营养评分仅基于现有结构化数据和用户目标进行辅助比较，不能替代营养师或医疗建议。"
}
```
