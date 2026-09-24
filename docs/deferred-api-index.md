# 多人内测接口索引与接入

所有业务请求携带登录返回的 Bearer 凭证；查询参数 user_id 不能替代认证。登录页可设置局域网后端地址。旧用户成功登录后升级密码摘要，不向客户端返回摘要。注册需要管理员邀请码。API schema 以 /openapi.json 为准。

| 模块 | 前缀/路径 | 核心语义 |
|---|---|---|
| 认证 | /api/login、register、auth/session、logout、invitations | 七天过期、可撤销，管理员发邀请 |
| 偏好 | /api/v3/preferences | 每账号、版本化；忌口服务端执行 |
| 个人库存 | /api/inventory、add-to-inventory、inventory/confirm-recognition、inventory/consume | 兼容库存计数；编辑/删除必须 If-Match，新增支持 Idempotency-Key |
| 家庭 | /api/v3/households | 管理员邀请/移交/退出、成员权限即时核对、显式转入 |
| 采购 | /api/v3/households/{id}/shopping | 同单位合并、认领、购买和实际入库分步事务 |
| 家庭扣减 | /api/v3/households/{id}/consumption | 批次版本、实际数量、会话去重及凭证查询 |
| 复刻 | /api/v3/recipes | 标准/历史/收藏/社区/菜单来源，独立版本，执行前核对 |
| 社区 | /api/v3/community | 私有图片、主动发布、评论、点赞、近七天热门、举报/隐藏 |
| 剩菜 | /api/v3/leftovers | 生原料与熟食分开；时间与异常不能通过再利用重置 |
| 成长 | /api/v3/growth、challenges、badges | 确认完成去重，按账号统计与系统挑战发奖 |
| 菜单 | /api/v3/planning | 价格、预览、保存、执行核对；无解/超时/缺数据明确返回 |
| 学习 | /api/v3/learning | 开关、明确反馈、训练、清除/重置；评估不通过回退 |
| 私人媒体 | /api/upload-avatar、tts、v3/profile/media/{id} | 所有私人头像/语音授权读取；旧静态地址不公开 |

新增写入命令带 idempotency_key；修改带 expected_version，冲突 409。客户端保存凭证后发送，超时或响应丢失保留原内容和凭证核对，不能生成新凭证盲重试。权限检查在回放凭证前进行，退出家庭后旧请求也不能继续访问。菜单保存不预留库存、不下单、不扣减。

局域网采用单进程后端和 SQLite；启动、邀请码、迁移、备份及恢复见 deferred-sqlite-auth.md。未配置云模型时原识别/生成清楚返回不可用，手工库存、标准菜谱、规划及其他本地模块正常运行。正式版没有离线测试账号或后门。
