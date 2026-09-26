# CookX 界面规范

默认浅色（参考 Apple Home 的卡片式中控），可切换深色或跟随系统；「实时厨房」、烹饪导航、登录注册始终使用深色摄影风格（参考 Apple Weather：一个巨大的锅温读数，其余信息尽量少）。

## 主题与设计令牌

全部定义在 `frontend/src/assets/theme.css`。浅色令牌写在 `:root`，深色令牌写在 `:root[data-theme='dark']` 与 `.ck-dark`。旧的 `--cookx-*` 变量已映射到新令牌。

| 用途 | 令牌 | 浅色 | 深色 |
|---|---|---|---|
| 页面底色 | `--ck-bg` | `#F3F1EC` | `#0D100F` |
| 卡片 | `--ck-surface` / `--ck-glass` | 白色 + `--ck-shadow` | 半透明毛玻璃 |
| 主文字 | `--ck-text`（`-2` / `-3` 次要、辅助） | `#1B1D1C` | `#F6F3EE` |
| 火候橙 | `--ck-heat` / `--ck-heat-deep` / `--ck-heat-text` | 主操作、温度 | 同左 |
| 新鲜 / 临期 / 过期 | `--ck-fresh` / `--ck-warn` / `--ck-danger`（带 `-soft` / `-text`） | | |
| 奶油建议卡 | `--ck-cream` | 仅用于「CookX 建议」 | |

- 外观偏好存在 `localStorage['cookx:theme']`（`light` / `dark` / `system`），由 `services/theme.js` 应用。`index.html` 里的内联脚本会提前应用深色，避免闪白。设置入口在「我的 → 外观」。
- 始终深色的页面在根元素加 `ck-dark` 类，并调用 `setForcedDarkPage(true)`，让状态栏图标变为浅色。
- 数字字体 `CookX Numeric`（Outfit，OFL，随包内置）通过 `.ck-num` 使用。
- 通用类：`.ck-page .ck-card .ck-head .ck-title .ck-section-title .ck-btn(--heat/--cream/--ghost/--block) .ck-disclosure`。

## 信息架构：每页少放东西

- 一级页面只放摘要，详情进入二级页或抽屉：
  - 冰箱总览只显示统计、临期前 2 项和分类；完整列表在 `/fridge`，卡片折叠，点开才显示鲜度依据和编辑按钮。
  - 「AI 菜谱」是独立 Tab（`/home?tab=Recipes`）。
  - 实时厨房只显示锅温、阶段和建议；温度曲线放在「温度趋势」抽屉，设备与诊断放在设备弹窗。
  - 烹饪导航只显示当前步骤；步骤列表、计时、提醒、调整、设备收在「烹饪工具」面板。
- 二级页的次要表单（新增、生成、历史）使用 `<details class="ck-disclosure">`，默认折叠。

## 实时厨房

- `services/liveKitchen.js` 是全局锅温状态，包括温度、历史、当前烹饪会话、阶段、ETA 和预警。所有页面、悬浮窗和通知都读取它。
  - 预警规则：`warn` 为 ≥235 ℃ 或高于目标上限 15 ℃；`danger` 为 ≥260 ℃、高于目标上限 40 ℃，或评估结果为危险。
  - 进入 `danger` 时振动，并语音提醒「锅温过高，请立即调小火力」。点击阶段标签可静音 60 秒。
  - 开发环境可用 `window.__cookxLive.inject(温度)` 注入温度来调试。
- `CkStageBackdrop` 在 5 张状态背景之间淡入淡出（1.6 秒）：`idle`、`preheat`、`heating`、`sear`、`overheat`。叠加 `CkSteam` 画布蒸汽/烟雾；过热时四周出现红色脉冲。
  - 背景图位于 `frontend/src/assets/backdrops/stage-*.webp`，由 `frontend/scripts/generate-kitchen-backdrops.py` 从现有锅具照片调色生成。
  - 如有实拍或设计稿，直接替换同名文件即可（竖图，约 900×1950，锅具位于画面中下部）。
- `CkSenseCard` 是首页的 CookX Sense 卡片，`variant="mini"` 为悬浮小窗样式。
- `CkLiveFloat`：连接设备或正在烹饪时，在其他页面显示可拖动的悬浮小窗（松手自动贴边）。点击回到厨房；关闭后会一直隐藏，直到会话或连接状态变化。

## App 退到后台：常驻通知

- `services/liveNotification.js` 在页面隐藏时调用原生插件 `LiveCooking.show`，回到前台后隐藏。
- Android 实现位于 `LiveCookingNotifier.java`：
  - Android 16+：Live Updates（`ProgressStyle` + 状态栏胶囊短文本），这是 Android 上与「灵动岛」对应的系统位置。
  - 更早的版本：使用与 Sense 卡片一致的自定义通知卡片（`res/layout/live_card_*.xml`）。
  - 蓝牙读取线程在后台也会直接解析温度并更新通知；过热时通过高优先级渠道弹出提醒。

## 适配

- `index.html` 使用 `viewport-fit=cover`。Capacitor 8 的 SystemBars 会注入 `--safe-area-inset-*`，统一读取 `--sat / --sab / --sal / --sar`（浏览器中回退到 `env()`）。
- 不写死 `100vh - 70px` 之类的高度，也不在页面内再套滚动容器。
- 只有一级页面显示底栏；二级页面用 `CkNavBar` 返回栏；烹饪导航时隐藏底栏。

## 约定

- 新页面只用令牌和上述组件，不写死颜色，同时检查浅色和深色两种效果。
- 信息超过一屏时，先考虑拆成二级页、抽屉或折叠区。
