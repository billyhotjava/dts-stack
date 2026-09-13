# T04: PostgreSQL 目标表、目录与血缘回写

**优先级**: P0
**状态**: READY
**依赖**: T03

## 目标

运行成功后把目标表、字段、数据集、血缘和运行证据回写到 DTS 资产目录。

## 技术设计

- DWD/DWS/ADS 目标表必须带 warehouse layer 和 ModelSpec revision。
- dbt manifest 作为字段、依赖和 lineage 的技术来源。
- 资产目录登记失败不回滚已成功的数据运行，但模型状态标记为 `SUCCEEDED_WITH_CATALOG_WARNING`。

## 影响范围

- catalog sync/lineage adapter。
- 模型详情和资产详情跳转。

## 验证

- [ ] 运行成功后能从模型详情跳转目标数据集和血缘。
- [ ] 目录同步失败有告警，不伪装成完全成功。

## 完成标准

- [ ] 运行证据可在 `it/evidence/airflow/` 和 `it/evidence/backend/` 复核。
