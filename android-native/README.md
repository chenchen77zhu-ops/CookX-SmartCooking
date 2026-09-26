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

## 与 Web 版的差异

- 登录凭证保存在本机（七天有效期不变），重启 App 不必重新登录；更换后端地址会清除本机登录。
- 计时提醒改用系统 AlarmManager + 通知；精确闹钟权限可在 AI 厨房的提醒卡片里设置。
- 拍照识别使用系统相机或相册，上传前压缩到长边 1600px。
- 食材插图从 1.5–2MB 的 PNG 转成了 3–20KB 的 WebP。
- 测温数据只在 App 位于前台时进入温度引擎，切到后台会显式失效，与 Web 版的可见性处理一致。
