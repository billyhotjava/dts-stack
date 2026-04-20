# 财务测试数据（v2.2.3 字段更新版）

覆盖 2025 / 2026 两个年度的样例数据，结构与客户 5.1-5.4 字段定义对齐。

## 文件清单

| 文件 | 对应 ODS 表 | 行数 | 说明 |
|---|---|---|---|
| `ods_finance_own_fund.csv` | `ods_finance_own_fund` | 18 | 2 年 × 3 基金来源 × 3 基金类别 |
| `ods_finance_project_fund.csv` | `ods_finance_project_fund` | 8 | 8 个项目，含是否重大项目/项目状态 |
| `ods_finance_aux_balance.csv` | `ods_finance_aux_balance` | 17 | 覆盖 7 类费用前缀 |
| `ods_finance_aux_balance_personal.csv` | `ods_finance_aux_balance_personal` | 15 | 覆盖 1122/2211 两类前缀 |

## 字段定义（与客户口径一致）

### 5.1 年度基金表 `ods_finance_own_fund`

| 字段 | 类型 | 枚举 | 说明 |
|---|---|---|---|
| `year_num` | int | - | 年度（如 2026、2027） |
| `fund_source` | varchar | ✓ | 基金来源：`年初` / `预计使用` / `预计增加` |
| `fund_category` | varchar | ✓ | 基金类别：`事业基金` / `职工福利基金` / `安全生产基金` |
| `amount` | numeric(15,2) | - | 金额（万元） |
| `note` | varchar | - | 备注 |

> **变化点**：原宽表结构（年度期间+事业基金+折旧基金+福利基金+安全生产基金+合计）改为 **长表**；移除"折旧基金"与"余额/合计"字段；新增 `备注`。每年 3×3=9 行。

### 5.2 项目经费表 `ods_finance_project_fund`

| 字段 | 类型 | 枚举 | 说明 |
|---|---|---|---|
| `row_no` | int | - | 行号 |
| `project_id` | varchar | - | 项目编号 |
| `cycle` | varchar | - | 研制周期（如 `2026.01-2027.06`） |
| `total_fund` | numeric | - | 总经费（万元） |
| `is_major_project` | boolean | - | 是否重大项目 |
| `research_dept` | varchar | - | 研究室 |
| `project_status` | varchar | ✓ | 项目状态：`已完成待收款` / `已完成审计` / `在研` / `支出待处理` |
| `direct_ctrl` | numeric | - | 直接成本控制数（万元） |
| `reserve_indirect` | numeric | - | 预留间接费用和收益（万元） |
| `direct_spent` | numeric | - | 直接成本账面支出总额（万元）— **新增** |
| `direct_rate` | numeric(8,2) | - | 直接成本执行率（%） |
| `indirect_spent` | numeric | - | 间接费用支出和收益总额（万元） |
| `received_fund` | numeric | - | 已收款（万元）— **新增** |
| `receivable_fund` | numeric | - | 待收经费（万元）— **新增** |

> **变化点**：新增 7 个字段（行号/是否重大项目/研究室/项目状态/直接成本账面支出总额/已收款/待收经费）。原 DWD 层推算的 `direct_spent` 现在直接从 ODS 读取。

### 5.3 辅助余额表-合同 `ods_finance_aux_balance`

字段不变：`subject_code` / `subject_name` / `dept_name` / `contract_name` / `balance`

- `subject_code` 前 4 位映射费用类别（5001=原材料/设备、5101=外协/服务、5201=折旧、5301=检测试验、5401=设计咨询、5501=租赁、5601=培训）
- `contract_name = '—'` 表示无合同

### 5.4 辅助余额表-个人维度 `ods_finance_aux_balance_personal`

字段不变：`subject_code` / `subject_name` / `employee_dept` / `employee_name` / `balance`

- `subject_code` 前 4 位：1122=其他应收-借款、2211=应付职工薪酬
- `balance` 正数=借方(应收)、负数=贷方(应付)

## 加载方式

```bash
cd worklog/v2.2.3/s10/v4/finance/test
psql -d <your_db> -f load_test_data.sql
```

## ⚠️ dbt 模型待同步调整

当前 dbt 模型链路基于**旧表结构**编写，本次 ODS 变更后需同步调整：

| 模型 | 调整点 |
|---|---|
| `stg_fin__own_fund` | 字段全部重写（`year_period` → `year_num`/`fund_source`/`fund_category`） |
| `biz_dwd_own_fund` | 去掉 `dim_year_period_suffix` 后缀解析；period_type 直接来自 `fund_source`；聚合口径按 `年初/预计增加/预计使用` 三类重新定义（无"余额"） |
| `dim_own_fund_period_type` | 枚举重建：`opening/increase/usage`（移除 `balance`） |
| `dim_year_period_suffix` | 可删除（不再需要） |
| `biz_dws_own_fund_yearly` | 汇总口径改为 按 `year_num × fund_category` 的二维聚合，而不是原四类 period_type × 四类基金 |
| `biz_ads_own_fund_kpi` | KPI 派生逻辑（`余额`来源删掉，改为 `年初 + 预计增加 − 预计使用` 推算年末余额） |
| `stg_fin__project_fund` | 新增 `is_major_project/research_dept/project_status/direct_spent/received_fund/receivable_fund` 字段 |
| `biz_dwd_project_fund` | `direct_spent` 改为直读 ODS 而不是由 `direct_ctrl × direct_rate / 100` 推算；新增收款类指标 |
| `biz_dws_project_fund_summary` / `biz_ads_project_fund_kpi` | 汇总新增维度（重大项目口径、已收/待收口径） |

是否现在一起更新 dbt 模型？如同意我即刻推进。
