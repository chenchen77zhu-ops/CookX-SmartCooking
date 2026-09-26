# CookX「实时厨房」界面规范

深色摄影氛围 + 毛玻璃卡片 + 火候橙。参考 Apple Weather 的信息组织方式：一个巨大的核心读数（锅温），下面按重要程度排列阶段、趋势和建议。

## 设计令牌

全部定义在 `frontend/src/assets/theme.css`，旧的 `--cookx-*` 变量已映射到新令牌，旧样式无需逐个改名。

| 用途 | 令牌 | 值 |
|---|---|---|
| 页面底色 | `--ck-bg` | `#0D100F` |
| 玻璃卡片 | `--ck-glass` / `--ck-glass-border` | `rgba(30,34,32,.62)` / `rgba(255,255,255,.09)` |
| 主文字 / 次要 / 辅助 | `--ck-text` / `-2` / `-3` | `#F6F3EE` 及 68%、44% 透明度 |
| 火候橙（主操作、温度） | `--ck-heat` / `--ck-heat-deep` | `#FF8A3D` / `#E8641F` |
| 新鲜 / 临期 / 过期 | `--ck-fresh` / `--ck-warn` / `--ck-danger` | `#3DD68C` / `#FFC15A` / `#FF6B5B` |
| 奶油建议卡 | `--ck-cream` | `#F5EFE6`（深色界面里唯一的浅色焦点，只用于「CookX 建议」和选中态） |
| 圆角 | `--ck-radius-xl/lg/md/sm` | 26 / 22 / 16 / 12 |
| 页边距 | `--ck-gutter` | 16px，内容最大宽度 640px |

## 适配

- `index.html` 使用 `viewport-fit=cover`；Capacitor 8 的 SystemBars 注入 `--safe-area-inset-*`，统一读取 `--sat / --sab / --sal / --sar`（浏览器回退到 `env()`）。
- 顶栏、底栏、固定按钮都要加安全区；不要再写死 `100vh - 70px` 之类的高度，也不要在页面内再套滚动容器。
- 只有一级页面（首页、冰箱、厨房、菜谱、我的）显示底栏；二级页面用顶部返回栏；烹饪导航时 `uiState.immersive` 隐藏底栏。

## 组件

`frontend/src/components/ck/`：

- `CkIcon`：统一 24px 线性图标（底栏、按钮、列表）。
- `CkBackdrop`：页面背景（`kitchen` 锅具照片、`warm` 暖色光晕、`fresh` 绿色光晕），由 `App.vue` 按路由选择。
- `CkNavBar`：二级页顶部返回栏，粘性 + 毛玻璃。
- `CkGauge`：厨房环形锅温仪表。
- `CkTempChart`：温度曲线（实测实线 + 预测虚线 + 目标温区）。

业务二级页（家庭、采购、菜谱、菜单、剩菜、晒菜、成长、学习）共用 `business.css`：`<header>` 自动变成顶部返回栏，`section` 是玻璃卡片，`button.primary` 是橙色主按钮，其余按钮为半透明胶囊。「我的」下的子页共用 `profile-pages.css`。

## 约定

- 新页面优先用令牌和上述组件，不再引入浅色背景或硬编码颜色。
- 温度阶段提示（预热 / 升温 / 煎香 / 爆炒 / 过热）只用于界面提示，不参与算法判断。
- 按钮文字、输入框占位符、表单标签是浏览器验收脚本的定位依据，改文案时同步修改 `frontend/scripts/*.cjs`。
