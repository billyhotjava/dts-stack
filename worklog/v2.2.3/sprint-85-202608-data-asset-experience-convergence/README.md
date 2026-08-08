# Sprint-85：数据资产门户体验收敛与功能串联

**时间**：2026-08
**状态**：DESIGN_APPROVED / IMPLEMENTATION_PENDING（本文件为重构方案与切片计划）
**类型**：UX Productization / Feature Convergence / Contract Unification
**目标**：把"数据地图与资产"域内的**资产地图、资产台账、数据搜索、血缘与影响分析、权限申请**五类入口从"多套并列表面"收敛为一条可导航的用户旅程：**概览（资产地图）→ 台账/搜索 → 详情 → 血缘 → 申请权限 → 审批/我的授权 → 审计**，任何一步都能链到下一步，且每一步只消费一个事实源。

## 背景与核心问题

2026-08 勘察确认（账本见 `assets/recon-ledger.md`）：数据资产域功能齐全但**表面碎片化**，用户无法理解"资产地图为什么是统计表"、"同一资产为什么有两种血缘"、"申请权限到底去哪点"。核心问题不是缺功能，而是：

1. **"资产地图"不是图**：菜单叫资产地图，页面是统计矩阵（`AssetOverviewPage.tsx`）；真正的血缘图谱藏在"血缘与影响分析"二级菜单下。矩阵 drill-down 的"其他 N 个域"合并列点击后落到全量域范围（有意为之但误导），治理缺口 drill-down 会静默丢掉 `UNCLASSIFIED/STALE` 过滤。
2. **双轨资产台账**：`ASSET_PORTAL_V2_ENABLED` 开关在台账与搜索页切换 assets-v2（OpenMetadata）与 legacy `listDatasets` 两套字段/过滤/结果形态；搜索页还并行跑第三条 tag 索引路径再按 key 去重。
3. **同一 URL 两种详情页**：`DatasetDetailPage` 先试 legacy `getDataset` 再回退 assets-v2，治理责任等 Tab 因数据源不同渲染完全不同组件。
4. **两套血缘语义并列**：资产详情"血缘与影响"Tab 同时展示 OpenMetadata 血缘缓存（FQN 表对）与 DTS 本地影响链（UUID 数据集对+作业/源节点），同一资产两种故事；血缘图谱的节点身份不稳定（job 合成 id、列伪 id `dsid::col`），随 `withJobs` 开关边数变化。
5. **权限申请散在四个路由**：申请入口在审批页（其"申请权限"按钮只是跳回资产地图），直接授权/我的授权/权限审计各自独立，台账与资产地图不链接任何权限入口。
6. **筛选状态各自为政**：台账/搜索/旧详情各自持久化 localStorage 筛选 JSON，schema 不同；血缘三个子页共享 7+ 控件筛选器但无 URL 持久化，刷新丢选择。
7. **组件层反向依赖页面层**：`components/lineage/LineageGraph.tsx` 从 `pages/catalog/lineageShared.tsx` 导入类型，共享组件与页面强耦合。

## 架构决策记录

| ID | 决策 | 理由 | 状态 |
|---|---|---|---|
| ADR-85-01 | **资产地图定位为"治理概览仪表盘"**，保留统计矩阵+治理缺口+待处置；改名文案为"资产概览"，血缘图谱保留在"血缘与影响分析"下；在概览上增加直达血缘/台账/权限的通路 | 统计矩阵对治理管理有价值，不是 bug；问题是命名与导航误导，而不是删除功能 | ACCEPTED |
| ADR-85-02 | **资产台账与搜索以 assets-v2 为唯一事实源**；legacy `listDatasets` 仅作深链兼容读取，新 UI 一律走 assets-v2；删除前端双轨开关 | 双轨造成字段/过滤/形态漂移，用户无法判断"我看到的资产对不对" | ACCEPTED |
| ADR-85-03 | **详情页以 assets-v2 为唯一事实源**；legacy `getDataset` 仅用于 legacyId 解析与旧深链跳转，不再参与 Tab 渲染分支 | 同 URL 两页面的双身份渲染是最大困惑源 | ACCEPTED |
| ADR-85-04 | **血缘以 DTS 本地影响链为唯一展示引擎**；OpenMetadata 血缘缓存降级为"同步状态/证据说明"（不渲染第二张图），图表页只消费 `/catalog/lineage/impact` | 两套血缘并存是语义混乱根源；OM 缓存保留为同步证据 | ACCEPTED |
| ADR-85-05 | **血缘图节点/边身份稳定化**：作业与源节点使用可稳定复现的 id（`job:{jobId}`/`source:{connId}:{ns}:{stream}` 已可复现），列节点改 `{dsid}:{col}` 规范化 id；关键词输入即过滤（不做"高亮但不过滤"的双重语义） | 稳定身份是 CSV 导出、深链、跨会话一致性的前提 | ACCEPTED |
| ADR-85-06 | **权限申请旅程统一入口**：资产台账行级"申请权限"、详情"权限申请"Tab、资产地图待处置卡均深链到审批页新建申请（`/security/dataset-access-approval?action=new&assetId=`）；审批页标题统一为"数据资产 · 权限申请与审批"；我的授权/权限审计从审批页侧边入口可达 | 四个路由功能保留，但入口与命名统一，用户不再找不到 | ACCEPTED |
| ADR-85-07 | **统一筛选状态协议**：台账/搜索共用 URL query 协议（`domain/layer/assetType/classification/tags/query`）+ 单一 localStorage key `catalog.asset.filter.v2`；搜索页"应用资产筛选"改为双向同步（台账↔搜索）；血缘子页筛选进入 URL 并支持 `?datasetId=` 深链 | 消除跨页筛选漂移 | ACCEPTED |
| ADR-85-08 | **共享契约下沉**：血缘图类型（nodes/edges/columnLineages 等 DTO 类型）迁至 `src/api/ingestion`-style 契约模块（如 `src/features/catalog/lineageContracts.ts`），页面与组件都从契约模块导入 | 解除组件→页面反向依赖 | ACCEPTED |
| ADR-85-09 | **旧 `AssetDetailPage` 抽屉退役**：旧深链收敛到 `DatasetDetailPage`，`/catalog/asset-detail` 路由 410 重定向；保留源码门禁断言 | 两个详情页并存是维护与认知双重负担 | ACCEPTED |
| ADR-85-10 | **不新增菜单/不新建平行引擎**：本 Sprint 只收敛表面与导航，不动后端血缘引擎、权限模型、指标 goldenchain 血缘、多租户 | 范围控制，交付闭环优先 | ACCEPTED |

## 端到端用户旅程（本 Sprint 要实现的目标链路）

```text
数据治理 → 数据地图与资产
  ├─ 资产概览(资产地图) ──矩阵/缺口/待处置──┬─▶ 资产台账(带真实过滤深链)
  │                                        ├─▶ 元数据管理(治理处置)
  │                                        └─▶ 血缘与影响分析(影响/图谱)
  ├─ 资产台账 ──行级操作──┬─▶ 详情(7 Tab, 单一事实源)
  │                       ├─▶ 申请权限(新建申请)
  │                       └─▶ 授权管理/我的授权
  ├─ 数据搜索 ──结果──┬─▶ 详情
  │                   └─▶ 台账(带同一筛选)
  ├─ 血缘与影响分析 ──图谱/影响/字段/导入/对比──▶ 详情(血缘 Tab 深链回图)
  └─ 权限申请 ──我的申请/待办/已办──▶ 授权管理/权限审计
```

## Gate Registry

| Gate | 项目 | 状态 | 证据 | 未过则关联 Task |
|---|---|---|---|---|
| G0 | 勘察账本与页面能力矩阵 | PASS（2026-08 勘察） | `assets/recon-ledger.md` | - |
| G1 | ADR 与契约冻结 | PASS（D01～D10） | `assets/decision-register.md` | - |
| G2-UI-TRUTH | 无 legacy 双轨渲染、无误导性 drill-down | PENDING | 各 Feature 完成标准 + 源码门禁 | F0/F1/F2 |
| G2-CONTRACT-WIRING | 概览→台账→详情→血缘→权限 全链路可达且深链参数被消费 | PENDING | 契约测试 + E2E | F3/F4 |
| G2-EVIDENCE | 聚焦 RED/GREEN + 构建 | PENDING | 每个 Task 的测试命令 | 各 Task |
| G3 | 发布/回滚（纯前端+契约收敛，无 schema 变更） | PENDING | 发布计划 | F5 |
| G4 | 真实 E2E（授权账号 + Chrome 实机） | PENDING | `it/` | F5/T03 |

## Feature 列表

| ID | Feature | 核心内容 | 优先级 |
|---|---|---|---|
| F0 | 资产概览（资产地图）定位与导航收敛 | 更名文案、矩阵 drill-down 修复、缺口过滤贯通、概览→台账/血缘/权限通路 | P0 |
| F1 | 台账与搜索单一事实源 | assets-v2 唯一化、搜索单路径、筛选状态统一协议 | P0 |
| F2 | 血缘展示收敛与图谱体验 | 详情血缘 Tab 单引擎、图节点身份稳定、筛选 URL 化、契约下沉 | P0 |
| F3 | 权限申请旅程串联 | 统一申请入口（台账/详情/概览）、审批页命名与侧边入口、深链参数消费 | P0 |
| F4 | 详情页统一与旧页退役 | DatasetDetailPage 单一事实源、AssetDetailPage 退役收敛 | P1 |
| F5 | 集中验证与交付证据 | 全量聚焦测试、构建、一次联合 E2E、文档同步 | P0 |

**顺序**：F0 → F1/F2 → F3 → F4 → F5。

## 非目标

- 不重构后端血缘引擎（`CatalogLineageResource` BFS/位时模型、作业/源节点生成逻辑不变）。
- 不改变权限模型（申请工作流、直接授权、审计语义不变）。
- 不动建模域血缘（goldenchain 指标血缘、模型关系图）。
- 不做多租户、不做 OpenMetadata 同步引擎改动。
- 不新增菜单或页面路由（除必要的 410 收敛）。

## 完成标准（全局）

- 生产页面无 legacy 双轨渲染分支，无"其他域合并列"误导深链，无"高亮但不过滤"的双重语义。
- 概览→台账→详情→血缘→申请权限→审批→授权 全链路可达；每个深链参数（layer/domain/governance/assetId/datasetId）都被目标页真实消费，未命中给出显式反馈。
- 台账与搜索共用同一筛选状态；血缘筛选 URL 化，刷新不丢。
- `AssetDetailPage` 与 `/catalog/asset-detail` 已收敛，旧深链有显式跳转。
- 代码/契约/构建/部署/E2E 五类证据分层登记；E2E 只在最终镜像部署后执行一次。
