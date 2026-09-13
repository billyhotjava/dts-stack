# 建模原型合入 DTS 架构评估

## 结论

采用“保留内核、重组界面、分级退役”。

原型有价值的是交互骨架，不是其静态实现。DTS 已有完整的规划、模型、指标、发布和物化控制面；将原型代码直接搬入产品会形成平行状态源。目标架构以 `/modeling/workbench` 为 Shell，把现有页面的业务面板抽出后嵌入，并通过兼容深链逐步退役旧页。

## 复用 / 改造 / 删除矩阵

| 现有资产 | 结论 | 处理方式 | 删除条件 |
|---|---|---|---|
| WarehousePlan API、stage projection、计划表 | 直接复用 | 作为工作台 `planning` 模块唯一事实源 | 不删除 |
| DimensionDefinition API/表 | 直接复用 | 业务维度目录与模型字段映射共用 | 不删除 |
| ModelSpec API、revision、stage gates | 直接复用 | 单页编辑器继续写 canonical ModelSpec | 不删除 |
| Indicator 工作台与治理 API | 直接复用 | 抽为工作台 `metrics` 面板 | 不删除 |
| ReleaseCandidate、Build/Publish Intent | 直接复用 | 包装为发布/物化短流程 | 不删除 |
| `ModelingWorkbenchPage` | 重构 | 变为轻量 Shell；计划卡片抽为 `planning` 面板 | 新 Shell 稳定后删除旧页面内部重复布局 |
| `WarehousePlanLedgerPage` / `WarehousePlanDetailPage` | 抽取并复用 | 面板组件嵌入 Shell；旧路由先重定向 | 两版本零访问且深链回归通过 |
| `DimensionCatalogPage` / `ModelCenterPage` / `MetricWorkbenchPage` | 抽取并复用 | 保留状态/命令逻辑，移除重复页头与旅程说明 | 同上 |
| `ModelSpecDetailPage` | 大幅改造 | 三阶段页面重组为单页编辑器；stage gate 仅作为顶部状态和按钮门禁 | 新编辑器功能等价后删除旧布局组件 |
| `SqlModelingPage` / `ModelPipeline` | 保留专业入口 | 仅在工具栏提供深链，不塞入主编辑器 | 不删除；后续独立拆分 4741 行巨型页 |
| `ModelTemplatesPage` | 保留 | 属于项目空间，不是重复模型页 | 不删除 |
| `DbtFileBrowserPage.tsx` | Batch A 已删除 | GitNexus LOW、current HEAD 无运行时引用；dbt 文件/运行能力由 `SqlModelingPage` 持有 | `pnpm build` 已通过 |
| `modelingCompatibility.ts` 旧注册 helper | Batch A 已删除 | 仅孤儿测试引用，helper 与测试一起删除；正式 redirect owner 是 `modelingCompatibilityRoute.ts` | focused test 已通过 |
| `ModelingCompatibilityPage` + 8 条兼容路由 | 删除候选 B | 先增加访问观测，继续 redirect/recovery | 所有部署两版本零访问、客户旧对象完成映射、回滚演练通过 |
| `modeling_business_object`、`semantic_*` 等旧表/API | 删除候选 C | 本 Sprint 只做画像和 removal proposal | 客户环境 dry-run、零写入/零读取、备份、审批和独立 migration |

## 目标组件架构

```text
/modeling/workbench
└─ ModelingWorkspaceShell
   ├─ WorkspaceTopNav(home/planning/standards/models/metrics/tools/graph)
   ├─ PlanContextSelector(planId)
   ├─ WorkspaceObjectTree(assetKind/assetId)
   └─ WorkspaceCanvas
      ├─ OverviewPanel
      ├─ PlanningPanel                  -> WarehousePlan APIs
      ├─ StandardsPanel                 -> existing governance pages/APIs
      ├─ ModelEditorCanvas
      │  ├─ BasicInfoPanel
      │  ├─ CompactFieldGrid
      │  ├─ PartitionAndImplementationDrawer
      │  ├─ AssociationDrawer
      │  └─ DeliveryDialog              -> Build/Publish Intent
      ├─ MetricWorkbenchPanel           -> Indicator APIs
      ├─ ModelingToolsPanel             -> import/reverse/validate/deep links
      └─ RelationshipGraphPanel         -> read-only projection
```

Shell 只拥有 URL 和视图选择状态，不保存业务事实。所有编辑命令继续调用现有 API；所有列表/详情组件对 query context 采用受控输入，避免再次出现 session context 与 planId 竞争。

## 六类新建动作映射

| 原型动作 | DTS 落点 | 说明 |
|---|---|---|
| 新建维度 | DimensionDefinition | 保存时生成系统编码；属性只定义业务语义 |
| 新建贴源表 | 计划来源注册 / 逆向建模候选 | 不创建第五类 ModelSpec |
| 新建维度表 | ModelSpec `DIMENSION` | 必须引用 CURRENT DimensionDefinition revision |
| 新建明细表 | ModelSpec `FACT` | 业务过程可选但粒度必须明确 |
| 新建汇总表 | ModelSpec `SUMMARY` | DWS，统计粒度/周期进入逻辑契约 |
| 新建应用表 | ModelSpec `APPLICATION` | ADS，消费场景必须明确 |

## 删除批次

1. **Batch A — orphan cleanup**：无运行时 import 的页面/helper；同提交删除测试漂移并跑前端构建。
2. **Batch B — page convergence**：抽取面板后，旧 canonical 页面路由改为保留 query 的重定向；不得立即删除 route code。
3. **Batch C — compatibility retirement**：两版本零访问后删除 8 条 redirect 路由和兼容页。
4. **Batch D — data/API retirement**：仅在客户环境画像、dry-run、备份审批通过后另立 migration；本 Sprint 不执行。

## 关键风险

- 将“单页”误解为跳过门禁，会破坏逻辑设计、实现、发布职责边界。
- 将本地 0 行外推为客户 0 行，会造成不可恢复的数据删除。
- 直接把现有大页面嵌套在 Shell 中，会形成多页头、多滚动容器和更差体验。
- 为关系图新建台账会制造第二套血缘/依赖事实。
- 短流程自动完成审核或发布，会绕过职责分离。
