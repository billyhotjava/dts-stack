# 财务四张大屏指标核对口径

## 1. 适用范围

本文档按仓库里的四套财务大屏模板整理：

- `fin-auxiliary-balance`：辅助余额大屏
- `fin-own-fund`：自有资金表
- `fin-personal-balance`：辅助余额表_个人维度
- `fin-project-fund`：项目经费表

整理依据：

- 模板定义：`source/dts-platform-webapp/src/analytics/pages/screens/screenTemplates.ts`
- 财务插件渲染：`source/dts-platform-webapp/src/analytics/pages/screens/plugins/custom/financeShared.tsx`
- 财务数仓模型：`worklog/v2.2.3/s10/v4/finance/dbt_model/models/`

重要说明：

1. 这份文档对应的是当前代码里的模板口径，不是现场库里“某个已发布大屏实例”必然 100% 一致的口径。
2. 如果现场这四张大屏在创建后被二次编辑过，最终应以该大屏自身 `screen spec` 里的 `dataSource.sqlConfig.query` 为准。
3. 财务插件本身不做前端二次聚合：`kpi-card`、`ranking-list`、`summary-table`、`status-grid` 都是直接吃 SQL 返回结果。

## 2. 统一核对原则

### 2.1 数仓链路

四张财务大屏统一走下面这条链路：

```text
ODS -> STG -> DWD -> DWS -> ADS
```

含义：

- `STG`：只做清洗、空值/占位符归一、类型转换
- `DWD`：做业务解释和字段派生
- `DWS`：做主题汇总
- `ADS`：做大屏 KPI 直出字段

### 2.2 金额与百分比格式

- 金额格式：`FM999,999,999,990.00`
- 百分比格式：`FM990.00`
- 辅助余额 / 个人辅助余额页面主单位：`元`
- 自有资金 / 项目经费页面主单位：`万元`

### 2.3 筛选的真实生效范围

有些“筛选条”只是展示文案，不是真筛选。核对时一定要区分：

| 大屏 | 页面上显示的筛选条 | 实际有 SQL 绑定的变量 |
| --- | --- | --- |
| 辅助余额大屏 | 部门 / 费用类别 / 合同状态 | `filterDept`、`searchText` |
| 自有资金表 | 年度 / 基金类别 / 统计口径 | `selectedYear` |
| 辅助余额表_个人维度 | 部门 / 职工 / 科目 | `filterDept`、`filterEmployee`、`searchText` |
| 项目经费表 | 研制周期 / 研究室 / 重大项目 | 无，当前模板未绑定真实筛选变量 |

## 3. 基础表与 ADS 口径

这一章补的是“完整数据链”里最容易缺失的部分：`ADS` 本身怎么从 `DWS/DWD` 算出来。

### 3.0 四条完整链路总览

| 主题 | ODS | STG | DWD | DWS | ADS | 当前大屏实际主要取数层 |
| --- | --- | --- | --- | --- | --- | --- |
| 辅助余额 | `ods_finance_aux_balance` | `stg_fin__aux_balance` | `biz_dwd_aux_balance` | `biz_dws_aux_balance_by_dept` | `biz_ads_aux_balance_kpi` | `DWD` |
| 个人辅助余额 | `ods_finance_aux_balance_personal` | `stg_fin__aux_balance_personal` | `biz_dwd_aux_balance_personal` | `biz_dws_aux_balance_personal_by_dept` | `biz_ads_aux_balance_personal_kpi` | `DWD` |
| 自有资金 | `ods_finance_own_fund` | `stg_fin__own_fund` | `biz_dwd_own_fund` | `biz_dws_own_fund_yearly` | `biz_ads_own_fund_kpi` | `ADS + DWS` |
| 项目经费 | `ods_finance_project_fund` | `stg_fin__project_fund` | `biz_dwd_project_fund` | `biz_dws_project_fund_summary` | `biz_ads_project_fund_kpi` | `ADS + DWD` |

### 3.1 `biz_dwd_aux_balance`

来源：`stg_fin__aux_balance`

关键派生：

- `subject_code_prefix = subject_code` 前 4 位
- `expense_category`：按 `subject_code_prefix` 映射费用类别；映射不到则为 `其他`
- `contract_name_norm = contract_name`
- `has_contract = contract_name IS NOT NULL`
- `abs_balance = ABS(balance)`
- `balance_sign`：
  - `positive`：`balance > 0`
  - `negative`：`balance < 0`
  - `zero`：`balance = 0`

### 3.2 `biz_dwd_aux_balance_personal`

来源：`stg_fin__aux_balance_personal`

关键派生：

- `subject_code_prefix = subject_code` 前 4 位
- `subject_category`：按 `subject_code_prefix` 映射个人往来科目类别
- `abs_balance = ABS(balance)`
- `balance_direction`：
  - `debit`：`balance > 0`
  - `credit`：`balance < 0`
  - `zero`：`balance = 0`

### 3.3 `biz_dws_own_fund_yearly`

来源：`biz_dwd_own_fund`

关键派生：

- `opening_amount = SUM(amount WHERE fund_source_code = '年初')`
- `increase_amount = SUM(amount WHERE fund_source_code = '预计增加')`
- `usage_amount = SUM(amount WHERE fund_source_code = '预计使用')`
- `derived_balance = opening_amount + increase_amount - usage_amount`
- `usage_rate = usage_amount / (opening_amount + increase_amount) * 100`
- `net_change_rate = (derived_balance - opening_amount) / opening_amount * 100`
- `year_over_year_growth_rate = (本年 derived_balance - 上年 derived_balance) / 上年 derived_balance * 100`
- 同时产出一行 `fund_category_code = '全部'` 的年度汇总

### 3.4 `biz_dwd_project_fund`

来源：`stg_fin__project_fund`

关键派生：

- `total_spent = direct_spent + indirect_spent`
- `remaining_fund = total_fund - total_spent`
- `total_rate = total_spent / total_fund * 100`
- `indirect_rate = indirect_spent / reserve_indirect * 100`
- `received_rate = received_fund / total_fund * 100`
- `is_direct_rate_over_100 = direct_rate > 100`
- `is_total_rate_over_100 = total_rate > 100`

### 3.5 `biz_ads_aux_balance_kpi`

来源：`biz_dwd_aux_balance`

SQL 原理很直接，这张表就是辅助余额的“全局汇总快照”：

- `total_balance = SUM(balance)`
- `total_abs_balance = SUM(abs_balance)`
- `subject_count = COUNT(DISTINCT subject_code)`
- `contract_count = COUNT(DISTINCT contract_name_norm)`
- `dept_count = COUNT(DISTINCT dept_name)`
- `record_count = COUNT(*)`

可以理解成：

```text
biz_ads_aux_balance_kpi
  = 对 biz_dwd_aux_balance 做一次无筛选全表聚合
```

当前模板里，这张 ADS 表没有被 `fin-auxiliary-balance` 直接使用；当前大屏是直接查 `biz_dwd_aux_balance` 做现算。

#### 3.5.1 字段级血缘

| ADS 字段 | 直接来源 | 计算方式 | 备注 |
| --- | --- | --- | --- |
| `total_balance` | `biz_dwd_aux_balance.balance` | `SUM(balance)` | 余额代数和 |
| `total_abs_balance` | `biz_dwd_aux_balance.abs_balance` | `SUM(abs_balance)` | 绝对值总额 |
| `subject_count` | `biz_dwd_aux_balance.subject_code` | `COUNT(DISTINCT subject_code)` | 科目去重数 |
| `contract_count` | `biz_dwd_aux_balance.contract_name_norm` | `COUNT(DISTINCT contract_name_norm)` | `NULL` 不计数 |
| `dept_count` | `biz_dwd_aux_balance.dept_name` | `COUNT(DISTINCT dept_name)` | 空部门已在 DWD 归一到“未分配部门” |
| `record_count` | `biz_dwd_aux_balance.*` | `COUNT(*)` | 明细行数 |

### 3.6 `biz_ads_aux_balance_personal_kpi`

来源：`biz_dwd_aux_balance_personal`

SQL 原理：

- `net_balance = SUM(balance)`
- `debit_total = SUM(balance WHERE balance_direction = 'debit')`
- `credit_total = SUM(abs_balance WHERE balance_direction = 'credit')`
- `employee_count = COUNT(DISTINCT employee_name)`
- `subject_count = COUNT(DISTINCT subject_code)`
- `dept_count = COUNT(DISTINCT employee_dept)`
- `record_count = COUNT(*)`

可以理解成：

```text
biz_ads_aux_balance_personal_kpi
  = 对 biz_dwd_aux_balance_personal 做一次无筛选全表聚合
```

当前模板里，这张 ADS 表也没有被 `fin-personal-balance` 直接使用；当前大屏是直接查 `biz_dwd_aux_balance_personal` 做现算。

#### 3.6.1 字段级血缘

| ADS 字段 | 直接来源 | 计算方式 | 备注 |
| --- | --- | --- | --- |
| `net_balance` | `biz_dwd_aux_balance_personal.balance` | `SUM(balance)` | 借贷相抵后的净额 |
| `debit_total` | `biz_dwd_aux_balance_personal.balance` | `SUM(balance WHERE balance_direction = 'debit')` | 只累计正数余额 |
| `credit_total` | `biz_dwd_aux_balance_personal.abs_balance` | `SUM(abs_balance WHERE balance_direction = 'credit')` | 贷方转绝对值后累计 |
| `employee_count` | `biz_dwd_aux_balance_personal.employee_name` | `COUNT(DISTINCT employee_name)` | 职工去重数 |
| `subject_count` | `biz_dwd_aux_balance_personal.subject_code` | `COUNT(DISTINCT subject_code)` | 科目去重数 |
| `dept_count` | `biz_dwd_aux_balance_personal.employee_dept` | `COUNT(DISTINCT employee_dept)` | 部门去重数 |
| `record_count` | `biz_dwd_aux_balance_personal.*` | `COUNT(*)` | 明细行数 |

### 3.7 `biz_ads_own_fund_kpi`

来源：`biz_dws_own_fund_yearly`

这张表不是重新发明一套公式，而是把 `DWS` 已经算好的年度基金指标“平铺成看板直出字段”，并额外补几个状态字段。

继承自 `biz_dws_own_fund_yearly` 的核心字段：

- `opening_amount`
- `increase_amount`
- `usage_amount`
- `derived_balance AS year_end_balance`
- `usage_rate`
- `net_change_rate`
- `year_over_year_growth_rate`
- `source_count`
- `record_count`
- `is_complete_sources`

ADS 层新增/转译字段：

- `year_end_balance = derived_balance`
- `usage_rate_level`
  - `danger`：`usage_rate > 95`
  - `warning`：`usage_rate > 80 AND usage_rate <= 95`
  - `healthy`：`usage_rate <= 80`
- `net_change_direction`
  - `positive`：`net_change_rate > 0`
  - `negative`：`net_change_rate < 0`
  - `flat`：`net_change_rate = 0`

所以 `biz_ads_own_fund_kpi` 的完整计算链是：

```text
ods_finance_own_fund
-> stg_fin__own_fund
-> biz_dwd_own_fund
-> biz_dws_own_fund_yearly
   opening_amount       = SUM(年初)
   increase_amount      = SUM(预计增加)
   usage_amount         = SUM(预计使用)
   derived_balance      = opening_amount + increase_amount - usage_amount
   usage_rate           = usage_amount / (opening_amount + increase_amount) * 100
   net_change_rate      = (derived_balance - opening_amount) / opening_amount * 100
   year_over_year_growth_rate
                        = (本年 derived_balance - 上年 derived_balance) / 上年 derived_balance * 100
-> biz_ads_own_fund_kpi
   year_end_balance     = derived_balance
   usage_rate_level     = CASE usage_rate ...
   net_change_direction = CASE net_change_rate ...
```

这张 ADS 表是 `fin-own-fund` 的核心来源，尤其顶部 KPI、趋势图、类别对比图、结构图都直接或间接依赖它。

#### 3.7.1 先看 `opening_amount` 的完整来路

以你关心的 `opening_amount` 为例，完整链路是这样的：

```text
ods_finance_own_fund.year_num
ods_finance_own_fund.fund_source
ods_finance_own_fund.fund_category
ods_finance_own_fund.amount
    ->
stg_fin__own_fund.year_num / fund_source / fund_category / amount
    ->
biz_dwd_own_fund.year_num / fund_source_code / fund_category_code / amount
    ->
biz_dws_own_fund_yearly.opening_amount
    = SUM(amount WHERE fund_source_code = '年初')
      GROUP BY year_num, fund_category_code
    ->
year_totals 行（fund_category_code = '全部'）
    = SUM(各基金类别 opening_amount)
    ->
biz_ads_own_fund_kpi.opening_amount
    = 直接透传 biz_dws_own_fund_yearly.opening_amount
    ->
大屏组件 of-kpi-open
```

如果按 `selectedYear = 2026`、`fund_category_code = '全部'` 来看，实际等价于：

```sql
SELECT SUM(amount) AS opening_amount
FROM public.biz_dwd_own_fund
WHERE year_num = 2026
  AND fund_source_code = '年初';
```

之所以这里不再加 `fund_category_code = '全部'`，是因为“全部基金”这一行不是 ODS/DWD 里天然存在的，而是 `biz_dws_own_fund_yearly.year_totals` 这一步把各基金类别加总后生成的。

也就是说：

1. `事业基金`、`职工福利基金`、`安全生产基金` 会各自产生一行 `opening_amount`。
2. 然后 DWS 再额外产出一行 `fund_category_code = '全部'`。
3. ADS 直接把这行复制出来。
4. 大屏 `of-kpi-open` 再按 `year_num = selectedYear AND fund_category_code = '全部'` 取值。

#### 3.7.2 `biz_ads_own_fund_kpi` 字段级血缘

| ADS 字段 | 直接来源 | 计算方式 | 层级说明 | 当前大屏使用点 |
| --- | --- | --- | --- | --- |
| `year_num` | `biz_dws_own_fund_yearly.year_num` | 直接透传 | 分组键 | 全屏 |
| `fund_category_code` | `biz_dws_own_fund_yearly.fund_category_code` | 直接透传 | 分组键 | 全屏 |
| `fund_category_label` | `biz_dws_own_fund_yearly.fund_category_label` | 直接透传 | 展示标签 | 类别图、汇总表 |
| `opening_amount` | `biz_dws_own_fund_yearly.opening_amount` | `SUM(amount WHERE fund_source_code='年初')` | DWS 聚合后 ADS 透传 | `of-kpi-open`、`of-chart-category`、`of-summary` |
| `increase_amount` | `biz_dws_own_fund_yearly.increase_amount` | `SUM(amount WHERE fund_source_code='预计增加')` | DWS 聚合后 ADS 透传 | `of-kpi-increase`、`of-chart-category`、`of-summary` |
| `usage_amount` | `biz_dws_own_fund_yearly.usage_amount` | `SUM(amount WHERE fund_source_code='预计使用')` | DWS 聚合后 ADS 透传 | `of-kpi-use`、`of-chart-trend`、`of-chart-category`、`of-summary` |
| `year_end_balance` | `biz_dws_own_fund_yearly.derived_balance` | `opening_amount + increase_amount - usage_amount` | ADS 仅改名，不重算 | `of-kpi-balance`、`of-chart-trend`、`of-chart-category`、`of-chart-structure` |
| `usage_rate` | `biz_dws_own_fund_yearly.usage_rate` | `usage_amount / (opening_amount + increase_amount) * 100` | DWS 算好 | `of-status` |
| `net_change_rate` | `biz_dws_own_fund_yearly.net_change_rate` | `(derived_balance - opening_amount) / opening_amount * 100` | DWS 算好 | 当前模板未直接展示 |
| `year_over_year_growth_rate` | `biz_dws_own_fund_yearly.year_over_year_growth_rate` | `(本年 derived_balance - 上年 derived_balance) / 上年 derived_balance * 100` | DWS 用窗口函数 `lag` 算好 | `of-kpi-yoy`、`of-summary` |
| `source_count` | `biz_dws_own_fund_yearly.source_count` | `COUNT(DISTINCT fund_source_code)` | DWS 算好 | `of-status` |
| `record_count` | `biz_dws_own_fund_yearly.record_count` | `COUNT(*)` | DWS 算好 | 当前模板未直接展示 |
| `is_complete_sources` | `biz_dws_own_fund_yearly.is_complete_sources` | `source_count = 3` | DWS 算好 | `of-status` |
| `usage_rate_level` | `biz_ads_own_fund_kpi.usage_rate_level` | `CASE WHEN usage_rate > 95 THEN 'danger' WHEN usage_rate > 80 THEN 'warning' ELSE 'healthy' END` | ADS 新增状态字段 | 当前模板未直接展示 |
| `net_change_direction` | `biz_ads_own_fund_kpi.net_change_direction` | `CASE WHEN net_change_rate > 0 THEN 'positive' WHEN net_change_rate < 0 THEN 'negative' ELSE 'flat' END` | ADS 新增状态字段 | 当前模板未直接展示 |

#### 3.7.3 `biz_dws_own_fund_yearly` 中“全部基金”怎么来的

这是自有资金最容易误解的点。

`biz_dws_own_fund_yearly` 先做两步：

1. `pivot_by_category`
   - 按 `year_num + fund_category_code` 聚合
   - 每个基金类别各出一行
2. `year_totals`
   - 再按 `year_num` 汇总一次
   - 额外生成：
     - `fund_category_code = '全部'`
     - `fund_category_label = '全部基金'`

所以：

- 类别行是真实分类聚合
- “全部基金”行是 DWS 二次汇总生成的总计行

#### 3.7.4 自有资金字段核对 SQL

如果你要单独核 `opening_amount`，最直接是跑这三层：

1. 先核 DWD 明细：

```sql
SELECT year_num, fund_category_code, fund_source_code, amount
FROM public.biz_dwd_own_fund
WHERE year_num = 2026
ORDER BY fund_category_sort, fund_source_sort;
```

2. 再核 DWS 分类行：

```sql
SELECT year_num, fund_category_code, opening_amount, increase_amount, usage_amount, derived_balance
FROM public.biz_dws_own_fund_yearly
WHERE year_num = 2026
ORDER BY fund_category_sort;
```

3. 最后核 ADS：

```sql
SELECT year_num, fund_category_code, opening_amount, increase_amount, usage_amount, year_end_balance
FROM public.biz_ads_own_fund_kpi
WHERE year_num = 2026
ORDER BY fund_category_sort;
```

你会看到：

- `ADS.opening_amount = DWS.opening_amount`
- `DWS.opening_amount = DWD 中 fund_source_code='年初' 的金额汇总`

### 3.8 `biz_ads_project_fund_kpi`

来源：`biz_dws_project_fund_summary`

`summary_scope = 'all_projects' AND scope_key = 'all'` 这一行是项目经费大屏 KPI 的总口径。关键字段：

- `sum_total_fund`
- `sum_total_spent`
- `sum_remaining_fund`：只汇总 `remaining_fund > 0` 的项目
- `sum_overspend_amount`：只汇总 `remaining_fund < 0` 的绝对值
- `sum_received_fund`
- `sum_receivable_fund`
- `overall_direct_rate = sum_direct_spent / sum_direct_ctrl * 100`
- `overall_total_rate = sum_total_spent / sum_total_fund * 100`

这张表的完整计算链是：

```text
ods_finance_project_fund
-> stg_fin__project_fund
-> biz_dwd_project_fund
   total_spent      = direct_spent + indirect_spent
   remaining_fund   = total_fund - total_spent
   total_rate       = total_spent / total_fund * 100
   indirect_rate    = indirect_spent / reserve_indirect * 100
   received_rate    = received_fund / total_fund * 100
-> biz_dws_project_fund_summary
   sum_total_fund        = SUM(total_fund)
   sum_total_spent       = SUM(total_spent)
   sum_remaining_fund    = SUM(remaining_fund WHERE remaining_fund > 0)
   sum_overspend_amount  = SUM(ABS(remaining_fund) WHERE remaining_fund < 0)
   sum_received_fund     = SUM(received_fund)
   sum_receivable_fund   = SUM(receivable_fund)
-> biz_ads_project_fund_kpi
   overall_direct_rate   = sum_direct_spent / sum_direct_ctrl * 100
   overall_total_rate    = sum_total_spent / sum_total_fund * 100
   overall_indirect_rate = sum_indirect_spent / sum_reserve_indirect * 100
   overall_received_rate = sum_received_fund / sum_total_fund * 100
   overall_receivable_rate
                        = sum_receivable_fund / sum_total_fund * 100
```

#### 3.8.1 `biz_ads_project_fund_kpi` 字段级血缘

| ADS 字段 | 直接来源 | 计算方式 | 当前大屏使用点 |
| --- | --- | --- | --- |
| `sum_total_fund` | `biz_dws_project_fund_summary.sum_total_fund` | `SUM(total_fund)` | `pf-kpi-budget` |
| `sum_total_spent` | `biz_dws_project_fund_summary.sum_total_spent` | `SUM(total_spent)` | `pf-kpi-spent` |
| `sum_remaining_fund` | `biz_dws_project_fund_summary.sum_remaining_fund` | `SUM(remaining_fund WHERE remaining_fund > 0)` | `pf-kpi-remaining` |
| `sum_overspend_amount` | `biz_dws_project_fund_summary.sum_overspend_amount` | `SUM(ABS(remaining_fund) WHERE remaining_fund < 0)` | 当前模板未直接取此字段，而是自己在 `pf-kpi-overspend` 现算 |
| `sum_received_fund` | `biz_dws_project_fund_summary.sum_received_fund` | `SUM(received_fund)` | `pf-kpi-received` |
| `sum_receivable_fund` | `biz_dws_project_fund_summary.sum_receivable_fund` | `SUM(receivable_fund)` | `pf-kpi-receivable` |
| `overall_direct_rate` | ADS 自身派生 | `sum_direct_spent / sum_direct_ctrl * 100` | `pf-status` |
| `overall_total_rate` | ADS 自身派生 | `sum_total_spent / sum_total_fund * 100` | `pf-status` |
| `overall_indirect_rate` | ADS 自身派生 | `sum_indirect_spent / sum_reserve_indirect * 100` | 当前模板未直接展示 |
| `overall_received_rate` | ADS 自身派生 | `sum_received_fund / sum_total_fund * 100` | 当前模板未直接展示 |
| `overall_receivable_rate` | ADS 自身派生 | `sum_receivable_fund / sum_total_fund * 100` | 当前模板未直接展示，模板自己又算了一次“待收占比” |
| `project_count` | `biz_dws_project_fund_summary.project_count` | `COUNT(DISTINCT project_id)` | 当前模板未直接展示 |
| `major_project_count` | `biz_dws_project_fund_summary.major_project_count` | `COUNT(DISTINCT project_id WHERE is_major_project = TRUE)` | 当前模板未直接展示 |
| `completed_project_count` | `biz_dws_project_fund_summary.completed_project_count` | `COUNT(DISTINCT project_id WHERE project_is_completed = TRUE)` | 当前模板未直接展示 |

#### 3.8.2 项目经费里一个容易踩坑的点

`pf-kpi-overspend` 没有直接取 `biz_ads_project_fund_kpi.sum_overspend_amount`，而是自己查了：

```sql
SELECT COALESCE(SUM(GREATEST(-remaining_fund, 0)), 0) AS value
FROM public.biz_dwd_project_fund
```

理论上它和 `ADS.sum_overspend_amount` 应该一致，但核数时你要知道：

- 这个组件当前走的是 `DWD` 现算
- 不是直接读 `ADS`

### 3.9 维度映射表在链路中的作用

这部分不是大屏直接查的表，但会直接影响大屏结果分组是否正确。

#### 3.9.1 自有资金维度

- `dim_fund_source`
  - `年初`
  - `预计增加`
  - `预计使用`
- `dim_fund_category`
  - `事业基金`
  - `职工福利基金`
  - `安全生产基金`

它们决定了 `biz_dwd_own_fund` 和后续 `DWS/ADS` 的分类轴。

#### 3.9.2 项目经费状态维度

- `dim_project_status`
  - `在研`
  - `支出待处理`
  - `已完成待收款`
  - `已完成审计`

它决定了 `biz_dws_project_fund_summary` 里 `by_status` 这类 scope 的分组规则。

#### 3.9.3 辅助余额前缀映射

- `dim_expense_code_prefix`
  - `5001 -> 原材料/设备`
  - `5101 -> 外协/服务`
  - `5201 -> 折旧`
  - `5301 -> 检测试验`
  - `5401 -> 设计咨询`
  - `5501 -> 租赁`
  - `5601 -> 培训`

它决定了 `biz_dwd_aux_balance.expense_category / expense_category_label`，从而影响费用结构饼图和状态卡。

#### 3.9.4 个人辅助余额前缀映射

- `dim_personal_subject_code_prefix`
  - `1122 -> 其他应收-借款`
  - `2211 -> 应付职工薪酬`

它决定了 `biz_dwd_aux_balance_personal.subject_category`，从而影响个人往来口径解释。

## 4. 大屏一：辅助余额大屏

模板 ID：`fin-auxiliary-balance`

### 4.0 完整数据链

```text
ods_finance_aux_balance
-> stg_fin__aux_balance
-> biz_dwd_aux_balance
-> biz_dws_aux_balance_by_dept
-> biz_ads_aux_balance_kpi
-> fin-auxiliary-balance
```

说明：

- 当前模板实际展示时，主要直接查询 `biz_dwd_aux_balance`
- `biz_dws_aux_balance_by_dept` / `biz_ads_aux_balance_kpi` 是这条链路里的汇总资产，但当前模板没有直接引用

### 4.1 实际筛选

```sql
WHERE 1 = 1
  AND dept_name = :filterDept              -- 仅当 filterDept 非空时生效
  AND (
    subject_code ILIKE '%' || :searchText || '%'
    OR subject_name ILIKE '%' || :searchText || '%'
    OR COALESCE(contract_name, '') ILIKE '%' || :searchText || '%'
    OR expense_category_label ILIKE '%' || :searchText || '%'
  )                                        -- 仅当 searchText 非空时生效
```

### 4.2 组件口径

| 组件 ID | 屏上名称 | 口径 / 公式 | 来源 |
| --- | --- | --- | --- |
| `ab-kpi-total` | 余额合计 | `SUM(balance)` | `biz_dwd_aux_balance` |
| `ab-kpi-subject` | 科目数量 | `COUNT(DISTINCT subject_code)` | `biz_dwd_aux_balance` |
| `ab-kpi-contract` | 合同数量 | `COUNT(DISTINCT contract_name_norm)`，且 `has_contract = TRUE` | `biz_dwd_aux_balance` |
| `ab-kpi-dept` | 涉及部门 | `COUNT(DISTINCT dept_name)` | `biz_dwd_aux_balance` |
| `ab-chart-dept` | 部门余额分布 | 每个柱 = `SUM(balance) BY dept_name`，按 `dept_balance DESC` 取前 8 | `biz_dwd_aux_balance` |
| `ab-chart-structure` | 费用结构分布 | 每个扇区 = `SUM(balance) BY expense_category_label` | `biz_dwd_aux_balance` |
| `ab-ranking` | 合同余额 TOP 5 | 每条 = `SUM(balance) BY contract_name_norm, dept_name`，按余额降序前 5 | `biz_dwd_aux_balance` |
| `ab-summary` | 辅助余额明细 | 每行 = 一条明细记录；按 `balance DESC, subject_code` 排序取前 8 | `biz_dwd_aux_balance` |

### 4.3 状态卡口径

`ab-status` 一共 4 张卡：

| 卡片标题 | 取值逻辑 | 说明 |
| --- | --- | --- |
| 最大余额部门 | `SUM(balance) BY dept_name` 后取第 1 名 | 提示文案展示该部门余额 |
| 最大合同 | `SUM(balance) BY contract_name_norm, dept_name`，仅 `has_contract = TRUE`，取第 1 名 | 提示文案展示 `部门 / 金额` |
| 无合同科目 | `COUNT(DISTINCT subject_code WHERE has_contract = FALSE)` | 统计无合同挂账科目数 |
| 主要费用类别 | `SUM(balance) BY expense_category_label` 后取第 1 名 | 提示文案展示该类别金额 |

### 4.4 明细表每列含义

`ab-summary` 表头与列值一一对应：

- `科目编号` = `subject_code`
- `科目名称` = `subject_name`
- `部门名称` = `dept_name`
- `合同名称` = `COALESCE(contract_name_norm, '无合同')`
- `余额(元)` = `TO_CHAR(balance, 金额格式)`

### 4.5 建议核对 SQL

```sql
WITH base AS (
  SELECT *
  FROM public.biz_dwd_aux_balance
  WHERE 1 = 1
    -- AND dept_name = :filterDept
    -- AND (...)
)
SELECT COALESCE(SUM(balance), 0) AS total_balance FROM base;
SELECT COUNT(DISTINCT subject_code) AS subject_count FROM base;
SELECT COUNT(DISTINCT contract_name_norm) AS contract_count FROM base WHERE has_contract = TRUE;
SELECT COUNT(DISTINCT dept_name) AS dept_count FROM base;
SELECT dept_name, SUM(balance) AS dept_balance FROM base GROUP BY dept_name ORDER BY dept_balance DESC LIMIT 8;
SELECT expense_category_label, SUM(balance) AS category_balance FROM base GROUP BY expense_category_label ORDER BY category_balance DESC;
```

## 5. 大屏二：自有资金表

模板 ID：`fin-own-fund`

### 5.0 完整数据链

```text
ods_finance_own_fund
-> stg_fin__own_fund
-> biz_dwd_own_fund
-> biz_dws_own_fund_yearly
-> biz_ads_own_fund_kpi
-> fin-own-fund
```

说明：

- 当前模板的 KPI 和图表主要查 `biz_ads_own_fund_kpi`
- 汇总表 `of-summary` 直接查 `biz_dws_own_fund_yearly`
- 所以这张屏是四张里最标准的 `DWS -> ADS -> 大屏` 用法

### 5.1 实际筛选

只有 `selectedYear` 真正进入 SQL。

```sql
WHERE year_num = CAST(:selectedYear AS INTEGER)
```

页面上显示的“基金类别: 全部”“统计口径: 年末余额 / 同比”当前只是说明文案，不参与 SQL 条件。

### 5.2 组件口径

| 组件 ID | 屏上名称 | 口径 / 公式 | 来源 |
| --- | --- | --- | --- |
| `of-kpi-open` | 年初余额 | `opening_amount`，取 `fund_category_code = '全部'` 且 `year_num = selectedYear` | `biz_ads_own_fund_kpi` |
| `of-kpi-increase` | 本年预计增加 | `increase_amount`，同上 | `biz_ads_own_fund_kpi` |
| `of-kpi-use` | 本年预计使用 | `usage_amount`，同上 | `biz_ads_own_fund_kpi` |
| `of-kpi-balance` | 年末余额 | `year_end_balance = opening_amount + increase_amount - usage_amount` | `biz_ads_own_fund_kpi` |
| `of-kpi-yoy` | 同比增长率 | `year_over_year_growth_rate` | `biz_ads_own_fund_kpi` |
| `of-chart-trend` | 年度余额趋势 | 每个点 = `year_end_balance / usage_amount BY year_num`，只取 `fund_category_code = '全部'` | `biz_ads_own_fund_kpi` |
| `of-chart-category` | 基金类别对比 | 每个类别一组柱：`opening_amount / increase_amount / usage_amount / year_end_balance` | `biz_ads_own_fund_kpi` |
| `of-chart-structure` | 年末余额构成 | 每个扇区 = `year_end_balance BY fund_category_label` | `biz_ads_own_fund_kpi` |
| `of-summary` | 年度与类别资金汇总 | 展示“上年全部基金”1 行 + “本年各类别 + 本年全部基金”4 行 | `biz_dws_own_fund_yearly` |

### 5.3 状态卡口径

`of-status` 一共 4 张卡：

| 卡片标题 | 取值逻辑 | 说明 |
| --- | --- | --- |
| 数据完整性 | `is_complete_sources` | 只有 `source_count = 3` 才显示“完整” |
| 净增加额 | `year_end_balance - opening_amount` | 反映本年余额净变化 |
| 使用率 | `usage_rate = usage_amount / (opening_amount + increase_amount) * 100` | 总量口径 |
| 主体基金 | 当年 `year_end_balance` 最大的基金类别 | 只在非“全部基金”类别里比较 |

### 5.4 汇总表每列含义

`of-summary` 表头与列值：

- `年度` = `year_num`
- `基金类别` = `fund_category_label`
- `年初` = `opening_amount`
- `增加` = `increase_amount`
- `使用` = `usage_amount`
- `年末` = `derived_balance`
- `同比` = `year_over_year_growth_rate`

注意：

1. 汇总表第一行是“上一年度全部基金”，不是当前年度分类之一。
2. 当前年度最后一行仍会出现“全部基金”总计。

### 5.5 建议核对 SQL

```sql
SELECT *
FROM public.biz_ads_own_fund_kpi
WHERE year_num = :selectedYear
ORDER BY fund_category_sort;

SELECT *
FROM public.biz_dws_own_fund_yearly
WHERE year_num IN (:selectedYear, :selectedYear - 1)
ORDER BY year_num, fund_category_sort;
```

## 6. 大屏三：辅助余额表_个人维度

模板 ID：`fin-personal-balance`

### 6.0 完整数据链

```text
ods_finance_aux_balance_personal
-> stg_fin__aux_balance_personal
-> biz_dwd_aux_balance_personal
-> biz_dws_aux_balance_personal_by_dept
-> biz_ads_aux_balance_personal_kpi
-> fin-personal-balance
```

说明：

- 当前模板实际展示时，主要直接查询 `biz_dwd_aux_balance_personal`
- `DWS/ADS` 已经具备，但当前模板没有直接吃这两层

### 6.1 实际筛选

```sql
WHERE 1 = 1
  AND employee_dept = :filterDept           -- 仅当 filterDept 非空时生效
  AND employee_name = :filterEmployee       -- 仅当 filterEmployee 非空时生效
  AND (
    employee_name ILIKE '%' || :searchText || '%'
    OR employee_dept ILIKE '%' || :searchText || '%'
    OR subject_name ILIKE '%' || :searchText || '%'
  )                                         -- 仅当 searchText 非空时生效
```

页面上展示的“科目: 借款 / 应付薪酬”当前是说明文案，不是真筛选。

### 6.2 组件口径

| 组件 ID | 屏上名称 | 口径 / 公式 | 来源 |
| --- | --- | --- | --- |
| `pb-kpi-net` | 净余额 | `SUM(balance)` | `biz_dwd_aux_balance_personal` |
| `pb-kpi-debit` | 借方余额 | `SUM(balance WHERE balance > 0)` | `biz_dwd_aux_balance_personal` |
| `pb-kpi-credit` | 贷方余额 | `SUM(ABS(balance) WHERE balance < 0)` | `biz_dwd_aux_balance_personal` |
| `pb-kpi-employee` | 涉及职工 | `COUNT(DISTINCT employee_name)` | `biz_dwd_aux_balance_personal` |
| `pb-kpi-dept` | 涉及部门 | `COUNT(DISTINCT employee_dept)` | `biz_dwd_aux_balance_personal` |
| `pb-chart-employee` | 职工净余额分布 | 每个柱 = `SUM(balance) BY employee_name`，按 `net_balance ASC` 取前 8 | `biz_dwd_aux_balance_personal` |
| `pb-chart-subject` | 借贷结构 | 两个扇区：`借方余额 = SUM(balance > 0)`；`贷方余额 = SUM(ABS(balance) WHERE balance < 0)` | `biz_dwd_aux_balance_personal` |
| `pb-ranking` | 贷方净额 TOP 5 | 每条 = `SUM(balance) BY employee_name, employee_dept`，仅取 `< 0`，按 `net_balance ASC` 前 5 | `biz_dwd_aux_balance_personal` |
| `pb-summary` | 个人借贷明细 | 每行 = 某职工在某部门的借/贷/净额汇总，按 `ABS(net_balance) DESC` 取前 8 | `biz_dwd_aux_balance_personal` |

### 6.3 状态卡口径

`pb-status` 一共 4 张卡：

| 卡片标题 | 取值逻辑 | 说明 |
| --- | --- | --- |
| 净额方向 | `SUM(balance) < 0` 则显示“贷方净额”，否则“借方净额” | 提示文案展示净额金额 |
| 重点部门 | `SUM(balance) BY employee_dept`，按 `ABS(net_balance)` 最大取 1 个部门 | 看绝对影响最大部门 |
| 最大单人 | `SUM(balance) BY employee_name, employee_dept`，按 `ABS(net_balance)` 最大取 1 人 | 看绝对值最大个人 |
| 借方集中 | `SUM(balance WHERE balance > 0) BY employee_dept`，按借方合计最高取 1 个部门 | 看借方资金集中部门 |

### 6.4 明细表每列含义

`pb-summary` 表头与列值：

- `职工` = `employee_name`
- `部门` = `employee_dept`
- `借方余额(元)` = `SUM(balance WHERE balance > 0)`
- `贷方余额(元)` = `SUM(ABS(balance) WHERE balance < 0)`
- `净余额(元)` = `SUM(balance)`
- `财务关注`：
  - `net_balance <= -10000`：`贷方净额较大`
  - `-10000 < net_balance < 0`：`贷方略高`
  - `net_balance >= 10000`：`借方净额较大`
  - 其他：`基本平衡`

### 6.5 建议核对 SQL

```sql
WITH employee_balance AS (
  SELECT
    employee_name,
    employee_dept,
    SUM(CASE WHEN balance > 0 THEN balance ELSE 0 END) AS debit_total,
    SUM(CASE WHEN balance < 0 THEN ABS(balance) ELSE 0 END) AS credit_total,
    SUM(balance) AS net_balance
  FROM public.biz_dwd_aux_balance_personal
  WHERE 1 = 1
    -- AND employee_dept = :filterDept
    -- AND employee_name = :filterEmployee
    -- AND (...)
  GROUP BY employee_name, employee_dept
)
SELECT * FROM employee_balance ORDER BY ABS(net_balance) DESC;
```

## 7. 大屏四：项目经费表

模板 ID：`fin-project-fund`

### 7.0 完整数据链

```text
ods_finance_project_fund
-> stg_fin__project_fund
-> biz_dwd_project_fund
-> biz_dws_project_fund_summary
-> biz_ads_project_fund_kpi
-> fin-project-fund
```

说明：

- 当前模板顶部 KPI 和状态卡主要查 `biz_ads_project_fund_kpi`
- 图表、明细、排行主要查 `biz_dwd_project_fund`
- 所以它是 `ADS + DWD` 混合用法

### 7.1 实际筛选

当前模板没有任何真实 SQL 参数绑定。页面上显示的：

- `研制周期: 全部`
- `研究室: 全部`
- `重大项目: 全部`

都只是静态说明文案。

### 7.2 组件口径

| 组件 ID | 屏上名称 | 口径 / 公式 | 来源 |
| --- | --- | --- | --- |
| `pf-kpi-budget` | 总经费 | `sum_total_fund`，取 `summary_scope='all_projects' AND scope_key='all'` | `biz_ads_project_fund_kpi` |
| `pf-kpi-spent` | 总支出 | `sum_total_spent` | `biz_ads_project_fund_kpi` |
| `pf-kpi-remaining` | 剩余经费 | `sum_remaining_fund`，只累计 `remaining_fund > 0` 的项目 | `biz_ads_project_fund_kpi` |
| `pf-kpi-overspend` | 超支金额 | `SUM(GREATEST(-remaining_fund, 0))` | `biz_dwd_project_fund` |
| `pf-kpi-received` | 已收款 | `sum_received_fund` | `biz_ads_project_fund_kpi` |
| `pf-kpi-receivable` | 待收经费 | `sum_receivable_fund` | `biz_ads_project_fund_kpi` |
| `pf-chart-budget` | 项目预算与执行 | 每个项目一组柱：`total_fund`、`total_spent`、`GREATEST(remaining_fund, 0)`；按 `project_id` 排序，取前 8 | `biz_dwd_project_fund` |
| `pf-chart-collection` | 项目收款情况 | 每个项目两根柱：`received_fund`、`receivable_fund`；按待收降序前 6 | `biz_dwd_project_fund` |
| `pf-summary` | 项目经费明细 | 每行 = 一个项目；按 `project_id` 排序取前 8 | `biz_dwd_project_fund` |
| `pf-ranking` | 待收经费 TOP 4 | 每条 = `receivable_fund BY project_id`，按待收降序前 4 | `biz_dwd_project_fund` |

### 7.3 状态卡口径

`pf-status` 一共 4 张卡：

| 卡片标题 | 取值逻辑 | 说明 |
| --- | --- | --- |
| 总执行率 | `overall_total_rate = sum_total_spent / sum_total_fund * 100` | 总支出 ÷ 总经费 |
| 直接成本执行率 | `overall_direct_rate = sum_direct_spent / sum_direct_ctrl * 100` | 直接支出 ÷ 直接控制数 |
| 待收占比 | `sum_receivable_fund / sum_total_fund * 100` | 不是按项目状态筛出来的待收，而是总待收 / 总经费 |
| 超支项目 | `COUNT(project_id WHERE remaining_fund < 0)` | 提示文案展示超支金额绝对值汇总 |

### 7.4 明细表每列含义

`pf-summary` 表头与列值：

- `项目编号` = `project_id`
- `研究室` = `research_dept`
- `总经费(万)` = `total_fund`
- `总支出(万)` = `total_spent = direct_spent + indirect_spent`
- `剩余经费(万)` = `remaining_fund = total_fund - total_spent`
- `已收款(万)` = `received_fund`
- `待收经费(万)` = `receivable_fund`
- `项目属性`：
  - `is_major_project = '是'`：`重大项目`
  - `is_major_project = '否'`：`非重大项目`
  - 其他：`未标注`

### 7.5 建议核对 SQL

```sql
SELECT *
FROM public.biz_ads_project_fund_kpi
WHERE summary_scope = 'all_projects'
  AND scope_key = 'all';

SELECT
  project_id,
  total_fund,
  direct_spent,
  indirect_spent,
  direct_spent + indirect_spent AS total_spent,
  total_fund - (direct_spent + indirect_spent) AS remaining_fund,
  received_fund,
  receivable_fund
FROM public.biz_dwd_project_fund
ORDER BY project_id;
```

## 8. 现场核对建议

### 8.1 先核“总卡”，再核“明细”

推荐顺序：

1. 先核每屏顶部 KPI 卡
2. 再核状态卡
3. 再核图表分组值
4. 最后核排行和明细表

原因：

- KPI 卡最容易看出总口径对不对
- 图表和明细通常只是同一批底表按不同维度分组

### 8.2 三个最容易误判的点

1. 项目经费的“剩余经费”卡只算正余额项目，超支部分单独在“超支金额”卡统计。
2. 自有资金表真正可变的只有年度，页面上其他筛选条当前不是 SQL 条件。
3. 个人辅助余额的“贷方余额”展示的是绝对值合计，不是负数。

### 8.3 如果现场数对不上

优先排查这几项：

1. 现场大屏实例是否已经被二次编辑过 SQL。
2. 现场使用的库是否就是 `databaseId = 1` 对应的财务库。
3. 页面显示的筛选条是否被误认为真实筛选，但模板里其实没有参数绑定。
4. dbt 跑数时间与大屏缓存刷新时间是否一致。
