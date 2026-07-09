# 页面能力矩阵

| 阶段 | 当前页面/路由 | 当前缺口 | UI 目标 | 主要文件 | 验收 |
|------|---------------|----------|---------|----------|------|
| 旅程入口 | `/workbench` | 工作台已有端到端信息，但缺少固定 journey context 和阶段进度 | 增加端到端旅程轨道、阶段状态、下一步、返回入口 | `DataManagementWorkbenchPage.tsx` | source-contract 固定 8 阶段和跳转参数 |
| 数据集成 | `/foundation/data-sources`、`/explore/etl/transform/new` | 数据源选择和建模规划衔接弱 | 数据源卡片显示“生成数仓规划/创建 ODS 草稿” | `DataSourcesPage.tsx`、`TransformCreatePage.tsx` | 数据源按钮携带 journey/sourceId |
| 数仓规划 | `/studio/low-code-development` 或新规划面板 | 缺少 ODS/DWD/DWS/ADS 的 UI 化分层规划 | 在旅程工作台提供分层规划草稿卡片和缺口状态 | `DataManagementWorkbenchPage.tsx`、`LowCodeDevelopmentPage.tsx` | 能看到 ODS_RAW、ODS_STANDARDIZED、DWD、DWS、ADS |
| 数据标准 | `/foundation/standard-package`、`/governance/standards/elements` | 标准包应用后到建模的路径还不够显眼 | 标准应用结果显示字段落标、模型草稿、指标绑定下一步 | `StandardPackagePage.tsx`、`ElementsPage.tsx` | 标准包成功后可一键继续 |
| 维度建模 | `/studio/low-code-development`、`/studio/sql-modeling` | 标准草稿传递已开始，但缺少统一上下文和来源证据 | 低代码/SQL 建模显示标准来源、字段数、缺口、下一步 | `LowCodeDevelopmentPage.tsx`、`SqlModelingPage.tsx` | `standardDraftId` 读取和 UI 状态可见 |
| 指标管理 | `/modeling/metric-workbench` | 指标口径与标准/模型的视觉关联弱 | 指标工作台展示模型/标准上下文和口径绑定缺口 | `MetricWorkbenchPage.tsx` | 指标入口携带 modelId/standardDraftId |
| 数据开发 | `/studio/sql-modeling`、`/modeling/dbt-files` | SQL/dbt 文件、发布门禁、运行证据分散 | SQL 页右侧展示开发 -> 门禁 -> 运维证据链 | `SqlModelingPage.tsx`、`DbtFileBrowserPage.tsx` | 门禁按钮和证据入口同屏可见 |
| 数据服务 | `/services/apis`、`/services/products`、`/bi/report-factory` | 消费入口和模型/指标上下文弱 | 从模型/指标进入 API/数据产品发布向导 | `ApiServicesPage.tsx`、`DataProductsPage.tsx` | 服务入口识别 modelId/metricId |
| 运行证据 | `/ops/instances`、`/ops/audit-evidence` | 运维页独立存在，客户验收材料需人工拼 | 聚合质量、运行、审计、权限证据为验收包 | `OpsInstancesPage.tsx`、`AuditEvidencePage.tsx` | 验收包链接能回到源模型/服务 |

## UI 主导约束

- 每一行必须有用户可见入口，不允许只写后端任务。
- 如果 API 不存在，页面必须显示明确 blocker，不允许用静态成功态代替。
- 每个阶段的 source-contract 至少断言：路由、按钮、阶段文案、上下文参数。
