# 数据集成接入工作台合并发布计划

状态：`IMPLEMENTED_API_E2E_PASS_UI_ACCEPTANCE_BLOCKED`  
范围：仅 `dts-admin`、`dts-platform`、`dts-ingestion`、`dts-platform-webapp` 及其配置/数据库变更。  
风险：高。原因是本次同时收敛菜单、准入版本、服务间认证、历史凭据迁移和回滚契约。

## 当前门禁

- 前端生产构建：通过。
- 前端聚焦测试：Vitest `33/33`，Node 契约测试 `37/37`。
- `dts-admin` 打包：通过；PostgreSQL 17 Liquibase 集成测试 `10/10` 已通过。
- `dts-platform` 打包：通过；接入与 ingestion client 聚焦测试已通过。
- `dts-ingestion`：聚焦单测 `33/33`、PostgreSQL 17 Liquibase 集成测试 `8/8`、retry/outbox/watchdog 回归测试 `7/7`、最终模块打包均已通过；共享工作区遗留的 selector/rollback 接口漂移已按正式契约收敛。
- 基线 UI 登录：`opadmin/opadmin123` 调用 `/api/keycloak/auth/login` 返回 `401`。最终 UI 点击验收仍可能受此阻塞；API E2E 必须单独给出结果，不能以 API 结果冒充 UI 验收。
- 线上聚焦 API E2E：通过；数据库、API、离线文件三类任务的列表、详情、修订和执行记录可读，pairwise token 正向访问 `200`，错误 token 与 Airflow 越权访问均为 `403`。

## 数据库变更

### dts-admin

执行顺序：

1. `20260731-03a_data_integration_access_menu_audit_width_prelude.xml`
2. 已冻结的 `20260731-04_data_integration_access_menu_convergence.xml`
3. `20260801-01_data_integration_access_menu_convergence_hardening.xml`

规则：不修改已执行 changeset 的内容或 checksum；仅扩宽审计字段，菜单快照与恢复 fail-closed。已覆盖全新安装、旧 checksum 升级、回滚及重放。

### dts-ingestion

新增：

- `20260801-01_ingestion_admission_dag_reconciliation.xml`
- `20260801-02_ingestion_task_secret_migration.xml`
- `20260801_02a_archive_ingestion_task_direct_dbt.xml`
- `20260801_03_retire_ingestion_task_direct_dbt.xml`
- `20260801_04_ingestion_rollback_saga_outbox.xml`

规则：索引、外键和状态表采用扩展式变更；历史明文必须先加密写入 revision，再清理任务主表。缺密钥或证据漂移时标记 `BLOCKED`，不得继续执行。直接绑定 ingestion 任务的两个旧 dbt selector 在删除前均执行非空数据 fail-closed 检查；dbt 发布改由 ReleaseCandidate/Materialization 链负责。回滚 saga 使用独立 outbox、唯一键和阶段状态记录，部分失败不得伪装成功。

### dts-platform

- `20260801_08_rollback_invalidation_availability.xml` 已执行，终态回执约束同时要求完成证据非空，并补充事件外键索引。
- `20260517_03_code_asset_lifecycle_backfill.xml` 仅增加生产中已执行历史版本的精确 `validCheckSum`，没有通配、重跑或手工清 checksum。

## 发布顺序

1. 冻结接入任务写入并备份当前镜像标签、Compose 配置和数据库。
2. 注入并校验 `DTS_PLATFORM_TO_INGESTION_TOKEN` 与 `DTS_AIRFLOW_TO_INGESTION_TOKEN`：非空、彼此不同，只校验长度/摘要，不输出明文。
3. 发布 `dts-admin`，确认菜单迁移成功。
4. 发布 `dts-ingestion`，确认 schema、凭据迁移状态和健康检查。
5. 发布 `dts-platform`，确认 pairwise token、准入/执行/回滚代理契约。
6. 发布 `dts-platform-webapp`，确认旧路由只做跳转，新左侧菜单和三类接入工作台生效。
7. 全部发布完成后只执行一次聚焦 E2E。

## E2E 范围

- 菜单与表格概览：数据库、API、离线文件、默认参数四个入口。
- 文件：上传、文件预检、DRAFT、密级准入、执行、运行后质量结果。
- 数据库/API：建 DRAFT、准入、执行、运行后质量结果；创建阶段不做全量扫描。
- 编辑已生效任务：保留 ACTIVE 继续运行，同时生成独立 DRAFT；未准入 DRAFT 不得执行。
- 回滚：确认令牌只使用一次；部分失败返回 `207` 和人工恢复信息，不继续级联/dbt。
- 服务认证：platform 与 Airflow 分别使用 pairwise token 回调 ingestion。

## 回滚与降级

- 保留发布前四个镜像标签、Compose 配置和数据库备份；按 Web → Platform → Ingestion → Admin 逆序回退。
- 不回写、不重算已冻结 changeset；新 changeset 仅按其显式 rollback 执行。
- 如需降级到仍读取任务主表明文的旧 ingestion：先以 ADMIN 执行 restore dry-run，再显式确认、每批最多 100 条恢复为 `RESTORED_COMPAT`；确认当前版本健康检查阻断这些任务后，立即完成旧镜像降级。不得让当前版本继续运行兼容明文任务。
- 任一步迁移或健康检查失败即停止后续发布，不做部分版本混跑。

## 已知非阻断项

- 未映射兼容服务仍保留 legacy token fallback；默认 Compose 使用 pairwise token。
- ingestion 直连接口和部分内部日志仍可能保留上传路径/文件元数据；浏览器经 platform 的响应已移除路径。
- rollback 审计普通文本节点及部分下游错误日志仍可能保留内部路径形态文本。
- 存量任务 `18` 的一次异步执行暴露 nullable `fallback` 的遗留 NPE；该任务失败后已终止且未形成无限重试，不属于本次工作台合并链路，另行修复。
- 当前工作树包含外部自动提交 `382d0e87f521a475246d35718bebbf458a578f3b` 和无关 modeling 变更。本发布不重写历史、不回退他人修改；构建和发布只以明确列出的四个模块为边界。
- GitNexus 最终全局检查因共享工作树的 `232` 个文件、`2218` 个符号、`26` 条受影响流程及无关 modeling 变更报告 `CRITICAL`；本次接入范围命中的创建任务、版本日志、详情页和服务鉴权流程已通过聚焦测试与线上读链路 E2E，不能把共享全局风险误报为本次接入变更已被隔离。

## 放行条件

- 三个 Java 模块和前端生产构建全部通过。
- GitNexus 最终变更影响检查无新增的接入范围外执行流；共享工作区全局风险单独说明。
- 四个服务健康，数据库迁移完成，pairwise token 校验通过。
- 聚焦 API E2E 通过；UI 登录恢复后完成 UI 点击验收。若登录仍为 `401`，发布结果必须标记 UI 验收阻塞，不得写成全部完成。

## 最终验收结果

- 线上管理健康端点 `/management/health` 返回 `200 / UP`；Admin、Platform、Ingestion、Analytics、Airflow Webserver 均 healthy，Webapp、Scheduler、Triggerer 运行正常，所有目标容器重启数均为 `0`。
- 数据接入目录共 `23` 个任务：数据库 `5`、API `2`、离线文件 `16`；三类代表任务的详情、修订和执行记录请求均返回 `200`，响应未发现密码、密钥或 token 标量。
- 五个正式入口 `/foundation/data-sources{,/database,/api,/files,/defaults}` 均返回 HTTP `200`。
- API 驱动 E2E 已通过；UI 点击 E2E 因现有测试账号登录 `401` 未执行，不能视作 UI 人工验收通过。完整证据见 `../it/final-e2e.md`。
