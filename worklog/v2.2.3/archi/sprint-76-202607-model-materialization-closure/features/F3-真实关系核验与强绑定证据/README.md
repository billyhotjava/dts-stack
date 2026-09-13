# F3：真实关系核验与强绑定证据

**优先级**：P0
**状态**：DONE（T01～T04 GREEN）
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
| T01 | 建立RelationLocator与目标库实时探针 | P0 | DONE | F2/T02 |
| T02 | 建立append-only核验证据与新鲜度约束 | P0 | DONE | T01 |
| T03 | 汇聚构建状态并实现adapter能力fail-closed | P0 | DONE | T02、F2/T03 |
| T04 | 建立 typed-column 物理类型契约与核验 | P0 | DONE | T01～T03 |

## Definition of Ready

- [x] inspector port 和 observation schema 已具体定义。
- [x] P0 adapter 与 unsupported 行为已明确。
- [x] UI 四态已命名。
- [x] F0 架构复审确认 probe 执行边界。

## 完成标准

- [x] run_results success 不能绕过 relation absence。
- [x] target identifier、relation type、列名及顺序与 current artifact 对齐。
- [x] DESIGNER_GENERATED 的字段数据类型与 current ModelSpec/dbt artifact/真实 relation 对齐。
- [x] observation append-only、可追踪且不能跨 revision。
- [x] unsupported adapter 不产生 false positive。

## 完成证据与边界

- 自动化证据：`../../it/evidence/f3-real-physical-relation/README.md`。
- 真实 PostgreSQL IT 创建隔离表并通过 `pg_catalog` 读取关系类型、列顺序和数据类型；
  两次 probe 保留 attempt 1/2，数据库触发器拒绝 update/delete。
- 只有 `markDbtSucceeded + append observations` 成功，且全部 observation verified 后，
  才在同一事务内把 pipeline 与 Candidate 推进到 BUILT；Candidate 转换失败不会留下
  半完成的 BUILT 真值。
- P0 只注册 PostgreSQL inspector；未知 adapter 明确 fail-closed。原
  `DBT_MANAGED` 资产同步兼容测试保持通过。
- 2026-07-28 架构复审确认：`ModelSpec.dataType` 已是必填契约，投影时丢失属于正确性
  缺陷，不是可选 hardening。T04 已以 40 个聚焦单测和 2 个真实 PostgreSQL IT
  关闭；F4 只因此解除 typed-column 阻断，不代表发布治理已实现。
