# Demo 架构与范围

## 1. 为什么选择三张源表

“组织—项目—任务快照”是可以验证数据治理关系的最小集合：

- 组织体现可复用主数据、层级和受治理变更。
- 项目体现稳定业务编码、责任组织和业务主数据。
- 任务快照体现事实粒度、时间语义、状态码映射、金额、比率和周期增量。

继续增加人员、供应商、预算、质量问题等源表，会增加录入量，却不会新增关键的 DTS 模块关系，因此不属于本 Demo。

## 2. 物理隔离

```text
独立 Demo PostgreSQL
└── it_demo_src
    ├── org
    ├── project
    └── task_snapshot

DTS 入湖目标
├── ods_it_demo_org
├── ods_it_demo_project
└── ods_it_demo_task_snapshot

DTS/dbt 模型目标
├── it_demo_dwd_dim_date
├── it_demo_dwd_dim_org
├── it_demo_dwd_dim_project
├── it_demo_dwd_fct_task_snapshot
├── it_demo_dws_project_health
└── it_demo_ads_project_overview
```

源库优先使用独立数据库。如果只能复用现有 PostgreSQL 实例，必须使用独立账号和 `it_demo_src` Schema，并限制该账号不能访问客户 ODS Schema。

## 3. DTS 模块关系

| 阶段 | DTS 模块/入口 | 本 Demo 产物 | 下游依赖 |
|---|---|---|---|
| 数据集成 | 数据源、入湖任务 | 数据源连接、3 个同步任务或一个多表任务、3 张 Demo ODS | 元数据、规划来源 |
| 元数据 | 元数据管理、资产目录 | 表字段快照、owner、业务描述、密级 | 建设计划、标准、模型 |
| 数仓规划 | 业务分类、数据集市、建设计划 | 业务域、业务过程、集市、计划和来源基线 | 维度和模型 |
| 数据标准 | 术语、数据元、码表、单位 | 稳定标准 ID/版本和字段落标 | 模型发布、质量、指标 |
| 数据建模 | 维度目录、模型中心 | 3 个维度定义、6 个 ModelSpec 及 revision | 物化、血缘、指标 |
| 数据开发 | SQL/dbt、调度 | 编译制品、测试制品、运行记录 | 资产、血缘、消费 |
| 数据质量 | 质量规则与运行 | 规则绑定、失败结果、修复后通过结果 | 发布门禁、可信标签 |
| 指标治理 | 指标工作台 | 原子指标、派生指标、owner 和口径版本 | BI、API、数据产品 |
| 资产治理 | 资产目录、标签、密级 | 唯一资产身份、业务标签、密级、owner | 权限、消费 |
| 血缘 | 血缘图谱、影响分析 | source→ODS→DWD→DWS→ADS | 变更评估、验收 |
| 权限 | 资产授权、访问审批 | read/write/export 授权记录 | BI、API、数据产品 |
| 数据消费 | BI、API、数据产品 | 项目健康看板、查询 API、数据产品 | 业务用户 |
| 运行审计 | 实例、日志、审计证据 | 一次成功、一次质量失败、一次修复成功 | 最终验收 |

ModelSpec 是模型唯一台账；SQL/dbt 只负责同一 ModelSpec revision 的实现，不得再登记一套平行逻辑模型。

## 4. 业务对象与主数据

| 对象 | 编码 | 定义 |
|---|---|---|
| 业务域 | `IT_DEMO_PROJECT` | 研发项目计划、执行和健康状态治理 |
| 业务过程 | `IT_DEMO_PROJECT_HEALTH` | 按快照日期检查项目任务进度、风险和成本 |
| 数据集市 | `IT_DEMO_PM_MART` | 面向项目负责人和管理人员提供项目健康分析 |
| 建设计划 | 系统生成 ID | IT Demo 项目健康度建设计划 |
| 数据源 | 系统生成 ID | IT Demo PostgreSQL |

组织和项目是本 Demo 的主数据。平台当前没有独立的“主数据维护中心”作为必选步骤，因此主数据通过以下既有能力治理：

1. 数据源和元数据目录登记权威来源。
2. 数据元与公共码表约束编码和属性。
3. 维度定义声明业务主键、复用范围和层级。
4. DIMENSION ModelSpec 声明 SCD 与物理实现。
5. 质量规则保证唯一性、完整性和引用关系。
6. 资产目录维护 owner、密级、标签和生命周期。

## 5. 四类模型关系

```text
DIMENSION
  it_demo_dwd_dim_date
  it_demo_dwd_dim_org
  it_demo_dwd_dim_project
             │
             ├──────────────┐
             ▼              │
FACT                         │
  it_demo_dwd_fct_task_snapshot
             │
             ▼
SUMMARY
  it_demo_dws_project_health
             │
             ▼
APPLICATION
  it_demo_ads_project_overview
             │
             ├── 指标
             ├── BI 看板
             ├── 数据 API
             └── 数据产品
```

- DIMENSION、FACT 位于 DWD。
- SUMMARY 位于 DWS。
- APPLICATION 位于 ADS。
- ODS/STG 是接入和技术实现层，不创建为第五类 ModelSpec。
- 普通实现中的 STG 由系统管理并优先使用 ephemeral；只有真实物化的高级 STG 才登记技术资产。

## 6. 角色分工

| 角色 | 主要动作 |
|---|---|
| 平台管理员 | 创建 Demo 数据源连接、选择既有安全等级、分配基础权限 |
| 数据架构师 | 创建业务域、业务过程、数据集市、计划、维度和模型 |
| 数据管家 | 维护术语、数据元、码表、单位、owner、标签和质量规则 |
| 数据工程师 | 创建入湖任务、字段映射、模型实现、编译、测试和运行 |
| 业务分析师 | 确认指标口径、建立 BI 看板 |
| 安全管理员 | 配置 read/write/export、RLS/masking 并验证无权访问 |
| 运维人员 | 查看实例、日志、失败恢复和审计证据 |

实际 UI 选择 owner 时必须从用户/组织目录选择，不使用文档中的角色名称充当账号。

## 7. 不纳入本轮 Demo

- 不修改或迁移客户 Excel ODS。
- 不创建人员、供应商、预算等额外主数据。
- 不验证实时流、湖仓表格式或高可用。
- 不用自然语言字符串作为下游稳定编码。
- 不把密级写成业务标签。
- 不通过手工 SQL 直接伪造模型台账、资产血缘或发布成功记录。
- 不把页面可保存草稿视为构建、部署或运行成功。
