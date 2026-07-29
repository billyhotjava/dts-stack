# Sprint-78：P0 安全与稳定性加固（评审缺口闭环）

**时间**：2026-07
**状态**：READY
**类型**：Security Hardening / Ops Reliability / Delivery Cleanup
**目标**：关闭 2026-07-29 评审确认的四个 P0 风险——新建用户初始口令不再是公开常量、TLS 私钥不再随源码分发、PostgreSQL 具备定时备份与已验证的恢复能力、Hetu 遗留代理从交付物中彻底移除。

## 背景与价值

2026-07-29 对代码、配置与 worklog 的复核确认：4 月架构评审（`worklog/v2.2.3/architecture-review.md`）的 8 项缺陷尚无一项完全修复，其中四项风险修复成本低、收益大、可立即关闭：

- **S1**：新建用户初始口令硬编码 `"sa"` 且 `temporary=false`（账本#1/#2）。三员管理体系下，任何知情者可用公开口令登录全部新建账号。
- **S2**：三个模块的 TLS `keystore.p12` 受 Git 跟踪、口令明文 `"password"`（账本#7/#8），私钥事实公开。
- **缺陷-1（恶化）**：单实例 PostgreSQL 已承载 10 个业务库，仍无任何定时备份，仅有升级时的文件级快照（账本#11/#12）。
- **缺陷-8**：Hetu 已于 2026-04 决策移除，但 compose 与 traefik file provider 仍保留 9 组代理路由（账本#13/#14），形成未审计的外部暴露面与运维噪音。

本 Sprint 只做这四项，不夹带其他重构。

## 架构决策记录（ADR）

| ID | 决策点 | 选择 | 理由 | 影响 |
|---|---|---|---|---|
| ADR-78-01 | 初始口令生成 | SecureRandom 一次性强随机口令 + `temporary=true`，删除 `DEFAULT_INITIAL_PASSWORD` 常量 | 任何常量口令都是公开口令；首登强制改密由 Keycloak 原生支持（账本#3） | dts-admin |
| ADR-78-02 | 口令交付通道 | 审批执行结果中一次性明文展示；不落库、不进日志、不进审计明细，审计仅记录 delivered 元数据 | 离线客户现场无邮件/短信保证；持久化口令等于二次泄露（security-compliance） | dts-admin + dts-admin-webapp |
| ADR-78-03 | 存量账号处置 | 提供可选批量工具为存量用户设置 `UPDATE_PASSWORD` required action，由现场决定执行范围；不强制全量重置 | 避免升级即锁死现场用户；处置动作可审计 | dts-admin + 升级说明 |
| ADR-78-04 | TLS 私钥唯一来源 | 部署期 `services/certs`（gen-certs.sh）生成；源码仓库零密钥材料；Git 历史中的旧私钥视为已泄露，升级时轮换 | 仓库可分发；私钥生命周期归部署（账本#10） | source×3 + init.sh |
| ADR-78-05 | tls profile 形态 | 保持默认关闭（TLS 仍由 Traefik 终止，账本#9）；激活时经 `SERVER_SSL_KEY_STORE` / `SERVER_SSL_KEY_STORE_PASSWORD` 环境变量注入，不再内置 classpath keystore | 休眠配置不应携带密钥；激活路径显式化 | application-tls.yml×2 |
| ADR-78-06 | 备份形态 | 宿主机 `bin/dts-backup` 调 `pg_dump`（custom format），每库一文件 + manifest，保留 14 天，权限 600，失败非零退出；不引入新容器、不动 `services/dts-pg/data` | 离线现场可执行；devops-runbook 要求不碰持久化数据 | bin/ + crontab |
| ADR-78-07 | Hetu 移除方式 | 路由层硬删除（compose labels + file provider + host alias），不保留停用开关；内置 `/bi` 为唯一分析入口 | 移除决策已于 2026-04 做出；开关即债务（账本#13/#14） | docker-compose-app.yml + traefik-dynamic.yml + platform-webapp |
| ADR-78-08 | 交付纪律 | 全部变更遵循 release-offline-upgrade：现场无网可执行、先备份后变更、回滚步骤先演练 | 本 Sprint 自身即变更管理对象 | `assets/release-plan.md` |

## 端到端契约链（Vertical Slice）

本 Sprint 属安全/运维加固，"UI 入口"层仅在 F1/F4 存在；命令行与配置契约视同对外契约管理。

| 层 | 契约/落点 | 签名要点 |
|---|---|---|
| UI 入口 | dts-admin-webapp 用户创建审批结果 | 一次性展示初始口令 + 复制确认（F1/T02） |
| API | 审批 applyCreate 执行结果 detail | `initialPasswordDelivered: true`；口令明文仅存于执行响应内存（F1/T01） |
| Service | `KeycloakAdminClient.resetPassword(userId, pwd, true, token)` | 复用既有签名（账本#3）；拒绝路径保留既有行为：设密失败则创建整体失败（账本#2） |
| 命令 | `bin/dts-backup [--dir DIR] [--retention-days N]` | 退出码 0/非 0；产物 `backups/<ts>/<db>.dump` + `manifest.txt`（F3/T01） |
| 配置 | `application-tls.yml`（admin/platform） | `key-store` 指向外部文件路径、口令无默认值、经环境变量注入（F2/T01） |
| 配置 | compose + traefik file provider | 9 组 hetu 路由与 `hetu@file` 服务删除；`/dashboards` 等 PathPrefix 回落平台 SPA（F4/T01） |
| 数据/迁移 | 无建表；存量账号处置为可选运维动作（F1/T03） | 幂等、审计化、默认不执行 |
| 回滚 | `assets/release-plan.md` | F2 证书回滚、F4 路由 git revert、F1/F3 回滚边界 |

## 现状勘察账本（Context Ledger）

| # | 事实 | 证据 |
|---|---|---|
| 1 | 初始口令常量 `DEFAULT_INITIAL_PASSWORD = "sa"` | `source/dts-admin/src/main/java/com/yuzhi/dts/admin/service/user/AdminUserService.java:112` |
| 2 | `applyCreate` 以 `temporary=false` 设置初始口令；设密失败则创建整体失败 | `AdminUserService.java:3957-3962` |
| 3 | `resetPassword` 契约已支持 temporary 标志（接口/REST 客户端/内存桩三实现） | `service/keycloak/KeycloakAdminClient.java:32`、`KeycloakAdminRestClient.java:321`、`InMemoryKeycloakAdminClient.java:89` |
| 4 | 审批制重置密码流程已支持 temporary 与审计 | `AdminUserService.java:4312-4324`、`web/rest/KeycloakApiResource.java:601` |
| 5 | SecureRandom 在 dts-admin 已有使用先例 | `service/infra/InfraSecretService.java:33`、`service/pki/PkiChallengeService.java:31` |
| 6 | 审批 detail 经 `copyIfPresent` 脱敏后输出，`defaultPasswordApplied` 已在脱敏白名单 | `AdminUserService.java:1878` |
| 7 | 三个 `keystore.p12` 受 Git 跟踪 | `dts-admin/dts-common/dts-platform/src/main/resources/config/tls/keystore.p12`（`git ls-files`） |
| 8 | tls profile 口令明文 `"password"`、alias `selfsigned` | `dts-platform/src/main/resources/config/application-tls.yml:12-15`（dts-admin 同构） |
| 9 | tls profile 未被 compose 激活（prod/dev 均不含 tls），TLS 由 Traefik 终止 | `docker-compose-app.yml:86,591,1300`（`SPRING_PROFILES_ACTIVE`） |
| 10 | 部署期证书链已存在：ca/server 证书、server.p12、keystore.p12、gen-cert(s).sh | `services/certs/` |
| 11 | 10 个业务库经 `PG_DB_*` 在 init.sh 注册 | `init.sh:801-880` |
| 12 | 仓库无任何定时备份脚本；仅升级时文件级快照 | `bin/lib/dts-upgrade-common.sh`（`upgrade_backup_postgres_data_dir`）；`scripts/security/` 仅签名/脱敏两个脚本 |
| 13 | compose 中 9 组 hetu-* 路由 + `hetu.upstream` host alias + 主 UI 排除规则中 8 个 PathPrefix | `docker-compose-app.yml:1435,1482,1486-1543` |
| 14 | file provider 中 hetu service 与 fallback 路由仍在；4 月变更留有 .bak | `services/dts-proxy/dynamic/traefik-dynamic.yml:10-185`、`traefik-dynamic.yml.bak.bi_yuzhicloud_20260412161329` |
| 15 | 前端已有 Hetu 入口向内置 `/bi` 的重定向判定 | `source/dts-platform-webapp/src/utils/biLinkUrl.ts`（`shouldRedirectHetuEntryToAnalytics`） |

**开放问题**（实施期确认，不影响契约）：

- dts-admin-webapp 审批详情对 detail 的渲染是否需新增一次性口令区块（F1/T02 的勘察点；禁止重扫账本已有项）。
- `HETU_UPSTREAM_IP` 在 init.sh / .env 模板中的生成点（F4/T01 顺带定位并标记退役，不删除客户 .env 既有值）。

## Gate Registry

| Gate | 项目 | 状态 | 证据 | 未过则关联 Task |
|---|---|---|---|---|
| G0 | 交付基线 | GAP | `it/baseline.md`：命令行/构建/脚本类验收可执行；浏览器登录基线 401 未恢复（Sprint-77 F0） | F4/T03（仅浏览器证据段） |
| G0 | 领域与数据画像 | PASS | `assets/domain-profile.md` | - |
| G0 | 领域不变量自检 | PASS | ADR-78-01/02/04/06/08 对照 security-compliance、release-offline-upgrade、devops-runbook | - |
| G1 | 契约链贯通 | PASS | 本文"端到端契约链" | - |
| G1 | 非功能预算 | PASS | `assets/nfr-budget.md` | - |
| G3 | 发布安全 | PASS | `assets/release-plan.md` | - |
| G4 | 可运维性 | PASS | `assets/runbook.md` | - |
| G4 | DoD 验收 | PENDING | `it/` | - |

## Feature 列表

| ID | Feature | Task 数 | 优先级 | 状态 |
|---|---|---:|---|---|
| F1 | 新建用户初始口令安全治理 | 3 | P0 | READY |
| F2 | TLS 私钥出库与部署期注入 | 3 | P0 | READY |
| F3 | PostgreSQL 定时备份与恢复验证 | 3 | P0 | READY |
| F4 | Hetu 遗留代理移除与内置 BI 收敛 | 3 | P0 | READY |

**依赖顺序**：四个 Feature 相互独立可并行；Feature 内 T01 → T02 → T03。F4/T03 的浏览器 smoke 段依赖 Sprint-77 F0 基线恢复。

## 追溯矩阵

| 需求点 | Feature/Task | 验收证据 |
|---|---|---|
| 评审 S1：默认口令 `"sa"` 且不强制改密 | F1/T01、T02 | IT-01 |
| 评审 S1 存量面：历史弱口令账号 | F1/T03 | IT-02 |
| 评审 S2：keystore.p12 入 Git | F2/T01、T02、T03 | IT-03 |
| 评审缺陷-1：PG 无定时备份 | F3/T01、T02、T03 | IT-04、IT-05 |
| 评审缺陷-8：Hetu 已决策未移除 | F4/T01、T02、T03 | IT-06 |
| 合并质量门（dts-quality-gate） | 全部 | IT-07 |

## 完成标准

- [ ] 新建用户初始口令为一次性随机口令且首登强制改密；仓库无 `DEFAULT_INITIAL_PASSWORD` 残留（IT-01）。
- [ ] 审批详情一次性展示口令且不持久化；存量处置工具幂等可审计（IT-02）。
- [ ] `git ls-files` 无任何 keystore.p12；tls profile 经环境变量注入可启动；旧私钥完成轮换（IT-03）。
- [ ] `bin/dts-backup` 对 10 库备份成功；恢复演练校验通过（IT-04/IT-05）。
- [ ] compose 与 file provider 无 hetu 路由；旧路径行为按契约回落；前端无 HETU 引擎选项（IT-06）。
- [ ] `changed_module_checks.sh` 建议项全部执行并有输出（IT-07）。

## 非目标

- 不引入 Vault/KMS 等外部密钥系统；不做服务间 token 拆分（下一批）。
- 不做 PostgreSQL 主从、PgBouncer 或连接池治理（架构项，另行立项）。
- 不拆分 `AdminApiResource`/`AdminUserService` 巨型类（重构债，单独排期）。
- 不重写 Git 历史删除旧 p12（以轮换代替，风险声明写入 release-plan）。
- 不删除客户 `.env` 中已有的 `HETU_UPSTREAM_IP`（仅标记退役）。
- 不处理 Kafka 消费者接入、Prometheus/Grafana 可观测性（P1 项）。
