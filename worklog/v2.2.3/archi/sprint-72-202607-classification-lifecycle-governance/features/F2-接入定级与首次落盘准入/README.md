# F2: 接入定级与首次落盘准入

**优先级**: P0
**状态**: IN_PROGRESS
**编码状态**: DONE（统一验证延后）
**估算**: 11～14 人日

## 目标

在 JDBC/API/Excel/CSV 数据第一次生产落盘前采集、确认和封存密级；缺少有效 seal 的任务只能预检，不能写 ODS。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | JDBC源表字段密级采集与映射 | P0 | IN_PROGRESS | F1-T03 |
| T02 | ExcelCSV文件与字段密级录入 | P0 | IN_PROGRESS | F1-T03 |
| T03 | API流式接入密级契约 | P0 | IN_PROGRESS | F1-T03 |
| T04 | 首次落盘Seal准入门禁 | P0 | IN_PROGRESS | T01,T02,T03 |
| T05 | 接入向导密级确认与证据预览 | P0 | IN_PROGRESS | T01,T02,T03 |

## 完成标准

- [ ] 所有生产接入任务携带 seal id/version/checksum。
- [ ] 无密级数据只能进入受控隔离预检。
- [ ] 文件级密级是所有字段最低值。
- [ ] 重试和断点续传不改变已封存密级。
