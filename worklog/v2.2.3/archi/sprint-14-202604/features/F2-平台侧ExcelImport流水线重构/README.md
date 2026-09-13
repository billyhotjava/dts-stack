# F2: 平台侧 ExcelImport 流水线重构

**优先级**: P0
**状态**: READY
**依赖**: F1

## 目标

把 `ExcelImportService` 改造成“先解析、后产物化”的流水线，同时保持当前 REST 返回与下游 CSV 契约不变。

## Task 列表

| ID | Task | 优先级 | 状态 |
|---|---|---|---|
| T01 | ExcelImportFacade 收口 | P0 | READY |
| T02 | 延后 CSV 化与 ArtifactWriter | P0 | READY |
| T03 | REST 兼容与迁移开关 | P1 | READY |
