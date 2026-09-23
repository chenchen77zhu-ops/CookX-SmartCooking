# 多目标推荐与成本评分口径

当前成本能力准确命名为 **cost-score framework based on explicit recipe `estimated_cost`**。它只使用菜谱中显式存在的整道菜估算成本，不生成、补全或推测生产价格。

## 评分公式

可用的正向分量为 I、F、P、W、B、D。仅将非 `null` 且配置权重大于 0 的分量纳入归一化：

- I：既有库存食材匹配度。
- F：既有真实时间鲜度所形成的使用紧迫度。
- P：既有明确用户偏好匹配度。
- W：既有库存利用与减少浪费评分。
- B：本次新增的显式整道菜成本相对预算评分。
- D：菜谱显式难度与用户明确目标之间的适配度；不是菜谱原始难度，也不是“越难分越高”。
- M：既有必需食材缺失比例，始终作为独立惩罚项。

默认配置权重为 I=0.35、F=0.25、P=0.20、W=0.20、B=0.15、D=0.10，默认缺失惩罚系数 `lambda=0.15`。D 使用较低默认权重，且仅在用户目标和菜谱难度均可靠时参与；未提供 `difficulty_target` 的旧请求与 A5 的分数、有效权重、排名和版本行为一致。B 不可用时仍沿用 A5 的可用分量归一化规则。

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

## 数据真实性与当前边界

- `estimated_cost` 表示整道菜的估算成本，不是库存抵扣后的新增采购成本。
- 生产 `app/data/recipes/recipes.json` 中的 `estimated_cost` 当前全部为 `null`，所以当前生产 B 仍为 unavailable。
- 测试价格只存在于测试夹具，没有写入生产菜谱或库存。
- 不得将 `estimated_cost` 描述为精确或实时市场价格。
- 当前未实现食材级价格目录、单位换算、库存数量抵扣或 `cost_breakdown`。
- 生产菜谱当前 22 道均有显式 `difficulty`：16 道“简单”、6 道“中等”；没有显式“困难”菜谱。本轮没有修改或补造这些值。
- 当前未实现从烹饪时间、步骤文本、技法或工具推导难度，也不会调用大模型、菜名、ID、哈希或随机数猜测难度。
- N 仍未实现并保持 unavailable。

## API 与版本规则

`POST /api/recommendations` 的顶层可选字段 `budget` 是严格数值类型，必须大于 0 且有限；字符串、0、负数、NaN 和 Infinity 均返回 422。请求模型禁止未声明的顶层字段。旧请求不提供 `budget` 时继续正常执行。

- 所有返回推荐的 B 均未参与时：`algorithm_version = multi_objective_v1`。
- 至少一条返回推荐的 B 实际参与且 D 均未参与时：`algorithm_version = multi_objective_v2`。
- 至少一条返回推荐的 D 实际进入 `effective_weights` 时：`algorithm_version = multi_objective_v3`。D 的版本优先级高于 B。

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
      "data_quality_notes": [
        "无可用的真实入库/购买/到期时间，F不可用",
        "未提供可评分偏好，P不可用",
        "缺少可靠菜谱成本或有效预算，B不可用；未推测价格",
        "N缺少可靠数据，保持unavailable"
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
