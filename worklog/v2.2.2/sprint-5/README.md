# Sprint-5：Platform 后端重构前置需求收口

## 目标

把本轮对 `source/dts-platform` 的代码审查结果沉淀成一个独立 Sprint 的需求材料，作为后续重构的输入基线。

本 Sprint 只做三件事：

- 固化 review 结论
- 明确重构目标和非目标
- 拆出后续可执行的重构任务

不在本 Sprint 内直接修改 `source/dts-platform` 业务实现。

## 审查范围

- 分支：`v2.2.2`
- 基线提交：`b8cb30b65`
- 模块：`source/dts-platform`
- 本次重点关注文件：
  - `config/AirflowProperties.java`
  - `service/etl/AirflowClient.java`
  - `service/etl/DbtDagService.java`
  - `service/etl/DbtOutputRelationService.java`
  - `service/etl/RollbackCascadeService.java`
  - `service/ops/OpsService.java`
  - `web/rest/EtlResource.java`

## 本 Sprint 输出

- 需求材料：`req/`
- 重构设计：`design.md`
- 重构计划：`plan.md`
- 任务拆分：`tasks/`

## 结论摘要

本轮 review 收敛出 3 个需要优先重构的问题：

1. Airflow DAG 可见性检查会吞掉真实错误，误报成“DAG 未注册”
2. `DbtOutputRelationService` / `EtlResource` 的重建语义已经变化，但测试与契约没有同步
3. 回滚后二次触发 dbt full-refresh 的失败会被吞掉，接口仍可能返回成功

另外，当前针对这批改动的后端回归面已经是红的，后续重构必须以恢复测试绿灯为第一验收门槛。

## 文档

- [design.md](./design.md)
- [plan.md](./plan.md)
- [req/README.md](./req/README.md)
- [tasks/README.md](./tasks/README.md)
