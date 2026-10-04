# 领域与数据画像 — 数据血缘

**状态**: GAP（术语与不变量已确立；真实数据画像待实测，见开放问题 Q1-Q3）

## 统一语言 (Ubiquitous Language)

| 术语 | 含义 | 落点 |
|---|---|---|
| 血缘边 (lineage edge) | 上游数据集 → 下游数据集的一条有向关系，带关系类型与有效期 | `catalog_dataset_lineage` |
| 字段血缘 (column lineage) | 挂在某条血缘边下的列级映射，带推断置信度 | `catalog_column_lineage.dataset_lineage_id` |
| 血缘任务 (lineage job) | 产生血缘的执行体（Addax 作业 / dbt 模型 / Airflow 任务） | `catalog_lineage_job` |
| 关系类型 (relationType) | `ADDAX` / `DBT` / `AIRFLOW` / `AUTO_VIEW` / `MANUAL`，决定图上的连线配色 | 前端 `lineageContracts.ts` RELATION_STROKE |
| 核验状态 (verificationStatus) | `DECLARED`（声明未验）/ `KNOWN_UNVERIFIED`（已知存疑）/ `VERIFIED`（已核验） | `catalog_dataset_lineage.verification_status` |
| 置信度 (confidence) | 字段血缘的推断可信度：`PARSED`（SQL 表达式匹配）/ `INFERRED`（同名投影兜底） | `catalog_column_lineage.confidence` |
| 时间旅行 (time travel) | 以 `valid_from <= at < valid_to` 还原某一时刻的血缘快照 | `findByEitherSideAt` |
| 软失效 | 置 `valid_to = now()` 而非物理删除，保证历史可回溯 | ADR-90-03 |

## 业务不变量

1. **血缘只增不删**：任何"删除"都是软失效；物理删除仅限显式高权限 `force=true` 路径，且必须审计。
2. **人工结论优先于自动采集**：一条边被治理员置为 `VERIFIED` 后，后续采集重跑不得把它降级回 `DECLARED`（ADR-90-04）。
3. **字段血缘不能脱离表级边存在**：`dataset_lineage_id` 必须指向一条存在的表级边；表级边失效时，其下字段血缘同步失效。
4. **时间语义单一**：同一次查询里，表级与字段级必须使用同一个 `at`。当前实现违反此条（账本#7），由 F1/T01 修复。
5. **部门可见性贯穿**：血缘节点必须逐个过 `accessChecker.canRead + departmentAllowed`，不可见节点连同其边一起从结果中剔除——已实现，回归时不得绕过。
6. **置信度必须诚实**：`INFERRED` 是同名兜底的猜测，不得在 UI 上与 `PARSED` 同等呈现（ADR-90-08）。

## 真实数据画像（待实测）

规划期无运行实例，以下必须在 G0 关闭时用现网/脱敏库实测填入：

| 指标 | 用途 | 现值 |
|---|---|---|
| `catalog_dataset_lineage` 总行数 / 当前有效行数 | F4/T04 批量化阈值 | **待测（Q1）** |
| `catalog_column_lineage` 总行数 / 已失效行数占比 | 验证 F1/T01 修复的实际影响面 | **待测（Q1）** |
| 单节点最大扇出（上游+下游） | depth=5 时的最坏遍历规模 | **待测（Q1）** |
| `relation_type` 取值分布 | 核验队列默认筛选（Q3） | **待测** |
| `verification_status` 取值分布 | 待核验队列体量 | **待测** |
| `confidence` 中 `INFERRED` 占比 | 判断置信度筛选的实际价值 | **待测** |
| OpenLineage 事件是否有真实流入 | F3/T02 健康卡片形态（Q2） | **待测** |
| `catalog_dataset` 总数 | 验证数据集下拉 300 条上限是否已被击穿（账本#15） | **待测** |

## 外部边界

| 边界 | 方向 | 契约 | 风险 |
|---|---|---|---|
| dts-ingestion | 入 | `POST /api/catalog/lineage/ingestion-executions`（账本#11） | 本 Sprint 不改其请求体，避免破坏上游 |
| Airflow / OpenLineage | 入 | `POST /api/internal/lineage/openlineage`（账本#12） | 只读健康状态，不改接收逻辑 |
| dbt 产物 | 入 | manifest.json 上传 + ELT 链路 `DbtAssetSyncService` | 两条路不等价（账本#9），F3/T02 需在 UI 上如实说明 |
| OpenMetadata | 双向缓存 | 维持 ADR-85-04 降级为"同步证据" | 不恢复第二张血缘图 |

## 合规约束

- 血缘查询已按部门隔离，本 Sprint 新增的登记/核验接口必须复用同一套 `accessChecker`，不得放宽。
- 新增审计事件：`CATALOG_LINEAGE_CREATE` / `CATALOG_LINEAGE_DELETE` / `CATALOG_LINEAGE_VERIFY`，与既有 `CATALOG_LINEAGE_VIEW` / `_IMPACT_VIEW` / `_DIFF_VIEW` 同规格落审计库。
