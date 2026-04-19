# Finance 建模治理说明

## 1. 目标架构

当前财务域统一采用一套从 ODS 到 ADS 的单链路模型：

```text
ODS -> STG -> DWD -> DWS -> ADS
```

其中：

- `ODS`：保留原始落地表结构，只承担追溯与装载边界
- `STG`：做字段清洗、占位值收敛、类型统一、粒度声明
- `DWD`：做业务明细解释，补齐主键、维度映射和核心派生字段
- `DWS`：做主题汇总，沉淀大屏直接消费的聚合视角
- `ADS`：做看板 KPI 输出，只保留应用侧真正要消费的字段

## 2. 当前 ODS 范围

本次只保留四张 ODS 源表信息，不沿用旧模型历史包袱：

- `ods_finance_own_fund`
- `ods_finance_project_fund`
- `ods_finance_aux_balance`
- `ods_finance_aux_balance_personal`

对应 DDL 参考：

- `ods_table/create_finance_tables.sql`

## 3. 强制规则

### 3.1 依赖规则

- `dwd/*` 禁止直接 `source(...)`
- `dws/*` 禁止直接 `source(...)`
- `ads/*` 禁止直接 `source(...)`
- 所有下游模型必须从 `stg_fin__*` 进入语义链路

### 3.2 STG 规则

- 命名统一为 `stg_fin__{entity}`
- 单张 ODS 表尽量对应单张 STG 表
- STG 不做跨表复杂 join
- STG 不做聚合
- STG 保留来源字段：
  - `source_row_id`
  - `source_table`

### 3.3 DWD 规则

- DWD 必须提供稳定业务主键
- DWD 必须保留 `source_row_id` 以支持回溯到 ODS
- DWD 负责定义大屏依赖的派生口径，例如：
  - `direct_spent`
  - `total_rate`
  - `expense_category`
  - `balance_direction`

### 3.4 命名规则

- 维度表统一命名为 `dim_*`
- 业务明细事实统一命名为 `biz_dwd_*`
- 主题汇总统一命名为 `biz_dws_*`
- 应用层 KPI 统一命名为 `biz_ads_*`

## 4. 当前语义链路

### STG

- `stg_fin__own_fund`
- `stg_fin__project_fund`
- `stg_fin__aux_balance`
- `stg_fin__aux_balance_personal`

### DWD

- `dim_own_fund_period_type`
- `dim_expense_category`
- `dim_balance_direction`
- `dim_personal_subject_category`
- `biz_dwd_own_fund`
- `biz_dwd_project_fund`
- `biz_dwd_aux_balance`
- `biz_dwd_aux_balance_personal`

### DWS

- `biz_dws_own_fund_yearly`
- `biz_dws_project_fund_summary`
- `biz_dws_aux_balance_by_dept`
- `biz_dws_aux_balance_personal_by_dept`

### ADS

- `biz_ads_own_fund_kpi`
- `biz_ads_project_fund_kpi`
- `biz_ads_aux_balance_kpi`
- `biz_ads_aux_balance_personal_kpi`

## 5. 治理原则

- ODS 只保留原始结构，不塞业务口径
- 所有指标口径优先以大屏 SQL 和 `metric/指标计算原理.md` 为准
- 同一业务字段只保留一套命名，不再维护 `cli/web-upload` 双口径
- 输出物只包含当前有效模型和 ODS DDL，不保留旧目录壳子
