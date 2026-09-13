# T03: 编译、运行、血缘和证据 API

**优先级**: P0
**状态**: IN_PROGRESS
**依赖**: T01,T02

## 目标

把模型编译、Airflow 投递、Addax 批次、dbt run/test 和血缘信息统一为可查询证据。

## 技术设计

- `POST /api/modeling/vnext/model-specs/{id}/compile`
- `POST /api/modeling/vnext/runs`
- `GET /api/modeling/vnext/runs/{id}`
- `GET /api/modeling/vnext/lineage/{id}`
- 运行请求带 `sourceBatchId`、`airflowDagId`、`dbtSelector`、`target`。

状态统一为 `DRAFT/COMPILED/SUBMITTED/RUNNING/SUCCEEDED/FAILED/DRIFTED`。

## 影响范围

- Resource、运行编排 service、血缘查询 service。
- 前端运行状态和证据抽屉。

## 验证

- [x] 编译失败的契约状态为 FAILED，不进入发布态。
- [x] 前端 API client 已暴露 compile/run/lineage 端点及统一状态枚举。
- [x] 后端运行请求已统一 Addax batch、Airflow DAG、dbt selector 和 PostgreSQL target 的校验契约。
- [ ] 重复运行请求返回同一个持久化幂等运行记录。
- [ ] 运行记录能关联 Addax、Airflow、dbt 三类外部标识。

## 完成标准

- [ ] 页面可从模型详情一路查看到目标表和运行日志入口。
