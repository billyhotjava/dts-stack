# T05: raw落地与按资源事务

**优先级**: P0
**状态**: IN_PROGRESS
**依赖**: T01

## 目标

JdbcTemplate 实现 raw landing（`_dts_raw_record JSONB` + 9 技术列）与 checkpoint 持久化；**按资源独立事务**（修复一错全回滚）；建立幂等键去重。

## 技术设计

- 落地表：迁移 `_ensure_landing_table` DDL（id BIGSERIAL + `_dts_raw_record` JSONB + 9 技术列），经 `TargetTableProvisioner` 风格的 quoteIdentifier 防注入；目标库连接走**平台数据源解析**（修复硬编码 `dts-pg/biadmin`，对齐 `AirflowDagService:1205` 整改）。
- **事务边界 = 单资源**：资源 A 失败不回滚资源 B；每资源成功后立即提交其数据+checkpoint（修复旧全局 `conn.commit()`）。失败资源记入 execution 明细，整体状态 = PARTIAL_FAILED/FAILED 语义（对齐 F3-T02）。
- **幂等键**：落地表增加 `_dts_record_hash`（记录 JSON 规范化 SHA-256）+ `(resource, hash)` 唯一索引，`ON CONFLICT DO NOTHING`——支撑 lookback 重叠窗口与手动重跑不产生重复行。
- checkpoint：沿用 `dts_api_ingestion_checkpoint` 表与 UPSERT 语义（`:1488-1497`），迁到 JdbcTemplate；批量插入用 batchUpdate（批大小可配）。
- 旧表兼容：已存在的落地表自动 `ALTER TABLE ADD COLUMN IF NOT EXISTS _dts_record_hash` + 创建唯一索引（存量重复行先去重再建索引，提供迁移 SQL）。

## 影响范围

- 新增 `service/etl/api/ApiRawLandingService.java`
- `dts_api_ingestion_checkpoint` 表（结构不变）
- 迁移 SQL 存 assets/

## 验证

- [x] 单测：资源 A 抛错后资源 B 数据已提交；checkpoint 仅推进成功资源
- [x] SQL 断言：raw insert 使用 `_dts_record_hash` + `ON CONFLICT`，checkpoint 使用 UPSERT
- [x] 存量表迁移模板已补 assets（具体表名执行需 IT 替换）
- [x] 幂等：同批数据重放两次，真实目标库行数不变
- [x] 目标库连接进一步抽象为平台 target datasource 解析（`destinationConfig.targetDataSourceId`/`destinationDataSourceId`/`dataSourceId` -> `IngestionSourceResolver.resolveJdbcInfo`，保留 `jdbcUrl` 兼容兜底）

## 完成标准

- [ ] 重跑/补数/lookback 场景零重复行，事务边界有 IT 证据

## 进展记录

### 2026-06-12

- 新增 `ApiRawLandingService`：
  - 目标库连接走 `IngestionTask.destinationConfig` -> `JdbcMetadataService.openConnection`，不再使用旧 Python 的 `DTS_TARGET_DB_*`/`dts-pg` 硬编码。
  - 每个 resource 单独打开连接并独立 `commit/rollback`；失败 resource 记录到 `LandingResult.failedResources`，其他 resource 可继续提交。
  - raw 表 DDL 包含 `_dts_raw_record JSONB`、9 个技术列和 `_dts_record_hash`。
  - insert 使用 `(resource, hash)` 幂等唯一索引和 `ON CONFLICT DO NOTHING`。
  - checkpoint 表沿用 `dts_api_ingestion_checkpoint`，成功 resource 内 UPSERT；backfill 执行不推进 checkpoint。
- `ApiIngestionExecutor` 已接入 raw landing；`rowsRead` 来自 HTTP records，`rowsWritten` 来自 raw landing 受影响行数。
- 目标库连接支持平台数据源 ID：`ApiRawLandingService` 优先用 `targetDataSourceId` 解析运行期 JDBC 信息，避免在任务配置里长期保存目标库连接串/密码；旧 `jdbcUrl` 配置继续作为兼容兜底。
- 补充 live PostgreSQL IT：`it/scripts/api-raw-landing-idempotency.sh` 在临时 schema 上按 `_dts_source_resource + _dts_record_hash` 唯一键重放同批 JSON，第一次插入 2 行、第二次插入 0 行、总行数保持 2；证据见 `it/evidence/api-raw-landing-idempotency-20260612.txt`。
- 已跑：
  - `(cd source && ./mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -pl dts-ingestion -Dtest=ApiRawLandingServiceTest test)`
  - `(cd source && ./mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -pl dts-ingestion -Dtest=ApiRawLandingServiceTest,ApiIngestionExecutorTest,ApiHttpEngineTest,CursorTrackerTest,JsonPathLiteTest,ApiHttpSourceConnectorTest,IngestionTaskServiceTest#execute_shouldRunApiTaskThroughConnectorRegistryAndApiExecutor test)`
  - `RUN_LIVE=1 bash worklog/v2.2.3/sprint-38-202606/it/scripts/api-raw-landing-idempotency.sh`
