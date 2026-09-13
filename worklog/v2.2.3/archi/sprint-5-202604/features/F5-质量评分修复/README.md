# F5: 质量评分修复

**优先级**: P0
**状态**: READY

## 目标
修复质量评分公式：补充 rows_total 字段，修正通过率计算，补全 metric_value 写入。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | gov_quality_run 新增 rows_total + Liquibase | P0 | READY | - |
| T02 | QualityRunService 填充 rows_total + metric_value | P0 | READY | T01 |
| T03 | QualityScoreService 修正评分公式 | P0 | READY | T01 |

## 完成标准
- [ ] rows_total 字段迁移成功
- [ ] 新执行的规则正确记录 rows_total
- [ ] 评分公式：score = (total - failing) / total × 100
- [ ] metric_value / threshold_value 正确填充
