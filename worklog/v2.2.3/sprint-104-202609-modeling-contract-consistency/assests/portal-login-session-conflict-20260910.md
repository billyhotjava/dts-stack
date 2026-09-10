# 平台登录活动会话唯一约束冲突

## 问题与修复

2026-09-10 用户登录平台时触发 `uq_portal_sessions_active_username_partial`。不保存用户提供的 SQL 参数或令牌。

源码已在撤销旧会话时 saveAndFlush；仅补 flush 不能解决并发。原 findActiveForUpdate 只锁已匹配行：首次同时登录时无行可锁；替换会话时，等待中的查询也可能在原行撤销后返回空，而另一事务已经插入新活动行，随后插入违反唯一约束。

在 createSessionInternal 的用户名规范化后、查询活动会话前增加 PostgreSQL 按用户名的事务级 advisory lock，持有到事务提交。首次登录和接管都串行执行；大小写统一，同浏览器仍更新原会话，跨浏览器仍遵循 allow-takeover。保留唯一索引、旧会话撤销记录与权限校验，无数据库迁移或会话清空。

密码登录的兜底错误返回统一中文提示，不再向页面或此处的登录审计返回原始数据库异常（其中可能含完整令牌）。

GitNexus：创建会话影响 5 个重载入口以及密码/PKI 登录，MEDIUM；登录错误返回影响两个转发入口，LOW。未扩大到其他业务模块。

## 验证与交付

- 源码修复提交 `434801426`；交付源码 `654dd6758fa175c5e778bc524a6327f24c84eb04`。开发目录提交推送后，部署目录快进核对 SHA。
- 相关测试 15 项通过：PostgreSQL 并发测试 1（两轮各 8 次同时登录，覆盖首次创建和替换活动会话，每轮最终只有一个活动会话）、会话回归 9、认证接口 5。
- 原 POM 排除了部分旧认证测试，首次 Maven 成功未实际执行这些测试；已恢复本次三个测试类的编译入口。同步旧内存仓库的锁定刷新查询映射，以及 Cookie 过期头断言后完成复测。未宣称全仓库测试通过。
- 正式构建入口：在 `/opt/prod/s10/deploy` 执行 `builds/dts-build.sh --image dts-platform --no-save`，构建成功。因既有产物为 root 所有，使用 sudo 和独立 Maven 锁文件；已用开发账号完成快进，构建脚本 root 身份的重复拉取失败后使用已核对的源码。未生成补丁包或镜像 tar。
- 2026-09-10 14:44:12 在现有 `/opt/dts/release/dts-stack` 配置和 `dts-stack` Compose 项目，仅执行 `up -d --no-deps dts-platform`，替换原后端容器，未迁移运行环境。
- 新镜像 `sha256:c5a2cfa961a23b364a2871dafe1755fdb2026160c60dc51f6c5428a8410a4ba3`，源码标签同交付 SHA。
- 原镜像 `sha256:dc2cd25f905ad1b082e6c2b49f76b775757dce324b749148ac90cb83620edc76` 保留。前端镜像未替换，容器挂载按 Destination 排序比较完全一致。
- 14:45 检查：容器 healthy，`/management/health` 返回 UP；HTTPS 登录页 HTTP 200，`/api/session/status` 正常返回未认证状态。
- 当前浏览器工具没有可连接的浏览器，真实账号登录页面验收未执行；HTTP 可用不等同于登录验收。
- 本机原始日志：`/tmp/s104-session-test.log`（并发及会话通过，旧 Cookie 断言失败记录）、`/tmp/s104-session-auth-retest.log`（认证 5 项复测通过）、`/tmp/s104-session-build.log`（正式镜像成功）。
