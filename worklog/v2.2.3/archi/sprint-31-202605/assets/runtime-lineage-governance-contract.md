# 运行血缘与资产治理闭环契约

## 目标

Sprint-31 F3 将 Addax / Airflow / OpenLineage / dbt manifest 血缘统一写入 platform Catalog，并继承 Sprint-31A 的资产治理状态、权限和 schema contract。

## 事实源

| 来源 | relationType | verificationStatus | 说明 |
|---|---|---|---|
| dbt manifest | DBT | DECLARED | 声明式模型依赖，代表设计血缘 |
| Addax ODS mapping | ADDAX | DECLARED / VERIFIED / KNOWN_UNVERIFIED | 入湖映射和运行观测 |
| Airflow OpenLineage | AIRFLOW | DECLARED / VERIFIED / KNOWN_UNVERIFIED | DAG 运行观测 |
| dbt asset sync column lineage | DBT | column confidence | 字段级来源映射 |

## 自动资产治理

OpenLineage 事件无法解析到已有 Catalog dataset 时，platform 自动创建 Catalog 资产：

- `lifecycleStatus=PENDING_GOVERNANCE`
- `warehouseLayer` 从表名前缀推断，无法推断时为 `UNKNOWN`
- `owner` / `ownerDept` / `classification` 优先从 OpenLineage facet 读取
- 缺失治理字段时由 Sprint-31A governance gap 报告继续阻断消费

## 失败可观测

ingestion 调用 platform 血缘同步失败时写审计事件：

```text
action = INGESTION_LINEAGE_SYNC
stage = FAIL
```

审计元数据包含 `taskId`、`taskName`、`executionId`、`batchId`、`status` 和失败原因。

## API

```bash
GET /api/catalog/lineage/impact?datasetId={id}&direction=BOTH&depth=3&withColumns=true&withJobs=true
```

支持：

- `withColumns=true` 返回字段级血缘。
- `withJobs=true` 展开运行 job 虚拟节点。
- `at={instant}` 查看历史快照。
- `changedWithinHours`、`projectName`、`layers`、`sourceId` 过滤。

## 验收

- 未解析资产不会直接成为可消费资产，必须处于待治理状态。
- 运行血缘同步失败有审计事件，不只写日志。
- dbt 声明血缘和运行观测血缘可通过 relationType / verificationStatus 区分。
- 影响分析能返回列级血缘和运行 job 维度。
