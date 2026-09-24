# CookX 离线功能测试版

**当前分支：`feat/p2-local-test-apk`。仅用于独立测试，禁止合入 main。**

[下载离线测试 APK（2026-09-24）](https://github.com/chenchen77zhu-ops/CookX-SmartCooking/releases/tag/offline-test-20260924)

- 在 GitHub 左上方切换到本分支，通过上方发布页下载 `.apk`，无需服务器、数据库或登录。
- 应用名“CookX 本地测试”，包名 `com.smartcooking.app.localtest`，可与正式版共存。推荐下载发布页 APK；Actions 的调试签名可能不同。
- 已同步 B1–B9 页面及批次核对、字段编辑、BDN 目标入口和 JDY-31 修复。测试账号 A/B、示例重置、故障注入均在顶部面板。
- 鲜度/识别/推荐/菜谱使用明确标注的本地样例；没有运行真实视觉、FreshFusion、云端推荐服务。温度回放/模型随包提供，也可连接真实 JDY-31；系统语音是否离线可用取决于手机服务。
- 编译：`cd frontend` 后依次 `npm ci`、`npm run build`、`npx cap sync android`，再使用 JDK 21 与 Android SDK 36 构建。Windows 可运行 `frontend/scripts/build-localtest-android.ps1`，配置 JAVA_HOME/ANDROID_HOME。
- 本分支 push 后运行 `Offline test APK` 并生成 Actions 产物。正式修复只从 main 单向同步到本分支，**不要发起离线分支→main 的 PR**。

[本轮验证及限制](docs/localtest/20260924-delivery.md) · [正式版完成情况](docs/p2-online-completion.md)
