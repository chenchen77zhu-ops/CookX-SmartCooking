# 本地测试版交付记录（2026-09-22）

- APK：`CookX-localtest-532176a-debug.apk`；50,885,187 字节（约 48.5 MiB）。
- 源码：`532176afa6ce001f6b85e6914f566f890e295af3`；构建前工作区干净。本记录为后续文档，不改变 APK 运行代码。
- 包名：`com.smartcooking.app.localtest`；名称：CookX 本地测试；版本：1.0-localtest；最低 Android 7.0 / API 24。
- SHA256：`da3a02b0b57c6bcf6807a781bbc0c84bc2da7a263b9ab0a9a250b3bcbff8f5f8`。
- 本地交付目录：`artifacts/CookX-localtest-20260922`，含 APK、使用说明与功能对照、截图、构建日志、包身份/签名验证、构建清单及验证 JSON。

## 本轮验证

| 检查 | 结果与口径 |
|---|---|
| 前端单元测试 | 原有 62 项 + 本地数据 6 项全部通过 |
| 本地版浏览器 | 390×844 桌面 Chromium；18 类检查通过，无未捕获错误，无 API/外网请求。包含真实本地存储写入、刷新、账户切换、日期/小数/null、识别样例确认、菜谱失败恢复、计时/指令、调整撤销、扣减响应丢失核对、温度回放与模型加载 |
| 离线操作 | 页面加载后关闭浏览器网络，预置推荐仍可用；APK 冷启动无需远端静态资源，但未在手机上做飞行模式验收 |
| 原版回归 | 普通构建及 copilot、p2-fixture-ui、p2-write-faults 三组浏览器回归通过；这些属于接口替身测试 |
| Android | assembleDebug、testDebugUnitTest 成功；后者仅有 1 个已有基础 JVM 测试，不是 ASR/通知/蓝牙设备测试 |
| APK 检查 | v2 签名通过；独立包名/启动 Activity 正确；60 个前端资源逐字节匹配；包含 ONNX、WASM、回放数据，无远端启动 URL |
| 后端全流程 | 本轮未通过该检查：在线 browser-p2-flow 因本机 8000 服务未启动而无法开始。本轮交付使用本地适配器，不能据此声称新一轮真实后端联调通过 |
| 真机 | adb 未发现手机；实际安装、中文 ASR/TTS、通知权限/锁屏、SPP 和测温效果继续待实测 |

已有 Vite 大包警告及 Android SDK/Gradle 兼容性提示未阻止构建。未升级依赖，上一批记录的发布前依赖维护项仍适用。

代码仅在 `feat/p2-local-test-apk` 保存，未推送、未创建 PR、未合并 main。功能数与文档逐项边界见 [使用说明](README.md)。
