> 历史批次记录：2026-09-24 后端接入与完成状态以 [最新验收表](../p2-online-completion.md) 为准。下文保留当时事实，不代表当前仍缺接口。

# 接口与协作交接

## 当前结构

| 结构 | 版本 / 字段 | 负责行为 |
|---|---|---|
| CookingSession | schemaVersion=1；user/id/recipeVersion/stepIndex/status/timers/adjustments/reminders/consumption | 每用户一个当前会话；完成记录在替换时归档，最多保留 50 条 |
| StepTimer | visited/remainingMs/deadline/round/notificationKey | deadline 为 Unix 毫秒，暂停置 null；round 防止超时重复提醒 |
| Adjustment | schemaVersion=1；sessionId/baseVersion/type/patches/version/undone | patches 记录 before/after/index，拒绝旧版本和已进入步骤 |
| ReminderEvent | schemaVersion=1；key/type/object/risk/at/dismissed/text | 会话内去重；计时每轮一次，其他默认五分钟 |
| Consumption | schemaVersion=1；user/sessionId/items/status/at | 提交前写本地 uncertain；回读一致再 confirmed，禁止自动重发 |
| DeviceDiagnostics | schemaVersion=1；source/info/state/records | 有界原始帧与解析样本、连接/恢复/超时事件；仿真独立导出 |

原生 `CookingVoice`：`capabilities/start/stop/speak/stopSpeaking`，单次 start 返回 text 和可选 confidence；`speechState` 事件携带 id 和状态。仅调用时请求麦克风权限。`TemperatureBluetooth` 增加 `getDiagnosticsInfo/exportDiagnostics`，导出用系统文档选择器，无全盘存储权限。

## 主人仍需处理

1. 库存批次 key 应区分购买/到期日期，并保留小数保质期精度。目前日期/小数编辑可用，但归并规则仍不能完整表达这些差异。
2. 约定旧 purchase_date、storage/storage_method 回退字段的 null 清空及无时区历史迁移。页面保留旧值；有对应旧字段时禁用清空，不删除重建记录。
3. 消耗接口目前按名称（含别名）扣所有匹配记录，每条数量减一。前端发现多批次会禁止自动提交；需要后端提供 item_id、数量/单位和幂等键，并返回实际变更结果。
4. 本地回读只能确认当前库存与预期一致，无法证明另一设备是否并发写入；服务端原子事务/幂等是最终解决方案。响应丢失保留待核对，不重发。
5. 手机可达后端与认证部署单独联调；APK 当前沿用既有固定局域网地址。前端状态版本不要求重构 FreshFusion、推荐或温度模块。

## 复测入口

- `npm ci --prefix frontend`；`npm test --prefix frontend`；`npm run build --prefix frontend`。
- 安装 requirements-test.txt 后执行 `python -m pytest tests -q`，使用测试占位 API key，不调用真实云端。
- 启动 `tests/browser_server.py`（127.0.0.1:8000），生产前端 preview；设置 COOKX_BASE_URL 和 Playwright 路径，再运行 CI 中七个 browser 脚本。
- JDK 21 / Android SDK 36：`npx cap sync android` 后 `gradlew assembleDebug testDebugUnitTest`。Windows 非 ASCII 路径采用临时 ASCII 构建目录，复制同提交的 Android 与对应 Capacitor 包。

参考：[Android SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer)、[Capacitor Local Notifications](https://capacitorjs.com/docs/apis/local-notifications)、[Android 蓝牙权限](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)。系统置信度可能缺失；0.8 是交互门槛，不是识别准确率。精确计时权限未授予时明确展示可能延迟。
