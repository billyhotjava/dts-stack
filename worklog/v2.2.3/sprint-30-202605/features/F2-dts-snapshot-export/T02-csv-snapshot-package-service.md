# T02: CSV 快照包生成服务

**优先级**: P0  
**状态**: READY  
**依赖**: T01

## 目标

实现从 Postgres/dbt 模型导出五件套 snapshot package。

## 技术设计

服务职责：

- 查询 `metro_dwd_lstm_training_snapshot` 生成 `data.csv`。
- 查询或组装契约信息生成 `manifest.json`。
- 从模型字段元数据生成 `schema.json`。
- 从质量模型/规则结果生成 `quality_report.json`。
- 从 dbt manifest/catalog 或 DTS 资产血缘生成 `lineage.json`。

输出路径建议：

```text
${DTS_TRAINING_SNAPSHOT_ROOT}/metro/{snapshotId}/
```

默认配置：

```yaml
dts:
  ml:
    training-snapshot-root: ${DTS_TRAINING_SNAPSHOT_ROOT:/opt/dts/training-snapshots}
    max-export-rows: ${DTS_TRAINING_SNAPSHOT_MAX_ROWS:1000000}
```

## 影响范围

- `source/dts-platform/src/main/resources/config/application*.yml`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/config/*Properties.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/ml/*`

## 验证

- [ ] 服务单测使用临时目录断言五件套文件存在。
- [ ] CSV 头部字段顺序与 `schema.json` feature_index 一致。
- [ ] 大文件导出使用流式写，不一次性载入内存。

## 完成标准

- [ ] 本地可生成完整 snapshot package。
- [ ] 错误信息明确区分模型不存在、无数据、字段缺失、路径不可写。
