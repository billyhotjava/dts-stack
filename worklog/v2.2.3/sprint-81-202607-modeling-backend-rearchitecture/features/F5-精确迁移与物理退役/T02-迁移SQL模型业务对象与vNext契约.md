# T02：迁移 SQL 模型、业务对象与 vNext 契约

**优先级**：P0
**状态**：PLANNED
**依赖**：F0/T03；F2～F4 canonical 链

## 目标

把 `modeling_sql_model`、`modeling_business_object` 和内部 vNext service/contract 的真实语义精确映射到 canonical owner，清零内部消费者。

## 技术设计（Contract-first）

- **SQL model**：逻辑名称/字段/依赖/SQL checksum → ModelSpec revision；implementation/artifact → canonical implementation/materialization；资产 → CatalogAssetKey。
- **Business object**：plan/domain/process → WarehousePlan/既有规划 owner；逻辑字段 → ModelSpec revision；词汇 → glossary；无意义字段 `IGNORE`；模糊字段 `CONFLICT`。
- **vNext contract**：HTTP DTO/facade 调用改 canonical application ports；无独立业务数据则仅 caller manifest；有投影按 revision checksum 对账。
- **manifest**：每 source PK/version/checksum 对应 target ID/revision/checksum 和 `MIGRATE|MAP|IGNORE|CONFLICT`。
- **apply**：500 行/批、checkpoint、幂等；重验 databaseId/source version/manifest checksum。
- **错误路径**：一对多/多对一未声明、target 漂移、conflict/orphan>0 全部阻断，不静默取 latest。

## 影响范围

legacy migration service、SQL/business-object/vNext callers、canonical import/application adapters。

## 验证

- [ ] dry-run 零写；代表存量 apply/verify/rollback。
- [ ] revision、dependency、artifact、CatalogAssetKey checksum 对账。
- [ ] 重复 apply 不新增 target revision。
- [ ] legacy caller=0，canonical 功能回归通过。

## Definition of Done

- [ ] 每条 source 可追溯到唯一 target 或获批准的 IGNORE。
- [ ] CONFLICT/orphan=0，备份恢复通过。
- [ ] SQL/business-object/vNext 可进入物理删除。
