# F4：发布、物化与资产证据闭环

**优先级**：P1
**状态**：CODE_COMPLETE / RUNTIME_E2E_PENDING
**依赖**：F1/T03 与至少一个已固定 `ImplementationRevision` 生产者（F2/T03 或 F3/T04）。F4 面向统一实施契约，不要求高级提交与 ZIP apply 两条来源同时完成；F2/T04～T05 在 F4/T03 后收口，不形成 Feature 级依赖环。

## 目标

确保普通可视化生成、高级 dbt 编辑和外部导入产生的模型都只经 Sprint-81 canonical 链发布与物化，并把运行事实投影到结构、血缘、质量和 Catalog，而不从 UI 直接运行 dbt。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 将固定 Implementation Revision 接入 StageGate 与 Lifecycle | P1 | CODE_COMPLETE | F1/T03；F2/T03 或 F3/T04 至少一个 |
| T02 | 统一创建 ReleaseCandidate 并经 DbtExecutionGateway 执行 | P1 | CODE_COMPLETE | T01、F0/T05 |
| T03 | 回写 manifest/relation/lineage/quality/Catalog 证据 | P1 | CODE_COMPLETE | T02 |
| T04 | 执行期间实现/来源漂移时 fail-closed 为 FAILED_STALE | P1 | CODE_COMPLETE | T02～T03 |

## 契约约束

- Candidate 必须固定 model revision、implementation revision、artifact checksum、source pins。
- “校验 SQL”不等于“物化”；“物化成功”必须同时有 dbt success 和 relation evidence。
- 现有“发布与物化”对话框选择的是实现来源/修订和物化策略，不是临时编辑 SQL，也不是绕过生命周期切换 ownership。
- `DESIGNER_GENERATED` 的 dbt 制品由系统隐藏生成；`DBT_MANAGED` 使用已固定的高级/外部制品。首期只有 dbt runtime 时不伪造第二执行引擎。
- Catalog 复用稳定逻辑 `CatalogAssetKey`，分别维护 latestPublishedRef 与 servingRef：PUBLISHED 使逻辑资产可发现但新修订不可消费；成功 MATERIALIZED + relation evidence/质量门禁通过后才切换 serving。失败/stale/build-only 保留旧 serving。
- 物理 relation 使用既有 datasource/catalog/schema/relation 身份；不得按 ModelSpec revision 或 dbtUniqueId 建新资产副本。

## Definition of Ready

- [x] D08 已确认。
- [ ] Sprint-81 部署/环境门禁在目标验收环境可满足。

## 完成标准

- [ ] UI 和 import service 均没有 `/etl/dbt/run` 调用。
- [ ] stale callback/old attempt 不能覆盖新实施版本。
- [ ] 资产、质量和血缘都引用同一 CatalogAssetKey 与 candidate evidence。
- [ ] latestPublishedRef/servingRef 可不同；新修订失败时旧 serving 仍可消费，乱序 callback 不能回退 serving。
