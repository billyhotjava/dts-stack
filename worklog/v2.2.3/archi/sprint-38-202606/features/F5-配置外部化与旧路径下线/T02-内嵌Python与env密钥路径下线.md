# T02: 内嵌Python与env密钥路径下线

**优先级**: P0
**状态**: DONE
**依赖**: F2 全部, F3-T01

## 目标

删除 385 行内嵌 Python 实现与 env 密钥语义；存量 API 任务的 DAG 文件重新生成为瘦触发版，平滑迁移不丢 checkpoint。

## 技术设计

- 删除：`buildApiDagSource` 旧体（被 F3-T01 瘦模板替换）、`escapeTripleQuotedJson`（如无他用）、`_secret(os.environ)` 语义、`DTS_API_BEARER_TOKEN/DTS_API_KEY/DTS_API_USERNAME/DTS_API_PASSWORD` 约定。
- 迁移：启动或运维端点触发对所有 `isApiSourceType` 任务执行 `ensureDagForTask(force=true)` 重写 DAG 文件（`writeDagFile` 内容比对机制天然支持）；`dts_api_ingestion_checkpoint` 表结构不变，游标无缝衔接（F2-T05 哈希列迁移 SQL 单独执行）。
- 任务 sourceConfig 兼容：`tokenEnv/apiKeyEnv` 等旧字段若存量存在，迁移脚本提示改配 secretRef（不静默兼容，避免 env 路径复活）。
- 按项目规则：删除/修改前对 `buildApiDagSource`、`ensureDagForTask` 跑 gitnexus_impact；完成后 `gitnexus_detect_changes` 核对影响面。

## 2026-06-12 进展

- 已新增运维端点：`POST /api/ingestion/tasks/dags/rebuild-api`，仅 `INFRA_MAINTAINERS` 可触发，批量扫描任务并只对 active API source 强制重写瘦 DAG。
- 已新增服务方法：`IngestionTaskService.rebuildApiDags()`，返回 `total/migrated/skipped/failed/items` 迁移摘要；跳过非 API、deleted、Airflow disabled 任务；失败项保留当前 dagId 与裁剪后的错误原因，不改 checkpoint。
- 已补回归测试：`IngestionTaskServiceTest#rebuildApiDags_shouldForceRebuildOnlyActiveApiTasks`、`IngestionTaskResourceTest#rebuildApiDags_returnsMigrationSummary`、`AirflowDagServiceTest#shouldGenerateThinApiDagThatCallsInternalIngestionEndpoint` 追加旧 API 密钥 env 负断言。
- 已做残留扫描：生产/测试代码中旧 `DTS_API_BEARER_TOKEN/DTS_API_KEY/DTS_API_USERNAME/DTS_API_PASSWORD` 仅作为负断言出现；`SOURCE_CONFIG_JSON`、`escapeTripleQuotedJson`、`tokenEnv/apiKeyEnv`、旧 `_request_json`/landing Python 片段无生产代码命中。`resolve_secret`、`psycopg2` 命中仍属于非 API 的 Addax/JDBC DAG 分支。
- live 生成 DAG `/opt/airflow/dags/dts_api_e2e_orders.py` 已验证不含 `SOURCE_CONFIG_JSON`/`psycopg2`/旧 API env 密钥标记；内部 HTTP 调用使用 `urllib.request.ProxyHandler({})` 绕过 Airflow 容器代理环境。证据见 `../../it/evidence/api-end-to-end-20260612.txt`。
- IT-08 已验证存量 API 任务批量重建：`rebuild_migrated=1`、`rebuild_failed=0`，DAG 文件旧 env/旧 Python 标记为 0；迁移后 Airflow 触发新增 1 行，checkpoint 从 `2026-06-12T00:05:00Z` 推进到 `2026-06-12T00:06:00Z`。证据见 `../../it/evidence/api-dag-migration-20260612.txt`。

## 回滚预案

1. 发布前保留当前 git tag、镜像 tag 与 `services/dts-airflow/dags/*.py` 备份。
2. 如需回滚，先停 dts-ingestion 调度入口，切回上一版 dts-ingestion 镜像/代码，再恢复备份 DAG 或用上一版镜像重建 DAG。
3. 不回滚 `dts_api_ingestion_checkpoint`；若新版本已写入 ODS，用执行记录 `batch_id/execution_id` 做数据侧回滚，避免游标倒退造成重复抓取。
4. 禁止恢复旧 `DTS_API_*` env 密钥语义；发现存量 `tokenEnv/apiKeyEnv` 配置时，先改为数据源 secrets 后再放行任务。

## 影响范围

- `service/etl/AirflowDagService.java`（大删）
- 存量 DAG 文件（dagsDir 下 api 任务）、迁移说明

## 验证

- [x] 旧 API 密钥 env 无生产代码引用：`DTS_API_BEARER_TOKEN/DTS_API_KEY/DTS_API_USERNAME/DTS_API_PASSWORD`
- [x] API DAG 测试断言瘦触发模板不含 `SOURCE_CONFIG_JSON`、`psycopg2`、旧 landing/checkpoint Python 业务片段
- [x] 聚焦测试通过：`./mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -pl dts-ingestion -Dtest=IngestionTaskServiceTest#rebuildApiDags_shouldForceRebuildOnlyActiveApiTasks,IngestionTaskResourceTest#rebuildApiDags_returnsMigrationSummary,AirflowDagServiceTest#shouldGenerateThinApiDagThatCallsInternalIngestionEndpoint test`
- [x] API/F3/F5 相关宽测试通过：`./mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -pl dts-ingestion -Dtest=ApiPropertiesTest,ApiHttpEngineTest,ApiHttpSourceConnectorTest,ApiRawLandingServiceTest,ApiIngestionExecutorTest,ApiAuthProviderRegistryTest,ApiConnectorContractResourceTest,InternalApiIngestionResourceTest,ExecutionFailureClassifierTest,PlatformInfraClientTest,AirflowDagServiceTest#shouldGenerateThinApiDagThatCallsInternalIngestionEndpoint,AirflowExecutionSyncServiceTest,IngestionTaskServiceTest#executeInternalApi_shouldUseRequestBatchAndBackfillWindow,IngestionTaskServiceTest#rebuildApiDags_shouldForceRebuildOnlyActiveApiTasks,IngestionTaskResourceTest test`
- [x] 存量任务迁移后首轮增量游标正确衔接（IT-08：`../../it/evidence/api-dag-migration-20260612.txt`）

## 完成标准

- [x] 旧路径代码 0 残留，回滚预案文档化（保留 git tag）
