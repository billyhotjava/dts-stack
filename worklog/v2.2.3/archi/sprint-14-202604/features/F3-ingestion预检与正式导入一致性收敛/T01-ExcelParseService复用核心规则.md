# T01: ExcelParseService 复用核心规则

**优先级**: P0
**状态**: READY

## 目标

让 `dts-ingestion` 的 `ExcelParseService` 至少复用平台侧同一套 normalize 规则，避免负数、日期、公式字符串再次漂移。

## 完成标准

- [ ] 字符串单元格、公式 display/cached string、普通文本都走统一 normalize 逻辑
- [ ] 不再在 ingestion 内复制第二份独立 Excel 语义
