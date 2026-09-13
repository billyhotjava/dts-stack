# T03: Addax/Airflow 来源证明

**优先级**: P0
**状态**: DONE
**依赖**: F1

## 目标

把 Addax 任务、Airflow DAG、运行实例和数据源映射写入资产来源证明，连接接入层和 Catalog。

## 技术设计

- `IngestionLineageWriter` 在 Addax 血缘 notes 中写入 source/target assetKey。
- 自动创建 source / ODS 资产时写入 `PENDING_GOVERNANCE` 生命周期。
- 保留 mappingId、sourceDataSourceId、source/target table、executionId、executionStatus、batchId。
- ODS 资产缺生命周期时补 `PENDING_GOVERNANCE`。

## 影响范围

- dts-ingestion 回写契约
- platform ingestion task API
- Catalog lineage
- `worklog/v2.2.3/sprint-31a-202605/assets/addax-airflow-provenance.md`

## 验证

- [x] ODS 资产能看到来源数据源和任务。
- [x] 缺映射不静默成功。
- [ ] 统一测试在 Sprint-31A/31/32 完成后执行。

## 完成标准

- [x] Sprint-31 黄金链路能证明接入到资产的链路。
