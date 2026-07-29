# Demo 数据与治理设计

## 1. 源数据契约

### 1.1 `it_demo_src.org`

粒度：一个组织一行。

| 字段 | 类型 | 含义 | 治理要求 |
|---|---|---|---|
| `org_code` | varchar(32) | 组织稳定编码 | ASCII；非空；唯一 |
| `org_name` | varchar(100) | 组织名称 | 非空 |
| `parent_org_code` | varchar(32) | 上级组织编码 | 可空；引用同一主数据 |
| `org_level_code` | varchar(32) | 组织层级稳定码 | `OL-HQ`/`OL-DEPT` |
| `record_status_code` | varchar(32) | 记录状态 | `RS-ACTIVE`/`RS-INACTIVE` |
| `updated_at` | timestamptz | 源端更新时间 | 非空 |
| `source_batch_id` | varchar(64) | 源批次号 | 非空 |

### 1.2 `it_demo_src.project`

粒度：一个项目一行。

| 字段 | 类型 | 含义 | 治理要求 |
|---|---|---|---|
| `project_code` | varchar(32) | 项目稳定编码 | ASCII；非空；唯一 |
| `project_name` | varchar(200) | 项目名称 | 非空 |
| `owner_org_code` | varchar(32) | 责任组织编码 | 必须命中组织主数据 |
| `manager_name` | varchar(100) | 合成项目负责人 | Demo 展示属性，不作为人员主键 |
| `project_status_code` | varchar(32) | 项目状态稳定码 | `PJ-ACTIVE`/`PJ-CLOSED` |
| `plan_start_date` | date | 计划开始日期 | 不晚于计划结束日期 |
| `plan_end_date` | date | 计划结束日期 | 非空 |
| `budget_amount` | numeric(18,2) | 项目预算 | 非负；本轮不直接作为维度属性发布 |
| `updated_at` | timestamptz | 源端更新时间 | 非空 |
| `source_batch_id` | varchar(64) | 源批次号 | 非空 |

### 1.3 `it_demo_src.task_snapshot`

粒度：一个任务在一个快照日期一行。

| 字段 | 类型 | 含义 | 治理要求 |
|---|---|---|---|
| `task_code` | varchar(32) | 任务稳定编码 | 与快照日期组成唯一键 |
| `snapshot_date` | date | 业务快照日期 | FACT 的业务时间 |
| `project_code` | varchar(32) | 所属项目编码 | 必须命中项目主数据 |
| `task_name` | varchar(200) | 任务名称 | 非空 |
| `owner_name` | varchar(100) | 合成任务负责人 | 仅展示，不建人员主数据 |
| `task_status_raw` | varchar(32) | 源任务状态 | 必须映射到标准码 |
| `risk_level_raw` | varchar(32) | 源风险等级 | 必须映射到标准码 |
| `plan_start_date` | date | 计划开始日期 | 不晚于计划结束日期 |
| `plan_end_date` | date | 计划结束日期 | 用于判断延期 |
| `actual_finish_date` | date | 实际完成日期 | 未完成时可空 |
| `progress_pct` | numeric(5,2) | 完成进度 | 0～100 |
| `plan_cost` | numeric(18,2) | 计划成本 | 非负 |
| `actual_cost` | numeric(18,2) | 实际成本 | 非负 |
| `updated_at` | timestamptz | 源端更新时间 | 非空 |
| `source_batch_id` | varchar(64) | 源批次号 | 非空 |

基线脚本执行后的行数应为：

| 表 | 行数 |
|---|---:|
| `org` | 3 |
| `project` | 2 |
| `task_snapshot` | 8 |

源表故意不创建组织/项目外键和业务值域 CHECK，以便 DTS 质量模块能检测引用和语义错误；主键、唯一约束和检索索引仍显式创建。

## 2. ODS 目标约定

由 DTS 入湖任务创建或写入：

| 来源 | ODS目标 |
|---|---|
| `it_demo_src.org` | `ods_it_demo_org` |
| `it_demo_src.project` | `ods_it_demo_project` |
| `it_demo_src.task_snapshot` | `ods_it_demo_task_snapshot` |

ODS 除源字段外至少保留平台实际生成的导入时间字段。若任务支持，应同时启用来源表、批次和记录哈希等审计元数据。不得为了满足本文档而修改客户 ODS 的公共结构。

## 3. 业务术语

| 稳定编码 | 名称 | 定义 |
|---|---|---|
| `IT-BT-PROJECT` | 研发项目 | 为形成明确研发成果而设立的受计划、成本和责任组织约束的工作集合 |
| `IT-BT-TASK` | 项目任务 | 项目内具有明确负责人、计划区间和完成状态的最小跟踪单元 |
| `IT-BT-TASK-SNAPSHOT` | 任务快照 | 在指定统计日期记录的任务状态、风险、进度和成本事实 |
| `IT-BT-COMPLETION-RATE` | 任务完成率 | 已完成任务数除以任务总数 |
| `IT-BT-OVERDUE-TASK` | 延期任务 | 快照日未完成且计划结束日期早于快照日，或源状态明确标识为延期的任务 |
| `IT-BT-PROJECT-HEALTH` | 项目健康状态 | 综合延期、高风险、完成进度和成本偏差形成的项目管理提示 |

术语只表达业务语义，不保存 SQL、表名或 dbt 文件路径。

## 4. 数据元与计量单位

计划治理策略选择“键字段和度量字段”，避免为 Demo 人工建立大量无复用价值的数据元。

| 数据元编码 | 名称 | 推荐类型 | 可复用字段 |
|---|---|---|---|
| `IT-DE-RECORD-ID` | 记录稳定标识 | string | 各模型派生主键 |
| `IT-DE-DATE-KEY` | 日期代理键 | integer | `date_key` |
| `IT-DE-ORG-CODE` | 组织编码 | string(32) | `org_code`、`owner_org_code` |
| `IT-DE-PROJECT-CODE` | 项目编码 | string(32) | `project_code` |
| `IT-DE-TASK-CODE` | 任务编码 | string(32) | `task_code` |
| `IT-DE-COUNT` | 计数值 | integer | 任务数、完成数、延期数、高风险数 |
| `IT-DE-PERCENT` | 百分比 | decimal(7,4) | 进度、完成率、延期率 |
| `IT-DE-AMOUNT-CNY` | 人民币金额 | decimal(18,2) | 计划成本、实际成本、成本偏差 |
| `IT-DE-STATUS-CODE` | 状态标准编码 | string(32) | 项目、任务、健康状态 |

优先复用平台已有单位，不重复创建：

| 单位编码 | 名称 | 符号 | 精度 |
|---|---|---|---:|
| `COUNT` | 个 | 个 | 0 |
| `PERCENT` | 百分比 | `%` | 2 |
| `CNY` | 人民币元 | 元 | 2 |

数据元、码表和单位应保存稳定 ID 与版本引用；模型不能只保存中文名称。

## 5. 公共码表

### 5.1 任务状态 `IT-RC-TASK-STATUS`

| 原始值 `code` | 标准码 `standard_code` | 标签 | 排序 |
|---|---|---|---:|
| 未开始 | `TS-NOT-STARTED` | 未开始 | 10 |
| 待启动 | `TS-NOT-STARTED` | 未开始 | 10 |
| 进行中 | `TS-IN-PROGRESS` | 进行中 | 20 |
| 执行中 | `TS-IN-PROGRESS` | 进行中 | 20 |
| 已完成 | `TS-DONE` | 已完成 | 30 |
| 完成 | `TS-DONE` | 已完成 | 30 |
| 延期 | `TS-OVERDUE` | 已延期 | 40 |
| 已延期 | `TS-OVERDUE` | 已延期 | 40 |
| NULL/空值 | `TS-UNKNOWN` | 未知 | 99 |

### 5.2 风险等级 `IT-RC-RISK-LEVEL`

| 原始值 `code` | 标准码 `standard_code` | 标签 | 严重度 |
|---|---|---|---:|
| 低 | `RL-LOW` | 低风险 | 1 |
| 低风险 | `RL-LOW` | 低风险 | 1 |
| L | `RL-LOW` | 低风险 | 1 |
| 中 | `RL-MID` | 中风险 | 2 |
| 中风险 | `RL-MID` | 中风险 | 2 |
| M | `RL-MID` | 中风险 | 2 |
| 高 | `RL-HIGH` | 高风险 | 3 |
| 高风险 | `RL-HIGH` | 高风险 | 3 |
| H | `RL-HIGH` | 高风险 | 3 |
| NULL/空值 | `RL-UNKNOWN` | 未知 | 0 |

STG/DWD 映射前应清除前后空格、BOM、零宽字符并统一大小写。未识别值必须产生阻断级质量错误；不得静默映射成“其他”。

### 5.3 项目健康状态 `IT-RC-PROJECT-HEALTH`

| 原始/派生值 | 标准码 `standard_code` | 标签 | 严重度 |
|---|---|---|---:|
| GREEN | `PH-GREEN` | 健康 | 1 |
| AMBER | `PH-AMBER` | 关注 | 2 |
| RED | `PH-RED` | 预警 | 3 |
| NULL/空值 | `PH-UNKNOWN` | 未知 | 0 |

项目健康状态是 Demo 派生分类，不能直接输出未加前缀的颜色单词作为下游稳定编码。

## 6. 业务维度定义

### 6.1 日期

| UI属性 | 值 |
|---|---|
| 名称 | 日期 |
| 定义 | 统一的公历日期分析维度 |
| 业务主键属性 | `DATE_KEY` |
| 复用范围 | TENANT |
| 历史策略 | NONE |
| 层级 | 年→月→日期 |

### 6.2 组织

| UI属性 | 值 |
|---|---|
| 名称 | 组织机构 |
| 定义 | Demo 中承担项目责任的组织主数据 |
| 业务主键属性 | `ORG_CODE` |
| 复用范围 | DOMAIN |
| 历史策略 | TYPE1 |
| 层级 | 总部→部门 |

本 Demo 的周期历史由任务快照事实承担。组织维度只验证当前名称更新，不额外引入快照技术表。

### 6.3 项目

| UI属性 | 值 |
|---|---|
| 名称 | 研发项目 |
| 定义 | 具有稳定项目编码、计划周期和责任组织的项目主数据 |
| 业务主键属性 | `PROJECT_CODE` |
| 复用范围 | DOMAIN |
| 历史策略 | TYPE1 |
| 层级 | 不配置 |

维度定义创建后先处于 DRAFT，必须“设为现行”后才能被 DIMENSION ModelSpec 引用。

## 7. 六个模型

逐字段信息见 `assets/model-field-matrix.csv`。

### 7.1 `it_demo_dwd_dim_date`

| 属性 | 值 |
|---|---|
| 类型/层 | DIMENSION / DWD |
| 粒度 | 一个公历日期一行 |
| 粒度键 | `date_key` |
| 维度定义 | 日期 |
| 生成方式 | `GENERATED` / `DATE_DIMENSION` |
| 日期范围 | 2026-01-01 至 2027-12-31 |
| 物化 | table / FULL |

### 7.2 `it_demo_dwd_dim_org`

| 属性 | 值 |
|---|---|
| 类型/层 | DIMENSION / DWD |
| 粒度 | 一个组织一行 |
| 粒度键 | `org_code` |
| 维度定义 | 组织机构 |
| SCD | TYPE1 |
| 来源 | `ods_it_demo_org` 的已确认 revision |
| 物化 | table |
| 业务键 | `org_code` |

### 7.3 `it_demo_dwd_dim_project`

| 属性 | 值 |
|---|---|
| 类型/层 | DIMENSION / DWD |
| 粒度 | 一个研发项目一行 |
| 粒度键 | `project_code` |
| 维度定义 | 研发项目 |
| SCD | TYPE1 |
| 来源 | `ods_it_demo_project` 的已确认 revision |
| 物化 | table / FULL |

### 7.4 `it_demo_dwd_fct_task_snapshot`

| 属性 | 值 |
|---|---|
| 类型/层 | FACT / DWD |
| 粒度 | 一个项目任务在一个快照日期一行 |
| 粒度键 | `task_snapshot_id` |
| 事实形态 | `PERIODIC_SNAPSHOT` |
| 时间语义 | `SNAPSHOT_DATE` |
| 时间字段 | `snapshot_date` |
| 分析维度 | 日期、组织机构、研发项目 |
| 来源 | 已确认的 `ods_it_demo_task_snapshot` 和 `ods_it_demo_project` revision |
| 物化 | table / FULL |
| 去重 | `task_snapshot_id` |

`task_snapshot_id` 推荐按规范化后的 `task_code + snapshot_date` 生成稳定哈希。`snapshot_date` 单独作为 TIME 字段，不能同时承担 UI 粒度 KEY。

普通实现可完成来源选择、字段映射、类型转换、受控 JOIN 和去重，但不能表达本模型的码值归一、派生键和派生度量。本 Demo 因此保留同一 ModelSpec，在高级 SQL/dbt 工作区使用 `dbt/` 中的实现；不得另建第二套模型台账。

派生度量：

```text
task_count = 1
completed_task_count = task_status_code = TS-DONE
overdue_task_count =
  task_status_code = TS-OVERDUE
  OR (task_status_code <> TS-DONE AND plan_end_date < snapshot_date)
high_risk_task_count = risk_level_code = RL-HIGH
cost_variance_amount = actual_cost_amount - plan_cost_amount
```

### 7.5 `it_demo_dws_project_health`

| 属性 | 值 |
|---|---|
| 类型/层 | SUMMARY / DWS |
| 粒度 | 一个项目在一个快照日期一行 |
| 粒度键 | `project_health_id` |
| 上游 | 锁定 revision 的任务快照事实、项目维度、组织维度 |
| 物化 | table / FULL |

派生口径：

```text
completion_rate = completed_task_count / nullif(task_total, 0)
overdue_rate = overdue_task_count / nullif(task_total, 0)
health_status_code =
  PH-RED    when overdue_task_count > 0 or high_risk_task_count > 0
  PH-AMBER  when completion_rate < 0.50
  PH-GREEN  otherwise
```

健康状态只是 Demo 管理提示，不是安全密级，也不应作为自动决策结论。

### 7.6 `it_demo_ads_project_overview`

| 属性 | 值 |
|---|---|
| 类型/层 | APPLICATION / ADS |
| 粒度 | 一个项目在一个快照日期一行 |
| 粒度键 | `project_overview_id` |
| 上游 | 锁定 revision 的项目健康汇总 |
| 消费场景 | 项目负责人查看最新项目进度、延期、风险和成本概览 |
| 物化 | table / FULL |

该模型是 BI、API 和数据产品的默认消费入口；普通业务用户不直接选择 ODS、STG 或 DWD。

## 8. 质量规则

| 规则编码 | 对象 | 规则 | 基线期望 | 脏数据期望 |
|---|---|---|---|---|
| `IT-QR-ORG-CODE-UNIQUE` | 组织维度 | `org_code` 当前版本唯一且非空 | 通过 | 通过 |
| `IT-QR-PROJECT-ORG-REF` | 项目维度 | `owner_org_code` 命中当前组织 | 通过 | 失败 |
| `IT-QR-TASK-SNAPSHOT-UNIQUE` | 任务事实 | `task_snapshot_id` 唯一且非空 | 通过 | 通过 |
| `IT-QR-TASK-PROJECT-REF` | 任务事实 | `project_code` 命中项目维度 | 通过 | 失败 |
| `IT-QR-TASK-STATUS` | 任务事实 | 状态属于标准码白名单 | 通过 | 失败 |
| `IT-QR-RISK-LEVEL` | 任务事实 | 风险属于标准码白名单 | 通过 | 失败 |
| `IT-QR-PROGRESS-RANGE` | 任务事实 | `progress_pct between 0 and 100` | 通过 | 失败 |
| `IT-QR-COST-NONNEGATIVE` | 任务事实 | 计划和实际成本均非负 | 通过 | 失败 |
| `IT-QR-PLAN-DATE-ORDER` | 任务事实 | 计划开始日期不晚于计划结束日期 | 通过 | 失败 |
| `IT-QR-SNAPSHOT-FRESHNESS` | 任务事实 | 最新快照在约定周期内 | 通过 | 按运行日期判断 |

规则严重度均使用阻断级。执行错误和数据不合格必须分开显示；SQL/连接错误不得被算作“100%通过”。

## 9. 指标

优先从已发布的 DWS 模型 MEASURE 字段创建原子指标草稿，保留 ModelSpec ID、revision、字段名和计量单位版本。

| 指标编码 | 名称 | 类型 | 字段/公式 | 单位 |
|---|---|---|---|---|
| `IT_TASK_TOTAL` | 任务总数 | 原子 | `SUM(task_total)` | COUNT |
| `IT_TASK_COMPLETED` | 已完成任务数 | 原子 | `SUM(completed_task_count)` | COUNT |
| `IT_TASK_OVERDUE` | 延期任务数 | 原子 | `SUM(overdue_task_count)` | COUNT |
| `IT_TASK_HIGH_RISK` | 高风险任务数 | 原子 | `SUM(high_risk_task_count)` | COUNT |
| `IT_ACTUAL_COST` | 实际成本 | 原子 | `SUM(actual_cost_amount)` | CNY |
| `IT_COMPLETION_RATE` | 任务完成率 | 派生 | `{{metric:IT_TASK_COMPLETED}} / nullif({{metric:IT_TASK_TOTAL}}, 0)` | PERCENT |

所有指标填写业务口径、业务负责人、技术负责人、责任部门、时间粒度、日期字段和分析维度，并由 owner 人工确认后发布。派生指标只能依赖已发布治理指标。

## 10. 资产、标签、密级和权限

### 10.1 业务标签

建议只建立以下业务标签：

- `IT-DEMO`：合成演示资产
- `PROJECT-HEALTH`：项目健康分析
- `GOVERNED`：已完成本 Demo 治理门禁

`GOVERNED` 只能在标准、质量、owner、密级、血缘和权限证据齐全后使用。

### 10.2 密级

本 Demo 全部是合成数据，应选择平台现有的 `DATA_PUBLIC`/“公开”级别。若租户目录中编码不同，选择现有等价项，不另造业务标签代替密级。

### 10.3 权限

| 角色 | ADS资产 | BI | API | 数据产品 |
|---|---|---|---|---|
| Demo 管理员 | read/write/export | 编辑 | 管理 | 管理 |
| Demo 业务查看者 | read/export | 查看 | 调用 | 订阅/查看 |
| 无授权用户 | 不可见或拒绝 | 不可查看数据 | 403/拒绝 | 不可订阅 |

同一 platform policy/RLS/masking 快照应被资产门户、指标、BI、API 和数据产品复用。

## 11. 血缘验收目标

至少形成以下表级血缘：

```text
it_demo_src.task_snapshot
  → ods_it_demo_task_snapshot
  → it_demo_dwd_fct_task_snapshot
  → it_demo_dws_project_health
  → it_demo_ads_project_overview
  → BI/API/数据产品
```

至少形成以下字段级血缘：

- `task_status_raw` → `task_status_code`
- `risk_level_raw` → `risk_level_code`
- `progress_pct` → `avg_progress_pct`
- `actual_cost` → `actual_cost_amount`
- `completed_task_count` 与 `task_total` → `completion_rate`

血缘必须来自入湖运行、模型制品或受控导入证据，不能只在文档中手工声明为成功。
