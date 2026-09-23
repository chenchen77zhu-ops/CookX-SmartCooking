# 多目标推荐与成本评分口径

当前成本能力准确命名为 **cost-score framework based on explicit recipe `estimated_cost`**。它只使用菜谱中显式存在的整道菜估算成本，不生成、补全或推测生产价格。

## 评分公式

可用的正向分量为 I、F、P、W、B。仅将非 `null` 且配置权重大于 0 的分量纳入归一化：

- I：既有库存食材匹配度。
- F：既有真实时间鲜度所形成的使用紧迫度。
- P：既有明确用户偏好匹配度。
- W：既有库存利用与减少浪费评分。
- B：本次新增的显式整道菜成本相对预算评分。
- M：既有必需食材缺失比例，始终作为独立惩罚项。

默认配置权重为 I=0.35、F=0.25、P=0.20、W=0.20、B=0.15，默认缺失惩罚系数 `lambda=0.15`。这些配置权重仅在对应分量可用时进入归一化；因此 B 不可用时，I/F/P/W 的分数、相对有效权重、排名和总分与 A4 基线一致。

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

## 数据真实性与当前边界

- `estimated_cost` 表示整道菜的估算成本，不是库存抵扣后的新增采购成本。
- 生产 `app/data/recipes/recipes.json` 中的 `estimated_cost` 当前全部为 `null`，所以当前生产 B 仍为 unavailable。
- 测试价格只存在于测试夹具，没有写入生产菜谱或库存。
- 不得将 `estimated_cost` 描述为精确或实时市场价格。
- 当前未实现食材级价格目录、单位换算、库存数量抵扣或 `cost_breakdown`。
- D、N 仍未实现并保持 unavailable。

## API 与版本规则

`POST /api/recommendations` 的顶层可选字段 `budget` 是严格数值类型，必须大于 0 且有限；字符串、0、负数、NaN 和 Infinity 均返回 422。请求模型禁止未声明的顶层字段。旧请求不提供 `budget` 时继续正常执行。

- 所有返回推荐的 B 均未参与时：`algorithm_version = multi_objective_v1`。
- 至少一条返回推荐的 B 实际参与时：`algorithm_version = multi_objective_v2`。

每条推荐明确返回 `component_scores.B`、`effective_weights`、`estimated_cost`、`budget`、`currency`、`cost_status` 和 `cost_data_notes`。缺少预算或显式成本时不会用 0 元代替缺失数据。
