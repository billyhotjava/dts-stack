# F4：发布、物化与资产证据闭环

**优先级**：P0  
**状态**：DRAFT  
**依赖**：F2/T03、F3/T04。F2/T04～T05 在 F4/T03 后收口，不形成 Feature 级依赖环。

## 目标

确保高级编辑和外部导入产生的模型都只经 Sprint-81 canonical 链发布与物化，并把运行事实投影到结构、血缘、质量和 Catalog，而不从 UI 直接运行 dbt。

## Task 列表

| ID | Task | 状态 | 依赖 |
|---|---|---|---|
| T01 | 将两类入口统一接入 StageGate 与 Lifecycle | DRAFT | F2/T03、F3/T04 |
| T02 | 统一创建 ReleaseCandidate 并经 DbtExecutionGateway 执行 | DRAFT | T01 |
| T03 | 回写 manifest/relation/lineage/quality/Catalog 证据 | DRAFT | T02 |
| T04 | 执行期间实现/来源漂移时 fail-closed 为 FAILED_STALE | DRAFT | T02～T03 |

## 契约约束

- Candidate 必须固定 model revision、implementation revision、artifact checksum、source pins。
- “校验 SQL”不等于“物化”；“物化成功”必须同时有 dbt success 和 relation evidence。
- Catalog 可消费资产只在发布/物化门禁通过后产生。

## Definition of Ready

- [ ] D08 已确认。
- [ ] Sprint-81 部署/环境门禁在目标验收环境可满足。

## 完成标准

- [ ] UI 和 import service 均没有 `/etl/dbt/run` 调用。
- [ ] stale callback/old attempt 不能覆盖新实施版本。
- [ ] 资产、质量和血缘都引用同一 CatalogAssetKey 与 candidate evidence。
