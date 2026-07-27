# F3：真实关系核验与强绑定证据

**优先级**：P0
**状态**：DRAFT
**依赖**：F2

## 目标

在 dbt build success 后直接查询执行目标数据库，证明 relation 当前存在、类型和字段正确，并把该事实强绑定到本次 candidate/run/model/implementation。

## 契约定义

| 类型 | 契约 | 关键字段 |
|---|---|---|
| locator | `RelationLocator` | adapter、database、schema、identifier、expectedType |
| port | `PhysicalRelationInspector.observe` | target context + locator → observation |
| 证据 | `modeling_physical_relation_observation` | revisions/checksums/invocation/locator/exists/metadata checksum |
| candidate gate | `BUILT` | all entry DBT_SUCCEEDED + current exists observation |

## UI/UX 规格

关系核验是交付工作台的构建证据，不新增操作按钮。用户看到：

- 核验中：目标 database.schema.identifier；
- 成功：relation 类型、字段数、核验时间、run id；
- 失败：未找到/类型不符/标识不符/凭据或 adapter 不支持，并给修复入口；
- 不显示连接密码、JDBC URL 查询参数或原始异常堆栈。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 建立RelationLocator与目标库实时探针 | P0 | DRAFT | F2/T02 |
| T02 | 建立append-only核验证据与新鲜度约束 | P0 | DRAFT | T01 |
| T03 | 汇聚构建状态并实现adapter能力fail-closed | P0 | DRAFT | T02、F2/T03 |

## Definition of Ready

- [x] inspector port 和 observation schema 已具体定义。
- [x] P0 adapter 与 unsupported 行为已明确。
- [x] UI 四态已命名。
- [ ] F0 架构复审确认 probe 执行边界。

## 完成标准

- [ ] run_results success 不能绕过 relation absence。
- [ ] target identifier/type/columns 与 current artifact 对齐。
- [ ] observation append-only、可追踪且不能跨 revision。
- [ ] unsupported adapter 不产生 false positive。
