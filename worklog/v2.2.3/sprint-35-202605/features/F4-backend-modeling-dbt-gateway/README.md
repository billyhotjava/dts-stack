# F4: 后端建模与 dbt 网关

**优先级**: P0
**状态**: READY

## 目标

实现后端领域模型和服务编排：`dts-metrics` 保存 graph/DSL/artifact/status，生成候选 DWS/ADS artifact；`dts-platform` 执行资产契约、权限、RLS/masking、dbt compile/test/build、release gate、BI/lineage 注册和审计。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | visual asset adapter 与 platform contract client | P0 | READY | F2 |
| T02 | graph persistence 与 DSL preflight | P0 | READY | T01 |
| T03 | DWD/DWS/ADS artifact generator | P0 | READY | T02 |
| T04 | platform/dbt validation gateway 编排 | P0 | READY | T03 |
| T05 | 旧 semantic dry-run 与兼容迁移 | P0 | READY | T04 |

## 完成标准

- [ ] `dts-metrics` 不直读 platform 表或 dbt 项目目录。
- [ ] DWD -> DWS 候选生成前检查粒度、标准码、血缘和策略。
- [ ] 候选 artifact 只进入 platform/dbt validation，不直接发布。
- [ ] 发布结果保存 platform reference，支持撤销和回滚。
