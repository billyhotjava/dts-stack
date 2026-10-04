# F6：模型中心与高级 dbt 分离

**优先级**：P0
**状态**：READY
**依赖**：F4-T02、F5-T01
**目标**：让模型中心持有业务和逻辑模型真值，让高级 dbt 持有工程实现，并通过显式所有权和产物回写形成闭环。

## Task

| Task | 状态 | 依赖 | 交付物 |
|---|---|---|---|
| [T01-重构模型中心信息架构](T01-重构模型中心信息架构.md) | READY | F4-T02 | 经典默认模型中心和关系视图 |
| [T02-重构高级dbt工作区](T02-重构高级dbt工作区.md) | READY | T01 | dbt 项目、文件、compile/test/run |
| [T03-实现dbt产物与模型证据回写](T03-实现dbt产物与模型证据回写.md) | READY | T01/T02 | artifact、lineage、test、run 回写 |
| [T04-建立实现所有权与发布门禁](T04-建立实现所有权与发布门禁.md) | READY | T03 | ownership 切换、冲突和发布诊断 |

## 完成标准

- 普通模型设计不暴露 dbt 必修步骤；
- dbt 工作区不维护第二套业务模型真值；
- `DESIGNER_GENERATED` 和 `DBT_MANAGED` 所有权明确；
- import 只生成候选语义；
- compile/test/run/lineage 结果进入同一计划证据链。
