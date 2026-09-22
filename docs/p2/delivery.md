# 人员二 B1–B4 交付跟踪

基线：`539c231bec5f0211d0a5663c9e685a3be829095a`。后端/算法保持此基线。

| 功能 | 分支 | 依赖 | 状态 |
|---|---|---|---|
| B1 鲜度卡片 | feat/p2-freshness-ui | main 基线 | 已验收（本地软件） |
| B2 鲜度依据 | feat/p2-freshness-details | B1 | 待开发 |
| B3 库存字段 | feat/p2-inventory-fields | B2 | 待开发 |
| B4 页面闭环 | feat/p2-inventory-flow | B3 | 待开发 |

采用串联草稿 PR，不更新远端 main，不自动合并。本地集成分支不推送。
每个功能提交可单独检出复现；回退顺序 B4 → B3 → B2 → B1。
真实后端路由的隔离测试和浏览器测试替身分开记录；不声称真实云端识别、手机或食品安全验证。

B1：35 项前端测试通过、生产构建通过；390px Chromium 测试替身验证 0 分、未知、HTTP 500/422、200 业务失败、重试和库存保留。截图 tmp/p2/freshness-mobile.png。
