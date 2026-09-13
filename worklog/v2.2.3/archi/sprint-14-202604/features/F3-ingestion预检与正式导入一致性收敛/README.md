# F3: ingestion 预检与正式导入一致性收敛

**优先级**: P0
**状态**: READY
**依赖**: F1, F2

## 目标

消除 `dts-ingestion` 预检与 `dts-platform` 正式导入对同一 Excel 给出不同值的风险，至少统一关键格式语义。

## Task 列表

| ID | Task | 优先级 | 状态 |
|---|---|---|---|
| T01 | ExcelParseService 复用核心规则 | P0 | READY |
| T02 | 预检与正式导入结果对齐 | P0 | READY |
| T03 | Addax 与 ODS 落地契约校验 | P1 | READY |
