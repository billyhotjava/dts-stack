# DTS 平台 vs Metabase 功能差异分析

**分析日期**: 2026-03-27
**Metabase 版本**: v59 (2026-03)
**DTS 版本**: v2.2.2

## 一、功能全景对比

### 1. 查询与数据探索

| 功能 | Metabase | DTS 现状 | 差距 | 优先级 |
|------|----------|----------|------|--------|
| 无代码查询构建器（点选式） | 完整的 GUI query builder，支持 join/filter/group/sort | ✅ 已有 NotebookEditor 6步构建器（选表→JOIN→选列→过滤→汇总→排序），入口 /questions/new | 基本持平，需完善细节 | - |
| SQL 编辑器 | 内置 SQL editor，支持变量模板 `{{variable}}`、自动补全、结果可视化 | 有 Monaco SQL Editor（SqlWorkbenchExperimental），支持自动补全 | 基本持平，DTS缺少模板变量 | P2 |
| AI 自然语言查询 (Metabot) | AI 问答助手，自然语言→SQL，Cloud 版可用 | 无 AI 查询能力 | **大差距** | P1 |
| 数据探索 Drill-through | 点击图表自动下钻，多级穿透 | 大屏有 useDrillView（战略→管控→执行），但仅限大屏 | 中等差距，看板/报表无 drill-through | P1 |
| 数据模型 (Models) | 可创建带元数据注释的语义模型，定义指标/维度 | dbt SQL 模型（ModelingSqlModel），但面向开发者非业务用户 | 中等差距，缺少面向业务用户的语义层 | P1 |
| CSV 上传分析 | 直接上传 CSV 到 Metabase 中查询分析 | Excel/CSV 导入到数仓（ExcelImportService），但不能直接在 BI 层分析 | 中等差距 | P2 |

### 2. 仪表盘与可视化

| 功能 | Metabase | DTS 现状 | 差距 | 优先级 |
|------|----------|----------|------|--------|
| 拖拽式仪表盘编辑器 | Drag-and-drop dashboard editor | 大屏设计器（绝对定位），项目看板（固定布局） | 部分持平，大屏可拖拽但不是标准仪表盘风格 | P1 |
| 仪表盘过滤器 | 丰富的过滤器类型（日期/类别/ID），支持跨卡片联动 | 大屏有筛选器组件 + SharedStore 联动 | 基本持平 | - |
| 交叉过滤 (Cross-filtering) | 点击图表A自动过滤图表B | 有 SharedStore + useQueryDAG 依赖图 | 基本持平 | - |
| 仪表盘订阅 | 定时发送到 Email/Slack/Webhook | **无** | **大差距** | P1 |
| 数据告警 | 数据超过阈值自动通知 | **无** | **大差距** | P1 |
| 集合管理 (Collections) | 文件夹式组织仪表盘/问题 | 大屏有列表页，但无层级文件夹/标签组织 | 中等差距 | P2 |
| 官方认证标记 (Verified) | 标记官方可信内容 | **无** | 小差距 | P3 |
| 查询缓存 | 可配置的查询结果缓存 | 有部分缓存（manifest缓存），但无通用查询缓存 | 中等差距 | P2 |

### 3. 图表类型

| 图表类型 | Metabase | DTS 现状 | 差距 |
|----------|----------|----------|------|
| 折线图 | ✅ | ✅ ECharts | 持平 |
| 柱状图 | ✅ | ✅ ECharts | 持平 |
| 面积图 | ✅ | ✅ ECharts | 持平 |
| 饼图/环形图 | ✅ 含旭日图 | ✅ ECharts | 持平 |
| 散点图 | ✅ | ✅ ECharts | 持平 |
| 组合图 (Combo) | ✅ 柱线混合 | ✅ ECharts mixedLine | 持平 |
| 漏斗图 | ✅ | ✅ ECharts | 持平 |
| 箱线图 (Boxplot) | ✅ (v59新增) | ❌ | 小差距 |
| 仪表盘 (Gauge) | ✅ | ✅ ECharts gauge | 持平 |
| 表格 | ✅ | ✅ ThemedScrollTable | 持平 |
| 数字卡片 | ✅ | ✅ 数字统计卡 | 持平 |
| 地图-标记点 (Pin map) | ✅ | ❌ | **中等差距** |
| 地图-区域填色 (Choropleth) | ✅ | ✅ ECharts GeoJSON map | 持平 |
| 地图-网格 (Grid map) | ✅ | ❌ | 小差距 |
| 详情视图 (Detail) | ✅ 单行详情展示 | ❌ | 小差距 |
| 进度条 | ✅ (Goal line) | ❌ | 小差距 |
| 趋势线 | ✅ 自动趋势叠加 | ❌ 需手动配置 | 小差距 |
| 3D 图表 | ❌ | ✅ ECharts GL (bar3D/scatter3D等) | **DTS 领先** |
| 装饰边框 | ❌ | ✅ DataV 装饰组件 | **DTS 领先** |
| 动态数字翻牌 | ❌ | ✅ DataV 数字翻牌器 | **DTS 领先** |

### 4. 嵌入式分析 & 白标

| 功能 | Metabase | DTS 现状 | 差距 | 优先级 |
|------|----------|----------|------|--------|
| 嵌入式分析 SDK (React) | 开源 SDK，嵌入图表/仪表盘/AI Chat | **无** SDK，大屏仅支持公开链接/iframe | **大差距** | P2 |
| 白标 (White-label) | 自定义品牌/Logo/颜色 (Pro/Enterprise) | 无白标支持 | 中等差距 | P3 |
| 静态嵌入 | iframe 嵌入签名 URL | 大屏公开链接 + PublicScreenPage | 基本持平 | - |
| 交互式嵌入 | 嵌入完整交互功能 (Pro/Enterprise) | 无 | **大差距** | P2 |

### 5. 权限与数据治理

| 功能 | Metabase | DTS 现状 | 差距 | 优先级 |
|------|----------|----------|------|--------|
| 基于角色的权限 | 集合/数据库/表级权限 | 有角色权限（RBAC），按部门/角色控制 | 基本持平 | - |
| 行级安全 (Row-level security) | Data Sandboxing (Pro/Enterprise) | 有 CatalogDatasetSecurityMapping + RowFilterRule | 基本持平 | - |
| 列级安全 | 列级权限控制 (Pro/Enterprise) | 有 CatalogColumnSchema 权限 | 基本持平 | - |
| 数据库连接模拟 | Connection Impersonation (Enterprise) | 无 | 小差距 | P3 |
| 多租户隔离 | Tenants 功能 (Pro/Enterprise) | 有部门级隔离（ownerDept） | 基本持平 | - |
| 审计日志 | Usage Analytics (Pro/Enterprise) | 完整审计系统（AuditService → dts-admin） | **DTS 领先** |
| 数据分类分级 | 无原生支持 | 有分类映射 + 脱敏规则（CatalogMasking） | **DTS 领先** |

### 6. 语义层 & 数据治理 (Data Studio)

| 功能 | Metabase | DTS 现状 | 差距 | 优先级 |
|------|----------|----------|------|--------|
| 语义层 (Semantic Layer) | Data Studio (v59新增)：定义指标/维度/关系 | dbt 模型 + 数据标准（DataStandard），面向开发者 | 中等差距，Metabase更面向分析师 | P1 |
| 数据目录 | 基础数据浏览器 | 完整数据目录（CatalogDataset/Table/Column + 血缘 + 质量） | **DTS 领先** |
| 数据血缘 | 无原生支持 | 完整血缘追踪（CatalogLineage + SqlTableReferenceExtractor） | **DTS 领先** |
| 数据质量 | 无原生支持 | 质量规则引擎（QualityRule/Task/Run） | **DTS 领先** |
| 数据标准 | 无 | 数据标准管理（DataStandard + 自动映射） | **DTS 领先** |
| 元数据变更追踪 | 无 | CatalogMetadataChangeLog | **DTS 领先** |

### 7. 数据接入 & ETL

| 功能 | Metabase | DTS 现状 | 差距 | 优先级 |
|------|----------|----------|------|--------|
| 数据库连接 | 30+ 数据库（MySQL/PG/Mongo/BigQuery/Snowflake等） | JDBC 多源（PG/Hive/Inceptor + 通用JDBC） | 基本持平，Metabase连接器更多 | P3 |
| ETL/ELT 能力 | **无** （仅查询层） | 完整 ETL（Addax + Airflow + dbt） | **DTS 大幅领先** |
| 数据调度 | **无** | Airflow DAG 调度 | **DTS 大幅领先** |
| 数据建模 | 无（依赖上游dbt等） | 内置 dbt 集成 + SQL 模型管理 | **DTS 大幅领先** |

### 8. 协作 & 分享

| 功能 | Metabase | DTS 现状 | 差距 | 优先级 |
|------|----------|----------|------|--------|
| 链接分享 | Quick links | 公开链接（PublicScreen） | 持平 |
| 定时邮件订阅 | Email/Slack/Webhook 订阅 | **无** | **大差距** | P1 |
| 评论/讨论 | 无原生支持 | 无 | 持平 | - |
| 编辑锁 | 无 | 有（ScreenEditLock） | **DTS 领先** |
| 版本管理 | 无 | 有（ScreenVersion + rollback） | **DTS 领先** |
| 导出 PDF/PNG | 基础导出 | 完整导出（PDF/PNG + 水印 + 服务端渲染） | **DTS 领先** |

---

## 二、DTS 领先 Metabase 的领域

| 领域 | DTS 优势 |
|------|---------|
| **数据全链路** | 从数据接入→ETL→建模→治理→BI 一站式，Metabase 只覆盖 BI 层 |
| **数据治理** | 完整的数据目录、血缘、质量、标准、分类分级、脱敏规则 |
| **大屏设计** | 绝对定位自由画布、DataV装饰组件、3D图表、入场动画、暗色主题 |
| **审计安全** | 完整审计日志系统（200+操作类型，多阶段流程，加密存储） |
| **版本控制** | 大屏版本管理 + 一键回滚 + 编辑锁 |
| **导出能力** | 服务端渲染 PDF/PNG + 水印 + 合规控制 |
| **信创适配** | ARM/Kunpeng + KylinOS + 离线部署，Metabase 无信创支持 |

## 三、DTS 需补齐的关键差距 (按优先级)

### ~~P0~~ — 已具备
| # | 功能 | 说明 |
|---|------|------|
| ~~1~~ | ~~无代码查询构建器~~ | ✅ 已有 NotebookEditor（6步 Notebook 式），路由 `/questions/new`，支持 JOIN/过滤/汇总/排序，Builder↔SQL 双模式 |

### P1 — 重要差距
| # | 功能 | 说明 | 工作量估算 |
|---|------|------|-----------|
| 2 | **仪表盘订阅 & 告警** | 定时推送到邮件/企业微信/钉钉 + 阈值告警 | 中（1-2 sprint） |
| 3 | **AI 自然语言查询** | 自然语言→SQL，降低数据使用门槛 | 中（1-2 sprint） |
| 4 | **Drill-through 泛化** | 从大屏扩展到看板/报表，点击图表自动下钻 | 小（0.5 sprint） |
| 5 | **面向分析师的语义层** | 让业务用户定义指标/维度，而非只有开发者用 dbt | 中（1-2 sprint） |

### P2 — 增值功能
| # | 功能 | 说明 | 工作量估算 |
|---|------|------|-----------|
| 6 | **嵌入式分析 SDK** | React SDK 让客户将 DTS 分析能力嵌入自己的应用 | 大（2-3 sprint） |
| 7 | **SQL 模板变量** | SQL 编辑器支持 `{{date_range}}` 等参数化模板 | 小（0.5 sprint） |
| 8 | **通用查询缓存** | 可配置的查询结果缓存，提升仪表盘加载速度 | 小（0.5 sprint） |
| 9 | **集合/文件夹管理** | 层级文件夹组织看板、报表、大屏 | 小（0.5 sprint） |

### P3 — 锦上添花
| # | 功能 | 说明 |
|---|------|------|
| 10 | 白标支持 | 客户品牌定制 |
| 11 | 箱线图/Pin地图 | 补全图表类型 |
| 12 | 连接模拟 (Connection Impersonation) | 企业级数据库权限 |

---

## 四、战略建议

1. **短期 (v2.2.3)**: 仪表盘订阅+告警、Drill-through 泛化 — 投入小收益大
2. **中期 (v2.3)**: AI 查询 + 语义层完善 — 提升自助分析体验
3. **长期 (v3.0)**: 嵌入式 SDK — 商业化差异化

DTS 的核心优势在于**数据全链路**（接入→ETL→建模→治理→BI），这是 Metabase 完全不具备的。应在保持这一优势的同时，补齐 BI 层的自助分析能力。

---

Sources:
- [Metabase Features](https://www.metabase.com/features/)
- [Interactive Dashboards](https://www.metabase.com/product/interactive-dashboards)
- [Embedded Analytics SDK](https://www.metabase.com/product/embedded-analytics-sdk)
- [Visualization Guide](https://www.metabase.com/docs/latest/questions/visualizations/visualizing-results)
- [Data Segregation](https://www.metabase.com/features/data-segregation)
- [Row and Column Security](https://www.metabase.com/docs/latest/permissions/row-and-column-security)
- [Dashboard Subscriptions](https://www.metabase.com/docs/latest/dashboards/subscriptions)
- [Metabase Review 2026](https://valiotti.com/blog/metabase-review/)
- [Metabase Releases](https://www.metabase.com/releases)
- [Metabase Roadmap](https://www.metabase.com/roadmap)
