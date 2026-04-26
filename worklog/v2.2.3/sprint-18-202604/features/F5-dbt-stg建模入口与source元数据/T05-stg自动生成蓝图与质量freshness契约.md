# T05: stg 自动生成蓝图与质量 freshness 契约

**优先级**: P1  
**状态**: READY  
**依赖**: T01, T02, T03

## 目标

为 Phase 4 的 stg 自动生成能力定义蓝图，确保 ODS 保持源端只读镜像层，所有规范化逻辑迁移到 stg。

## 范围

- 定义从 schema snapshot 生成 stg 模型的输入输出契约。
- 支持字段重命名、类型标准化、枚举标准化、空值处理、技术字段保留策略。
- 支持生成 dbt tests：`not_null`、`unique`、`accepted_values`、关系完整性等。
- 支持生成 source freshness 配置，基于 `_dts_import_time` 或源端更新时间字段。
- 定义 stg 到 DWD/DWS 的承接规则：stg 只做单源标准化，不做跨主题汇总。

## 完成标准

- [ ] 输出 stg 自动生成设计文档和示例。
- [ ] 示例覆盖数据库源和文件源。
- [ ] 明确 ODS 中现有“规范化”逻辑应迁移到 stg 的清单。
- [ ] 明确后续 DWD/DWS 建模依赖 stg，而不是直接依赖 ODS。
