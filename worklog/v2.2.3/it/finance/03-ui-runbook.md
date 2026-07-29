# 财务 Demo DTS UI 手工实施 Runbook

## 0. 开始前

### 0.1 测试同事需要准备

- 一个独立 PostgreSQL Demo 数据库或至少一个可隔离的 Schema。
- 只能访问 `it_fin_demo_src` 的源端账号。
- DTS 租户、财务 Demo 管理员和财务 Demo 查看者账号。
- 实际 owner、责任部门和公开密级编码。
- DTS 入湖目标数据源/数据湖。
- 一个用于保存运行证据的工作目录。

严禁把密码、Token、Cookie 或完整连接串写入本目录。

### 0.2 初始化源数据

```bash
psql "$IT_FIN_DEMO_SOURCE_DSN" -v ON_ERROR_STOP=1 \
  -f worklog/v2.2.3/it/finance/sql/01-source-bootstrap.sql
```

预期：

```text
it_fin_demo_src.cost_center                 3
it_fin_demo_src.budget_account              4
it_fin_demo_src.budget_execution_snapshot   8
```

如果不是此行数，先停止并修复源端，不进入 DTS 配置。

### 0.3 全程登记

每创建一个对象，立即填写：

`assets/demo-object-register.csv`

至少记录实际 ID、revision、状态和证据路径。系统自动生成编码的对象，不要自行编造 ID。

## 1. 数据源与入湖

入口：

- 数据源：`/foundation/data-sources`
- 入湖/转换：`/explore/etl/transform`

### 1.1 创建财务 Demo 数据源

| UI 字段 | 填写值 |
|---|---|
| 名称 | IT Finance Demo PostgreSQL |
| 类型 | PostgreSQL/JDBC |
| Host/Port/Database | Demo 源库实际值 |
| Schema | `it_fin_demo_src` |
| 用户名/凭据 | 独立 Demo 账号，通过凭据控制面保存 |
| owner | 实际数据工程师 |
| 描述 | 合成财务预算执行数据，仅用于 DTS 治理闭环测试 |

执行“测试连接”。失败时停止，不允许用手工登记 ODS 绕过。

### 1.2 Schema 探测

只选择：

- `it_fin_demo_src.cost_center`
- `it_fin_demo_src.budget_account`
- `it_fin_demo_src.budget_execution_snapshot`

验收：

| 表 | 字段数 |
|---|---:|
| `cost_center` | 7 |
| `budget_account` | 7 |
| `budget_execution_snapshot` | 12 |

日期、金额、整数和时间戳类型必须识别正确，结果中不得出现客户 ODS。

### 1.3 创建入湖任务

| 来源 | 目标表 | 建议模式 |
|---|---|---|
| `cost_center` | `ods_it_fin_demo_cost_center` | 全量覆盖或按 `updated_at` 增量 |
| `budget_account` | `ods_it_fin_demo_budget_account` | 全量覆盖或按 `updated_at` 增量 |
| `budget_execution_snapshot` | `ods_it_fin_demo_budget_snapshot` | 按 `snapshot_date`/`updated_at` 增量 |

任务名：`IT Finance Demo 预算治理入湖`。

如果一个任务不能选择多表，则创建 3 个单表任务；对象登记表分别记录实际 ID。

### 1.4 运行基线

运行后确认：

```text
ods_it_fin_demo_cost_center      3
ods_it_fin_demo_budget_account   4
ods_it_fin_demo_budget_snapshot  8
```

保存源表行数、ODS 行数、入湖 run ID、开始/结束时间和日志证据。

## 2. 元数据与目录资产

入口：

- `/catalog/metadata-management`
- `/catalog/assets`

执行结构采集，确认 3 张 ODS 成为正式目录资产。

| ODS | 业务名称 | 粒度描述 |
|---|---|---|
| `ods_it_fin_demo_cost_center` | 财务 Demo 成本中心 | 一个成本中心一行 |
| `ods_it_fin_demo_budget_account` | 财务 Demo 预算科目 | 一个预算科目一行 |
| `ods_it_fin_demo_budget_snapshot` | 财务 Demo 预算执行快照 | 一个预算明细在一个快照日期一行 |

每张资产填写：

- owner：从用户目录选择实际人员。
- 责任部门：从组织目录选择。
- 描述：使用上表粒度。
- 密级：平台既有公开级。
- 生命周期：Demo/测试用途，按实际 UI 选项。
- 业务标签：先添加 `IT-FIN-DEMO`、`BUDGET-GOVERNANCE`。

质量门禁完成前不要添加 `GOVERNED`。密级和业务标签分别维护。

## 3. 业务分类与数据集市

入口：`/governance/subjects`

### 3.1 业务域

| 字段 | 值 |
|---|---|
| 编码 | `IT_FIN_FINANCE` |
| 名称 | 财务治理 |
| 定义 | 对预算、执行、预测和责任主体进行统一数据治理 |
| owner/责任部门 | 实际人员和组织 |

### 3.2 业务过程

| 字段 | 值 |
|---|---|
| 编码 | `IT_FIN_BUDGET_MONITOR` |
| 名称 | 预算执行监控 |
| 定义 | 按快照日期监控成本中心预算占用、实际、应付、预测和超支 |

### 3.3 数据集市

| 字段 | 值 |
|---|---|
| 编码 | `IT_FIN_BUDGET_MART` |
| 名称 | 财务预算分析集市 |
| 用途 | 为财务负责人提供统一预算执行分析 |
| 所属业务域 | 财务治理 |
| owner | 实际财务数据负责人 |

创建后执行“确认”，状态必须为 CURRENT。

## 4. 建设计划与来源确认

入口：`/modeling/plans`

### 4.1 创建计划

| 字段 | 值 |
|---|---|
| 名称 | IT Finance Demo 预算治理建设计划 |
| 建设目标 | 打通财务数据接入、标准、主数据、建模、质量、指标、资产、权限、血缘和消费 |
| 建设范围 | 仅 `it_fin_demo_src`、3 张财务 Demo ODS 和 6 个财务 Demo 模型 |
| owner/责任部门 | 实际人员和组织 |
| 接入方式 | 从现有资产开始 |

### 4.2 规划策略

| 字段 | 值 |
|---|---|
| 字段标准覆盖范围 | 键字段和度量字段 |
| 质量测试要求 | 必须通过，否则阻止发布 |
| 默认时区 | `Asia/Shanghai` |

### 4.3 确认基线

1. 纳入并确认 `IT_FIN_FINANCE`。
2. 纳入并确认 `IT_FIN_BUDGET_MONITOR`。
3. 纳入现行数据集市 `IT_FIN_BUDGET_MART`。
4. 从正式目录加入 3 张财务 Demo ODS。
5. 每个来源结论设为“确认纳入”，freshness/revision 为 CURRENT。

只登记 ODS 物理来源；不创建 ODS/STG 类型的额外 ModelSpec。

## 5. 数据标准

入口：

- 术语：`/governance/standards/glossary`
- 数据元：`/governance/standards/elements`
- 公共码表：`/governance/standards/reference`
- 计量单位：`/governance/standards/units`

按 `02-data-governance-design.md` 创建：

- 8 个业务术语。
- 9 个数据元。
- 3 个公共码表。
- 复用 `CNY`、`PERCENT` 两个单位。

操作规则：

- 编码全部使用文档给出的 ASCII 值。
- 先搜索同编码；已有客户自定义内容时不得覆盖。
- 同编码且语义一致时记录实际 ID/version 并复用。
- 同编码但语义冲突时停止，保留冲突证据，使用租户允许的 Demo 后缀编码。
- 码表必须录入原始别名、稳定 `standard_code` 和中文标签。
- 发布/生效后再绑定模型字段。

## 6. 维度目录

入口：`/modeling/dimensions`

### 6.1 财务日期

如果租户已有现行日期维度且属性满足要求，直接复用；否则创建：

- 名称：财务日期。
- 定义：财务预算分析使用的统一公历日期。
- 范围：DOMAIN。
- 复用范围：TENANT。
- 属性：`DATE_KEY`、`FULL_DATE`、`YEAR_NO`、`QUARTER_NO`、`MONTH_NO`、`ISO_WEEK_NO`。
- 层级：年 → 季度 → 月 → 日期。

### 6.2 成本中心

- 名称：成本中心。
- 定义：对费用承担预算控制责任的组织单元。
- 范围：DOMAIN。
- 复用范围：DOMAIN。
- 主键属性：`COST_CENTER_CODE`。
- 其他属性：名称、上级编码、财务负责人、记录状态。
- 历史策略：TYPE1。
- 层级：总部费用中心 → 部门费用中心。

### 6.3 预算科目

- 名称：预算科目。
- 定义：财务预算用途的稳定分类主数据。
- 范围：DATA_MART。
- 数据集市：财务预算分析集市。
- 复用范围：PLAN。
- 主键属性：`ACCOUNT_CODE`。
- 其他属性：名称、科目类别标准码、管控类型标准码、记录状态。
- 历史策略：TYPE1。
- 层级：科目类别 → 预算科目。

每个新维度保存后执行“设为现行”。只有 CURRENT 维度才能被模型选择。

## 7. 模型中心

入口：`/modeling/models`

依赖顺序：

1. `it_fin_demo_dwd_dim_date`
2. `it_fin_demo_dwd_dim_cost_center`
3. `it_fin_demo_dwd_dim_budget_account`
4. `it_fin_demo_dwd_fct_budget_snapshot`
5. `it_fin_demo_dws_cost_center_budget`
6. `it_fin_demo_ads_finance_overview`

所有字段按 `assets/model-field-matrix.csv` 录入。

### 7.1 日期维度

| 属性 | 值 |
|---|---|
| 类型/层 | DIMENSION / DWD |
| 维度定义 | 财务日期或实际复用日期维度 |
| 粒度 | 一个公历日期一行 |
| 粒度键 | `date_key` |
| 历史策略 | NONE |
| 输入 | GENERATED / DATE_DIMENSION |
| 目标 | `it_fin_demo_dwd_dim_date` |
| 物化/装载 | table / FULL |

### 7.2 成本中心维度

| 属性 | 值 |
|---|---|
| 类型/层 | DIMENSION / DWD |
| 维度定义 | 成本中心 |
| 粒度 | 一个成本中心一行 |
| 粒度键 | `cost_center_code` |
| SCD | TYPE1 |
| 来源 | 已确认 `ods_it_fin_demo_cost_center` revision |
| 目标 | `it_fin_demo_dwd_dim_cost_center` |
| 物化/装载 | table / FULL |

### 7.3 预算科目维度

| 属性 | 值 |
|---|---|
| 类型/层 | DIMENSION / DWD |
| 维度定义 | 预算科目 |
| 粒度 | 一个预算科目一行 |
| 粒度键 | `account_code` |
| SCD | TYPE1 |
| 来源 | 已确认 `ods_it_fin_demo_budget_account` revision |
| 目标 | `it_fin_demo_dwd_dim_budget_account` |
| 物化/装载 | table / FULL |

`account_category_code` 和 `control_type_code` 绑定现行码表版本，原始值作为审计属性保留。

### 7.4 预算执行快照事实

| 属性 | 值 |
|---|---|
| 类型/层 | FACT / DWD |
| 粒度 | 一个预算明细在一个快照日期一行 |
| 粒度键 | `budget_snapshot_id` |
| 事实形态 | PERIODIC_SNAPSHOT |
| 时间语义 | SNAPSHOT_DATE |
| 时间字段 | `snapshot_date` |
| 分析维度 | 财务日期、成本中心、预算科目 |
| 来源 | 已确认 `ods_it_fin_demo_budget_snapshot` revision |
| 目标 | `it_fin_demo_dwd_fct_budget_snapshot` |
| 物化/装载 | table / FULL |
| 去重 | `budget_snapshot_id` |

金额字段绑定 `IT-FIN-DE-AMOUNT-CNY` 和 `CNY`，业务编码绑定对应数据元。

### 7.5 成本中心预算汇总

| 属性 | 值 |
|---|---|
| 类型/层 | SUMMARY / DWS |
| 粒度 | 一个成本中心在一个快照日期一行 |
| 粒度键 | `cost_center_budget_id` |
| 上游 | 锁定当前事实和成本中心维度 revision |
| 目标 | `it_fin_demo_dws_cost_center_budget` |
| 物化/装载 | table / FULL |

不选择 ODS 物理来源。

### 7.6 财务预算概览

| 属性 | 值 |
|---|---|
| 类型/层 | APPLICATION / ADS |
| 粒度 | 一个成本中心在一个快照日期一行 |
| 粒度键 | `finance_overview_id` |
| 消费场景 | 财务负责人查看预算执行、预测、超支和健康状态 |
| 上游 | 锁定汇总模型 revision |
| 目标 | `it_fin_demo_ads_finance_overview` |
| 物化/装载 | table / FULL |

### 7.7 阶段动作

每个模型依次执行：

```text
保存逻辑设计
  → 配置数据实现
  → 验证实现
  → 生成并发布
```

逐项检查：

- 技术字段为小写英文、数字、下划线。
- 中文业务名称完整。
- KEY、TIME、ATTRIBUTE、MEASURE 角色正确。
- 粒度键唯一命中 KEY 字段。
- 键和度量绑定现行数据元/单位版本。
- 状态字段绑定现行码表版本。
- 密级单独选择既有公开级。
- 上游依赖锁定实际 revision。

## 8. 高级 SQL/dbt 实现

入口：`/studio/sql-modeling`

1. 创建或选择财务 Demo dbt 项目。
2. 使用 `finance/dbt/` 中的文件作为实现内容。
3. 源表 Schema 不是 `public` 时设置变量 `it_fin_demo_ods_schema`。
4. 将 6 个 dbt 节点分别绑定同名 ModelSpec ID 和精确 revision。
5. 先执行 parse/compile，再执行 build。
6. 保存 manifest、run_results、compiled SQL、测试结果和 run ID。

绑定关系见 `finance/dbt/README.md`。不得另外创建 6 个“SQL模型”充当第二套业务模型。

当前物化链路存在 profile lease 返回字段契约风险。如果平台运行失败：

- 保留真实错误、run ID 和候选版本。
- 不反复消费同一 lease/token。
- 不把本地 dbt 成功写成平台物化成功。
- 等产品缺口修复、打包、部署并现场复测后再勾选物化通过。

## 9. 质量规则

入口：`/governance/quality`

创建 `02-data-governance-design.md` 第 9 节的 12 个规则，绑定当前 ModelSpec revision：

| 规则组 | 严重度 | 发布策略 |
|---|---|---|
| 主数据编码和标准码 | 阻断 | 失败禁止发布 |
| 事实唯一和主数据引用 | 阻断 | 失败禁止发布 |
| 金额及会计期间 | 阻断 | 失败禁止发布 |
| 快照时效 | 阻断 | 超期禁止发布 |

先运行基线，所有适用规则应通过。执行错误必须显示 ERROR，不得显示 100% 通过。

## 10. 指标工作台

入口：`/modeling/metric-workbench`

先创建并发布 6 个原子指标：

1. `IT_FIN_BUDGET_AMOUNT`
2. `IT_FIN_COMMITTED_AMOUNT`
3. `IT_FIN_ACTUAL_AMOUNT`
4. `IT_FIN_PAYABLE_AMOUNT`
5. `IT_FIN_FORECAST_AMOUNT`
6. `IT_FIN_OVERRUN_AMOUNT`

再创建两个派生指标：

7. `IT_FIN_EXECUTION_RATE`
8. `IT_FIN_OCCUPATION_RATE`

每个指标填写：

- 业务定义和计算口径。
- 业务 owner、技术 owner、责任部门。
- 来源 ModelSpec ID、精确 revision 和字段。
- 时间字段：`snapshot_date`。
- 分析维度：成本中心、财务日期。预算科目已在 DWS 汇总粒度中消失，不能在指标配置中虚构为可切分维度。
- 单位版本：CNY 或 PERCENT。

派生指标从工作台选择已发布依赖，不重复粘贴一套脱离指标版本控制的 SQL。

## 11. 资产、血缘和权限

入口：

- 资产：`/catalog/assets`
- 血缘：`/catalog/lineage/graph`
- 授权：`/governance/asset-grants`

### 11.1 ADS 资产

为 `it_fin_demo_ads_finance_overview` 补充：

| 属性 | 值 |
|---|---|
| 业务名称 | 财务预算概览 |
| owner | 实际财务数据负责人 |
| 责任部门 | 实际组织 |
| 密级 | 平台既有公开级 |
| 业务标签 | `IT-FIN-DEMO`、`BUDGET-GOVERNANCE` |

只有标准、质量、owner、密级、血缘和权限证据齐全后添加 `GOVERNED`。

### 11.2 血缘

确认：

- 3 张源表到 3 张 ODS。
- ODS 到 3 个 DWD 维度和 1 个 DWD 事实。
- DWD → DWS → ADS。
- ADS → 指标/BI/API/数据产品。
- 至少验证 `actual_amount → execution_rate` 等关键字段级血缘。

### 11.3 权限

创建或绑定：

- 财务 Demo 管理员：read/write/export。
- 财务 Demo 查看者：read/export。
- 无授权用户：不得读取 ADS、BI、API 和数据产品。

分别用三个角色验证正向和拒绝证据。

## 12. BI、API 和数据产品

入口：

- BI：`/bi/dashboards`
- API：`/services/apis`
- 数据产品：`/catalog/data-products`

### 12.1 BI

名称：`财务预算执行概览`。

只选择 `it_fin_demo_ads_finance_overview`，建议组件：

- 最新快照预算、实际、承诺、预测 KPI 卡。
- 成本中心预算执行率柱状图。
- 预算健康状态分布。
- 快照日期趋势。

### 12.2 API

名称：`财务预算概览 API`。

只暴露 ADS 必需字段，至少支持：

- `snapshot_date`
- `cost_center_code`
- `budget_health_code`

过滤条件不允许拼接任意 SQL。使用查看者凭据验证成功，使用无授权账号验证 401/403。

### 12.3 数据产品

名称：`财务预算治理数据产品`。

内容：

- ADS 财务概览资产。
- 8 个治理指标。
- owner、责任部门、密级、使用说明和质量状态。
- BI 和 API 入口。

质量失败时不得把数据产品标为可验收状态。

## 13. 三阶段执行

### 13.1 G1 基线

1. 已运行 `01-source-bootstrap.sql`。
2. 运行入湖。
3. 运行模型编译/测试/物化。
4. 运行质量规则。
5. 验证 DWS/ADS 各 4 行。
6. 验证 8 个指标和 ADS 抽样值。
7. 归档 run ID 和证据。

### 13.2 G2 注入脏数据并验证阻断

```bash
psql "$IT_FIN_DEMO_SOURCE_DSN" -v ON_ERROR_STOP=1 \
  -f worklog/v2.2.3/it/finance/sql/02-source-dirty-cases.sql
```

重新入湖并运行。期望失败：

- 科目类别/管控类型标准码。
- 成本中心引用。
- 预算必须大于 0。
- 金额非负。
- 应付不大于实际。
- 预测不小于实际。
- 财年与快照日期一致。

DWS/ADS 的新 revision 和消费发布必须被阻断或跳过。上一次成功物化表可以作为 last-good 保留，但必须显示旧快照时间/质量状态，不能把它误报为脏数据批次成功。保留失败 run ID，不删除失败记录。

### 13.3 G3 修复并增加第三期快照

```bash
psql "$IT_FIN_DEMO_SOURCE_DSN" -v ON_ERROR_STOP=1 \
  -f worklog/v2.2.3/it/finance/sql/03-source-remediation-and-increment.sql
```

重新入湖和运行，预期：

```text
源/ODS：3 / 4 / 12
DWD 日期：730
DWD 成本中心：3
DWD 预算科目：4
DWD 预算事实：12
DWS：6
ADS：6
```

同时验证：

- `CC-RD` 名称 TYPE1 更新为“研发与创新费用中心”。
- `BA-SERVICE` 的“服务费/软管控”别名映射成功。
- 2026-08-04 CC-RD 为 `BH-GREEN`。
- 2026-08-04 CC-QA 为 `BH-AMBER`。
- 失败后的质量状态恢复，审计链连续。

## 14. 可选回收

仅在明确结束 Demo 且已保存证据后运行：

```bash
psql "$IT_FIN_DEMO_SOURCE_DSN" -v ON_ERROR_STOP=1 \
  -f worklog/v2.2.3/it/finance/sql/99-source-cleanup.sql
```

脚本要求输入 `DROP_IT_FIN_DEMO_SRC`，只删除源端 `it_fin_demo_src`。DTS 内部 ODS、模型、标准和审计对象需按平台生命周期单独下线，不能假定源 Schema 删除会自动清理治理对象。
