# v3 建模治理说明

## 1. 目标架构

v3 模型统一采用以下分层：

```text
ODS -> STG -> DWD -> DWS -> ADS
```

其中：

- `ODS`：原始接入层，只负责落地与追溯，不承载业务语义
- `STG`：结构化语义层，负责轻量标准化、类型统一、脏值收敛、粒度声明
- `DWD`：业务明细层，负责事实/维度语义、业务布尔字段、主事实键
- `DWS`：主题汇总层，负责按主题聚合
- `ADS`：应用层，负责看板/接口直接消费

## 2. 强制规则

### 2.1 依赖规则

- `dwd/*` 禁止直接 `source(...)`
- `dws/*` 禁止直接 `source(...)`
- `ads/*` 禁止直接 `source(...)`
- 所有下游模型必须经由 `stg_*` 进入语义链路

### 2.2 STG 规则

- 命名统一为 `stg_[domain]__[entity]`
- 单张 ODS 表尽量对应单张 STG 表
- STG 不做跨表复杂 join
- STG 不做聚合
- STG 必须保留来源元数据：
  - `source_row_id`
  - `source_table`
  - `source_system`
  - `source_file`
  - `source_sheet_name`
  - `source_batch_id`
  - `source_row_num`
  - `imported_at`

### 2.3 字段规则

每个字段都应尽量明确：

- 业务含义
- 粒度
- 类型
- 是否标准化值
- 是否可聚合

### 2.4 主键规则

- STG 的 `source_row_id` 必须做 `unique + not_null`
- DWD 的业务主键必须做 `unique + not_null`
- DWD 必须保留 `source_row_id` 以支撑从指标回溯到 ODS 原始记录
- DIM 必须提供稳定的 `*_id` 作为查询依据，中文 `code/label` 仅用于映射和展示

## 3. 当前 v3 语义链路

当前已纳入 dbt 语义链路的 ODS 表：

- `ods_project_subject_domain_v2`
- `ods_quality_issue_v2`
- `ods_tech_state_v2`
- `ods_risk_info_v2`

对应新增 STG 表：

- `stg_pm__project_subject_domain_v2`
- `stg_pm__quality_issue_v2`
- `stg_pm__tech_state_v2`
- `stg_pm__risk_info_v2`

## 4. 治理原则

- ODS 保留“原样”和“可追溯”
- STG 负责“结构化”和“收敛”
- DWD 负责“业务解释”
- 指标定义优先于 SQL 实现
- 血缘必须可解释到“Excel 字段 -> STG 字段 -> DWD 字段 -> 指标”
