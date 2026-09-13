# F5: 领域类型化

**优先级**: P1（持续重构）
**状态**: IN_PROGRESS（artifact builder 拆分 DONE + `VisualAssetSummary` record DONE；`GraphNode`/`ValidationDiagnostic`/`ModelState` 及 Map↔实体收紧 followup）
**对应缺陷**: #4 Map<String,Object> 裸字典

## 目标

把核心契约对象从无类型 Map 改为 record DTO，恢复编译期保障，对齐项目 Java 规范（DTO 用 record）。优先覆盖持久化与契约边界。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T00 | 拆分 artifact builder（类型化前置，腾行数） | P1 | DONE | F1 |
| T01 | 核心契约 DTO record 化 | P1 | IN_PROGRESS（VisualAssetSummary DONE，其余待） | F1 |
| T02 | 用 record 替换 Map（边界优先，渐进） | P1 | IN_PROGRESS（visual-assets resource 边界 DONE） | T01 |

## 完成标准
- [~] `GraphNode` / `VisualAssetSummary` / `ValidationDiagnostic` / `ModelState` 等核心对象有 record 定义（字段与 API 契约 `dts-metrics-api-contract.md` 一致）。
  - [x] `VisualAssetSummary`（17 字段，含契约必需 warehouseLayer/grain/governanceStatus/permissionDecision；字段名/顺序与原 map 一致 → JSON 不变）
  - [ ] `GraphNode`（sourceAssetKey/warehouseLayer/validationState）— **followup**
  - [ ] `ValidationDiagnostic`（nodeId|edgeId 至少其一）— **followup**
  - [ ] `ModelState`（lifecycle 状态机的持久化往返）— **followup**（最高风险，触及 MetricModelStateMapper transient_state jsonb）
- [x]（部分）对外 Resource 边界优先用 record：`MetricVisualAssetResource.listVisualAssets` 返回 `List<VisualAssetSummary>`。
- [ ] `@SuppressWarnings("unchecked")` 与手写强转辅助显著减少（随 ModelState record 化收口）。

## 落地说明（Sprint-35b）
- **T00 拆分**（提交 `33c4834`）：`MetricCandidateArtifactBuilder` + 共享 `MetricIdentifiers`，lifecycle 799→502 行，为类型化腾出行数空间。
- **VisualAssetSummary**：`service/dto/VisualAssetSummary.java`；`toVisualAsset` 直接构造 record，消除一处 `LinkedHashMap` 裸字典。
  - **wire 安全核查**：本应用无全局 Jackson 命名策略，且 `MetricArtifactPreviewResult`/`MetricPackValidationResult` 等 record 已在 REST 返回（camelCase 生效），故 record 序列化与原字面量 map key 完全一致，前端契约不破。
- **风险排序**：剩余三个 record 中 `ModelState` 风险最高（lifecycle 的 Map↔实体往返 + transient_state jsonb），建议独立小步、由黄金 SQL/持久化 IT 守护后再做。
