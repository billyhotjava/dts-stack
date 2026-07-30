# DTS UI 手工实施 Runbook

## 0. 执行前准备

准备以下信息，但不得提交到本目录：

- Demo PostgreSQL 主机、端口、数据库名。
- 仅允许访问 `it_demo_src` 的账号和凭据。
- DTS 当前租户。
- 实际数据负责人、责任部门和业务负责人。
- 平台既有公开密级的实际编码。
- 入湖目标数据源/数据湖。

在 Demo 源库运行：

```bash
psql "$IT_DEMO_SOURCE_DSN" -v ON_ERROR_STOP=1 \
  -f worklog/v2.2.3/it/project/sql/01-source-bootstrap.sql
```

不要在 shell 历史、截图或文档中显示密码。

## 1. 数据源与入湖

入口：`/foundation/data-sources`

### 1.1 创建数据源

| UI字段 | 填写值 |
|---|---|
| 名称 | IT Demo PostgreSQL |
| 类型 | PostgreSQL/JDBC |
| Host/Port/Database | 使用 Demo 源库实际值 |
| Schema | `it_demo_src` |
| 用户名/凭据 | 使用独立 Demo 账号，通过平台凭据控制面保存 |
| 负责人 | 实际数据工程师 |

执行“测试连接”，保存连接测试结果截图或响应。连接失败时停止后续步骤，不允许用手工登记表绕过。

### 1.2 Schema 探测

执行 Schema 探测，只选择：

- `it_demo_src.org`
- `it_demo_src.project`
- `it_demo_src.task_snapshot`

验收：

- 表数量为 3。
- `task_snapshot` 字段数量为 15。
- 日期、金额、时间戳类型识别正确。
- 不出现任何客户 ODS 表。

### 1.3 生成同步任务

在数据源页执行“生成同步任务”，或进入 `/explore/etl/transform` 创建入湖任务。

| 来源 | 目标表 | 建议同步模式 |
|---|---|---|
| `it_demo_src.org` | `ods_it_demo_org` | 全量覆盖或按 `updated_at` 增量 |
| `it_demo_src.project` | `ods_it_demo_project` | 全量覆盖或按 `updated_at` 增量 |
| `it_demo_src.task_snapshot` | `ods_it_demo_task_snapshot` | 按 `snapshot_date`/`updated_at` 增量 |

如果 UI 支持一个任务选择多表，可创建一个 `IT Demo 项目健康入湖` 任务；否则创建 3 个任务。表数量不变。

运行任务后在任务实例和目标库确认基线行数：

```text
ods_it_demo_org             3
ods_it_demo_project         2
ods_it_demo_task_snapshot   8
```

## 2. 元数据与资产目录

入口：

- `/catalog/metadata-management`
- `/catalog/assets`

执行结构采集并确认 3 张 ODS 表和字段已进入正式目录。为每张资产补充：

| 治理属性 | 填写要求 |
|---|---|
| 业务名称 | Demo 组织、Demo 项目、Demo 任务快照 |
| 业务描述 | 使用 `02-data-governance-design.md` 的粒度定义 |
| owner | 从用户目录选择实际负责人 |
| 责任部门 | 从组织目录选择 |
| 密级 | 选择平台既有公开级别 |
| 生命周期 | Demo/测试用途；按实施环境实际选项填写 |
| 业务标签 | 先添加 `IT-DEMO`、`PROJECT-HEALTH`；质量通过前不加 `GOVERNED` |

密级与业务标签分别维护。

## 3. 业务分类与数据集市

入口：`/governance/subjects`

### 3.1 业务域

| 字段 | 值 |
|---|---|
| 编码 | `IT_DEMO_PROJECT` |
| 名称 | 研发项目治理 |
| 定义 | 对研发项目计划、任务执行、风险和成本进行统一治理 |
| owner/责任部门 | 实际人员和组织 |

### 3.2 业务过程

| 字段 | 值 |
|---|---|
| 编码 | `IT_DEMO_PROJECT_HEALTH` |
| 名称 | 项目健康监测 |
| 定义 | 按快照日期检查项目任务进度、延期、风险和成本 |

### 3.3 数据集市

在业务域的“数据集市”页签创建：

| 字段 | 值 |
|---|---|
| 编码 | `IT_DEMO_PM_MART` |
| 名称 | 项目健康分析集市 |
| 用途 | 为项目负责人提供进度、延期、风险和成本分析 |
| 所属业务域 | 研发项目治理 |
| owner | 实际负责人 |

创建后执行“确认”，使状态变成 CURRENT/现行。

## 4. 建设计划与来源基线

入口：`/modeling/plans`

### 4.1 创建计划

| 字段 | 值 |
|---|---|
| 名称 | IT Demo 项目健康度建设计划 |
| 建设目标 | 打通 DTS 数据接入、治理、建模、质量、指标、资产、权限、血缘和消费闭环 |
| 建设范围 | 仅限 `it_demo_src`、3 张 Demo ODS 和 6 个 Demo 模型 |
| owner/责任部门 | 实际人员和组织 |
| 接入方式 | 从现有资产开始 |

### 4.2 规划策略

| 字段 | 值 |
|---|---|
| 字段标准覆盖范围 | 键字段和度量字段 |
| 质量测试要求 | 必须通过，否则阻止发布 |
| 默认时区 | `Asia/Shanghai` |

### 4.3 基线确认

依次完成：

1. 将 `IT_DEMO_PROJECT` 业务域和 `IT_DEMO_PROJECT_HEALTH` 业务过程纳入并确认。
2. 将现行数据集市 `IT_DEMO_PM_MART` 纳入计划。
3. 在“来源盘点”中从正式目录加入 3 张 Demo ODS。
4. 每个来源结论设为“确认纳入”，确认 freshness 为 CURRENT。

来源只登记 ODS 物理资产；STG 不作为计划中的第五类业务模型。

## 5. 数据标准

入口：

- 业务术语：`/governance/standards/glossary`
- 数据元：`/governance/standards/elements`
- 公共码表：`/governance/standards/reference`
- 计量单位：`/governance/standards/units`

按照 `02-data-governance-design.md` 创建或复用：

- 6 个业务术语。
- 9 个数据元。
- 3 个公共码表及别名映射。
- `COUNT`、`PERCENT`、`CNY` 三个单位；已有时直接复用。

注意：

- 标准编码全部使用大写 ASCII。
- `standard_code` 是下游稳定值，中文原始值仅用于映射。
- 标准发布/生效后再绑定模型字段。
- 不得覆盖客户已有同编码内容；若冲突，应更换 Demo 编码或停止并核查。

## 6. 维度目录

入口：`/modeling/dimensions`

在计划上下文中依次“登记业务维度”：

### 6.1 日期

- 名称：日期
- 定义：统一的公历日期分析维度
- 范围：DOMAIN
- 复用范围：TENANT
- 属性：`DATE_KEY` 主键、`FULL_DATE`、`YEAR_NO`、`MONTH_NO`、`ISO_WEEK_NO`、`IS_WORKDAY`
- 层级：`YEAR_NO` → `MONTH_NO` → `FULL_DATE`

### 6.2 组织机构

- 名称：组织机构
- 定义：承担项目责任的组织主数据
- 范围：DOMAIN
- 复用范围：DOMAIN
- 属性：`ORG_CODE` 主键、`ORG_NAME`、`PARENT_ORG_CODE`、`ORG_LEVEL_CODE`、`RECORD_STATUS_CODE`
- 层级：总部 → 部门

### 6.3 研发项目

- 名称：研发项目
- 定义：具有稳定编码、计划周期和责任组织的研发项目
- 范围：DATA_MART
- 数据集市：项目健康分析集市
- 复用范围：PLAN
- 属性：`PROJECT_CODE` 主键、`PROJECT_NAME`、`OWNER_ORG_CODE`、`MANAGER_NAME`、`PROJECT_STATUS_CODE`

每个定义保存后执行“设为现行”。只有 CURRENT 维度才会出现在新建 DIMENSION 模型的选择器中。

## 7. 模型中心

入口：`/modeling/models`

创建顺序必须遵循依赖拓扑：

1. 日期维度表
2. 组织维度表
3. 项目维度表
4. 项目任务快照事实表
5. 项目健康汇总表
6. 项目健康概览应用表

新建模型时选择当前建设计划、已确认业务域和现行数据集市。字段录入使用 `assets/model-field-matrix.csv`。

### 7.1 日期维度

- 类型：DIMENSION；层：DWD。
- 选择现行“日期”维度定义。
- 粒度声明：一个公历日期一行。
- 粒度键：`date_key`。
- 历史策略：NONE。
- 实现输入：GENERATED。
- 生成器：DATE_DIMENSION。
- 目标：`it_demo_dwd_dim_date`。

### 7.2 组织维度

- 类型：DIMENSION；层：DWD。
- 选择现行“组织机构”。
- 粒度声明：一个组织一行。
- 粒度键：`org_code`。
- SCD：TYPE1。
- 实现来源：已确认 `ods_it_demo_org` revision。
- 目标：`it_demo_dwd_dim_org`。

### 7.3 项目维度

- 类型：DIMENSION；层：DWD。
- 选择现行“研发项目”。
- 粒度声明：一个研发项目一行。
- 粒度键：`project_code`。
- SCD：TYPE1。
- 实现来源：已确认 `ods_it_demo_project` revision。
- 目标：`it_demo_dwd_dim_project`。

### 7.4 任务快照事实

- 类型：FACT；层：DWD。
- 粒度声明：一个项目任务在一个快照日期一行。
- 粒度键：`task_snapshot_id`。
- 事实形态：PERIODIC_SNAPSHOT。
- 时间语义：SNAPSHOT_DATE。
- 时间字段：`snapshot_date`。
- 分析维度：日期、组织机构、研发项目。
- 实现来源：已确认 `ods_it_demo_task_snapshot` 和 `ods_it_demo_project` revision。
- 受控关联：`src_0.project_code = src_1.project_code`；以任务快照为主表。
- 目标：`it_demo_dwd_fct_task_snapshot`。
- 物化：table。
- 装载：FULL。
- 去重：`task_snapshot_id`。

### 7.5 项目健康汇总

- 类型：SUMMARY；层：DWS。
- 粒度声明：一个项目在一个快照日期一行。
- 粒度键：`project_health_id`。
- 上游模型：锁定当前任务快照事实及所需维度 revision。
- 不选择 ODS 物理来源。
- 目标：`it_demo_dws_project_health`。
- 物化：table / FULL。

### 7.6 项目健康概览

- 类型：APPLICATION；层：ADS。
- 粒度声明：一个项目在一个快照日期一行。
- 粒度键：`project_overview_id`。
- 消费场景：项目负责人查看最新项目进度、延期、风险和成本概览。
- 上游模型：锁定当前项目健康汇总 revision。
- 不选择 ODS 物理来源。
- 目标：`it_demo_ads_project_overview`。
- 物化：table / FULL。

### 7.7 每个模型的阶段动作

```text
保存逻辑设计
  → 配置数据实现
  → 验证实现
  → 生成并发布
```

每个字段必须：

- 技术编码使用小写英文、数字和下划线。
- 填写中文业务名称。
- KEY 和 MEASURE 绑定现行数据元/单位版本。
- 状态字段绑定现行码表版本。
- 显式选择既有公开密级。
- 直接映射字段保存来源字段引用。

### 7.8 高级 dbt 实现

当前普通编译器不生成码值归一、哈希键、条件度量和汇总表达式。本 Demo 的复杂实现统一使用本目录 `dbt/` 项目，并继续绑定上述 6 个 ModelSpec：

| ModelSpec目标 | dbt unique ID |
|---|---|
| `it_demo_dwd_dim_date` | `model.it_demo_governance.it_demo_dwd_dim_date` |
| `it_demo_dwd_dim_org` | `model.it_demo_governance.it_demo_dwd_dim_org` |
| `it_demo_dwd_dim_project` | `model.it_demo_governance.it_demo_dwd_dim_project` |
| `it_demo_dwd_fct_task_snapshot` | `model.it_demo_governance.it_demo_dwd_fct_task_snapshot` |
| `it_demo_dws_project_health` | `model.it_demo_governance.it_demo_dws_project_health` |
| `it_demo_ads_project_overview` | `model.it_demo_governance.it_demo_ads_project_overview` |

操作原则：

1. 先在模型中心完成逻辑 ModelSpec 和 revision。
2. 在高级 SQL/dbt 工作区导入或写入 `dbt/` 项目。
3. 将每个 dbt 节点绑定原 ModelSpec ID/revision。
4. 选择 DBT_MANAGED/DBT_BACKED 所有权，不让普通表单覆盖高级 SQL。
5. 先执行 parse/compile/test，再生成发布候选。

dbt 工程中的 CTE 是技术实现，不新增物理 STG 表。

## 8. 质量规则

入口：`/governance/quality`

按 `02-data-governance-design.md` 建立 10 条规则，绑定到当前已发布或可执行的数据集。先运行基线：

- 规则执行状态必须为成功。
- 数据不合格数必须为 0。
- 执行错误不能被显示成质量通过。

随后执行：

```bash
psql "$IT_DEMO_SOURCE_DSN" -v ON_ERROR_STOP=1 \
  -f worklog/v2.2.3/it/project/sql/02-source-dirty-cases.sql
```

重跑入湖、模型和质量，预期组织引用、项目引用、状态、风险、进度、成本和日期规则失败。不得继续发布。

最后运行修复/增量脚本，再次入湖和检查：

```bash
psql "$IT_DEMO_SOURCE_DSN" -v ON_ERROR_STOP=1 \
  -f worklog/v2.2.3/it/project/sql/03-source-remediation-and-increment.sql
```

预期质量恢复通过，组织维度当前名称更新，事实新增 4 条 2026-08-04 快照。

## 9. 指标工作台

入口：`/modeling/metric-workbench`

选择当前已发布的 DWS 模型，依次从 MEASURE 字段创建原子指标草稿：

- `IT_TASK_TOTAL`
- `IT_TASK_COMPLETED`
- `IT_TASK_OVERDUE`
- `IT_TASK_HIGH_RISK`
- `IT_ACTUAL_COST`

原子指标至少填写：

- 指标编码、名称、分类。
- 业务口径。
- 数据集、聚合方式和度量字段。
- 时间粒度 DAY、日期字段 `snapshot_date`。
- 分析维度 `project_code`、`org_code`。
- 技术负责人、业务负责人、责任部门。
- 计量单位。
- 数据密级和隐私级别。
- 人工确认。

发布原子指标后，创建派生指标 `IT_COMPLETION_RATE`：

```text
{{metric:IT_TASK_COMPLETED}} /
nullif({{metric:IT_TASK_TOTAL}}, 0)
```

派生指标只选择已发布依赖，不复制原子指标 SQL。

## 10. 血缘、资产、权限和消费

### 10.1 血缘

入口：

- `/catalog/lineage/graph`
- `/catalog/lineage/impact`

确认 source→ODS→DWD→DWS→ADS 表级血缘和关键字段血缘。缺少 source、target 或运行证据时保持待治理，不能手工把状态改成已通过。

### 10.2 资产治理

入口：`/catalog/assets`

为 6 个模型物理资产补齐 owner、责任部门、业务描述、公开密级和业务标签。质量门禁通过后再添加 `GOVERNED`。

### 10.3 权限

入口：`/governance/asset-grants`

至少准备两个测试账号/角色：

- 已授权业务查看者：ADS read/export。
- 无授权用户：无 ADS 权限。

分别验证资产门户、指标、BI、API 和数据产品的可见性一致。

### 10.4 BI

入口：`/bi/dashboards`

从 `it_demo_ads_project_overview` 创建一个“项目健康概览”看板，最少包含：

- 项目数量/任务总数指标卡。
- 完成率指标卡。
- 延期任务数指标卡。
- 按项目展示健康状态、进度和实际成本的表格。
- 快照日期和责任组织筛选器。

### 10.5 API

入口：`/services/apis`

从 ADS 资产发布只读查询 API：

- 名称：项目健康概览 API。
- 数据资产：`it_demo_ads_project_overview`。
- 查询条件：项目编码、组织编码、快照日期。
- 返回字段：业务展示字段，不返回内部凭据、SQL 或 dbt 路径。
- 授权：复用 ADS 资产 read 权限。

### 10.6 数据产品

入口：`/catalog/data-products`

创建“项目健康分析数据产品”，关联：

- ADS 项目健康概览资产。
- 6 个治理指标。
- 项目健康概览 BI 看板。
- 项目健康概览 API。
- owner、说明、刷新频率和使用限制。

## 11. 运行与审计

入口：

- `/ops/instances`
- `/ops/audit-evidence`

至少归档三次运行：

1. 基线成功。
2. 脏数据导致质量阻断。
3. 修复和增量后的成功运行。

每次记录 run ID、开始/结束时间、输入批次、输出行数、规则结果、模型 revision、受影响资产和恢复动作。

## 12. 当前已知阻断

最终物化前必须确认当前部署已经修复以下源码契约：

- Airflow 消费租约后读取响应字段 `leaseId`：
  `services/dts-airflow/extra/dts_runtime/dbt_task_factory.py:404`
- Java `LeaseView` 实际返回字段 `profileLeaseId`：
  `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/etl/DbtRuntimeProfileLeaseService.java:768`

该问题会在租约已经消费后阻止 dbt 命令启动。仅修改源码不算闭合，必须完成：

1. Java/Python 契约统一。
2. focused test 和打包通过。
3. 部署实际镜像。
4. 真实运行确认 dbt build 启动。
5. 验证租约最终释放且可重试。

此外，当前前端仍把以下接口标为缺口：

- `GET /api/dbt/test-results`
- `GET /api/ops/run-evidence`
- `GET /api/data-product/acceptance-package`

因此本 Demo 当前使用 `evidence/` 手工归档页面、接口和数据库证据；不得声称已经自动形成完整验收包。
