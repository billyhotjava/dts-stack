# 项目管理 v3 建模治理说明

## 1. 目标架构

```text
ODS -> STG -> DWD -> DWS -> ADS
```

| 层 | 材质化 | 职责 |
|----|--------|------|
| `ODS` | 现有物理表 | 原始接入层，只负责落地与追溯，不承载业务语义 |
| `STG` | `view` | 类型转换 + 占位符归 NULL + 字段改名 + 来源元数据 |
| `DWD` | `table` | 业务解释：别名归一化、dim 打标签、派生口径 |
| `DWS` | `table` | 主题汇总 |
| `ADS` | `table` | 看板/接口直接消费 |

## 2. STG 层宪法（强制）

**允许做**：
- `parse_numeric_safe` / `parse_date_safe` 类型转换
- `nullif_placeholder` 占位符归 NULL
- `btrim` 收敛空白
- 字段改名
- 来源元数据：`source_row_id` / `source_table` / `source_system` / `source_file` / `source_sheet_name` / `source_batch_id` / `source_row_num` / `imported_at`

**禁止做（一条不能出现）**：
- `CASE WHEN`
- `LIKE '%xxx%'` / `column IN ('a','b')` 分类映射
- `upper() + IN` 布尔判断
- `to_char(date, 'YYYY-MM')` 月份格式化
- `EXTRACT(YEAR/QUARTER FROM ...)`
- `split_part` 复合字符串解析
- `abs()` 或任何跨列/同列派生运算
- 派生布尔字段（`has_zero_plan` / `has_review_signal` 等）

一句话：**STG 从 ODS 读一行，出来的行与 ODS 一一对应，字段只做"同位变换"，不做"跨列变换"或"值翻译"。**

## 3. DWD 层职责

### 3.1 两类 dim 表

| 类型 | 命名 | 内容 |
|------|------|------|
| Canonical dim | `dim_*_v2` | code → label → is_* flags，业务分类**唯一真值源** |
| Alias dim | `dim_*_alias` | `alias_raw` → `canonical_code`，负责别名归一化 |

当前 PJM Canonical dim：
- `dim_completion_status_v2` / `dim_node_type_v2` / `dim_risk_level_v2`
- `dim_quality_status_v2` / `dim_quality_category_v2`
- `dim_change_category_v2` / `dim_signature_status_v2`
- `dim_risk_category_v2`

当前 PJM Alias dim：
- `dim_node_type_alias`（`一般` / `一般节点` → `一般节点` 等）
- `dim_risk_level_alias`（`高` / `高风险` → `高` 等）
- `dim_risk_category_alias`（`技术` / `技术风险` / `供应链` / `管理` / `资源` 归一化）
- `dim_risk_status_alias`（仅 `已释放`，未命中由 DWD 层 coalesce 到 `未释放`）
- `dim_quality_category_alias`（`外协外购` / `外协` 归一化）
- `dim_quality_status_alias`（`处理中` / `进行中` / `未闭环` 等归入 `未完成归零`）
- `dim_change_category_alias`（罗马数字 / 阿拉伯数字 / 汉字 → `I` / `II` / `III`）
- `dim_boolean_alias`（通用 Y/YES/是/已提交 → `是`）
- `dim_reform_status_alias`（`已落实整改` / `已完成` → `已落实整改` 等）
- `dim_signature_status_alias`（**上下文相关**：(context, alias_raw) → canonical code，context 取值 `I_II_no_review` / `I_II_with_review` / `III`）

### 3.2 biz_dwd 流水线模板（三段论）

```
stg
  → normalized  (LEFT JOIN alias dim → 归一化 *_raw 到 canonical code)
  → derived     (to_char 月份、EXTRACT 年份、日期相减、布尔派生、abs 等)
  → final       (LEFT JOIN canonical dim_*_v2 → 打 label 与 is_* flags)
```

## 4. 依赖规则

- `dwd/*` 禁止直接 `source(...)`
- `dws/*` 禁止直接 `source(...)` 或 `ref('stg_pm*')`
- `ads/*` 禁止直接 `source(...)` 或 `ref('stg_pm*')`
- 所有下游模型必须经由 `stg_pm__*` 进入语义链路
- DWS/ADS 只消费 `biz_dwd_*` 或 `biz_dws_*`

## 5. 命名规则

| 层 | 字段后缀 | 含义 |
|---|---------|------|
| STG | `xxx_raw` | ODS 原值（trim + nullif 后） |
| DWD | `xxx` | canonical code（归一化后） |
| DWD | `xxx_id` | dim 表主键 |
| DWD | `xxx_label` | dim 表 label |
| DWD | `is_xxx` | 来自 canonical dim 的布尔 flag |
| DWD | `xxx_month` / `xxx_year` | 从日期派生的时间分组键 |

## 6. STG 元数据字段

所有 STG 必须保留以下字段用于回溯：

- `source_row_id`（unique + not_null）
- `source_table`
- `source_system`
- `source_file`
- `source_sheet_name`
- `source_batch_id`
- `source_row_num`
- `imported_at`

## 7. 当前语义链路

### STG
- `stg_pm__project_subject_domain_v2`
- `stg_pm__quality_issue_v2`
- `stg_pm__tech_state_v2`
- `stg_pm__risk_info_v2`

### DWD
- 维度：`dim_*_v2`（8 张，见 3.1）
- 别名：`dim_*_alias`（10 张，见 3.1）
- 事实：`biz_dwd_project_node_v2` / `biz_dwd_quality_issue_v2` / `biz_dwd_tech_state_v2` / `biz_dwd_risk_info_v2`

### DWS
- `biz_dws_progress_monthly_v2`
- `biz_dws_quality_monthly_v2`
- `biz_dws_tech_state_monthly_v2`
- `biz_dws_risk_monthly_v2`

### ADS
- `biz_ads_progress_kpi_v2` / `biz_ads_progress_derived_v2`
- `biz_ads_quality_kpi_v2` / `biz_ads_quality_derived_v2`
- `biz_ads_tech_state_kpi_v2` / `biz_ads_tech_state_derived_v2`
- `biz_ads_risk_kpi_v2`
- `biz_ads_composite_derived_v2`

## 8. 治理原则

- ODS 保留"原样"和"可追溯"
- STG 负责"结构化"和"收敛"，**禁止引入业务语义**
- 所有业务分类 / 映射 / 派生口径由 DWD 承担，且必须以 dim 表为真值源
- 指标定义优先于 SQL 实现
- 血缘必须可解释到 "Excel 字段 → ODS 列 → STG *_raw → DWD canonical → DWD 派生 → DWS → ADS"
