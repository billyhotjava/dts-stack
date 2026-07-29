# 财务 Demo 数据治理设计

## 1. 源数据契约

### 1.1 `it_fin_demo_src.cost_center`

粒度：一个成本中心一行。

| 字段 | 类型 | 含义 | 治理要求 |
|---|---|---|---|
| `cost_center_code` | varchar(32) | 成本中心稳定编码 | ASCII；非空；唯一 |
| `cost_center_name` | varchar(100) | 成本中心名称 | 非空 |
| `parent_cost_center_code` | varchar(32) | 上级成本中心 | 可空；应命中当前主数据 |
| `manager_name` | varchar(100) | 合成财务负责人 | 仅展示，不作为人员主键 |
| `record_status_code` | varchar(32) | 记录状态 | `RS-ACTIVE`/`RS-INACTIVE` |
| `updated_at` | timestamptz | 源端更新时间 | 非空 |
| `source_batch_id` | varchar(64) | 源批次号 | 非空 |

### 1.2 `it_fin_demo_src.budget_account`

粒度：一个预算科目一行。

| 字段 | 类型 | 含义 | 治理要求 |
|---|---|---|---|
| `account_code` | varchar(32) | 预算科目稳定编码 | ASCII；非空；唯一 |
| `account_name` | varchar(100) | 科目名称 | 非空 |
| `account_category_raw` | varchar(32) | 源科目类别 | 映射为稳定类别标准码 |
| `control_type_raw` | varchar(32) | 源预算管控类型 | 映射为稳定管控标准码 |
| `record_status_code` | varchar(32) | 记录状态 | `RS-ACTIVE`/`RS-INACTIVE` |
| `updated_at` | timestamptz | 源端更新时间 | 非空 |
| `source_batch_id` | varchar(64) | 源批次号 | 非空 |

### 1.3 `it_fin_demo_src.budget_execution_snapshot`

粒度：一个预算明细在一个快照日期一行。

| 字段 | 类型 | 含义 | 治理要求 |
|---|---|---|---|
| `budget_line_code` | varchar(32) | 预算明细稳定编码 | 与快照日期组成唯一键 |
| `snapshot_date` | date | 统计快照日期 | FACT 业务时间 |
| `fiscal_year` | integer | 财年 | 与快照日期年份一致 |
| `cost_center_code` | varchar(32) | 成本中心编码 | 必须命中成本中心主数据 |
| `account_code` | varchar(32) | 预算科目编码 | 必须命中预算科目主数据 |
| `budget_amount` | numeric(18,2) | 调整后预算 | 必须大于 0 |
| `committed_amount` | numeric(18,2) | 未转实际的承诺金额 | 非负 |
| `actual_amount` | numeric(18,2) | 累计实际发生额 | 非负 |
| `payable_amount` | numeric(18,2) | 实际中尚未支付部分 | 非负且不大于实际 |
| `forecast_final_amount` | numeric(18,2) | 财年预计最终发生额 | 非负且不小于实际 |
| `updated_at` | timestamptz | 源端更新时间 | 非空 |
| `source_batch_id` | varchar(64) | 源批次号 | 非空 |

基线行数：

| 表 | 行数 |
|---|---:|
| `cost_center` | 3 |
| `budget_account` | 4 |
| `budget_execution_snapshot` | 8 |

源表显式设置主键、ASCII CHECK 和查询索引，但故意不设置跨表外键及金额语义 CHECK，以便由 DTS 质量模块检测并阻断错误。

## 2. ODS 目标

| 来源 | DTS ODS 目标 |
|---|---|
| `it_fin_demo_src.cost_center` | `ods_it_fin_demo_cost_center` |
| `it_fin_demo_src.budget_account` | `ods_it_fin_demo_budget_account` |
| `it_fin_demo_src.budget_execution_snapshot` | `ods_it_fin_demo_budget_snapshot` |

ODS 保留全部源字段，并使用平台实际生成的导入时间、来源任务、批次或记录哈希审计字段。不得要求修改客户 ODS 公共结构。

## 3. 业务分类

| 对象 | 编码 | 名称 | 定义 |
|---|---|---|---|
| 业务域 | `IT_FIN_FINANCE` | 财务治理 | 对预算、执行、预测和责任主体进行统一数据治理 |
| 业务过程 | `IT_FIN_BUDGET_MONITOR` | 预算执行监控 | 按快照日期监控成本中心预算占用、实际、应付、预测和超支 |
| 数据集市 | `IT_FIN_BUDGET_MART` | 财务预算分析集市 | 为财务负责人提供统一预算执行分析 |
| 建设计划 | 系统生成 | IT Finance Demo 预算治理建设计划 | 打通财务数据治理完整链路 |

## 4. 业务术语

| 编码 | 名称 | 定义 |
|---|---|---|
| `IT-FIN-BT-COST-CENTER` | 成本中心 | 对费用承担责任并进行预算控制的组织单元 |
| `IT-FIN-BT-BUDGET-ACCOUNT` | 预算科目 | 对预算用途进行稳定分类的核算分析项目 |
| `IT-FIN-BT-BUDGET-LINE` | 预算明细 | 财年内成本中心与预算科目组合下的最小预算控制单元 |
| `IT-FIN-BT-COMMITTED-AMOUNT` | 承诺金额 | 已形成采购或合同承诺但尚未转为实际发生额的金额 |
| `IT-FIN-BT-ACTUAL-AMOUNT` | 实际发生额 | 截至快照日已确认入账或验收的累计金额 |
| `IT-FIN-BT-PAYABLE-AMOUNT` | 应付金额 | 已计入实际发生额但尚未支付的部分 |
| `IT-FIN-BT-EXECUTION-RATE` | 预算执行率 | 实际发生额除以调整后预算 |
| `IT-FIN-BT-BUDGET-HEALTH` | 预算健康状态 | 根据预测超支、预算占用和应付压力形成的管理提示 |

术语只描述业务语义，不保存 SQL、表名或 dbt 路径。

## 5. 数据元与单位

| 数据元编码 | 名称 | 类型 | 可复用字段 |
|---|---|---|---|
| `IT-FIN-DE-RECORD-ID` | 财务记录稳定标识 | string | 三类派生主键 |
| `IT-FIN-DE-DATE-KEY` | 日期代理键 | integer | `date_key` |
| `IT-FIN-DE-FISCAL-YEAR` | 财年 | integer | `fiscal_year` |
| `IT-FIN-DE-COST-CENTER-CODE` | 成本中心编码 | string(32) | 成本中心主外键 |
| `IT-FIN-DE-ACCOUNT-CODE` | 预算科目编码 | string(32) | 科目主外键 |
| `IT-FIN-DE-BUDGET-LINE-CODE` | 预算明细编码 | string(32) | `budget_line_code` |
| `IT-FIN-DE-AMOUNT-CNY` | 人民币金额 | decimal(18,2) | 全部金额度量 |
| `IT-FIN-DE-RATE` | 财务比率 | decimal(7,4) | 执行率、占用率、应付比 |
| `IT-FIN-DE-STATUS-CODE` | 财务状态标准码 | string(32) | 类别、管控、健康状态 |

优先复用平台已有单位：

| 单位编码 | 名称 | 符号 | 精度 |
|---|---|---|---:|
| `CNY` | 人民币元 | 元 | 2 |
| `PERCENT` | 百分比 | `%` | 2 |

数据元、码表和单位必须保存稳定 ID 和版本引用；不能只保存中文名称。

## 6. 公共码表

### 6.1 预算科目类别 `IT-FIN-RC-ACCOUNT-CATEGORY`

| 原始值 `code` | `standard_code` | 标签 |
|---|---|---|
| 人员、人员费、PERSONNEL | `BAC-PERSONNEL` | 人员费用 |
| 设备、设备费、EQUIPMENT | `BAC-EQUIPMENT` | 设备费用 |
| 材料、材料费、MATERIAL | `BAC-MATERIAL` | 材料费用 |
| 服务、服务费、SERVICE | `BAC-SERVICE` | 服务费用 |
| NULL/空值/未识别 | `BAC-UNKNOWN` | 未知 |

### 6.2 预算管控类型 `IT-FIN-RC-CONTROL-TYPE`

| 原始值 `code` | `standard_code` | 标签 |
|---|---|---|
| 硬管控、硬控制、HARD | `BCT-HARD` | 硬管控 |
| 软管控、软控制、SOFT | `BCT-SOFT` | 软管控 |
| NULL/空值/未识别 | `BCT-UNKNOWN` | 未知 |

### 6.3 预算健康状态 `IT-FIN-RC-BUDGET-HEALTH`

| 派生值 | `standard_code` | 标签 | 严重度 |
|---|---|---|---:|
| GREEN | `BH-GREEN` | 健康 | 1 |
| AMBER | `BH-AMBER` | 关注 | 2 |
| RED | `BH-RED` | 预警 | 3 |
| NULL/空值 | `BH-UNKNOWN` | 未知 | 0 |

映射前统一清除前后空格、BOM、不间断空格和零宽字符，并统一英文大小写。下游只输出 `standard_code`；原始值以 `*_raw` 保留用于审计。UNKNOWN 不在发布白名单中，必须触发阻断。

## 7. 业务维度定义

### 7.1 日期

| UI 属性 | 值 |
|---|---|
| 名称 | 财务日期 |
| 定义 | 财务预算分析使用的统一公历日期 |
| 业务主键属性 | `DATE_KEY` |
| 复用范围 | TENANT |
| 历史策略 | NONE |
| 层级 | 年 → 季度 → 月 → 日期 |

若租户已有满足要求的现行“日期”维度，可直接复用，不重复创建；对象登记表记录实际 ID。

### 7.2 成本中心

| UI 属性 | 值 |
|---|---|
| 名称 | 成本中心 |
| 定义 | 对费用承担预算控制责任的组织单元 |
| 业务主键属性 | `COST_CENTER_CODE` |
| 复用范围 | DOMAIN |
| 历史策略 | TYPE1 |
| 层级 | 总部费用中心 → 部门费用中心 |

### 7.3 预算科目

| UI 属性 | 值 |
|---|---|
| 名称 | 预算科目 |
| 定义 | 财务预算用途的稳定分类主数据 |
| 业务主键属性 | `ACCOUNT_CODE` |
| 复用范围 | DATA_MART |
| 历史策略 | TYPE1 |
| 层级 | 科目类别 → 预算科目 |

维度定义保存后必须设为 CURRENT，才能被 DIMENSION ModelSpec 引用。

## 8. 六个模型

完整字段见 `assets/model-field-matrix.csv`。

### 8.1 `it_fin_demo_dwd_dim_date`

| 属性 | 值 |
|---|---|
| 类型/层 | DIMENSION / DWD |
| 粒度 | 一个公历日期一行 |
| 粒度键 | `date_key` |
| 生成方式 | `GENERATED` / `DATE_DIMENSION` |
| 日期范围 | 2026-01-01 至 2027-12-31 |
| 物化/装载 | table / FULL |

### 8.2 `it_fin_demo_dwd_dim_cost_center`

| 属性 | 值 |
|---|---|
| 类型/层 | DIMENSION / DWD |
| 粒度 | 一个成本中心一行 |
| 粒度键 | `cost_center_code` |
| SCD | TYPE1 |
| 来源 | `ods_it_fin_demo_cost_center` 的已确认 revision |
| 物化/装载 | table / FULL |

### 8.3 `it_fin_demo_dwd_dim_budget_account`

| 属性 | 值 |
|---|---|
| 类型/层 | DIMENSION / DWD |
| 粒度 | 一个预算科目一行 |
| 粒度键 | `account_code` |
| SCD | TYPE1 |
| 来源 | `ods_it_fin_demo_budget_account` 的已确认 revision |
| 标准映射 | 科目类别、管控类型 |
| 物化/装载 | table / FULL |

### 8.4 `it_fin_demo_dwd_fct_budget_snapshot`

| 属性 | 值 |
|---|---|
| 类型/层 | FACT / DWD |
| 粒度 | 一个预算明细在一个快照日期一行 |
| 粒度键 | `budget_snapshot_id` |
| 事实形态 | `PERIODIC_SNAPSHOT` |
| 时间语义 | `SNAPSHOT_DATE` |
| 时间字段 | `snapshot_date` |
| 分析维度 | 财务日期、成本中心、预算科目 |
| 来源 | `ods_it_fin_demo_budget_snapshot` 的已确认 revision |
| 物化/装载 | table / FULL |
| 去重 | `budget_snapshot_id` |

派生：

```text
budget_snapshot_id =
  hash(budget_line_code || snapshot_date)

occupied_amount =
  actual_amount + committed_amount

remaining_available_amount =
  budget_amount - actual_amount - committed_amount

overrun_amount =
  max(forecast_final_amount - budget_amount, 0)
```

### 8.5 `it_fin_demo_dws_cost_center_budget`

| 属性 | 值 |
|---|---|
| 类型/层 | SUMMARY / DWS |
| 粒度 | 一个成本中心在一个快照日期一行 |
| 粒度键 | `cost_center_budget_id` |
| 上游 | 锁定 revision 的预算事实和成本中心维度 |
| 物化/装载 | table / FULL |

口径：

```text
execution_rate = actual_amount / nullif(budget_amount, 0)
occupation_rate = occupied_amount / nullif(budget_amount, 0)
payable_ratio = payable_amount / nullif(actual_amount, 0)
forecast_variance_amount = budget_amount - forecast_final_amount

budget_health_code =
  BH-RED    when forecast_final_amount > budget_amount
  BH-AMBER  when occupation_rate >= 0.85 or payable_ratio >= 0.30
  BH-GREEN  otherwise
```

健康状态是管理提示，不是会计结论、安全密级或自动审批依据。`overrun_amount` 在事实粒度计算后汇总，可保留被其他科目节余抵消前的明细超支风险；健康状态使用成本中心总预测与总预算比较。

### 8.6 `it_fin_demo_ads_finance_overview`

| 属性 | 值 |
|---|---|
| 类型/层 | APPLICATION / ADS |
| 粒度 | 一个成本中心在一个快照日期一行 |
| 粒度键 | `finance_overview_id` |
| 上游 | 锁定 revision 的成本中心预算汇总 |
| 消费场景 | 财务负责人查看预算、占用、实际、应付、预测和健康状态 |
| 物化/装载 | table / FULL |

BI、API 和数据产品只消费 ADS；普通业务用户不直接消费 ODS/DWD。

普通实现可以完成来源选择、映射、类型转换、受控 JOIN 和去重，但不能完整表达标准码归一、哈希键、条件派生和聚合。本 Demo 在同一 ModelSpec revision 上绑定 `dbt/` 高级实现，不另建模型台账。

## 9. 质量规则

UI 中创建 12 个治理规则；dbt 中可由多个测试节点共同提供规则证据。

| 编码 | 对象 | 规则 | 脏数据期望 |
|---|---|---|---|
| `IT-FIN-QR-CENTER-CODE` | 成本中心 | 编码非空且唯一 | 通过 |
| `IT-FIN-QR-ACCOUNT-CODE` | 预算科目 | 编码非空且唯一 | 通过 |
| `IT-FIN-QR-ACCOUNT-STANDARD` | 预算科目 | 类别和管控类型均命中标准码白名单 | 失败 |
| `IT-FIN-QR-SNAPSHOT-KEY` | 预算事实 | 快照派生键非空且唯一 | 通过 |
| `IT-FIN-QR-CENTER-REF` | 预算事实 | 成本中心命中主数据 | 失败 |
| `IT-FIN-QR-ACCOUNT-REF` | 预算事实 | 预算科目命中主数据 | 通过 |
| `IT-FIN-QR-BUDGET-POSITIVE` | 预算事实 | 预算金额大于 0 | 失败 |
| `IT-FIN-QR-AMOUNT-NONNEGATIVE` | 预算事实 | 预算、承诺、实际、应付和预测非负 | 失败 |
| `IT-FIN-QR-PAYABLE-BOUND` | 预算事实 | 应付不大于实际 | 失败 |
| `IT-FIN-QR-FORECAST-BOUND` | 预算事实 | 预测最终额不小于实际 | 失败 |
| `IT-FIN-QR-FISCAL-YEAR` | 预算事实 | 快照日期年份等于财年 | 失败 |
| `IT-FIN-QR-SNAPSHOT-FRESHNESS` | 预算事实 | 最新快照满足实施方约定周期 | 按运行日判断 |

全部规则严重度设为阻断级。执行错误与数据不合格必须分别展示；连接或 SQL 错误不得计为质量通过。

阻断表示失败批次不能推进新的 DWS/ADS revision 或消费发布，不要求删除上一次成功物化表。若平台保留 last-good 数据，BI/API 必须显示其快照时间和质量状态，不能把旧表误报为本次脏数据运行成功。

## 10. 治理指标

从已发布 DWS 模型的 MEASURE 字段创建原子指标，再创建派生指标：

| 指标编码 | 名称 | 类型 | 字段/公式 | 单位 |
|---|---|---|---|---|
| `IT_FIN_BUDGET_AMOUNT` | 预算金额 | 原子 | `SUM(budget_amount)` | CNY |
| `IT_FIN_COMMITTED_AMOUNT` | 承诺金额 | 原子 | `SUM(committed_amount)` | CNY |
| `IT_FIN_ACTUAL_AMOUNT` | 实际发生额 | 原子 | `SUM(actual_amount)` | CNY |
| `IT_FIN_PAYABLE_AMOUNT` | 应付金额 | 原子 | `SUM(payable_amount)` | CNY |
| `IT_FIN_FORECAST_AMOUNT` | 预测最终发生额 | 原子 | `SUM(forecast_final_amount)` | CNY |
| `IT_FIN_EXECUTION_RATE` | 预算执行率 | 派生 | `IT_FIN_ACTUAL_AMOUNT / IT_FIN_BUDGET_AMOUNT` | PERCENT |
| `IT_FIN_OCCUPATION_RATE` | 预算占用率 | 派生 | `(IT_FIN_ACTUAL_AMOUNT + IT_FIN_COMMITTED_AMOUNT) / IT_FIN_BUDGET_AMOUNT` | PERCENT |
| `IT_FIN_OVERRUN_AMOUNT` | 明细预测超支金额 | 原子 | `SUM(overrun_amount)` | CNY |

派生指标通过工作台选择已发布指标依赖，不能复制 SQL 口径形成第二套定义。所有指标填写 owner、责任部门、时间字段 `snapshot_date`、时间粒度、成本中心/财务日期分析维度和 ModelSpec 精确 revision。预算科目明细保留在 DWD 事实中，不虚构成当前成本中心粒度 DWS 指标的可切分维度。

## 11. 基线与修复后的抽样结果

基线 DWS/ADS 应为 4 行：

| 快照日 | 成本中心 | 预算 | 实际 | 预测 | 执行率 | 健康 |
|---|---|---:|---:|---:|---:|---|
| 2026-06-30 | CC-RD | 1,300,000 | 460,000 | 1,310,000 | 0.3538 | BH-RED |
| 2026-06-30 | CC-QA | 700,000 | 260,000 | 700,000 | 0.3714 | BH-GREEN |
| 2026-07-28 | CC-RD | 1,300,000 | 660,000 | 1,360,000 | 0.5077 | BH-RED |
| 2026-07-28 | CC-QA | 700,000 | 390,000 | 715,000 | 0.5571 | BH-RED |

修复增量后增加：

| 快照日 | 成本中心 | 预算 | 实际 | 预测 | 执行率 | 健康 |
|---|---|---:|---:|---:|---:|---|
| 2026-08-04 | CC-RD | 1,300,000 | 800,000 | 1,290,000 | 0.6154 | BH-GREEN |
| 2026-08-04 | CC-QA | 700,000 | 420,000 | 690,000 | 0.6000 | BH-AMBER |

这些数值用于验证口径，不代表客户真实财务情况。

## 12. 资产、标签、密级和权限

业务标签：

- `IT-FIN-DEMO`：合成财务演示资产。
- `BUDGET-GOVERNANCE`：预算治理分析。
- `GOVERNED`：只有治理门禁证据完整后才添加。

安全密级：全部选择平台现有公开级；不得用上述业务标签代替密级。

| 角色 | ADS | BI | API | 数据产品 |
|---|---|---|---|---|
| 财务 Demo 管理员 | read/write/export | 编辑 | 管理 | 管理 |
| 财务 Demo 查看者 | read/export | 查看 | 调用 | 查看/订阅 |
| 无授权用户 | 拒绝 | 拒绝 | 401/403 | 不可见或拒绝 |

验收时记录平台真实策略 ID 和审计日志；不得把页面可见误当成底层数据已经授权。

## 13. 血缘

表级目标：

```text
it_fin_demo_src.budget_execution_snapshot
  → ods_it_fin_demo_budget_snapshot
  → it_fin_demo_dwd_fct_budget_snapshot
  → it_fin_demo_dws_cost_center_budget
  → it_fin_demo_ads_finance_overview
  → BI / API / 数据产品
```

字段级至少验证：

- `budget_amount` → DWD/DWS/ADS `budget_amount`
- `actual_amount` → `execution_rate`
- `actual_amount + committed_amount` → `occupied_amount` → `occupation_rate`
- `payable_amount / actual_amount` → `payable_ratio`
- `forecast_final_amount - budget_amount` → `overrun_amount`
- 预测、占用、应付字段 → `budget_health_code`

血缘必须来自入湖运行、ModelSpec/dbt 制品或受控导入，不以本文档中的示意图作为成功证据。
