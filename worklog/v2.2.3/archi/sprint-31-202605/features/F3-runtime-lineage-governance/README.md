# F3: 运行血缘与资产治理闭环

**优先级**: P0
**状态**: DONE
**目标**: 把 Addax、Airflow、OpenLineage、dbt manifest 和 Catalog 血缘统一成可追溯、可治理、可失败告警的运行事实。

**Sprint-31A 依赖**: 运行血缘必须写入 Sprint-31A 的资产身份、治理状态、schema contract 和 lineage failure 契约，不再自建资产身份。

## 任务

| Task | 内容 | 验收 |
|---|---|---|
| T01 | OpenLineage 接收端资产解析增强 | DONE: 优先解析已有 Catalog dataset；不可解析时创建待治理资产 |
| T02 | 自动创建资产治理字段补齐 | DONE: OpenLineage 自动资产写入 warehouseLayer/lifecycleStatus，并从 facet 读取 owner、ownerDept、classification |
| T03 | Addax/Airflow 运行血缘失败告警 | DONE: ingestion -> platform 血缘同步失败写 `INGESTION_LINEAGE_SYNC` 审计失败事件 |
| T04 | dbt manifest import 与运行血缘合并 | DONE: dbt manifest 保持 `DECLARED`，Addax/Airflow/OpenLineage 运行事件写 observed 状态 |
| T05 | 列级血缘 MVP | DONE: dbt asset sync 写入 `CatalogColumnLineage`，影响分析支持列级输出 |
| T06 | 血缘 API 支持 includeColumns / job / run 维度 | DONE: `/api/catalog/lineage/impact` 支持 `withColumns`、`withJobs`、运行 job 信息和时间快照 |

## 代码关注点

- `AirflowDagService`
- `OpenLineageReceiverResource`
- `IngestionLineageWriter`
- `CatalogDbtLineageService`
- `CatalogAssetPortalService`

## 交付记录

- OpenLineage 自动发现资产统一进入 Sprint-31A 资产治理契约，默认 `PENDING_GOVERNANCE`，并在响应 `assetEvidence` 中返回治理状态。
- ingestion 运行血缘同步失败不再只是 warn log，统一落 `INGESTION_LINEAGE_SYNC` 审计失败事件。
- declared lineage 与 observed lineage 使用 `verificationStatus` 区分：dbt manifest 为 `DECLARED`，运行成功为 `VERIFIED`，运行失败为 `KNOWN_UNVERIFIED`。
- 列级血缘由 dbt asset sync 导入，影响分析接口通过 `withColumns=true` 返回。
- 交付契约见 `../../assets/runtime-lineage-governance-contract.md`。
