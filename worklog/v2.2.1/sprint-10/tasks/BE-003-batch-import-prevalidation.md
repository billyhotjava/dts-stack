# BE-003

## 标题

为 ZIP 批量导入增加 manifest 预检和失败类型细分。

## 范围

- `ModelingSqlModelService.batchImportFromArchive`

## 目标

- 在真正导入前发现 ZIP 结构与字段问题
- 将失败区分为 `validation_failed` 与 `write_failed`

## 交付

- ZIP 预检逻辑
- 批量导入结果状态细分
- 对应回归测试

## 验收

- 缺 `models.tsv`、缺 SQL、非法 layer/materialized 都能稳定报出

## 当前进度

- 状态：TODO

## 风险

- 预检规则过严会挡住部分历史包，需要兼容旧清单格式
