# F2：唯一建模状态链

**优先级**：P0
**状态**：PLANNED

## 目标

把规划、逻辑 revision、门禁、生命周期、候选和物化收敛为唯一状态链，使任何 API/facade 都不能形成平行 owner 或绕过证据。

## 契约定义

| 类型 | 契约 | 要点 |
|---|---|---|
| 规划 | WarehousePlan command/query | tenant 服务端解析，CAS + idempotency |
| 模型 | ModelSpec v2/revision | immutable revision、revision-pinned dependencies |
| 门禁 | StageGateRequest/Decision | 明确 revision；evidence refs + checksum；fail-closed |
| 生命周期 | transition(command, expectedVersion) | 唯一状态变更入口 |
| 发布 | ReleaseCandidate | candidateVersion/attempt、职责分离 |
| 物化 | MaterializationCommand | candidate/version/attempt 固定，委托 execution port |

## UI/UX 规格

后台接线落在 Sprint-80 `/data-modeling/**` 既有页面动作，不新增菜单或页面。每个保存/提交/发布动作在其 vertical slice 完成前保持禁用或失败关闭；错误展示使用明确 403/409/412/422，不显示虚假成功。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 收敛 WarehousePlan 与 ModelSpec v2/revision | P0 | PLANNED | F1 |
| T02 | 收敛 StageGate 与 Lifecycle 单一状态机 | P0 | PLANNED | T01、F3/T02 契约 |
| T03 | 收敛 ReleaseCandidate 与 Materialization | P0 | PLANNED | T02、F4/T01 契约 |

## Definition of Ready

- [x] 状态链顺序和 owner 已冻结。
- [x] 关键请求、状态码、CAS/idempotency 约束已定义。
- [ ] 现有状态机/候选/物化 symbol impact 完成。

## 完成标准

- [ ] 所有状态变化只经 Lifecycle/ReleaseCandidate/Materialization commands。
- [ ] revision、quality/catalog evidence 和 candidate attempt 可重放、可追溯。
- [ ] semantic/business object/SQL model/vNext 不再拥有状态。
