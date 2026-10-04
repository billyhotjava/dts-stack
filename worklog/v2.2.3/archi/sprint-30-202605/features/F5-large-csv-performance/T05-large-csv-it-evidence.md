# T05: 大 CSV 集成压测证据归档

**优先级**: P0  
**状态**: READY  
**依赖**: T04

## 目标

把大 CSV 性能结果沉淀为可复验的集成测试证据，作为正式版本准入材料。

## 证据目录

```text
worklog/v2.2.3/sprint-30-202605/it/evidence/large-csv/
```

建议包含：

- `dataset.md`：生成方式、字段、行数、文件大小、hash。
- `upload.log`：上传或文件接入日志。
- `precheck.log`：预检耗时、错误摘要、内存记录。
- `addax.log`：入湖任务日志和行数核对。
- `dbt.log`：dbt parse/run 输出。
- `snapshot-export.log`：导出耗时、文件清单、`data.csv` 行数。
- `metro-validate.log`：metro-stack 契约校验结果。

## 验证

- [ ] 证据能复现 100MB 样例。
- [ ] 证据能复现 500MB 或 1,000,000 行样例。
- [ ] 明确标注测试机器配置和运行模式。

## 完成标准

- [ ] Sprint-30 不能在没有大 CSV 证据时标记 DONE。
