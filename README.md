# CookX 鲜厨智享

正式分支 `main`：面向小范围邀请制内测，Android App 连接本机或局域网后端。无需公网部署；多人共享与算法运行需要后端在线。

[下载 1.3.0 内测 APK 与验收附件](https://github.com/chenchen77zhu-ops/CookX-SmartCooking/releases/tag/cookx-deferred-1.3.0) · [简短升级说明](docs/CookX-升级与验收简报.md) · [13 项逐项状态与回退](docs/deferred-status.md)

## 启动与手机连接

Python 3.12 安装 `requirements-local.txt`（不含可选 YOLO）或完整 `requirements.txt`，按[启动/迁移文档](docs/deferred-sqlite-auth.md)初始化 SQLite、创建管理员和邀请码，再启动单进程后端。旧 JSON 数据先备份、预检查和迁移，不能直接覆盖现有数据库。

手机与电脑在同一局域网。安装正式 APK，在登录页展开“局域网后端地址”，填 `http://电脑IPv4:8000`，然后使用内测账号登录。云视觉/生成服务未配置时会明确不可用；手工库存、标准菜谱、家庭/社区、菜单及学习链路可本地运行。详细接口见[接口索引](docs/deferred-api-index.md)。

## 本轮交付

家庭共享库存、共同采购、菜谱复刻、晒菜、评论、点赞、热门榜、剩菜改造、成长、挑战、徽章、七日约束菜单和个性化学习，共 13 项。原单餐评分、FreshFusion 与温度算法保持原实现。398 项后端、71 项前端、3 项温度研究测试及移动浏览器/Android 软件检查通过。系统语音、锁屏通知、持续蓝牙及真实烹饪仍需真机验证；长期学习收益尚无真实长期数据支持。

## 独立离线测试版

没有后端时可切换到 [`feat/p2-local-test-apk`](https://github.com/chenchen77zhu-ops/CookX-SmartCooking/tree/feat/p2-local-test-apk) 并按该分支 README 下载离线版。它保留烹饪/硬件测试和明确标注的本机样例，不能替代真实多人同步、CP-SAT 或学习训练。正式版包名 `com.smartcooking.app`，离线版 `com.smartcooking.app.localtest`，可共存。

离线分支只从 main 单向同步，永不合入 main。每批功能保留独立分支及 PR，合并前完成本地和 GitHub 检查；不强推历史。
