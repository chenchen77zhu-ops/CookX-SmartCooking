# CookX 原生界面与离线分支验收记录

日期：2026-09-27。

| 版本 | 功能提交 | Android 包名 / versionCode | 本地结果 |
|---|---|---|---|
| 正式 main | 2d22c81（PR #49，含 1b64d14 和 3f812f5） | com.smartcooking.app / 7 | release 构建通过，24 项测试通过 |
| 独立演示 | 92a594b | com.smartcooking.app.showcase / 8 | release 构建通过，49 项测试通过 |
| 独立离线测试 | c59935c | com.smartcooking.app.localtest / 9 | release 构建通过，49 项测试通过 |

三个 APK 均为 2.1.0，包含 arm64-v8a / armeabi-v7a，非 debuggable。沿用本机测试签名（证书 SHA256 dc6cf3024db90fcc75e7282eb3bb209a015b4863da32f3e3ea78b4f355fdde2a），用 apksigner 核验 v1/v2/v3，用 zipalign -c -P 16 4 检查对齐，用 aapt 核对启动 Activity、包名和版本。

两个离线 APK 的最终合并 Manifest 没有 INTERNET 权限。正式版有网络能力。离线温度没有生成数据接口；真实 JDY-31 与用户主动启用的既有回放仍分开。

## 测试证据

- 原生既有 LogicTest：15；LiveRulesTest：9。新增质量不足抑制建议、时长/华氏数值不能误读为摄氏目标。
- 离线 LocalEngineTest：23，覆盖 32 食材初始内容、账号隔离/注销、日期清空和小数保质期、版本冲突、识别入库幂等、批次扣减和凭证恢复、写入失败回滚、家庭转入/成员移除、认领/购买/入库、复刻/收藏、点赞/评论/隐藏/删除、成长事件去重、剩菜异常、偏好设置/训练/清除、七日菜单保存/核对、预算营养缺失拒绝及忌口/过期食材排除。
- 鲜度对照：在一项测试中逐项检查 15 组来自 Python 正式服务的固定输入，比较 T/S、有效权重、FreshScore、置信度和日期风险状态。没有视觉或储存环境温度测量。
- Compose UI：2 项测试覆盖首页、冰箱、厨房、我的，以及家庭、采购、菜谱库、社区、剩菜、成长、菜单、偏好学习、AI 菜谱，共 13 个页面。截图来自 Robolectric Android 35 / 390×844 dp，不是真机截图。
- 开发工具链：Windows、JDK 21、Android SDK 36、Gradle 8.14.3；ASCII 临时构建目录；release R8 和 lintVital 通过。

具体日志和 JUnit XML 位于交付目录；APK 的 SHA256 见 SHA256SUMS.txt。

## Git 边界与回退

正式主分支通过 PR #49 普通合并。独立演示分支与离线测试分支仅推送；没有创建合并它们到 main 的 PR。正式 main 不含原生 offline 或 Web localtest 目录。离线分支的边界检查也会阻止离线目录进入以 main 为目标的 PR。

功能修复保留独立提交，未改写已上传历史：演示内容 44cd01c → 记录正式基线 afc1aed → 食材排除与家庭菜单份量修复 92a594b；离线测试为正式同步合并 → e30cb7f → 包名/版本隔离 6c29fa6 → c59935c。回退时检出相应提交构建，不用 force push。

旧 WebView 本地测试数据不会自动迁移成原生记录；文件不主动删除，升级前有需要请导出。任何清除应用数据操作都会删除该应用本机记录。

## 未被本轮验证的项目

未连接手机做安装复测；未复测系统语音、锁屏通知、JDY-31 长连或实际烹饪。不能把本次构建、模拟环境 UI 和本机内容结果写成真机算法准确率或真实多人同步。云端识别与问答为预置内容；本机菜单组合器不等同于服务端 CP-SAT。说明详见 docs/NATIVE-OFFLINE-SHOWCASE.md。
