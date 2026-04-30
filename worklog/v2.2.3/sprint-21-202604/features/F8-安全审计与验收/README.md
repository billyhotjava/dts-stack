# F8: 安全审计、验收与发布材料

**优先级**: P0
**状态**: IN_PROGRESS

## 目标

把接入中心能力收口到可交付状态：权限、审计、凭据安全、现场冒烟、发布步骤和回滚说明完整。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 数据源/连接器/任务权限矩阵 | P0 | DONE | F1-F6 |
| T02 | 接入中心审计事件补齐 | P0 | DONE | F2-F6 |
| T03 | 端到端验收脚本与样例数据 | P0 | DONE | F1-F7 |
| T04 | 发布、升级、回滚与运维 Runbook | P1 | DONE | T03 |
| T05 | 凭据脱敏审计抽检脚本与证据模板 | P0 | DONE | T01-T04 |
| T06 | 数据库源/文件源现场冒烟归档 | P0 | IN_PROGRESS | T03-T05 |

## 完成标准

- [ ] 数据源凭据不在 API、日志、审计、导出中明文出现。
- [x] 所有接入中心关键操作进入审计。
- [ ] 至少一条数据库源和一条文件源完成端到端冒烟。
- [x] 验收覆盖：建数据源、discover、生成 ODS、建任务、运行、看日志、看血缘、重跑。
- [x] Runbook 包含部署参数、故障排查、升级回滚和现场演示脚本。

## 权限矩阵

| 对象 | 查看 | 创建/更新 | 删除/停用 | 执行/重跑 | 说明 |
|---|---|---|---|---|---|
| Connector Registry | `INFRA_MAINTAINERS` | 内置 seed/刷新接口 | 不开放删除 | 刷新目录 | 当前不做 Marketplace 写入。 |
| 数据源 | `INFRA_MAINTAINERS` | `INFRA_MAINTAINERS` | `INFRA_MAINTAINERS` | 连接测试 | 用户侧详情只返回脱敏结果；明文运行时凭据仅允许内部 `service:*` principal 读取。 |
| Schema Discover | `INFRA_MAINTAINERS` | - | - | 探测/强制刷新 | 只返回脱敏元数据和采样结果。 |
| ODS/dbt source 生成 | `INFRA_MAINTAINERS` | `INFRA_MAINTAINERS` | - | 预览/落库 | 写入 catalog、dbt source 和接入血缘。 |
| 同步任务 | 入湖任务权限 | 入湖任务权限 | 入湖任务权限 | 执行、失败重试、整批重跑 | 任务运行中心面向业务用户，Airflow UI 仍为运维入口。 |

## 当前落地

- 平台侧 `audit-action-catalog.json` 已补齐 Connector Center 相关动作：连接器目录、数据源登记/测试/停用、Schema Discover、ODS 预览/落库、同步任务草稿生成和调度任务动作。
- `dts-common` 默认 audit catalog 同步补齐同一批 Foundation 动作，避免不同服务加载默认目录时出现 unknown action fallback。
- 数据源 Resource 已显式记录数据源查看、创建、更新、删除、连接测试、Schema Discover、ODS preview/apply、ODS precheck 和 sync-task-draft 审计事件。
- 新增 `FOUNDATION_ODS_PRECHECK` 审计动作，用于区分建任务前 dry-run/precheck 与真正落库/创建任务。
- Run Center 已提供最新失败执行的失败重试与整批重跑入口，ingestion 服务侧已对任务创建、更新、删除、手动/异步执行、失败重试/整批重跑写入显式审计动作。
- 审计动作目录补齐 `INGESTION_TASK_CREATE`、`INGESTION_TASK_UPDATE`、`INGESTION_TASK_DELETE`、`INGESTION_TASK_EXECUTE`、`INGESTION_TASK_RETRY`、`INGESTION_EXECUTION_RETRY` 和 `INGESTION_BACKFILL_RUN`，平台侧与 common 默认 catalog 保持一致。
- 新增验收样例和脚本：`it/samples/postgres-source.sql`、`it/samples/budget-upload.csv`、`it/samples/ods-request-postgres.json`、`it/scripts/connector-center-smoke.sh`，覆盖 discover、ODS preview/precheck/apply、sync-task-draft、任务创建、执行提交、补数提交和 Run Center 观测。
- 新增 `it/runbook.md`，沉淀发布前检查、现场冒烟、升级、回滚和常见故障定位。
- 用户侧 `/api/infra/data-sources/{id}/detail` 已收敛为脱敏详情，不再返回 JDBC/API 明文 secrets；内部执行链路改走 `/runtime-detail`，并要求 `service:*` principal + 匹配服务名的 `X-DTS-Service-Token`。
- 平台入湖代理已补齐 `/api/ingestion/tasks/{id}/backfill`，保证 Connector Center smoke 和 Run Center 前端都从 platform 统一入口提交补数。
- 内部服务 token 改为数据库化治理：平台侧创建 `service:dts-ingestion` token 并仅保存 hash，ingestion 侧将明文 token 写入 `platform.serviceToken` 集成配置，走 `infra_service_settings` 加密/脱敏机制；`dev`、`app`、`legacy` 三种模式均不需要改初始化后的 `.env` 或 compose 文件。
- 新增 `it/scripts/file-source-smoke.sh`，覆盖 CSV/Excel 文件 prepare、parse、错误行预览和文件源摘要输出。
- 新增 `it/scripts/credential-redaction-audit.sh`，抽检用户侧数据源 API、`runtime-detail` 用户访问拒绝、伪造服务头无 token 拒绝、可选审计导出 CSV 以及 smoke 输出目录中的明文凭据哨兵。
- 验收脚本已兼容 `DTS_TOKEN`、`DTS_COOKIE` 与 `DTS_COOKIE_JAR` 三种认证方式，适配 sprint-22 后登录响应以 HttpOnly `portal_session` cookie 为主的会话形态。
- 新增 `it/evidence/` 证据目录模板，包含验收记录模板和 PostgreSQL/MySQL/Oracle/SQL Server/DM8 方言验证矩阵。
- 新增 `InfraDataSourceResourceTest`，覆盖用户 principal 即使持服务令牌也不能读取 `runtime-detail`、伪造服务 principal 但服务令牌错误被拒绝、内部服务 principal + 数据库托管服务令牌才能读取运行时 secrets。
- 新增 `PlatformInfraClientTest`，锁定 ingestion 侧读取数据源凭据必须走 `/runtime-detail`，并从 `platform.serviceToken` 集成配置携带 `X-DTS-Service-Token`。

## 端到端验收路径

```text
1. 新增 PostgreSQL/MySQL 数据源，完成连接测试。
2. 执行 Schema Discover，确认表、字段、主键、索引、增量候选和缓存/漂移状态。
3. 配置 ODS schema、系统编码、业务编码、同步模式，预览 ODS DDL / dbt source / Addax / Airflow 草稿。
4. 执行提交前预检，确认 PASS/WARN/FAIL、规则明细和审计事件。
5. 生成 ODS 映射与 dbt source，并检查 catalog 表字段和 Sprint-20 lineage graph。
6. 生成同步任务，执行一次全量同步。
7. 在 Run Center 查看行数、耗时、错误分类、日志和血缘入口。
8. 制造一次失败执行，验证失败重试、整批重跑和日志建议。
9. 切换源端 schema，强制刷新 discover，验证 schema drift 摘要。
10. 在审计日志中核对数据源、discover、ODS precheck、ODS apply、任务草稿、执行/重跑相关事件。
```

## 待补

- 需要在真实联调环境执行 `connector-center-smoke.sh` 和 `file-source-smoke.sh`，并把输出归档到 `it/evidence/<date>-<rc>/`。
- 需要用现场数据源密码或测试哨兵执行 `credential-redaction-audit.sh`，确认用户侧 API、审计导出和 smoke 输出中不出现明文凭据。
- 需要根据 `it/evidence/dialect-validation-matrix.md` 补齐 PostgreSQL、MySQL、Oracle、SQL Server、DM8 的基础元数据验证结果。
