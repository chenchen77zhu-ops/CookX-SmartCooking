# 暂缓任务逐项实施记录

基线：4ca38e3。先行联调发现并修复账号偏好隔离及缺失营养值补造问题，PR #28 通过本地及 GitHub 验收后合并。SQLite/认证基础为 PR #29，独立迁移及回退说明见 deferred-sqlite-auth.md。

| 项目 | 功能分支 | 当前状态 | 验证与限制 |
|---|---|---|---|
| 1 家庭共享库存 | feat/deferred-household-inventory | 已合并 PR #30 | 真 SQLite 双账号，邀请撤销/退出越权、管理员移交、原日期转入、幂等、并发版本冲突；移动页面流程通过 |
| 2 共同采购清单 | feat/deferred-shopping-list | 已合并 PR #31 | 手工/菜谱需求、同单位合并、认领冲突、购买后确认实际入库、历史与幂等；菜单入口已组合联调 |
| 3 一键复刻 | feat/deferred-recipe-reuse | 已合并 PR #32 | 标准/历史/收藏独立副本、来源版本、缺料核对；社区接口已组合联调，不自动计时/扣减 |
| 4 社区晒菜 | feat/deferred-community-posts | 已合并 PR #33 | 私有图片上传与真实格式/大小校验、主动确认发布、作者管理、举报/管理员隐藏、公开菜谱脱敏复刻 |
| 5 评论 | feat/deferred-community-comments | 已合并 PR #34 | 单层评论、去重提交、作者/管理员删除、隐藏后禁止互动，移动页面验证通过 |
| 6 点赞 | feat/deferred-community-likes | 已合并 PR #35 | 每账号每帖一票、取消、原凭证重试与版本冲突；移动页面切换计数验证通过 |
| 7 热门榜单 | feat/deferred-community-ranking | 已合并 PR #36 | 近七天有效点赞人数、评论人数及发布时间排序；隐藏/删除即排除，移动页面验证通过 |
| 8 剩菜改造 | feat/deferred-leftovers | 已合并 PR #37 | 原料引用原批次、熟食独立条件筛选及改造思路、再加热不重置时间、使用量幂等、移动页面 |
| 9 厨艺成长 | feat/deferred-growth | 已合并 PR #38 | 完成确认接入、响应丢失原凭证重试、服务端去重、按用户历史及明确确认旧记录导入 |
| 10 挑战 | feat/deferred-challenges | 已合并 PR #39 | 三类系统预置挑战、加入/进度/完成/结束，排除加入前及旧记录导入 |
| 11 徽章 | feat/deferred-badges | 已合并 PR #40 | 以完成事件/挑战为依据，每账号每徽章唯一，重登恢复并保留获奖依据 |
| 12 七日菜单 | feat/deferred-menu-planner | 已合并 PR #41 | 独立 CP-SAT、可追溯营养/用户价格、硬约束、库存不重复占用、保存/复刻/采购；家庭烹饪扣减已组合联调 |
| 13 长期学习 | feat/deferred-personalization | 已合并 PR #42 | 主动开启、明确反馈、时间划分逻辑回归、原排序对照、合格候选内重排、关闭/清除/重置；长期真实收益未知 |

最终软件代码：35f68f4（PR #45 合并）。公共基础 PR #29，跨功能修复 #43、私人媒体 #44、菜单时效 #45 均已通过 GitHub 的 app/backend/simulation/formal-boundary 检查后普通合并。离线分支没有并入 main。

**软件通过**：398 项后端测试、71 项前端测试、3 项温度研究测试；22 组浏览器脚本（20 组全量流程＋后端地址设置＋私人头像）分别通过，最新相关菜单/完整流程再次通过。JDK21 / SDK36 assembleDebug 和 testDebugUnitTest 通过。Android APK 源码提交 5af0f6d 与最终合并的应用代码相同；编译机器为 Windows PC，没有连接手机。

**真实数据不足**：长期学习仅验证训练、时间拆分、部署和回退链路；菜单采用公开营养数据及明确标为人工的价格实验。不能据此宣布长期偏好收益、营养实测精度或温度移动识别率。

**待真机验证**：系统语音、锁屏通知、持续蓝牙/断线恢复及真实烹饪。用户此前确认 JDY-31 可连接显示温度，本轮没有把这一反馈扩大为全部硬件验收。

个人旧库存保持“库存计数”语义，家庭新库存支持明确数量与单位和小数实际扣减；不会自行换算计数与克。多人功能需要单台本机/局域网后端；离线版仅提供本机演练，不是跨设备同步或服务端算法的替代。

## 回退与证据

按依赖逆序回退：菜单时效 #45 → 私人媒体 #44 → 跨功能 #43 → 学习 #42 → 菜单 #41 → 徽章/挑战/成长 #40–38 → 剩菜 #37 → 社区 #36–33 → 复刻/采购/家庭 #32–30 → 基础 #29。建议修复向前；不得用强推改写已发布历史。SQLite 已有新写入时不能覆盖为旧 JSON，先停止写入、备份并核对，再选择独立恢复路径。

各 PR 的 GitHub Actions 保留对应提交的 `CookX-temperature-debug` APK 和 `CookX-acceptance-evidence` 截图/测试附件；本地最终安装包与证据集中在工作区 artifacts/CookX-deferred-final-20260924。接口见 docs/deferred-api-index.md；算法详见 deferred-menu-planner.md、deferred-personalization.md 与两份 experiment-results.json。
