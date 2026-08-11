# Finance dbt 模型逆向导入操作手册

本手册用于将 `finance-dbt-model-reverse-import.zip` 导入 DTS 数据建模模块。导入包包含完整 dbt 依赖链，但只把 DWD、DWS、ADS 的 20 个节点投影为业务模型；4 个 STG 节点仅作为技术依赖展示。

## Step 1：确认数仓规划

进入“数据建模 → 数仓规划”，确认或创建以下对象：

| 对象 | 编码 | 名称 | 上级/归属 |
|------|------|------|-----------|
| 业务分类 | `S10_PRJ` | 研究所业务 | 顶层规划参数 |
| 数据域 | `FINANCE` | 财务管理域 | 研究所业务 |
| 业务过程 | `own-fund-accounting` | 自有资金核算 | 财务管理域 |
| 业务过程 | `project-fund-accounting` | 项目经费核算 | 财务管理域 |
| 业务过程 | `contract-aux-accounting` | 合同辅助核算 | 财务管理域 |
| 业务过程 | `personal-aux-accounting` | 个人辅助核算 | 财务管理域 |

检查“财务管理域”已绑定当前建模方案，状态为“已确认/可用”。不要按 ERP、财务系统等物理来源重复创建数据域；源系统在后续来源映射中选择。

## Step 2：生成导入 ZIP

在 `worklog/v2.2.3/s10/v4/finance` 目录执行：

```bash
./build-deploy.sh
```

脚本会生成并校验：

```text
finance-dbt-model-reverse-import.zip
```

ZIP 内以 `dbt_model/` 为项目目录，并直接包含 `dbt_project.yml`、`models.tsv`、`models/`；不要再增加其他外层目录。

## Step 3：上传并检查包结构

1. 进入“数据建模 → 维度建模 → 逆向建模”。
2. 上传 `finance-dbt-model-reverse-import.zip`。
3. 等待“包结构检查”完成。
4. 核对预期结果：

| 检查项 | 预期值 |
|--------|--------|
| 项目名 | `finance_analytics` |
| 包结构 | `SUPPORTED` |
| 导入投影 | `IMPORTABLE` |
| 业务模型 | 20 |
| 技术节点 | 4（仅 STG） |
| 需要映射 | 20 |
| 结构阻断 | 0 |

若出现“宏依赖无法验证”“动态依赖”或“字段无法验证”，应停止导入并重新生成 ZIP，不要绕过阻断。

## Step 4：完成上下文映射

1. 将包内数据域 `FINANCE` 映射为“财务管理域”。
2. 将四个 `fin_ods` source 表映射到当前环境已确认的数据源/数据集：

| dbt source | 业务含义 |
|------------|----------|
| `own_fund` | 自有资金来源表 |
| `project_fund` | 项目经费来源表 |
| `aux_balance` | 合同辅助余额来源表 |
| `aux_balance_personal` | 个人辅助余额来源表 |

来源必须真实存在且处于可用状态；不能仅依赖名称自动匹配后直接应用。

## Step 5：设置模型类型、层次与业务过程

### 5.1 维度与映射表

以下 8 个模型选择“维度模型”，数仓层次选择 `DWD`，无需绑定业务过程：

| 模型 | 建议业务主键/粒度 |
|------|-------------------|
| `dim_fund_source` | `fund_source_id`（稳定码 `code`） |
| `dim_fund_category` | `fund_category_id`（稳定码 `code`） |
| `dim_project_status` | `project_status_id`（稳定码 `code`） |
| `dim_expense_category` | `expense_category_id`（稳定码 `code`） |
| `dim_balance_direction` | `balance_direction_id`（稳定码 `code`） |
| `dim_personal_subject_category` | `subject_category_id`（稳定码 `code`） |
| `dim_expense_code_prefix` | `prefix` |
| `dim_personal_subject_code_prefix` | `prefix` |

### 5.2 DWD 明细事实表

以下 4 个模型选择“事实模型”，层次选择 `DWD`：

| 模型 | 业务过程 | 明细粒度/业务主键 |
|------|----------|-------------------|
| `biz_dwd_own_fund` | 自有资金核算 | 年度 × 基金来源 × 基金类别；`own_fund_id` |
| `biz_dwd_project_fund` | 项目经费核算 | 项目 × 研制周期；`project_fund_id` |
| `biz_dwd_aux_balance` | 合同辅助核算 | 科目 × 部门 × 合同；`aux_balance_id` |
| `biz_dwd_aux_balance_personal` | 个人辅助核算 | 科目 × 部门 × 职工；`personal_balance_id` |

### 5.3 DWS 汇总表

以下 4 个模型选择“汇总模型”，层次选择 `DWS`，沿用对应业务过程：

| 模型 | 业务过程 | 汇总粒度 |
|------|----------|----------|
| `biz_dws_own_fund_yearly` | 自有资金核算 | 年度 × 基金类别 |
| `biz_dws_project_fund_summary` | 项目经费核算 | 汇总范围 × 范围键 |
| `biz_dws_aux_balance_by_dept` | 合同辅助核算 | 部门 |
| `biz_dws_aux_balance_personal_by_dept` | 个人辅助核算 | 职工部门 |

### 5.4 ADS 应用表

以下 4 个模型选择“应用模型”，层次选择 `ADS`：

- `biz_ads_own_fund_kpi`
- `biz_ads_project_fund_kpi`
- `biz_ads_aux_balance_kpi`
- `biz_ads_aux_balance_personal_kpi`

ADS 除对应业务过程外，还应选择实际消费方的数据集市和主题域。当前包不预设环境相关的数据集市/主题域；未确认前不要执行最终应用。

## Step 6：预览并应用

1. 点击“生成预览”，逐项检查模型类型、分层、中文名称、业务主键、数据域、业务过程及来源映射。
2. 确认“阻断项”为 0，且没有自动匹配错误。
3. 记录预览批次和映射选择，经建模负责人确认后再点击“应用”。
4. 应用完成后在模型列表核对 20 个新模型及依赖关系，并抽查四条链路：

```text
STG → DWD 事实/维度 → DWS → ADS
```

本项目的自动化验证只覆盖 ZIP 结构、静态投影与 dbt 运行正确性；正式环境“应用”必须使用该环境的真实来源、数据集市和主题域完成验收。
