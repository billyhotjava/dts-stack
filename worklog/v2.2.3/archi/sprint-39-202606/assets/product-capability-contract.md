# 结构化数据黄金链路能力契约

## CAPABILITY

面向传统行业结构化数据客户，平台提供一条从数据接入到业务消费的可验收黄金链路。数据工程师负责接入、建模和治理门禁；数据管理员负责资产、权限、审批和运维；业务用户最终通过指标、报表、大屏、API 或数据产品消费治理后的数据，而不需要理解 SQL、dbt、ODS/DWD/DWS/ADS 的底层细节。

## CONSTRAINTS

- 结构化数据优先：JDBC/API/file 是 GA 路径，其他复杂数据形态进入后续 backlog。
- `dts-platform` 是资产、权限、RLS、审计、审批、dbt 发布、BI 注册控制面。
- `dts-ingestion` 是入湖执行面，负责任务执行、raw/ODS 落地、checkpoint、执行状态和执行侧证据。
- `dts-metrics` 是语义/指标/候选 artifact 面，只在 DWS/ADS 之上工作。
- 普通报表入口默认只展示 DWS/ADS；DWD 是高级建模入口；ODS/STG 是血缘和诊断入口。
- 治理门禁必须 fail-closed：无法确认权限、血缘、质量或分级时，不允许进入发布态。
- 业务展示必须使用客户/Excel 语言，不把 SQL、dbt 文件名、内部表名作为默认解释。

## IMPLEMENTATION CONTRACT

### Actors

| Actor | 职责 |
|-------|------|
| 数据工程师 | 创建数据源、入湖任务、建模方案、发布 DWD/DWS/ADS |
| 数据管理员 | 配置治理规则、资产 owner、分级分类、权限审批、数据产品 |
| 业务用户 | 选择指标/维度/报表/数据产品，不接触 SQL |
| 运维人员 | 查看运行状态、失败实例、告警、补数、重试和证据 |

### Surfaces

- 数据接入中心：数据源、连接器、驱动、元数据采集、入湖配置。
- 数据开发中心：项目空间、SQL/dbt 建模、dbt 文件浏览、发布门禁。
- 数据治理中心：主题域、术语、数据元、公共码表、质量、分级分类。
- 数据资产门户：资产地图、搜索、台账、血缘、影响分析、权限申请。
- 任务运维中心：运行概览、任务实例、告警记录、补数管理。
- 数据服务中心：API 服务、数据产品、共享 token。
- 商业智能应用/数据大屏：指标语义、BI 数据集、分析看板、大屏。

### States

黄金链路实例状态：

`DRAFT -> SOURCE_READY -> INGESTION_READY -> ODS_READY -> MODEL_READY -> GOVERNANCE_READY -> RELEASE_READY -> CONSUMABLE -> OPERATED`

失败或阻断状态：

- `BLOCKED_SOURCE`: 数据源不可连接、凭据无效、驱动缺失。
- `BLOCKED_INGESTION`: 入湖任务失败、checkpoint 异常、落地表异常。
- `BLOCKED_MODEL`: dbt compile/test/build 失败或模型契约缺失。
- `BLOCKED_GOVERNANCE`: owner、分级、质量、血缘、标准映射缺失。
- `BLOCKED_PERMISSION`: 权限审批、RLS/masking 或服务授权缺失。
- `BLOCKED_CONSUMPTION`: BI/API/数据产品注册失败或消费侧不可见。

### Interface/Data Implications

- 新增或复用黄金链路实例聚合接口，避免前端跨 8 个中心自行拼状态。
- 入湖任务、dbt 模型、Catalog 资产、指标模型、BI 数据集、API 服务、数据产品必须共享可追踪的 `chainId` 或等价 correlation key。
- 运行证据统一记录：触发人、开始/结束时间、状态、失败分类、日志位置、外部运行 ID、治理门禁结果。
- 数据产品/API/报表发布必须记录依赖资产版本和治理快照。

## NON-GOALS

- 不重写整个数据平台。
- 不把所有历史页面一次性并入单页面。
- 不把现代湖仓表格式作为当前交付依赖。
- 不允许 LLM 或自由 SQL 绕过治理门禁直接生成生产报表。

## OPEN QUESTIONS

- 黄金链路实例是否单独建表，还是先用现有 run log + asset mapping 聚合视图实现。
- 生产环境 dbt 发布门禁的阻断级别是否按环境开关区分 dev/demo/prod。
- 现有存量 dbt/semantic/BI 资产迁移是否纳入本 sprint IT，还是仅产出迁移清单。
- 数据服务 API 是否本 sprint 接入真实网关执行，还是先完成目录/授权/指标运营闭环。

## HANDOFF

本契约已足够进入 Sprint-39 方案拆解。执行时先做 F1 状态模型和聚合契约，再落 F2/F3 主链路门禁，最后补 F4 运维工作台和 F5 业务消费闭环。
