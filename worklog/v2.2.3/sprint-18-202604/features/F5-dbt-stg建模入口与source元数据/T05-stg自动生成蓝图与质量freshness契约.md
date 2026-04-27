# T05: stg 自动生成蓝图与质量 freshness 契约

**优先级**: P1  
**状态**: DONE
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

- [x] 输出 stg 自动生成设计文档和示例。
- [x] 示例覆盖数据库源和文件源。
- [x] 明确 ODS 中现有“规范化”逻辑应迁移到 stg 的清单。
- [x] 明确后续 DWD/DWS 建模依赖 stg，而不是直接依赖 ODS。

## 自动生成输入

| 输入 | 来源 |
|---|---|
| ODS 表与列 | `ingestion_schema_snapshot.columns_json`、catalog table/column schema |
| 技术字段 | `_dts_*` 固定契约 |
| 字段标准名 | 用户确认的 stg 字段映射或后续字段词典 |
| 类型标准化 | 源类型、目标承载类型、stg 类型规则 |
| 质量规则 | PK、nullable、accepted values、freshness 配置 |

## 输出产物

| 产物 | 路径 |
|---|---|
| stg SQL | `models/staging/<source_system>/stg_<source_system>__<entity>.sql` |
| stg YAML | `models/staging/<source_system>/stg_<source_system>__<entity>.yml` |
| source freshness | 同 stg YAML 或 `models/ods_sources.yml` |
| lineage meta | `meta.ingestion_task_id`、`meta.ods_table`、`meta.schema_snapshot_id` |

## 从 ODS 迁移到 stg 的逻辑

| 逻辑 | ODS | stg |
|---|---|---|
| 字段重命名 | 不做 | `select source_col as standard_col` |
| 类型标准化 | 只用目标库承载类型 | `cast()` 到建模语义类型 |
| 部门编码转名称 | 不做 | join 维表或引用维度模型 |
| 枚举翻译 | 不做 | case/seed/dim 映射 |
| 空值和占位符清洗 | 不做 | `nullif()` / 标准清洗函数 |
| 派生字段 | 不做 | stg 或后续 DWD/DWS 生成 |

DWD/DWS/ADS 禁止直接依赖 ODS 作为业务口径输入。允许依赖 ODS 的场景仅限数据排障、数据质量、freshness 和接入审计。
