# T01: 存量密级盘点与 Dry-run 报告

**优先级**: P0
**状态**: IN_PROGRESS
**依赖**: F1-T04

## 目标

盘点所有旧密级字段、空值、冲突、字段敏感标签、血缘和消费引用，零写入生成迁移报告。

## 技术设计

- 覆盖 dataset/table/column/model/metric/API/product/report/screen/file。
- 报告来源候选、旧值、计算值、缺密级、缺血缘、候选升密和候选降级冲突。
- 输出批次划分、预计事件量和不可自动迁移清单。
- 不扫描或导出业务数据内容。

## 影响范围

迁移工具、只读 repository、运维报告和审计。

## 验证

- [ ] 空库、小样本、大批量和重复 dry-run。
- [ ] 运行前后数据库业务表行数不变。

## 完成标准

- [ ] 自动迁移规则和人工清单边界清晰。
- [ ] 候选降级记录全部阻断。

## 编码进展

已实现平台 dataset/table/column/API/data product/report/file 与 Analytics card/metric/screen/dashboard、
Metrics 已发布模型的只读盘点和 dry-run 报告；报告区分创建、升密、缺密级、未知编码和候选降级。
运行验证待统一测试阶段执行。
