# F3: 运行血缘与资产治理闭环

**优先级**: P0
**状态**: READY
**目标**: 把 Addax、Airflow、OpenLineage、dbt manifest 和 Catalog 血缘统一成可追溯、可治理、可失败告警的运行事实。

## 任务

| Task | 内容 | 验收 |
|---|---|---|
| T01 | OpenLineage 接收端资产解析增强 | 能解析已有 Catalog dataset，不可解析时进入 `PENDING_GOVERNANCE` |
| T02 | 自动创建资产治理字段补齐 | source、owner、classification、warehouseLayer、lifecycleStatus 必须有值或明确待治理 |
| T03 | Addax/Airflow 运行血缘失败告警 | 血缘写入失败进入审计/告警，不只写 warn log |
| T04 | dbt manifest import 与运行血缘合并 | declared lineage 和 observed lineage 可区分展示 |
| T05 | 列级血缘 MVP | 至少支持 ODS -> DWD/DWS/ADS 的字段级映射导入或声明 |
| T06 | 血缘 API 支持 includeColumns / job / run 维度 | 前端能按运行或模型查看血缘 |

## 代码关注点

- `AirflowDagService`
- `OpenLineageReceiverResource`
- `IngestionLineageWriter`
- `CatalogDbtLineageService`
- `CatalogAssetPortalService`
