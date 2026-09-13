# F3: Excel/CSV 离线文件接入

**优先级**: P0  
**状态**: DONE
**依赖**: F1, F4

## 目标

把离线 Excel/CSV 纳入企业级接入中心主流程：上传、预检、schema 确认、ODS 建表、导入、坏行记录、文件血缘追踪。

## Task 列表

| ID | Task | 优先级 | 状态 |
|---|---|---|---|
| T01 | 文件接入契约与元数据模型 | P0 | DONE |
| T02 | Excel 预检与 schema 确认接入 ODS 契约 | P0 | DONE |
| T03 | CSV 预检与编码分隔符策略 | P0 | DONE |
| T04 | 文件血缘字段与坏行记录 | P0 | DONE |
| T05 | 文件接入样本库与回归测试 | P0 | DONE |

## 完成标准

- [x] Excel/CSV 走统一接入任务和 execution 体系。
- [x] 文件导入 ODS 时包含文件专属 `_dts_*` 字段。
- [x] 坏行可下载、可定位原始文件、sheet 和行号。
- [x] 与 Sprint-14 Excel 解析内核保持兼容。
- [x] CSV 预检使用独立 CSV parser，支持引号、逗号、双引号转义和字段内换行。
- [x] 同一任务重复预检时重建暂存表，避免旧 schema 或旧行污染。
