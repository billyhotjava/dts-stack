# F5: 领域类型化

**优先级**: P1（持续重构）
**状态**: READY
**对应缺陷**: #4 Map<String,Object> 裸字典

## 目标

把核心契约对象从无类型 Map 改为 record DTO，恢复编译期保障，对齐项目 Java 规范（DTO 用 record）。优先覆盖持久化与契约边界。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 核心契约 DTO record 化 | P1 | READY | F1 |
| T02 | 用 record 替换 Map（边界优先，渐进） | P1 | READY | T01 |

## 完成标准
- [ ] `GraphNode` / `VisualAssetSummary` / `ValidationDiagnostic` / `ModelState` 等核心对象有 record 定义（字段与 API 契约 `dts-metrics-api-contract.md` 一致）。
- [ ] 持久化边界与对外 Resource 边界优先用 record；内层 jsonb 可保留渐进迁移。
- [ ] `@SuppressWarnings("unchecked")` 与手写强转辅助显著减少。
