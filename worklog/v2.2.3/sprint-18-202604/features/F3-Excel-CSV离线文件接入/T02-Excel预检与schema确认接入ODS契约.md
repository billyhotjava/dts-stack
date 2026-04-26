# T02: Excel 预检与 schema 确认接入 ODS 契约

**优先级**: P0  
**状态**: READY  
**依赖**: T01

## 目标

让 Excel 预检结果成为 ODS 建表和导入的正式输入。

## 范围

- 复用 Sprint-14 的 Excel 解析内核。
- 支持选择 sheet、表头行、数据起始行。
- 输出字段名、类型推断、样例值、错误摘要。
- 用户确认后生成 ODS schema snapshot。

## 完成标准

- [ ] Excel 预检与正式导入使用同一 schema。
- [ ] 合并单元格、日期、负数、公式值至少有样本覆盖。
- [ ] sheet 名写入 `_dts_source_sheet`。
