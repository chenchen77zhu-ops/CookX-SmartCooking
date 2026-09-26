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
  - 实时厨房显示锅温、阶段、曲线摘要和建议；点「温度趋势」打开算法、回放与完整记录，设备与诊断放在设备弹窗。
  - 烹饪导航显示菜名、仪表、计时摘要、曲线、下一阶段和当前步骤；完整步骤、上一/下一步、语音、计时控制、提醒、调整、设备收在「烹饪工具」抽屉。点击当前建议卡或右上角省略号均可打开。完成核对使用独立抽屉。
- 二级页的次要表单（新增、生成、历史）使用 `<details class="ck-disclosure">`，默认折叠。

## 实时厨房

- `services/liveKitchen.js` 是全局锅温状态，包括温度、历史、当前烹饪会话、阶段、ETA 和预警。所有页面、悬浮窗和通知都读取它。
  - 预警规则：`warn` 为 ≥235 ℃ 或高于目标上限 15 ℃；`danger` 为 ≥260 ℃、高于目标上限 40 ℃，或评估结果为危险。
  - 进入 `danger` 时振动，并语音提醒「锅温过高，请立即调小火力」。点击阶段标签可静音 60 秒。
  - 开发环境可用 `window.__cookxLive.inject(温度)` 注入温度来调试。
- `CkStageBackdrop` 在 5 张状态背景之间淡入淡出（1.6 秒）：`idle`、`preheat`、`heating`、`sear`、`overheat`。叠加 `CkSteam` 画布蒸汽/烟雾；过热时四周出现红色脉冲。
  - 背景图位于 `frontend/src/assets/backdrops/stage-*.webp`，2026-09-26 已替换为同机位生成式摄影素材，生成记录见下方「图片资源」。旧调色脚本已移除。
  - 后续替换须保留同一锅具轮廓与机位：1080×2340，锅中心约为画面高度 51%，顶部与底部均为暗色低细节安全区。
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


## 2026-09-26 布局补充

- `App.vue` 的 `--ck-bottom-inset` 统一表示底栏总高度与 `--sab`；底栏 1px 边框包含在总高度中。页面通过 `100dvh - var(--ck-bottom-inset)` 使用可见高度。
- 首页将八个常用服务保留在右上角抽屉，不减少业务入口。短屏下减少卡片内间距与重复文案。
- 实时厨房使用七行 Grid，第四行专门留给锅具；三张下方卡片间隔 12px，最后一张卡片距离底栏 12px。厨房导航固定使用浅色令牌，其他导航随用户主题。
- 冰箱插画使用独立 30% 宽度区域，左侧统计使用 66% 宽度；`object-fit: contain` 保留完整设备与透明边缘。
- `chartGeometry.js` 仅负责显示：无效点过滤、时间排序与重复时间去重、保形曲线、动态坐标范围。0/1 个点显示灰色占位和“正在积累温度记录”，不把占位作为测量数据。X 轴为真实本地时刻，短窗口精确到秒。
- 预测虚线仅使用既有温度模块返回的未来点；没有预测时显示“预测待有效数据”。不为复刻示意图伪造预测温度、倒计时或连接状态。
- 新增摄影色板集中在 `theme.css` 的 `--ck-photo-tone-*`，保留原 UI 重构分支的摄影颜色；业务色仍用 `--ck-fresh/heat/warn/danger-*`。
- `frontend/scripts/browser-ui-review.cjs` 在 Vite 开发模式下生成 42 张页面截图和 4 张温度状态截图。设置 `COOKX_UI_SAFE_AREA=1` 可追加上下各 24px 安全区检查。库存、账号与注入温度均为明确的视觉夹具；真实 API 与算法回放由现有 22 个浏览器脚本独立验收。

## 图片资源

本轮素材由 OpenAI 内置 `image_gen` 于 2026-09-26 生成，不是实机拍摄记录。背景只用于氛围展示，画面中的食材、蒸汽不能当作传感器观测。原始 PNG 保留在本机生成目录；仓库分发下表 WebP。使用 Pillow 仅执行用户要求的尺寸转换和 WebP 压缩，未用程序绘制替代图像生成。

| 资源 | 输出路径 | 像素 | 文件字节 | 生成方式 |
|---|---|---|---:|---|
| idle | `frontend/src/assets/backdrops/stage-idle.webp` | 1080×2340 | 148040 | 全新生成 |
| preheat | `frontend/src/assets/backdrops/stage-preheat.webp` | 1080×2340 | 143972 | idle 底图编辑 |
| heating | `frontend/src/assets/backdrops/stage-heating.webp` | 1080×2340 | 165872 | idle 底图编辑 |
| sear | `frontend/src/assets/backdrops/stage-sear.webp` | 1080×2340 | 192504 | idle 底图编辑 |
| overheat | `frontend/src/assets/backdrops/stage-overheat.webp` | 1080×2340 | 193884 | idle 底图编辑 |
| kitchen | `frontend/src/assets/backdrops/kitchen.webp` | 1200×900 | 104204 | sear 风格参考编辑 |
| fridge | `frontend/src/assets/illustrations/fridge.webp` | 400×600 | 17954 | 透明背景生成 |
| dish | `frontend/src/assets/images/home-hero-dish.webp` | 600×600 | 109624 | 全新生成 |
| notification | `frontend/android/app/src/main/res/drawable-nodpi/live_card_pan.webp` | 600×450 | 26860 | kitchen 等比缩放 |

原始生成 ID 和完整提示词（英文为实际传给工具的提示词）：

### idle

原始文件：`exec-f539eaa2-d192-4bab-af2c-e500aaa69cbb.png`。

```text
Use case: photorealistic-natural. Asset: full-screen smartphone kitchen background, portrait 1080x2340 or same 6:13 aspect ratio. Produce a photograph only, no UI, no letters, no border. A single matte black nonstick frying pan with a black handle extending right, on a charcoal stone countertop in a dark domestic kitchen, viewed from a locked camera at a 45-degree overhead angle. Pan bowl center exactly at horizontal 50%, vertical 51% of the entire canvas. Pan bowl spans about 92% of canvas width, its visible bowl occupies vertical 43% to 59%; handle may extend beyond right edge. The top 0–40% is very dark softly blurred kitchen with restrained warm bokeh, deliberately low detail and space for white typography. Bottom 62–100% is uninterrupted very dark softly textured counter for cards. State IDLE: completely empty cold pan, absolutely no food, no oil, no steam, dim cool gray ambient light. Subtle rim highlights give realistic depth. Natural commercial food photography, elegant low contrast shadows, realistic materials. Keep all interesting objects in the narrow central pan band. No stove controls or props.
```

### preheat

原始文件：`exec-1066b131-e628-4208-8f3f-a0f5e0c20dfd.png`。 参考图：idle 原始 PNG。

```text
Edit target: provided kitchen photograph. State PREHEAT. Add only a thin shallow layer of oil in the pan with subtle sheen. No food and virtually no steam. Slightly warmer rim light. Preserve the exact pixel composition, same pan size, bowl center at same position, same handle, countertop horizon, dark background objects, camera angle and portrait aspect ratio. Do not zoom, crop, reposition the pan or add objects. Keep top 0–40% and bottom 62–100% dark and low contrast for app text overlays. Photo only, no text or UI.
```

### heating

原始文件：`exec-5ab6d089-7351-4e83-a4c1-de7d3c779f22.png`。 参考图：idle 原始 PNG。

```text
Edit target: provided kitchen photograph. State HEATING. Add a thin shallow layer of shimmering rippled hot oil inside the pan, with fine delicate wisps of white vapor rising just above the rim. No food. Warm orange ambient highlights. Preserve the exact pixel composition, same pan size, bowl center at same position, same handle, countertop horizon, dark background objects, camera angle and portrait aspect ratio. Do not zoom, crop, reposition the pan or add objects. Keep top 0–40% and bottom 62–100% dark and low contrast for app text overlays. Photo only, no text or UI.
```

### sear

原始文件：`exec-1600c539-2902-4f81-bbbc-a6dd893aef42.png`。 参考图：idle 原始 PNG。

```text
Edit target: provided kitchen photograph. State SEAR. Inside this same pan now sear two golden browned chicken breast pieces with three broccoli florets and a few red and yellow bell pepper slices. Thin hot oil, appetizing browning, tiny oil glints, visible delicate white cooking steam. Warm golden lighting. Preserve the EXACT composition and geometry: identical pan outline and handle position, countertop horizon, camera angle, portrait canvas, background shapes. Change only contents, vapor and illumination; do not move or resize the pan. Leave top 0–40% dark and low detail with smoke confined mostly above the pan in the central band; bottom 62–100% remains dark countertop. No text or UI.
```

### overheat

原始文件：`exec-07237df8-d6da-42ad-b64c-b11c28699123.png`。 参考图：idle 原始 PNG。

```text
Edit target: provided kitchen photograph. State OVERHEAT. Inside this same pan place two overly browned chicken breast pieces with dark charred edges and a few broccoli and bell pepper pieces. Dense gray-white smoke rising from overheated oil, red-orange rim illumination and a subdued red cast suggesting a warning. No flames. Preserve the EXACT composition and geometry: identical pan outline and handle position, countertop horizon, camera angle, portrait canvas, background shapes. Change only contents, vapor and illumination; do not move or resize the pan. Leave top 0–40% dark and low detail with smoke confined mostly above the pan in the central band; bottom 62–100% remains dark countertop. No text or UI.
```

### kitchen

原始文件：`exec-06b69026-1178-4628-9964-8c03c7695cc7.png`。 参考图：sear 原始 PNG。

```text
Create a landscape 4:3 commercial food photograph, 1200x900 equivalent. Dark elegant home kitchen, matte black skillet containing golden seared chicken breast, broccoli and red/yellow peppers with gentle white steam. Same charcoal pan and warm golden low-key photographic style as the reference. Composition for a mobile dashboard card: the entire left 48 percent is very dark softly blurred empty negative space for white labels, pan and food sit on the right, bowl centered at x=82%, y=63%, cropped by the right edge. Fine realistic oil highlights, charcoal countertop, warm brown glow near lower right. No text, no UI, no border.
```

### fridge

原始文件：`exec-9ad51e04-95d5-4034-af2d-c1b5f025ebc8.png`。 工具参数：`transparent_background=true`，WebP 保留 Alpha。

```text
Asset: transparent background isolated 3D lightly skeuomorphic smart-home appliance illustration. A complete mint green two-door refrigerator, freezer door above and large refrigerator door below, rounded corners, satin enamel, two tiny recessed handles on left edge, subtle pale metal feet. Three-quarter view showing front and a little right side, front facing slightly left. Elegant high quality Apple Home style miniature product render, realistic soft studio lighting, mint/ivory highlights, no brand, no letters, no UI. Refrigerator is centered with generous transparent margin all around; its entire top, doors, sides and feet must be visible. Portrait 2:3 composition, shadow subtle only under feet; fully transparent outside appliance. No floor, no scene.
```

### dish

原始文件：`exec-c63427a8-76e6-4dcd-95b3-560767888bc1.png`。

```text
Square 1:1 photorealistic overhead food photograph for a small recipe card. A warm ivory ceramic plate of Chinese stir-fried golden chicken breast bites, bright green broccoli, red and yellow bell peppers, naturally arranged with a light appetizing glossy sauce. Plate entirely visible, centered, occupies 88% of frame, neutral warm cream linen tabletop, soft warm daylight from upper left, clean editorial food photography, accurate textures, restrained styling. No utensils, no text, no hands, no extra props. 600x600 equivalent.
```

通知图没有新增生图提示词：将 `kitchen.webp` 等比缩放至 600×450，WebP quality=80。五张阶段背景采用 quality=85、method=6，均小于 300,000 字节。
