# 页面能力矩阵

本矩阵是 Sprint-94 的 UI 产品契约。所有页面沿用既有菜单与 canonical route，不新增主入口；“旧行为”仅用于兼容，不得继续扩展。

**全量 32 条路由的分母见 `route-inventory.md`**；本矩阵只覆盖本 Sprint 有改造动作的页面。本 Sprint 属 Metabase 退役 S0，**不做任何重定向**。

| 页面 / 路由 | 目标用户 | 当前问题 | Sprint-94 能力 | 四态与关键反馈 | 后端 owner | Feature |
|---|---|---|---|---|---|---|
| BI 数据集 `/bi/data` | 分析维护者 | 混合平台数据源与 Analytics Database；创建参数 `dbId` 与编辑器不匹配 | 只列 PUBLISHED Query Dataset 版本；领域/owner/刷新/密级筛选；查看契约；创建分析 | loading skeleton；empty 引导发布数据集；error 可重试+correlationId；success 显示版本/checksum/分层 | dts-platform QueryDataset | F1 |
| 分析工作区 `/bi/questions` | 维护者、发布者 | Question/Card/MBQL 术语；草稿与发布物混在一起 | 草稿/已发布/归档筛选；搜索、创建、编辑、复制、版本、受众摘要 | 未授权不显示；空态按筛选解释；旧只读资产有兼容标识 | dts-analytics Analysis facade | F2/F4 |
| 分析编辑器 `/bi/questions/new`、`/bi/questions/:id/edit` | 分析维护者 | 5 条路由共用 `SemanticCardEditorPage`；`:id/edit` 的 `CardEditorRoutePage` 是 semantic/legacy 兼容分流器；旧路径可触达 raw JSON | **canonical editor**（ADR-94-13）：分流器新增 analysis 分支并保留 legacy 行为；数据集摘要、字段区、配置区、画布/表格预览、校验栏；autosave 草稿 | 数据集失效 409 banner；字段非法 422 定位；超限 429；预览超时 504 | Analysis + Gateway | F2/F3 |
| `/bi/card/*`、`/bi/explore`、`/bi/virtual-datasets*` | 历史用户 | 与 canonical 编辑器共用组件（`/bi/explore` 实为探索页，非编辑器） | **冻结**：保持现状可用，行为零变更 | 拆分共享组件后须有回归证据 | - | F2/T02 |
| 看板列表 `/bi/dashboards` | 看板维护者、消费者 | 保存即接近可用；依赖/版本/受众不可解释 | 草稿/已发布/归档；版本、依赖健康、受众、更新时间；消费者只看可消费项 | 空/错误/无权限区分；依赖失效显示 blocker | Dashboard + Revision + ReportLink | F4 |
| 看板设计 `/bi/dashboards/:id/edit`（沿用现有实际 route） | 看板维护者、发布者 | Card 依赖可能漂移；无正式校验发布状态机 | 只选已发布 Analysis revision；布局/参数；校验；发布；版本历史；受众配置 | VALIDATING 进度；blocker 精确到组件；发布失败不改变当前 published | Dashboard/Revision | F4 |
| 大屏 `/bi/screens` 及设计器 | 大屏维护者、消费者 | 组件直接引用 cardId | **本 Sprint 保持不动**；大屏复用已发布分析顺延至退役 S3 | - | Screen | 后续 S3 handoff |
| 平台 BI 服务登记（既有 `BiLinksPage` route） | 平台发布/运营者 | 主要以 URL/engine 管理，稳定分析资产身份不足 | 展示 assetType/key/version、数据集版本、受众、密级、有效期、同步状态；支持 reconcile | 登记失败可重试；过期/禁用明确；不允许猜测匹配 | dts-platform Reports | F4/F6 |
| Collections / Models / Trash / report-factory / metric-lens / nl2sql-eval 等 | 历史用户、管理员 | 独立 Metabase IA | **本 Sprint 保持不动**，只登记观测来源与窗口；重定向属退役 S2 | - | - | 后续 S2 handoff |

## 页面级一致性规则

1. 用户文案统一为“分析、看板、大屏、BI 数据集”，不显示 Question、Card、MBQL、Metabase、raw JSON。
2. 每个资产页同时显示“草稿/已发布/归档”生命周期和“依赖正常/阻断”健康状态，两者不得混为一个标签。
3. 编辑器的保存、校验、发布必须是三个独立动作；前端不得在保存后自动推进发布。
4. 受众和密级在发布前可见；无权限项从列表过滤，直链仍需后端 403。
5. 错误反馈包含稳定业务原因和 correlationId，不展示 SQL、堆栈或内部表名。
6. 所有列表分页 0-based API、页面 UI 1-based 展示；**UI 默认每页 10 条**（沿用 dts-platform-webapp 既有 table 约定，服务端默认 20/最大 100 只是 API 上限）；切换每页条数必须重新拉取并重置到第 1 页；筛选写入 URL，刷新后保持。
7. Chrome 95 下不得依赖未经构建转译的新语法/CSS；超大表格和画布均需明确 `min-height: 0`/overflow 链。
