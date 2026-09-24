> 历史批次记录：2026-09-24 后端接入与完成状态以 [最新验收表](../p2-online-completion.md) 为准。下文保留当时事实，不代表当前仍缺接口。

# CookX 人员二第二批交付与回退表

后端基线：`3bc9da2758d7d0e644b52d4040263632557a657a`，交付前重新 fetch 确认 main 未变化。主人的 A3 提交已普通合并进原 B1→B4 分支链，新首分支从 B4 的 `e820d67` 开始。本批没有修改后端、固件、FreshFusion/推荐公式或温度算法。

## 功能检查点

以下是代码验收提交；本文件之后的文档提交不改变运行代码。

| 功能 | 分支 | 代码检查点 | 草稿 PR / 目标分支 | 状态 |
|---|---|---|---|---|
| 库存日期/小数/清空 | `feat/p2-inventory-edit-completion` | `585bbaf` | [#10](https://github.com/chenchen77zhu-ops/CookX-SmartCooking/pull/10) → `feat/p2-inventory-flow` | 已推送；本地/CI 软件验收通过；剩余归并规则待主人联调 |
| 菜谱校验与异常恢复 | `feat/p2-recipe-validation` | `f442fdf` | [#11](https://github.com/chenchen77zhu-ops/CookX-SmartCooking/pull/11) → `feat/p2-inventory-edit-completion` | 已推送；本地/CI 软件验收通过 |
| 会话、步骤与真实时间计时 | `feat/p2-cooking-session` | `594b422` | [#12](https://github.com/chenchen77zhu-ops/CookX-SmartCooking/pull/12) → `feat/p2-recipe-validation` | 已推送；本地/CI 软件验收通过 |
| 单句固定指令/系统播报 | `feat/p2-voice-commands` | `5195560` | [#13](https://github.com/chenchen77zhu-ops/CookX-SmartCooking/pull/13) → `feat/p2-cooking-session` | 已推送；本地/CI 软件验收通过；待真机 |
| 本地调整预览/撤销 | `feat/p2-cooking-adjustments` | `73c285c` | [#14](https://github.com/chenchen77zhu-ops/CookX-SmartCooking/pull/14) → `feat/p2-voice-commands` | 已推送；本地/CI 软件验收通过 |
| 系统提醒/完成扣减确认 | `feat/p2-cooking-reminders` | `380c2df` | [#15](https://github.com/chenchen77zhu-ops/CookX-SmartCooking/pull/15) → `feat/p2-cooking-adjustments` | 已推送；本地/CI 软件验收通过；待真机 |
| 设备生命周期/诊断导出 | `feat/p2-device-diagnostics` | `ba44ba6` | [#16](https://github.com/chenchen77zhu-ops/CookX-SmartCooking/pull/16) → `feat/p2-cooking-reminders` | 已推送；本地/CI 软件验收通过；待真机 |

七个草稿 PR 在上述检查点的 app、backend、simulation 检查全部 SUCCESS，自动合并均未开启。修复在所属功能分支提交，再向后普通合并；未强推。独立目录 `CookX-p2-copilot-integration` 的本地集成分支为 `integration/p2-copilot-local`，未推送、未跟踪远端。

## 软件验收

- 前端 62 项测试；真实隔离后端 146 项 pytest；温度仿真 3 项 unittest，通过。
- 移动端 Chromium（390×844）：库存异常/零分/未知、识别确认与本地库存回读、FreshFusion 和推荐、计时恢复/暂停、重复语音结果/低置信度确认、调整取消/确认/撤销、完成清单与真实扣减回读、诊断导出通过。
- 原有温度回放、WASM 推理、模型失败回退、HTML 音频暂停继续、语音服务异常时计时通过；未改算法。100 样本 Python/浏览器输出最大绝对差约 9.54e-7，桌面 WASM P95 约 0.4 ms，不是手机数据。
- Android `assembleDebug testDebugUnitTest` 通过，原生编译不代表真实权限、识别、通知投递和文件保存已经通过。构建的 JVM 测试仍主要是现有基础测试；原生行为列入真机表。
- 库存与鲜度使用本机临时存储的真实 API；视觉、云端菜谱和在线 TTS 为测试替身。没有云端服务部署验收。

组合回归修复了嵌套 Vue 菜谱数据无法复制的真实缺陷，补了包含营养信息的测试；另修正了回放测试在页面隐藏持久化之后未真正清除旧会话的测试隔离问题。没有删除失败路径的断言。

## 安装包与证据

`CookX-copilot-d3a5e8f-debug.apk`：50,877,086 字节；构建自本地集成提交 `d3a5e8ffc9804de2224f663cb1483a6a5d8ca31d`，运行代码等同 `ba44ba6`。

SHA256：`dedef1cdad68f5f9de74fd11f318e511c6735099123d707555ee921ef67aeda3`。

本地交付目录：`artifacts/CookX-copilot-20260922`，包括 APK、截图、测试日志、提交清单及依赖审计。GitHub Actions 也提供各 PR 的构建产物。APK 仍沿用仓库配置的 `http://192.168.43.49:8000`；该地址当前无已验收服务，手机库存/登录/推荐不能据此认定可用。后续需连接可达后端再进行真机全流程验收。

## 待联调及限制

日期编辑、正小数、无旧字段回退记录的 null 清空已用隔离后端验证。不同购买/到期日期与小数保质期批次归并、旧时间迁移、按批次扣减和服务端幂等仍由主人处理；前端对不支持路径明确限制。详见 [接口交接](interface-handoff.md)。

真实 SPP、移动测温、中文 ASR/TTS、权限、锁屏提醒、文件选择器保存均待真机。没有烧写硬件或承诺后台连续采集。详见 [设备验收表](device-acceptance.md)。

依赖审计列出 13 项（1 critical / 10 high / 2 moderate），涉及已有 axios、Vite/构建工具及间接依赖；新增 Capacitor App/Local Notifications 未被该审计单列为漏洞。未在本批强制升级 Vite 主版本；这属于发布前依赖维护事项，不影响本次功能测试事实，也不能将本次验收解释为生产安全审计通过。

## 回退

按设备诊断 → 提醒 → 调整 → 语音 → 会话 → 菜谱校验 → 库存收尾逆序退出依赖链。调试可在独立目录检出上表检查点；未合并的 PR 可保留草稿或关闭，不重写已上传历史。将来主人合并后如需撤回，应普通 revert 对应合并提交，并从后向前处理依赖。

回退前导出诊断并保留本地 `cookx:cooking:v1:<user>`、`cookx:consumption:v1:<user>` 和库存待确认记录。旧版本不理解新会话时不能据此再次扣减。清空数据不是回退步骤。

**未更新远端 main，未执行 GitHub 合并，未启用自动合并，未推送集成分支。**
