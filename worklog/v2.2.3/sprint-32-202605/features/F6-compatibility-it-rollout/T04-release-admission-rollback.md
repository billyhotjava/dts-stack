# T04: 发布准入和回滚 runbook

**优先级**: P0
**状态**: READY
**依赖**: T03

## 目标

定义 Sprint-32 React Flow 指标语义工作台的发布准入和回滚步骤。

## 技术设计

- 发布准入：graph preflight、platform contract precheck、dbt validation、review、release gate、IT evidence。
- 回滚：前端路由回滚、metrics 服务回滚、graph draft 版本回滚、dbt publish record 回滚。
- 不能删除底层 source asset。

## 影响范围

- `worklog/v2.2.3/sprint-32-202605/it/README.md`
- deployment/runbook docs

## 验证

- [ ] 回滚步骤不依赖手工改数据库。
- [ ] 发布失败能定位到失败阶段。

## 完成标准

- [ ] Sprint-32 不再用占位 UI 作为 DONE 标准。
