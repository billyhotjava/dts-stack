# T04: Addax/dbt/快照导出百万行基线

**优先级**: P0  
**状态**: READY  
**依赖**: T03

## 目标

验证大 CSV 在正式主链路中可以完成：文件入湖、dbt 建模、CSV 训练快照导出。

## 技术设计

基线链路：

```text
500MB CSV 或 1,000,000 行 CSV
  -> DTS 文件接入
  -> Addax txtfilereader 入 ODS
  -> dbt run metro_dwd_lstm_training_snapshot
  -> DTS snapshot export 流式写 data.csv
```

需要记录：

- 文件大小、行数、列数。
- 入湖耗时、dbt 耗时、快照导出耗时。
- 失败重试、错误行数、质量规则耗时。
- Postgres 表大小、索引策略和慢 SQL。

## 影响范围

- `source/dts-ingestion`
- `source/dts-platform`
- `worklog/v2.2.3/thales/v1/dbt_model`
- `worklog/v2.2.3/sprint-30-202605/it/evidence/large-csv/`

## 验证

- [ ] Addax CSV reader 作业成功。
- [ ] ODS 行数与 CSV 行数一致。
- [ ] dbt 模型输出行数符合窗口构造预期。
- [ ] snapshot package `data.csv` 使用流式导出。

## 完成标准

- [ ] 至少完成 100MB 样例基线。
- [ ] 正式交付前完成 500MB 或 1,000,000 行样例基线。
