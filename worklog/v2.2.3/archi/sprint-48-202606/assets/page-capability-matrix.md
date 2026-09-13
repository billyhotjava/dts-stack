# DTS 页面能力矩阵基线

**版本**: 2026-06-18
**来源**: `portal-menu-seed.json` + `static-routes.tsx` + `dynamic-resolver.tsx`
**原则**: 先看现有页面，再拆能力；默认不新增菜单或页面。

## 主链路

`工作台 -> 数据接入 -> 数据开发 -> 数据治理 -> 数据资产 -> 指标报表/数据服务 -> 运维审计`

## 页面矩阵

| 页面 | 路径 | 事实源 | 页面/组件 | 能力归属 | 主要控件 | API/数据契约 | 风险 | 下一步 |
|------|------|--------|-----------|----------|----------|-------------|------|--------|
| 我的概览 | `/workbench` | menu + static redirect | `pages/workbench/index.tsx` | 工作台 | 自定义工作台、刷新、恢复默认、查看资产、运行健康、查看成果 | `/api/workbench/preferences` 可选；本地偏好兜底 | PARTIAL | 后续保持后端未升级时无阻塞、无噪音 |
| 待办事项 | `/workbench/todo` | menu + static route | `WorkflowCenterPage` | 工作台/流程 | 筛选、查看、处理、跳转业务单据 | workflow/task API | PARTIAL | 核对按钮是否都有真实流转 |
| 连接器目录 | `/foundation/connectors` | menu + dynamic resolver | `ConnectorRegistryPage` | 数据接入 | 创建数据源、配置、查看模板、启用、停用、分类筛选 | connector registry + source create route | REAL | 表格列宽和操作列持续纳入 Chrome95 验收 |
| 数据源管理 | `/foundation/data-sources` | menu + dynamic resolver | `DataSourcesPage` | 数据接入 | 创建、编辑、测试连接、同步内置、查看详情 | datasource API | REAL | 保持主操作和行操作来源一致 |
| 驱动管理 | `/foundation/jdbc-drivers` | menu + dynamic resolver | `JdbcDriversPage` | 数据接入 | 上传驱动、刷新、校验、启用、禁用 | driver upload/list API；部分动作后端未开放 | PARTIAL | 禁用按钮必须保留 title 说明 |
| 元数据采集 | `/catalog/metadata` | menu + dynamic resolver | `MetadataPage` | 数据接入/资产 | 采集、查看结果、同步资产 | metadata ingestion API | PARTIAL | 补采集结果与资产门户的下一步关系 |
| 数据入湖配置 | `/explore/etl/transform` | menu + static route | `TransformPage` | 数据开发 | 新建任务、运行、查看详情、执行历史 | transform/ingestion API | REAL | 保持与数据源、ODS 配置的链路提示 |
| 脚本开发 | `/explore/etl/scripts` | menu + dynamic resolver | `ScriptStudioPage` | 数据开发 | 新建脚本、保存、运行、查看日志 | script/runtime API | PARTIAL | 防止脚本能力成为孤立入口 |
| 任务编排 | `/explore/etl/orchestration` | menu + dynamic resolver | `OrchestrationPage` | 数据开发/运维 | 画布、保存、运行、调度、查看运行 | orchestration API | REAL | 编排页需和任务运维中心双向跳转 |
| 即席查询 | `/explore/workbench` | menu + dynamic resolver | `SqlIdePage` 或 `QueryWorkbenchPage` | 数据开发 | 执行 SQL、保存 Tab、导出、图表、计划 | SQL workbench API | REAL | 入口文案避免暴露内部 IDE 概念 |
| 项目空间管理 | `/studio/projects` | menu + static route | `ModelTemplatesPage` | 数据开发 | 创建项目、模板、批量导入、查看建模链路 | modeling/template API | REAL | 保持项目空间和 SQL 建模的主次关系 |
| 逻辑建模（SQL） | `/studio/sql-modeling` | menu + static route | `SqlModelingPage` | 数据开发/建模 | 新建、构建、发布、治理检查、归档 | modeling API + governance gate | REAL | 后续和语义层 Sprint-44 避免重复 |
| 项目文件浏览 | `/modeling/dbt-files` | menu + dynamic resolver | `DbtFileBrowserPage` | 数据开发/建模 | 浏览、编辑、上传、运行、删除 | dbt file API | PARTIAL | 客户文案使用“建模项目文件” |
| 主题域管理 | `/governance/subjects` | menu + dynamic resolver | `SubjectAreasPage` | 数据治理 | 新建主题、编辑、删除、层级维护 | subject/domain API | REAL | 现场主题由客户配置，不内置场景 |
| 标准管理 | `/governance/standards/*` | menu + dynamic resolver | `GlossaryPage`/`ElementsPage`/`ReferenceCodesPage` | 数据治理 | 新建、编辑、删除、导入、映射、回滚 | standard/glossary/element/reference API | REAL | 权限禁用态必须说明原因 |
| 质量管控 | `/governance/rules` | menu + dynamic resolver | `QualityRulesPage` | 数据治理 | 新建规则、试运行、运行、发布、下线、删除 | quality rule API | REAL | 质量问题需能进入处置或报告 |
| 质量报告 | `/governance/quality` | menu + dynamic resolver | `QualityReportPage` | 数据治理 | 查看报告、筛选、修复入口 | quality report API | PARTIAL | 核对修复入口是否真实可达 |
| 数据资产门户 | `/catalog/assets` | menu + dynamic resolver | `DatasetsPage` | 数据资产 | 地图、表格、筛选、治理缺口、详情 | catalog/assets API | REAL | 资产地图与资产台账避免重复表达 |
| 数据搜索 | `/catalog/search` | menu + dynamic resolver | `DataSearchPage` | 数据资产 | 搜索、筛选、进入详情 | search API | REAL | 搜索空态需给下一步配置建议 |
| 数据产品 | `/catalog/data-products` | menu + dynamic resolver | `DataProductsPage` | 数据资产/消费 | 创建、发布、申请、查看 | data product API | REAL | 和服务中心“数据推送”命名需区分 |
| 血缘与影响分析 | `/catalog/lineage/*` | menu + static route | `LineagePage` | 数据治理/资产 | 影响分析、血缘图谱、字段血缘、导入、快照 | lineage API | REAL | 多子路径保持同一页面框架 |
| 权限申请 | `/security/dataset-access-approval` | menu + dynamic resolver | `DatasetAccessApprovalPage` | 数据治理/权限 | 申请、审批、查看记录 | approval/grant API | REAL | 和工作台待办建立互跳 |
| BI 分析 | `/bi/dashboards` `/bi/questions` `/bi/data` | menu + static route | analytics pages | 指标报表 | 新建、编辑、运行、保存、发布 | analytics API | REAL | 保持 BI 页和数据资产来源一致 |
| 数据大屏 | `/bi/screens` | menu + static route | `ScreensPage` | 指标报表/大屏 | 新建、编辑、发布、权限、导入导出 | screen API | REAL | 大屏编辑器继续做 Chrome95 布局验收 |
| 指标与语义 | `/bi-apps/metrics/*` | menu + static frame | `MetricsServiceFrame` | 指标语义 | 指标工作台、指标资产、语义建模、发布运行 | metrics service frame | DUPLICATE | Sprint-44/47 决定原生化与退役节奏 |
| 数据 API 管理 | `/services/apis` | menu + dynamic resolver | `ApiServicesPage` | 数据服务 | 创建服务、发布、查看调用、审计 | API service contract；部分动作未开放 | PARTIAL | 禁用态继续说明后端缺口 |
| 数据推送 | `/services/products` | menu + dynamic resolver | `DataProductsPage` | 数据服务 | 配置、发布、下线、归档 | outbound/data product API | PARTIAL | 名称避免和资产门户“数据产品”混淆 |
| 共享交换 | `/services/tokens` | menu + dynamic resolver | `TokensPage` | 数据服务 | 创建令牌、启停、审计 | token API；审计部分未接入 | PARTIAL | 审计按钮禁用原因必须保留 |
| 任务运维中心 | `/ops/*` | menu + static/dynamic route | ops pages | 运维审计 | 运行概览、实例、告警、补数 | ops/airflow/audit API | REAL | 和编排页、工作台待办形成闭环 |

## 首批整改优先级

| 优先级 | 页面/领域 | 原因 |
|--------|-----------|------|
| P0 | `/workbench` | 企业级数据中台首页，客户第一入口 |
| P0 | `/foundation/*` | 数据接入链路是黄金链路起点 |
| P0 | `/studio/*` + `/explore/*` | 数据开发页必须串到模型发布和治理门禁 |
| P0 | `/governance/*` + `/catalog/*` | 治理和资产是中台可信度核心 |
| P1 | `/services/*` | 服务化交付中仍存在后端未开放动作 |
| P1 | `/bi-apps/metrics/*` | 与 Sprint-44/47 语义整合大计划耦合 |

## 现场定义项

- 首页业务主题名称和排序
- 客户行业指标口径
- 报表推荐规则
- 数据产品分组方式
- 经营、质量、项目、客户等业务场景名称

这些内容不能作为产品内置 demo 固化，只能通过配置、空态提示或现场实施方案定义。
