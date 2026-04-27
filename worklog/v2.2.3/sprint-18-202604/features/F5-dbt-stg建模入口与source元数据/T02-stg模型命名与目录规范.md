# T02: stg 模型命名与目录规范

**优先级**: P1  
**状态**: DONE
**依赖**: T01

## 目标

规范 ODS 到 stg 的模型命名和目录，避免后续自动生成、血缘和治理混乱。

## 范围

- 建议命名：`stg_<domain>__<entity>`。
- stg 从 `source('ods_schema', 'ods_table')` 读取。
- stg 内处理字段重命名、类型转换、枚举标准化。
- 技术字段按规则保留或重命名。

## 完成标准

- [x] 输出 stg 命名规范。
- [x] 给出数据库和文件源各 1 个 stg 示例。
- [x] 与现有 finance/pjm dbt 模型不冲突。

## 命名规范

| 对象 | 规范 |
|---|---|
| 模型文件 | `models/staging/<source_system>/stg_<source_system>__<entity>.sql` |
| 模型名 | `stg_<source_system>__<entity>` |
| YAML | `models/staging/<source_system>/stg_<source_system>__<entity>.yml` |
| source 引用 | `{{ source('<ods_schema>', '<ods_table>') }}` |

`source_system` 使用数据源或文件批次的稳定编码，`entity` 使用源表名、sheet 名或文件逻辑资源名。stg 只做单源标准化，不做跨主题 join、聚合或指标口径。

## 示例

数据库源：

```sql
select
  cast(id as bigint) as project_id,
  project_name,
  dept_code,
  _dts_batch_id,
  _dts_execution_id,
  _dts_import_time
from {{ source('ods', 'ods_erp_project') }}
```

文件源：

```sql
select
  row_number() over () as stg_row_id,
  project_no,
  project_name,
  _dts_source_file,
  _dts_source_sheet,
  _dts_row_number,
  _dts_file_hash,
  _dts_batch_id,
  _dts_import_time
from {{ source('ods', 'ods_file_project_plan') }}
```
