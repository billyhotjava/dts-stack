# Finance 建模治理说明

## 1. 目标架构

财务域统一采用从 ODS 到 ADS 的单链路模型：

```text
ODS -> STG -> DWD -> DWS -> ADS
```

| 层 | 材质化 | 职责 |
|----|--------|------|
| `ODS` | 现有物理表 | 保留原始落地结构，只承担追溯与装载边界 |
| `STG` | `view` | 类型转换 + 占位符归 NULL + 字段改名 + 来源元数据 |
| `DWD` | `table` | 业务解释：别名归一化、dim 打标签、派生口径、补业务主键 |
| `DWS` | `table` | 主题汇总：沉淀大屏直接消费的聚合视角 |
| `ADS` | `table` | 看板 KPI：应用侧真正要消费的字段 |

## 2. STG 层宪法（强制）

**允许做**：
- `parse_numeric_safe` / `parse_date_safe` 类型转换
- `nullif_placeholder` 占位符归 NULL
- `btrim` 收敛空白
- 字段改名为业务语义名（例如 `金额1` → `total_cost`）
- 保留来源元数据：`source_row_id`、`source_table`

**禁止做（一条不能出现）**：
- `CASE WHEN`
- `LIKE 'xxxx%'` / `column IN ('a','b')` 分类映射
- `to_char(date, 'YYYY-MM')` 月份格式化
- `EXTRACT(YEAR/QUARTER FROM ...)` 年份/季度派生
- 复合字符串解析（`split_part` / 复杂 `substring`）
- `abs()` / `+` / `-` / `*` / `/` 跨列或同列派生
- 从数值派生文本标签（如 `balance > 0 → 'positive'`）
- 派生布尔字段（`has_contract` / `is_balance_row` 等）

一句话：**STG 从 ODS 读一行，出来的行与 ODS 一一对应，字段只做"同位变换"，不做"跨列变换"或"值翻译"。**

## 3. DWD 层职责

### 3.1 两类 dim 表

| 类型 | 命名 | 内容 |
|------|------|------|
| Canonical dim | `dim_*` | code → label → is_* flags，业务分类**唯一真值源** |
| Prefix / Suffix 映射 | `dim_*_code_prefix` / `dim_*_suffix` | 科目前缀或年度后缀 → canonical code |

当前 Finance dim 清单：
- `dim_balance_direction`（借方/贷方/零）
- `dim_expense_category`（8 类费用）
- `dim_own_fund_period_type`（年初/增加/使用/余额）
- `dim_personal_subject_category`（借款/薪酬/其他）
- `dim_expense_code_prefix`（`5001` → 原材料/设备 等）
- `dim_personal_subject_code_prefix`（`1122` → 借款 等）
- `dim_year_period_suffix`（`年初` → opening 等）

### 3.2 biz_dwd 流水线模板（三段论）

```
stg → normalized (prefix/suffix join 归一化) → derived (跨列派生) → final (canonical dim join 打标签)
```

## 4. 依赖规则

- `dwd/*` 禁止直接 `source(...)`
- `dws/*` 禁止直接 `source(...)` 或 `ref('stg_*')`
- `ads/*` 禁止直接 `source(...)` 或 `ref('stg_*')`
- 所有下游模型必须从 `stg_fin__*` 进入语义链路
- DWS/ADS 只消费 `biz_dwd_*` 或 `biz_dws_*`

## 5. 命名规则

| 层 | 字段后缀 | 含义 |
|---|---------|------|
| STG | `xxx_raw` | 原始清洗值（trim + nullif 后） |
| STG | `xxx` | 当没有别名问题时直接用语义名 |
| DWD | `xxx` | canonical code（归一化后） |
| DWD | `xxx_id` | dim 表主键 |
| DWD | `xxx_label` | dim 表 label |
| DWD | `is_xxx` | 来自 dim 的布尔 flag |

## 6. 当前语义链路

### STG
- `stg_fin__own_fund`
- `stg_fin__project_fund`
- `stg_fin__aux_balance`
- `stg_fin__aux_balance_personal`

### DWD
- 维度：`dim_balance_direction` / `dim_expense_category` / `dim_own_fund_period_type` / `dim_personal_subject_category`
- 映射：`dim_expense_code_prefix` / `dim_personal_subject_code_prefix` / `dim_year_period_suffix`
- 事实：`biz_dwd_own_fund` / `biz_dwd_project_fund` / `biz_dwd_aux_balance` / `biz_dwd_aux_balance_personal`

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

## 7. 治理原则

- ODS 只保留原始结构，不塞业务口径
- STG 只做"把数据变干净、让下游放心用"的层，不引入任何业务含义
- 所有业务分类/映射/派生口径由 DWD 承担，且必须以 dim 表为真值源
- 同一业务字段只保留一套命名
- ODS DDL 仅作为部署参考，放在 `ods_ddl/` 目录
