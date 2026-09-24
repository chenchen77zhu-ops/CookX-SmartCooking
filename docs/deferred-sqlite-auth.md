# 多人内测基础：SQLite 与会话认证

正式存储现为单后端 SQLite，账号、库存、聊天记录、通知及原扣减凭证保存在事务内。评分公式及温度算法未修改。旧接口路径继续存在，但请求必须携带登录返回的 Bearer 会话；仅传 user_id 将返回 401，访问其他账号返回 403。登录七天后过期，退出登录即撤销当前凭证。新密码用 Argon2id，旧 SHA-256 在密码验证成功后升级。任何账号响应都不包含密码哈希。

## 本地启动

安装 requirements.txt（完整视觉环境）；不使用本机视觉模型时可安装 requirements-local.txt，仍运行真实 SQLite 与全部业务接口。首次创建独立测试库：

```powershell
python scripts/manage.py --database tmp/private-test.sqlite3 init --admin 管理员
$env:COOKX_DATABASE = (Resolve-Path tmp/private-test.sqlite3).Path
python scripts/manage.py invite --uses 5
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --workers 1
```

密码由终端隐藏输入；邀请码只交给内测成员。实际手机与电脑连接同一局域网，用电脑 IPv4 地址。安装正式 APK 后，在登录页“后端连接设置”填写 `http://电脑地址:8000`。只接受不带路径的地址；更换地址会清除本机登录，旧服务器凭证按原过期时间失效，也可先正常退出以立即撤销。凭证绑定服务器地址，不转发到其他服务器。本机浏览器默认走 Vite 的 8000 端口代理。开发者需在系统防火墙仅允许所用私有网络访问后端。未配置有效云模型服务时，识别和生成会如实报告服务不可用；本地库存、规则及温度能力不依赖云模型。

## JSON 迁移与回退

停止所有旧后端进程，再执行预检查及迁移；不要在运行中的 JSON 后端上迁移。

```powershell
python scripts/manage.py precheck --source app/data/users
python scripts/manage.py migrate --source app/data/users --server-stopped
python scripts/manage.py backup backups/after-migration.sqlite3
```

源目录必须含 users.json 及其账号子目录。预检查拒绝未知归属、重复 ID、损坏的库存或凭证。先完整备份 JSON 字节，再事务导入，核对账号与文档数，最后写入 ready 标记。原 ID、日期字符串和凭证内容保留；prepared 扣减仅在内存中核对并恢复，源文件不变。相同源再次运行返回 already_migrated，不会重复导入；已有其他数据库时拒绝覆盖。失败没有 ready 标记，服务拒绝访问，可以修复源数据后重试。

切换前若没有任何数据库新写入，可停止新服务并回到原版本和原 JSON 快照。**切换后已有新写入，禁止把旧 JSON 覆盖回数据库。**使用 SQLite backup 命令产生一致快照；恢复时停止服务，将经 `PRAGMA integrity_check` 检查的备份复制到一个全新路径，再通过 COOKX_DATABASE 指向它，保留当前数据库以便核对。这会回到快照时刻，快照之后的写入须另行核对，不能自动丢弃。

## 验证口径

旧文件接口测试通过显式注入 FileStore 保留，用于既有字段和评分契约回归；正式程序不会根据环境或请求切回文件存储，也没有关闭认证的开关。新增 test_sqlite_auth.py 与 browser-sqlite.cjs 使用真实 SQLite、真实会话、两个独立账号，覆盖越权、失效、并发幂等扣减、重启读取、迁移备份与失败回滚。浏览器中的云菜谱和视觉仍为测试替身，不能作为云模型实测。

全部业务的验收状态见 deferred-status.md。图片与语音的历史本地文件目录需随旧 JSON 一起保留备份。

恢复命令：`python scripts/manage.py restore backups/after-migration.sqlite3 tmp/recovered.sqlite3 --server-stopped`。目标必须不存在，验证完整性后再由操作者切换 COOKX_DATABASE；原数据库不会被覆盖。迁移账号可用 `python scripts/manage.py admin --username 已有账号` 指定内测管理员。
