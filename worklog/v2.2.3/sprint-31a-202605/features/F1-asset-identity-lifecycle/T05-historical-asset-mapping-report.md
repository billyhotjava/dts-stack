# T05: 历史资产映射和冲突报告

**优先级**: P1
**状态**: DONE
**依赖**: T02-T04

## 目标

对历史 Catalog、OpenMetadata cache、QueryDataset、screen/report、semantic 模型资产进行 dry-run 映射，提前发现冲突。

## 技术设计

- 输出 `asset_key` 候选、冲突原因、建议合并或保留策略。
- 不默认删除历史数据。
- 生成面向运维和开发的迁移报告。

## 影响范围

- worklog evidence
- 迁移脚本或 dry-run CLI/API

## 验证

- [x] dry-run service 可重复执行且不写入。
- [x] 冲突结果包含 asset identity 和候选来源。
- [ ] F6/T01 暴露 API 或脚本后统一执行并归档 evidence。

## 完成标准

- [x] 为 Sprint-31 和 Sprint-32 迁移提供输入。
