# Sprint-14 集成测试与验收证据

本目录用于保存 Sprint-14 的样本文件、回归矩阵、导入结果对照、历史脏数据巡检 SQL 与上线/回滚说明。

## 计划证据

- [ ] `fixtures/`：Excel 兼容性样本库
- [ ] `regression-matrix.md`：解析回归矩阵
- [ ] `platform-vs-ingestion-compare.md`：平台侧与预检结果对比
- [ ] `ods-repair-plan.md`：历史脏数据巡检与修复建议
- [ ] `rollback-runbook.md`：回滚与开关策略

## 验收闸门

Sprint-14 标记 DONE 的最低要求：

1. 平台侧统一解析内核已接管正式导入主路径
2. ingestion 预检已复用同一套 normalize 规则
3. 负数 / 日期 / 公式的关键 fixtures 回归通过
4. 对外 REST 契约未破坏
