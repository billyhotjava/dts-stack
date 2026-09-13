# T01: ExcelImportFacade 收口

**优先级**: P0
**状态**: READY

## 目标

让 `ExcelImportService` 不再直接承担所有解析细节，而是收口到统一 facade，方便后续替换 scanner / writer / inferer 而不改 REST 层。

## 完成标准

- [ ] `ExcelImportService` 变成 orchestrator
- [ ] sheet 解析、归一化、列推断、产物写出职责拆开
- [ ] `ExcelImportResource` 对外接口保持不变
