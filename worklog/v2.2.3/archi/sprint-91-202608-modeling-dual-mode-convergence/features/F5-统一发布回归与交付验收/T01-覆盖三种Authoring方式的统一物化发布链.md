# T01: 覆盖三种 Authoring 方式的统一物化发布链

**优先级**: P0
**状态**: BLOCKED
**依赖**: F2～F4、F6～F8 完成；F0/T03 全链路样本；既有 `BUILT → CANCELLED` 语义已裁决

## 目标

用同一套 dependency snapshot、materialization plan 和 release candidate 契约验证 DESIGNER_GENERATED、手工 DBT、ZIP apply 与接管 DBT。authoring 方式只影响制品来源，不影响模型身份、依赖、候选、物化、质量、发布和治理资产登记。

## 技术设计 (Contract-first)

- **输入契约**: F0 双模式样本 + `E2E_S91_CHAIN_*` 四层样本；各自 current model/implementation/dependency revision/checksum；当前 command guard 给出的 actor/duty 权限。
- **输出契约**: 手工与 DESIGNER 四层链均按依赖计划从 DRAFT 经既有显式动作到 ONLINE；entry 钉住正确 model/implementation/dependency checksum。ZIP/接管路径至少完成同一 candidate/build/test/publish 等价性回归。
- **数据流**:
  - 手工 DBT（主验收）：UI 声明依赖 → draft commit → dependency preview → candidate → topological build/test/quality/review/publish；
  - DESIGNER（主验收）：UI 结构化转换 → compiler/artifact commit → 同一 preview/candidate/发布链；
  - ZIP（等价性）：inspect→preview→apply → 同一 dependency snapshot/candidate，禁止 ZIP 专属物化；
  - **DBT（接管而来）**：F2 接管 → compile → 同一 preview/candidate。这条验证 `{SQL,SCHEMA,CONFIG}` 制品仍可走完发布链。
- **单表与批量**: ADS 单表请求必须把缺失 DWS/DWD/DIM 标记 BUILD 或精确上游标记 REUSE；多选相同闭包去重后按同一拓扑执行。
- **二次物化**: 第二次执行新增 candidate/execution/observation，不新增 ModelSpec、implementation identity 或 CatalogAssetKey；上游精确验证后应由 BUILD 转 REUSE。
- **错误路径**: dependency/ownership 漂移后旧 candidate 必须 STALE 或被稳定冲突拒绝；发布失败使用 retry/rollback，不重写实现；候选不得引用旧 dependency/implementation checksum。

  > **已核实契约**：候选 scope 锁定 model revision/checksum/implementation mode；任一漂移在后续命令时转为 `STALE`，稳定码为 `MODEL_RELEASE_CANDIDATE_STALE`。本 Task 补 ownership transition 的定向回归，失败即阻断交付。
- **复用点**: F6 resolver、F8 planner、`ModelReleaseCandidateResource/ApplicationService`、ModelPublishDialog、现有 materialization dispatch、asset registration 与 Sprint-93 evidence seams。
- **禁止项**: 不增加 `if ownership then publishToX` 的第二状态机；分支只允许存在于 artifact preparation seam。

## Definition of Ready

- [ ] F0/T03、F2～F4、F6～F8 已完成，所有聚焦测试通过。
- [ ] `BUILT → CANCELLED` 基线语义已裁决，release regression 无未解释失败。
- [ ] 所级/部门账号、目标环境、物理结果和 Sprint-93 evidence 查询路径可用。

## 影响范围

- dependency/materialization/release/lifecycle focused integration tests
- ModelPublishDialog regression tests（若刷新语义需调整）
- `it/IT-06`、`IT-07`、`IT-11`～`IT-16` 真实证据

## 验证 (RED→GREEN)

- [ ] DESIGNER / 手工 DBT / ZIP apply / DBT 接管创建 candidate 的同一契约测试。
- [ ] 手工 DBT 与 DESIGNER 的四层链逐表和批量物化均达到 ONLINE，目标表字段/行数与粒度正确。
- [ ] **接管而来的模型能走完到 PUBLISHED**，且候选 entry 的 implementation checksum 指向接管产出的实现。
- [ ] candidate entry 的 model/implementation/dependency pins 与数据库 current revision 完全一致。
- [ ] ownership 或 dependency 转换后旧 candidate 转 STALE，返回稳定 stale code，不能继续发布。
- [ ] 每个 lifecycle 状态由显式命令推进；`xiezm` 只执行 guard 授权动作，部门账号跨范围请求 403；不存在前端自动审批/发布。
- [ ] 重复 publish idempotency key 不重复登记资产。
- [ ] 二次物化不重复登记 ModelSpec/implementation/CatalogAssetKey，并形成新的执行/观察审计。
- [ ] 发布后的表示 projection 回传 publication evidence。
- [ ] Sprint-93 可通过 correlation 查询资产、元数据、血缘、质量和 publication evidence。

## Definition of Done

- [ ] IT-06、IT-07、IT-11～IT-16 达到各自终态；主链达到 ONLINE，而非只到 compile/materialized。
- [ ] 四类路径共用同一 dependency/candidate/dispatch/observation/审计结构。
- [ ] 发布资产可从统一数据资产入口检索，且关联当前发布证据。
