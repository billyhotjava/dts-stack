# F3: SQL 建模

**优先级**: P1
**状态**: READY

## 目标

在阶段④落地面向高级用户的 SQL 建模能力：SQL 建模编辑 + 模板 + ModelPipeline（建模流水线）。把现网 modeling 筒仓的 `SqlModeling/ModelTemplates/ModelPipeline` 收口进阶段④，作为语义建模（F2）之外的「写 SQL 直接建模」高级旁路。这是给会写 SQL 的用户的进阶入口，但**仍不直接暴露 dbt 文件树编辑**——SQL 建模产出的模型同样由底层引擎编译为 dbt（隐藏），仅 F4 抽屉可查产物。命名对齐现网 `SqlModelingPage` / `ModelTemplatesPage` / `ModelPipelinePage`，service 用 `sqlModelingService`。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| [T01](./T01-SQL建模与流水线.md) | SQL 建模 + 模板 + ModelPipeline | P1 | READY | S4 |

## 完成标准

- [ ] `SqlModelingPage` 提供 SQL 编辑面（基于 S5 数据集），可保存为模型。
- [ ] `ModelTemplatesPage` 提供 SQL 建模模板，可一键起建。
- [ ] `ModelPipelinePage` 以列表/流程视图呈现建模流水线（编排占位），用 CompactTable 默认 10 条/页。
- [ ] 全部经 `sqlModelingService` 取数。
- [ ] SQL 建模产物编译为 dbt（隐藏）；本 Feature **不提供 dbt 文件树式编辑入口**，产物仅 F4 抽屉可查。
