# T01：收敛 WarehousePlan 与 ModelSpec v2/revision 主链

**优先级**：P0
**状态**：PLANNED
**依赖**：F1 模块/端口守卫

## 目标

让 WarehousePlan 和 ModelSpec v2/revision 成为规划与逻辑设计的唯一 owner，并冻结旧 plan/business object/SQL model 的目标映射。

## 技术设计（Contract-first）

- **输入契约**：`planId UUID`、`modelSpecId UUID`、`revision int`、`expectedVersion long`、`idempotencyKey string`；tenant/actor 服务端解析。
- **输出契约**：head + immutable revision；依赖引用为 `{modelSpecId,revision}` / `{dimensionDefinitionId,revision}` / `{assetType,assetKey}`。
- **数据流**：WarehousePlan context → create/update ModelSpec head → append revision → event/audit outbox。
- **错误路径**：plan 不存在/不可见 404/403；CAS 冲突 409/412；依赖跨租户或 revision 不存在 422；idempotency payload 不一致 409。
- **迁移映射**：old plan→WarehousePlan；business object 字段分类；SQL model logical content→revision，implementation/artifact 不写 revision。
- **禁止**：隐式 latest dependency、objectId/SQL model ID 作为发布 owner、双写旧表。

## 影响范围

plan/spec application service、revision codec/repository 与 DTO；实施前分别 impact。

## 验证

- [ ] create/update/replay/CAS/cross-tenant/revision-pin tests。
- [ ] old fields 的 classification 映射契约测试。
- [ ] 同 idempotency key 重放只产生一个 revision/outbox logical event。

## Definition of Done

- [ ] canonical head/revision 可承载所有新 UI 逻辑设计输入。
- [ ] 旧 owner 无新增写入；迁移目标字段无 TBD。
- [ ] schema/DTO/source contract 与 migration manifest 一致。
