# FreshFusion 与库存 API 接口说明

本文档冻结当前后端的 FreshFusion v1 与库存字段契约，供前端接入、联调和错误处理使用。所有示例均对应 `app/main.py` 与 `app/services/freshness_service.py` 的实际字段；库存接口不会伪造缺失的保质期、购买时间或到期时间。

## 1. FreshFusion 版本与用途

- 算法版本：`freshfusion_v1`
- 用途：基于可追溯的时间信息 T、储存适配度 S，以及未来可能接入的可信视觉信息 V 和历史信息 H，给出辅助鲜度结果。
- 当前公共 API 中，V 与 H 均为 `unavailable`：V 尚无服务端可信视觉结果；H 尚无可信历史数据源。
- 烹饪温度不是食材储存环境温度，不参与鲜度融合。
- 结果是辅助判断，不能替代专业食品安全检测。

## 2. 库存字段定义

| 字段 | 类型 | 新增必填 | 单位 | 来源 | 校验与行为 |
| --- | --- | --- | --- | --- | --- |
| `id` | string | 否 | 无 | 服务端 | 新增时由服务端生成；更新接口不可修改。 |
| `name` | string | 是 | 无 | 客户端 | 非空；服务端沿用现有食材名称兼容归一化。 |
| `quantity` | integer | 否 | 件或现有业务计数 | 客户端 | 默认 `1`；必须为正整数；拒绝 0、负数、布尔值、非整数和非数字。 |
| `add_time` | ISO 8601 string | 否 | 时间 | 客户端或服务端 | 客户端提供时验证并原样保存；缺失时生成当前 UTC 时间。它只参与库存时间计算，不能控制 FreshFusion 响应的 `evaluated_at`。 |
| `purchase_time` | ISO 8601 string | 否 | 时间 | 客户端 | 不自动生成；不得明显晚于服务端当前时间。当前允许 5 分钟时钟偏差。 |
| `shelf_life` | number | 否 | 天 | 客户端 | 可为正整数或正小数；必须有限且大于 0。缺失时不默认成 7 天。 |
| `expiry_date` | ISO 8601 string | 否 | 时间 | 客户端 | 不自动生成；存在 `add_time` 或 `purchase_time` 时，必须分别晚于这些起始时间。服务端不静默修正。 |
| `storage_type` | string | 否 | 无 | 客户端 | 必须命中 FreshFusion 共用别名表；保存客户端提供的受支持值，鲜度结果中的 `storage_details.storage_type` 返回标准值。 |

支持的储存标准值与别名：

- 冷藏 `refrigerated`：`冷藏`、`冰箱冷藏`、`refrigerated`、`refrigerator`、`fridge`
- 冷冻 `frozen`：`冷冻`、`冰冻`、`frozen`、`freezer`
- 常温 `room_temperature`：`常温`、`室温`、`room`、`room_temperature`
- 阴凉干燥 `cool_dry`：`阴凉干燥`、`干燥`、`cool_dry`

旧库存记录可以缺少 `purchase_time`、`expiry_date`、`shelf_life` 或 `storage_type`。读取时原样返回，不补默认值，也不因查询而重写库存文件。

## 3. 库存 CRUD

以下接口的 `user_id` 均为查询参数。用户不存在时返回 HTTP 404：

```json
{"detail":"用户不存在"}
```

### POST /api/add-to-inventory

请求体是库存对象数组：

```http
POST /api/add-to-inventory?user_id=abc123
Content-Type: application/json
```

```json
[
  {
    "name": "番茄",
    "quantity": 2,
    "purchase_time": "2026-09-20T08:00:00+08:00",
    "add_time": "2026-09-20T09:00:00+08:00",
    "shelf_life": 4,
    "expiry_date": "2026-09-24T09:00:00+08:00",
    "storage_type": "常温"
  }
]
```

成功响应为 HTTP 200；该接口不在响应中返回新记录，需随后调用 GET：

```json
{"status":"success","message":"已存入冰箱"}
```

### GET /api/inventory

```http
GET /api/inventory?user_id=abc123
```

成功响应为 HTTP 200，直接返回库存数组。已保存字段保持稳定；旧行缺少的新字段不会被补造：

```json
[
  {
    "id": "a1b2c3d4",
    "name": "西红柿",
    "quantity": 2,
    "add_time": "2026-09-20T09:00:00+08:00",
    "storage_type": "常温",
    "shelf_life": 4,
    "purchase_time": "2026-09-20T08:00:00+08:00",
    "expiry_date": "2026-09-24T09:00:00+08:00"
  }
]
```

用户存在但没有库存文件时返回 `[]`。

### PUT /api/inventory/{item_id}

请求体可以只包含需要更新的字段：`name`、`quantity`、`add_time`、`purchase_time`、`shelf_life`、`expiry_date`、`storage_type`。

```http
PUT /api/inventory/a1b2c3d4?user_id=abc123
Content-Type: application/json
```

```json
{
  "purchase_time": "2026-09-19T18:00:00+08:00",
  "expiry_date": "2026-09-23T18:00:00+08:00",
  "storage_type": "冰箱冷藏"
}
```

成功响应：

```json
{"status":"success"}
```

项目不存在时返回 HTTP 404：

```json
{"detail":"库存项目不存在"}
```

### DELETE /api/inventory/{item_id}

```http
DELETE /api/inventory/a1b2c3d4?user_id=abc123
```

成功响应保持原契约：

```json
{"status":"success"}
```

用户或项目不存在时分别返回 HTTP 404；删除行为不会影响其他库存项。

## 4. FreshFusion 单项评估

### POST /api/freshness/evaluate

该接口只评估、不持久化。请求模型禁止额外字段。

```json
{
  "ingredient_name": "番茄",
  "purchase_time": "2026-09-20T08:00:00Z",
  "add_time": "2026-09-20T09:00:00Z",
  "shelf_life": 4,
  "expiry_date": "2026-09-24T09:00:00Z",
  "storage_type": "常温"
}
```

响应字段如下；数值会随服务端评估时刻变化：

```json
{
  "evaluated_at": "2026-09-22T01:30:00Z",
  "algorithm_version": "freshfusion_v1",
  "fresh_score": 63.33,
  "freshness_level": "good",
  "freshness_label": "状态良好",
  "component_scores": {"T": 0.5, "V": null, "S": 1.0, "H": null},
  "effective_weights": {"T": 0.733333, "S": 0.266667},
  "unavailable_components": ["V", "H"],
  "confidence_score": 0.7,
  "confidence_level": "medium",
  "confidence_reasons": ["时间字段完整且可计算", "储存方式命中维护规则", "未包含可靠视觉输入"],
  "expired": false,
  "critical": false,
  "expiring_soon": false,
  "risk_flags": [],
  "reasons": ["时间鲜度T=0.50", "命中tomato类别的room_temperature储存规则：推荐储存方式", "暂无可靠视觉鲜度输入"],
  "data_quality_notes": ["暂无可靠视觉鲜度输入", "当前温度传感器用于烹饪锅温感知，不代表食材储存环境温度，因此未参与鲜度融合。", "当前尚无服务端可信视觉鲜度结果，V未参与融合。"],
  "time_details": {"status": "valid", "start_time": "2026-09-20T08:00:00+00:00", "expiry_time": "2026-09-24T09:00:00+00:00"},
  "storage_details": {"score": 1.0, "status": "recommended", "storage_type": "room_temperature", "category": "tomato", "match_type": "category_rule", "reason": "命中tomato类别的room_temperature储存规则：推荐储存方式"},
  "visual_details": null,
  "disclaimer": "结果为基于现有数据的辅助判断，不能替代专业食品安全检测。"
}
```

`reference_time` 和 `visual_freshness` 不属于公共请求模型，传入任一字段均返回 HTTP 422。客户端因此不能回拨评估时间、伪造视觉鲜度，或影响 `evaluated_at`。

## 5. 库存批量鲜度

### GET /api/users/{user_id}/inventory/freshness

该接口按库存文件顺序评估，不写回库存。

```json
{
  "algorithm_version": "freshfusion_v1",
  "user_id": "abc123",
  "evaluated_at": "2026-09-22T01:30:00Z",
  "generated_at": "2026-09-22T01:30:00Z",
  "sort_order": "inventory_file_order",
  "total_count": 2,
  "evaluable_count": 1,
  "unknown_count": 1,
  "expired_count": 0,
  "expiring_soon_count": 1,
  "items": [
    {
      "item_id": "a1b2c3d4",
      "name": "西红柿",
      "algorithm_version": "freshfusion_v1",
      "fresh_score": 32.53,
      "freshness_level": "high_risk",
      "freshness_label": "鲜度风险较高",
      "component_scores": {"T": 0.08, "V": null, "S": 1.0, "H": null},
      "effective_weights": {"T": 0.733333, "S": 0.266667},
      "unavailable_components": ["V", "H"],
      "confidence_score": 0.7,
      "confidence_level": "medium",
      "confidence_reasons": ["时间字段完整且可计算", "储存方式命中维护规则", "未包含可靠视觉输入"],
      "expired": false,
      "critical": false,
      "expiring_soon": true,
      "risk_flags": ["expiring_soon"],
      "reasons": ["剩余生命周期比例较低，建议尽快处理", "命中tomato类别的room_temperature储存规则：推荐储存方式", "暂无可靠视觉鲜度输入"],
      "data_quality_notes": ["暂无可靠视觉鲜度输入", "当前温度传感器用于烹饪锅温感知，不代表食材储存环境温度，因此未参与鲜度融合。", "当前尚无服务端可信视觉鲜度结果，V未参与融合。"],
      "time_details": {"status": "expiring_soon", "start_time": "2026-09-18T00:00:00+00:00", "expiry_time": "2026-09-22T06:00:00+00:00"},
      "storage_details": {"score": 1.0, "status": "recommended", "storage_type": "room_temperature", "category": "tomato", "match_type": "category_rule", "reason": "命中tomato类别的room_temperature储存规则：推荐储存方式"},
      "visual_details": null,
      "disclaimer": "结果为基于现有数据的辅助判断，不能替代专业食品安全检测。"
    }
  ]
}
```

查询参数中出现 `reference_time` 或 `visual_freshness` 时返回 HTTP 422。用户不存在返回 404；库存 JSON 无法读取返回 500。

## 6. 典型鲜度状态

以下片段展示真实响应字段的关键差异；完整单项均包含上一节列出的 FreshFusion 字段。

正常：

```json
{"fresh_score":82.4,"freshness_level":"fresh","freshness_label":"新鲜","expired":false,"critical":false,"expiring_soon":false,"risk_flags":[]}
```

数据不足：

```json
{"fresh_score":null,"freshness_level":"unknown","freshness_label":"数据不足","component_scores":{"T":null,"V":null,"S":null,"H":null},"unavailable_components":["T","V","S","H"],"expired":false}
```

临期：

```json
{"freshness_level":"high_risk","expiring_soon":true,"expired":false,"risk_flags":["expiring_soon"],"time_details":{"status":"expiring_soon","start_time":"2026-09-18T00:00:00+00:00","expiry_time":"2026-09-22T06:00:00+00:00"}}
```

过期：过期是时间硬状态，不会被较高的 S 或未来其他分量覆盖。

```json
{"freshness_level":"expired","freshness_label":"已过期","expired":true,"expiring_soon":false,"risk_flags":["expired"],"time_details":{"status":"expired","start_time":"2026-09-10T00:00:00+00:00","expiry_time":"2026-09-20T00:00:00+00:00"}}
```

## 7. 错误响应

库存 Pydantic 校验错误使用 FastAPI 标准 HTTP 422 结构，例如：

```json
{
  "detail": [
    {
      "type": "value_error",
      "loc": ["body", 0, "shelf_life"],
      "msg": "Value error, shelf_life必须为大于0的天数",
      "input": 0,
      "ctx": {"error": {}}
    }
  ]
}
```

会返回 422 的情况包括：非法 ISO 8601 时间、明显晚于当前时间的 `purchase_time`、早于或等于起始时间的 `expiry_date`、非正或非有限 `shelf_life`、未知 `storage_type`、非法 `quantity`、更新请求中的未知字段，以及 FreshFusion 公共接口中的禁用字段。

业务 404 使用稳定的简单结构：

```json
{"detail":"库存项目不存在"}
```

请求体形状或必填字段不符合模型时同样由 FastAPI 返回 422。库存文件损坏或无法读取时，GET/批量鲜度返回 HTTP 500 和 `{"detail":"用户库存数据无法读取"}`。

## 8. evaluated_at 可信边界

- `evaluated_at` 只由后端在处理请求时以 UTC 生成。
- 单项评估的客户端 `add_time`、`purchase_time` 或 `expiry_date` 只影响 T，不会替代评估时刻。
- 批量接口一次请求只生成一个评估时刻，并用于该批次所有项目。
- `generated_at` 在批量接口中与 `evaluated_at` 相同；推荐接口使用独立的 `generated_at`。
- 公共接口不开放 `reference_time`。内部服务和测试可以显式传入参考时间以实现确定性计算，但这不是 HTTP API 能力。

## 9. V 与 H 的当前状态

V 需要服务端可追溯的视觉结果，包括来源、模型版本、观测时间、置信度和证据。当前公共请求不接受 `visual_freshness`，批量评估也会移除库存中的同名字段，因此 V 为 `null` 并列入 `unavailable_components`。H 尚无可信历史鲜度或环境数据输入，固定为 `null`。两者都不会用随机值、名称哈希或默认分数替代。

## 10. 推荐接口兼容性

- `POST /api/recommendations` 继续存在，推荐算法版本为 `multi_objective_v1`。
- 推荐分量 F 继续复用 FreshFusion 的 `calculate_time_freshness` 结果；本次没有修改推荐公式或默认权重。
- `GET /api/recommend-recipe` 旧接口继续存在，契约未变。

## 11. 前端人员二接入顺序

1. 先调用 GET `/api/inventory` 回显原始库存字段；对旧记录缺失字段显示“未填写”，不要在前端偷偷补 7 天或冷藏。
2. 新增表单提交 `name` 与合法 `quantity`，其余时间、保质期和储存方式按用户真实输入选填。未填写 `purchase_time` 时不要构造当前时间。
3. 编辑时只提交用户实际改动字段。收到 422 时读取 `detail` 并定位字段；收到 404 时区分用户不存在与库存项目不存在。
4. 保存成功后重新 GET `/api/inventory`，再 GET `/api/users/{user_id}/inventory/freshness`，用 `item_id` 对齐展示。
5. 展示 `freshness_label`、`confidence_level`、`risk_flags` 和 `data_quality_notes`；`null` 分量显示“数据不足”，不要显示为 0。
6. `expired` 为最高优先级硬状态；不得用颜色或其他分量把过期项显示成新鲜。
7. 不向 FreshFusion HTTP 接口发送 `reference_time` 或 `visual_freshness`，也不要尝试从库存 `add_time` 推导或覆盖 `evaluated_at`。
