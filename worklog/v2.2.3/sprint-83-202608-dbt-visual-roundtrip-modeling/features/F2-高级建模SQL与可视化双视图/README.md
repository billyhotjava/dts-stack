# F2：高级建模 SQL 与可视化双视图

**优先级**：P0  
**状态**：DRAFT  
**依赖**：F1

## 目标

让用户在现有模型工作台内，基于同一模型/实施修订查看业务定义、dbt SQL、表结构、依赖和物化结果；SQL 修改只能通过隔离草稿提交为新实施修订。

## UI/UX 规格

- **入口**：`/data-modeling/dimensions/workbench?modelSpecId={uuid}&mode=advanced`。
- **页面上下文栏**：模型、Model revision/checksum、Implementation revision/checksum、ownership、stage、candidate。
- **视图**：业务定义｜表结构｜SQL/dbt｜依赖｜运行结果。
- **编辑规则**：DBT_MANAGED 的 SQL 可编辑、技术结构只读；DESIGNER_GENERATED 的结构可编辑、生成 SQL 只读。
- **四态**：无实施版本、加载中、解析/权限/冲突错误、修订固定成功态。

## Task 列表

| ID | Task | 状态 | 依赖 |
|---|---|---|---|
| T01 | 接入真实模型上下文和高级模式深链 | DRAFT | F1/T01 |
| T02 | 建立 SQL、表结构与依赖三视图 | DRAFT | F1/T03～T04、T01 |
| T03 | 建立隔离 SQL 草稿、校验与实施版本提交 | DRAFT | T01～T02 |
| T04 | 接入候选固定的物理表结构与样例数据 | DRAFT | F4/T02～T03 |
| T05 | 完成脏状态、冲突、阻断、权限和历史修订 UX | DRAFT | T01～T04 |

## Definition of Ready

- [ ] D01～D03、D11～D12 已确认。
- [ ] 草稿、validate、commit、preview 契约及 ETag/CAS 错误码冻结。

## 完成标准

- [ ] 页面任何时刻只展示一个明确 revision/checksum 上下文。
- [ ] SQL 保存不改共享 projectDir 当前值、不直接 run/publish。
- [ ] 物理数据预览执行权限、密级、脱敏和 limit 门禁。
