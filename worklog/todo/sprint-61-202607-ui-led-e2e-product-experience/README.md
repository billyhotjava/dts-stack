# Sprint-61: UI 主导的端到端数据产品体验闭环

**时间**: 2026-07
**状态**: IN_PROGRESS
**类型**: UI Productization / End-to-End Journey / Loop Engineering
**目标**: 让客户从一个连续界面体验 DTS：数据集成 -> 数仓规划 -> 数据标准 -> 建模与指标 -> 数据开发 -> 发布门禁 -> 数据服务 -> 运行证据，中间不靠频繁菜单跳转和口头说明补齐。

## 背景

当前 DTS 已经具备数据源、标准包、数据元、低代码建模、SQL 建模、指标工作台、数据服务和运维证据等页面，但客户体验仍容易被割裂：

- 页面之间靠菜单跳转串联，缺少统一旅程状态、下一步和返回入口。
- 标准包、数据元、字段落标草稿已经开始进入建模，但 UI 还没有形成“标准已驱动模型/指标”的清晰可见证据。
- 数据集成、数仓规划、ODS/DWD/DWS/ADS、SQL/dbt、指标、API/数据产品在产品语言上仍像多个工具集合。
- 数据开发后的发布门禁、运行实例、审计证据和客户验收材料没有在同一条旅程里收口。

本 sprint 不优先新增复杂后端能力；先以 UI 为主导，把真实已有页面串成一个客户可走、可验收、可讲清楚的端到端产品体验。后端缺口只用最小 API 契约或空状态 blocker 暴露。

## Loop 定义

```text
进入端到端工作台
  -> 选择业务目标和数据来源
  -> 生成数仓分层规划草稿
  -> 导入/确认标准包与数据元
  -> 输出字段落标草稿
  -> 生成低代码/SQL 模型候选
  -> 绑定指标口径
  -> 运行开发与发布门禁
  -> 发布 API / 报表数据集 / 数据产品
  -> 查看运行、质量、权限、审计证据
  -> 形成客户验收包
```

## Feature 列表

| ID | Feature | Task 数 | 状态 | 优先级 |
|----|---------|---------|------|--------|
| F1 | 端到端旅程工作台与上下文保持 | 3 | IN_PROGRESS | P0 |
| F2 | 数据集成到数仓规划的首屏引导 | 4 | IN_PROGRESS | P0 |
| F3 | 标准落标到建模与指标的可见传递 | 3 | READY | P0 |
| F4 | 数据开发到发布门禁与运行证据 | 3 | READY | P0 |
| F5 | 数据服务消费闭环与客户验收 | 3 | READY | P1 |
| F6 | 旅程上下文组件化与页面接入 | 3 | IN_PROGRESS | P0 |
| F7 | 阶段状态与缺口计算模型 | 3 | IN_PROGRESS | P0 |
| F8 | 客户验收包与证据聚合 | 3 | IN_PROGRESS | P1 |
| F9 | 可登录浏览器验收与回归基线 | 3 | READY | P0 |

**统计**: READY=17, IN_PROGRESS=4, DONE=7, BLOCKED=0

## 下一阶段实施顺序

1. F6 旅程上下文组件化与页面接入：先做共享组件和 query 保持，避免每个页面重复实现上下文逻辑。
2. F7 阶段状态与缺口计算模型：把阶段状态、缺口、下一步从静态文案升级为可计算结果。
3. F2/F3 业务链路深化：把数据源、数仓规划、标准草稿、建模、指标真正串起来。
4. F4/F5/F8 交付收口：把发布门禁、运行证据、数据服务和客户验收包串成可验收闭环。
5. F9 可登录浏览器验收与回归基线：并行解除当前登录/DNS blocker，沉淀可重复 smoke。

## 产品原则

- UI 不是模块入口集合，而是业务旅程控制台。
- 每个阶段必须显示：当前状态、缺口、下一步、负责角色、证据入口。
- 页面跳转必须携带 journey context，例如 `journey=e2e-data-product`、`standardDraftId`、`modelId`、`metricId`。
- 标准、模型、指标、服务之间的传递要在页面上可见，不把“已经关联”藏在后台数据里。
- 空状态不能只是“暂无数据”，必须给出可执行下一步。
- 复杂后端能力缺失时，UI 要明确显示 blocker 和需要补充的 API，不伪装成已完成。
- 所有新增按钮都要覆盖 default/loading/empty/disabled/error/success/permission 状态。

## 完成标准

- [ ] 工作台提供端到端数据产品旅程视图，覆盖集成、规划、标准、建模、指标、开发、服务和证据。
- [x] 用户从工作台进入任一阶段后，页面能保留旅程上下文并提供返回/下一步。
- [x] 子页面通过共享上下文组件展示当前阶段、来源对象、返回工作台和继续下一步。
- [x] 阶段状态、缺口和下一步动作来自统一模型或明确 blocker，不散落在页面硬编码里。
- [ ] 数据源或接入任务能在 UI 上转化为数仓分层规划草稿入口。
- [ ] 标准包/数据元字段落标草稿在低代码、SQL 建模、指标工作台中可见且可继续操作。
- [ ] SQL/dbt 发布前的标准门禁、质量门禁、运行证据在同一旅程里展示。
- [ ] 数据 API、报表数据集、数据产品发布入口能从模型/指标上下文进入。
- [x] 客户验收包能聚合模型、指标、服务、质量、权限和运行证据链接。
- [ ] Browser smoke 能在可登录环境覆盖工作台、数据源、标准、建模、指标、服务和运行证据。
- [ ] source-contract、`pnpm build`、必要 Playwright smoke 和 Chrome 95 视觉检查通过。

## 非目标

- 不重写全部页面壳。
- 不把所有能力塞进一个巨型页面。
- 不绕过现有权限、标准门禁、发布审核和审计。
- 不在本 sprint 完成完整 AI 自动建模；本 sprint 只做 UI 旅程闭环和可见传递。
- 不把分析说明直接放进产品页面；页面文案只服务用户行动。

## 关键页面与路由

- 工作台：`/workbench`，`source/dts-platform-webapp/src/pages/workbench/DataManagementWorkbenchPage.tsx`
- 数据源：`/foundation/data-sources`，`source/dts-platform-webapp/src/pages/foundation/DataSourcesPage.tsx`
- 标准包：`/foundation/standard-package`，`source/dts-platform-webapp/src/pages/foundation/StandardPackagePage.tsx`
- 数据元：`/governance/standards/elements`，`source/dts-platform-webapp/src/pages/governance/ElementsPage.tsx`
- 低代码建模：`/studio/low-code-development`，`source/dts-platform-webapp/src/pages/modeling/LowCodeDevelopmentPage.tsx`
- SQL 建模：`/studio/sql-modeling`，`source/dts-platform-webapp/src/pages/modeling/SqlModelingPage.tsx`
- 指标工作台：`/modeling/metric-workbench`，`source/dts-platform-webapp/src/pages/modeling/MetricWorkbenchPage.tsx`
- 数据服务：`/services/apis`，`source/dts-platform-webapp/src/pages/services/ApiServicesPage.tsx`
- 数据产品：`/services/products`，`source/dts-platform-webapp/src/pages/services/DataProductsPage.tsx`
- 运维证据：`/ops/instances`，`source/dts-platform-webapp/src/pages/ops/OpsInstancesPage.tsx`
- 审计证据：`/ops/audit-evidence`，`source/dts-platform-webapp/src/pages/ops/AuditEvidencePage.tsx`

## 资产

- 页面能力矩阵：`assets/page-capability-matrix.md`
- 按钮/组件矩阵：`assets/button-component-matrix.md`
- UI 旅程草图：`assets/ui-journey-flow.md`
- 下一阶段 Feature 依赖图：`assets/next-feature-dependency-map.md`
- 外部中台功能图参考：`assets/external-data-platform-function-reference.md`
- 集成验证计划：`it/README.md`
