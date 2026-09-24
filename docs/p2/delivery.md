# 人员二 B1–B4 交付记录

开发起点：`539c231bec5f0211d0a5663c9e685a3be829095a`。开发期间主人合入后端 A3：`3bc9da2758d7d0e644b52d4040263632557a657a`（PR #5）。本任务未修改后端、算法或硬件。

| 功能 | 分支 / 草稿 PR | 已测试代码提交 | 依赖 | 状态 |
|---|---|---|---|---|
| B1 鲜度卡片 | [feat/p2-freshness-ui / #6](https://github.com/chenchen77zhu-ops/CookX-SmartCooking/pull/6) | 20392f0 | main 539c231 | 已推送；本地软件验收通过 |
| B2 分项依据 | [feat/p2-freshness-details / #7](https://github.com/chenchen77zhu-ops/CookX-SmartCooking/pull/7) | c87f2ea | B1 20392f0 | 已推送；本地软件验收通过 |
| B3 共用表单 | [feat/p2-inventory-fields / #8](https://github.com/chenchen77zhu-ops/CookX-SmartCooking/pull/8) | a3f52d0 | B2 c87f2ea | 已推送；部分完成、待联调 |
| B4 页面闭环 | [feat/p2-inventory-flow / #9](https://github.com/chenchen77zhu-ops/CookX-SmartCooking/pull/9) | d7473e4 | B3 a3f52d0 | 已推送；部分完成、待联调 |

代码提交后的文档提交不改变 APK 内容。各 PR 的目标分支依次为 main、B1、B2、B3；全部保持草稿，未开启自动合并。

## 本地集成

独立目录：`../CookX-p2-integration`；分支 `integration/p2-inventory-local`，无远端跟踪，不推送。
从主人主分支 3bc9da2 开始，按 B1 → B2 → B3 → B4 普通合并，无冲突；最终软件测试及 APK 对应 `f3d7b657cf23072201e1d0f178cd3987c019699e`。
移动表单修复回 B3（7be8cf7），H 名称修复回 B2（c87f2ea），字段错误提示回 B3（5d33828）；随后普通合并向后传播，没有改写远端历史或在集成分支堆功能修复。

## 验收结果

- 前端 41 项测试通过，Vite 生产构建通过。
- 新后端组合 144 项 pytest 通过，温度仿真 3 项通过。后端后续未变化。
- 浏览器 390×844 Chromium：零分、数据不足、异常重试、表单日期校验、保存回读、响应丢失防重复、识别确认闭环、推荐失败恢复、用户隔离通过。
- FreshFusion、库存、推荐使用临时文件隔离的真实 FastAPI；识别、云菜谱和语音提供者为替身，故障场景另用 HTTP 拦截。
- 温度回放、离线 WASM、模型失败降级、计时与语音故障恢复通过；100 次桌面 WASM 推理 P95 约 0.30 ms，参考输出最大绝对误差 9.54e-7。不是手机性能结果。
- Android `assembleDebug testDebugUnitTest` 通过。原生单元测试为仓库现有示例测试，不能替代真机验收。

代码检查点 CI 三项（app/backend/simulation）均通过：[B1](https://github.com/chenchen77zhu-ops/CookX-SmartCooking/actions/runs/35685172814)、[B2](https://github.com/chenchen77zhu-ops/CookX-SmartCooking/actions/runs/35686359211)、[B3](https://github.com/chenchen77zhu-ops/CookX-SmartCooking/actions/runs/35686389294)、[B4](https://github.com/chenchen77zhu-ops/CookX-SmartCooking/actions/runs/35686392979)。

## 交付与边界

本机交付目录：`../../artifacts/CookX-p2-20260922`（相对仓库目录）；包含 APK、截图、测试日志、结果 JSON 与简述。
APK：`CookX-p2-f3d7b657-debug.apk`，50,803,034 字节；SHA256 `20d356e14d0d69325a765d60520e7220feabab04c9e12411358b6b73169c5348`。
安装包保留现有原生后端地址 `http://192.168.43.49:8000`；联网页面需该地址可达。本轮仅本地测试，没有部署服务器或验证 Android 真机。

日期编辑与小数保质期虽已获新后端支持，本批页面按约定仍未开放；清空、批次隔离及历史时间语义见 [A3 交接](a3-contract.md)。B3/B4 不能标记首批完整验收。
现有构建仍有大包提示；npm ci 报告继承锁文件中的 13 项依赖安全告警（2 moderate、10 high、1 critical），本批未做依赖升级或安全认证。

回退按 B4 → B3 → B2 → B1。未合并 PR 可直接关闭；需复现时在独立 worktree 检出表中提交。集成目录可检出 f3d7b657；不要将集成分支推送，也不要在远端 main 上 reset。最终合并由仓库主人决定。
