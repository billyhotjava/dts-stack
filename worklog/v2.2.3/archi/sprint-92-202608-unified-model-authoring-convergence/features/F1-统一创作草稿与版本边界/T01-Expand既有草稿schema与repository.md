# T01：Expand 既有草稿 schema 与 repository

**优先级**：P0  
**状态**：IMPLEMENTED  
**依赖**：F0/T01、F0/T02

## 目标

以向后兼容的 nullable Expand 扩展既有 dbt draft，使其能够暂存 ModelSpec snapshot、projection summary 和 provenance，不新建第二张 authoring 主表。

## 技术设计（Contract-first）

- **输入契约**：账本 L07～L10；现表 `modeling_dbt_implementation_draft` 的 pins/status/ETag/receipt 约束。
- **输出契约**：Liquibase 新列 `model_spec_snapshot jsonb null`、`projection_summary jsonb null`、`authoring_origin varchar(32) null`；repository row/view 对应字段；旧行读取为 null/derived。
- **数据流**：Expand migration → repository mapper → existing draft service compatibility read。
- **错误路径**：未知 origin 不拒绝旧行，返回 `UNKNOWN`；无 model snapshot 时从 canonical ModelSpec 只读装配，禁止回写。
- **复用点**：现有 draft table/file table、unique/index/check constraints。
- **实现方案**：新增独立 changeSet，master include；不改旧 changeSet；rollback 只 drop 新列且仅用于未写新数据环境。

## 影响范围

`source/dts-platform` Liquibase、dbtdraft repository/contract/tests；不触碰 model/implementation/release 表。

## 验证（RED→GREEN）

- [ ] RED：repository contract 无法读写三个新字段。
- [ ] GREEN：clean DB apply/rollback/re-apply；old rows/new rows 均可读。
- [ ] Schema 断言：旧约束和索引仍存在，无新主表。

## Definition of Done

- [ ] migration 可逆且向后兼容。
- [ ] repository round-trip 保留 JSON/checksum，未知 origin fail-safe。
- [ ] 旧 `DbtImplementationDraftService` 测试零回归。
