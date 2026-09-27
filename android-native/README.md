# CookX 原生 Android 客户端

用 Kotlin + Jetpack Compose (Material 3) 重写的 CookX App，替代 `frontend/` 下 Vue + Capacitor 的 WebView 壳。后端（`app/`）和接口契约不变：Bearer 会话、幂等键、`If-Match`、`expected_version` 都按原来的方式发送。

包名仍为 `com.smartcooking.app`，版本 2.0.0（versionCode 5），可以覆盖安装 1.3.0；签名不同时需要先卸载旧版。

## 构建

需要 JDK 17 和 Android SDK（首次构建会自动下载 platform 36 / build-tools）。

```powershell
cd android-native
.\gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
.\gradlew assembleRelease      # app/build/outputs/apk/release/app-release.apk（R8 压缩）
```

- 默认后端地址：`http://192.168.43.49:8000`，可用 `-Pcookx.backendOrigin=http://电脑IPv4:8000` 覆盖；App 登录页的「局域网后端地址」也能随时修改。
- 发布签名：在 `android-native/keystore.properties` 写入 `storeFile / storePassword / keyAlias / keyPassword`（不要提交），然后运行 `.\sign-release.ps1` 得到带 v1+v2+v3 签名的安装包。**不要用 debug 签名分发**：部分国产系统会静默拦截调试证书签名的应用。之后每次更新都必须用同一个证书，否则手机上无法覆盖安装，请备份好证书和密码。
- 只打包 `arm64-v8a` 和 `armeabi-v7a` 两种 ABI（ONNX Runtime 原生库很大，x86 只在模拟器上用得到）。
- 仓库在中文路径下时，`gradle.properties` 里的 `android.overridePathCheck=true` 让 AGP 正常构建。**单元测试**的 classpath 仍会被中文路径破坏，需要先映射一个 ASCII 盘符：`subst K: "<仓库上级目录>"`，然后在 `K:\CookX-web\android-native` 下运行。

## 测试

```powershell
.\gradlew testDebugUnitTest
```

- `logic/LogicTest`：移植自 Web 版 node:test 的用例（库存字段序列化与回读校验、菜谱规范化、测温帧解析、语音指令、烹饪会话计时、温度引擎）。
- `screenshots/ScreenshotTest`：用 Robolectric + Roborazzi 在 JVM 上渲染页面，输出到 `android-native/screenshots/`。
- `screenshots/LiveBackendTest`、`LiveFlowTest`：连接真实后端，渲染全部页面并走通各模块的写入流程。先启动带测试账号的 SQLite 后端，再设置 `COOKX_LIVE=1`：

```powershell
$env:PYTHONUTF8 = "1"; python tests/sqlite_browser_server.py   # 监听 127.0.0.1:8001
$env:COOKX_LIVE = "1"; .\gradlew testDebugUnitTest
```

没有 `COOKX_LIVE=1` 时这两组测试会自动跳过。

## 代码结构

```
app/src/main/java/com/smartcooking/app/
  core/        网络（OkHttp + 鉴权）、会话、本地存储、JSON 与时间工具、幂等命令 PendingCommand、依赖容器
  data/        库存/鲜度仓库、食材目录与图片、菜谱模型、识别草稿、账号偏好
  device/      经典蓝牙 SPP（JDY-31）连接与测温帧解析（原 TemperatureBluetoothPlugin）
  temperature/ 规则温度引擎、特征构造、ONNX 实验模型（onnxruntime-android）、仿真回放
  feature/
    auth/      登录、注册、后端地址
    home/      首页
    fridge/    冰箱、拍照识别与确认、智能推荐
    kitchen/   AI 厨房：菜谱对话、分步烹饪、语音、计时提醒、CookX Sense、完成与库存扣减
    cooking/   烹饪会话引擎、批次扣减、成长同步、系统通知、语音服务
    profile/   我的、偏好设置、账号与安全、烹饪记录、关于
    business/  家庭共享冰箱、共同采购、菜谱复刻/收藏、一起晒菜、剩菜改造、厨艺成长、七日菜单、偏好学习
  ui/          主题（CookX 品牌色）、通用组件、导航
```

## 界面设计（2.1）

- **主题**：默认浅色（参考 Apple Home 的卡片式中控），「我的 → 外观」可切换深色或跟随系统。颜色令牌在 `ui/theme/Theme.kt`（`CookX.*` 随主题实时切换），厨房页面固定使用 `KitchenColors` 深色摄影风格。温度、计时等数字使用内置的 Outfit 字体（OFL，见 `assets/licenses/OFL-Outfit.txt`）。
- **底栏**：首页 · 冰箱 · 厨房 · AI 菜谱 · 我的。每页只放必要信息，次要内容进入二级页或底部弹窗（全部服务、全部食材、温度趋势、烹饪工具、锅温预警）。
- **实时厨房**（`feature/live/`）：`LiveKitchen` 是全局锅温状态（设备读数、仿真回放、温度引擎、当前烹饪步骤），首页卡片、厨房、悬浮小窗与通知都读它。
  - 背景：`res/drawable-nodpi/kitchen_stage_{idle,preheat,heating,sear,overheat}.webp` 五张同机位照片，随锅温 1.6 秒淡入淡出，叠加蒸汽/烟雾粒子；卡片是重绘背景后模糊的毛玻璃。
  - 预警：≥235 °C 或高于目标上限 15 °C 为「温度偏高」，≥260 °C 或高于上限 40 °C（或温度引擎判定危险）为「锅温过高」——读数与背景变红、振动、语音提醒、通知栏高优先级提醒，并提供处理步骤、静音 60 秒与语音播报。
  - 悬浮小窗：烹饪中或已连接时，在其他页面显示可拖动的 CookX Sense 小卡片。
  - 通知：连接 CookX Sense 后以前台服务（`LiveCookingService`，connectedDevice）保持测温；Android 16 以「实时更新」显示，状态栏胶囊是锅温（相当于灵动岛），更早版本显示与首页卡片一致的自定义通知。未连接设备但正在烹饪时，退到后台才显示通知。
- **新增页面**：全部食材（搜索/分类/临期）、鲜度检测（`/api/freshness/evaluate`，不入库）、邀请内测成员（管理员，`/api/auth/invitations`）、AI 菜谱清空对话（`/api/clear-chat`）、账号与安全里的服务器登录核对（`/api/auth/session`）。
- 替换厨房背景：保持文件名与竖图比例（约 853×1844，锅位于画面中部），五张图机位一致，交叉淡化才不会跳动。

## 与 Web 版的差异

- 登录凭证保存在本机（七天有效期不变），重启 App 不必重新登录；更换后端地址会清除本机登录。
- 计时提醒改用系统 AlarmManager + 通知；精确闹钟权限可在 AI 厨房的提醒卡片里设置。
- 拍照识别使用系统相机或相册，上传前压缩到长边 1600px。
- 食材插图从 1.5–2MB 的 PNG 转成了 3–20KB 的 WebP。
- 测温数据只在 App 位于前台时进入温度引擎，切到后台会显式失效，与 Web 版的可见性处理一致。
