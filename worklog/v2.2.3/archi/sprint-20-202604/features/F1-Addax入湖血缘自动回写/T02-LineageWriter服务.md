# T02: LineageWriter 服务（写 catalog_dataset_lineage）

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

在 dts-platform 提供一个面向 dts-ingestion 的内部接口（HTTP 或共享 DB），把执行级 lineage 边写入 `catalog_dataset_lineage`，统一所有"非 dbt"来源的入口。

## 技术设计

### 服务位置

新增 `com.yuzhi.dts.platform.service.catalog.lineage.IngestionLineageWriter`：

```java
public class IngestionLineageWriter {
    public LineageWriteResult writeAddaxLineage(AddaxLineageRequest request);
    public LineageWriteResult markFailedExecution(UUID executionId, String reason);
}
```

请求结构：

```java
record AddaxLineageRequest(
    UUID executionId,
    UUID taskId,
    String taskName,
    String projectName,
    List<TableRef> sourceTables,    // {datasource, schema, table}
    List<TableRef> targetTables,
    Map<Integer, Integer> mapping   // sourceIdx -> targetIdx；多对多时多条
) {}
```

### 写入规则

1. **datasetId 解析**：用 `(datasource_id, schema, table)` 通过 `CatalogDatasetRepository.findByPhysical(...)` 解析；解析不到的源表自动登记为 `EXTERNAL_TABLE` 类型的影子 dataset（`asset_type=EXTERNAL_TABLE, project=__external__`）。
2. **upsert key**：`(upstream_dataset_id, downstream_dataset_id, relation_type='ADDAX')` —— 同 task 多次执行只更新 `notes` 与 `valid_from`，不重复落库。
3. **direction**：`UPSTREAM_TO_DOWNSTREAM`，与 `AUTO_VIEW` 保持一致。
4. **upstream/downstream asset_type**：源端 `EXTERNAL_TABLE` 或 `DATASET`，目标端 `DATASET`。
5. **失败 execution**：不写正式边，写到一张新表 `catalog_lineage_pending`（待 F4 落地后改造为 job 节点）。
6. **`relationType=ADDAX`**：与现有 DBT/AUTO_VIEW/MANUAL 不冲突。

### 调用方式

- **进程内**（推荐）：dts-platform 提供 REST API `POST /api/internal/lineage/addax`，dts-ingestion 通过 `RestTemplate`/`WebClient` 调用。
- 端点用 `@PreAuthorize("hasAuthority('AUTH_INTERNAL')")` + IP 白名单防护，不暴露给外网。
- 失败时调用方重试 3 次（exponential backoff），仍失败则在 `IngestionExecution` 记 `lineage_synced_at = NULL` + 错误日志。

### 关联文件

新增：

- `dts-platform/.../service/catalog/lineage/IngestionLineageWriter.java`
- `dts-platform/.../service/catalog/lineage/dto/AddaxLineageRequest.java`
- `dts-platform/.../web/rest/internal/InternalLineageResource.java`
- `dts-ingestion/.../service/etl/PlatformLineageClient.java`（调用方）

修改：

- `dts-platform/.../domain/catalog/CatalogDatasetLineage.java` — 增加 `EXTERNAL_TABLE` 枚举值（如果是 enum）；如已是 varchar32 则不需要
- 检查 `direction` 列长度（已经在 20260421_01 扩到 32）
- `audit-action-catalog.json` — 新增 `LINEAGE_INGEST_WRITE` 审计条目

## 影响范围

- 新增 4 个 Java 类
- 修改 3 个文件
- 1 条 Liquibase 迁移（如需建 `catalog_lineage_pending` 表）：`20260430_02_catalog_lineage_pending.xml`

## 验证

- [ ] 单测：`IngestionLineageWriterTest` 覆盖 5 个分支（多对多映射、source 不存在、target 不存在、upsert 命中、失败 execution）
- [ ] 集成测试：用 `@SpringBootTest` + Testcontainers PG 验证真实 SQL
- [ ] 同一 task 跑 2 次，DB 中只有 1 条 ADDAX 边（upsert 验证）
- [ ] 审计：`audit_event` 表出现 `LINEAGE_INGEST_WRITE` 记录
- [ ] 内部接口未授权访问返回 403，有授权返回 200

## 完成标准

- [ ] 服务 + REST 接口已实现并跑通单测
- [ ] dts-ingestion 端 `PlatformLineageClient` 已封装并配置可关闭开关 `dts.lineage.platform-callback.enabled`
- [ ] 失败重试 3 次的策略生效（mock 验证）
- [ ] 审计日志条目齐全
