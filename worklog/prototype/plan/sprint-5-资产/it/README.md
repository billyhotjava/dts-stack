# Sprint-5 IT · 阶段③ 资产 端到端验证

**状态**: READY
**范围**: 纯前端原型 + mock（`VITE_USE_MOCK=1`），不接真后端。验证阶段③「资产」全部页面可达、契约形状对齐现网、`@antv/g6` 血缘图 legacy 可用，以及样例项目「销售准备项目」从 S4 画布产出的 `ODS.宽表` 在资产阶段端到端连贯。

## 前置条件

- Sprint 1 地基已就绪（项目外壳 + 阶段导航轨 + Swiss 设计系统 + mock 框架）。
- 已选中样例项目「销售准备项目」；阶段②集成（S4）已产出 `ODS.宽表`（PLM 订单 + ERP 客户经去重/连接）。
- 构建开 `@vitejs/plugin-legacy`（chrome>=95）；`VITE_USE_MOCK=1`。

## 验证项

### IT-1 阶段③ 路由可达性（F1/F2/F3）
- [ ] 阶段③ slot 内全部页面可路由访问：`data-search` / `datasets` / `datasets/:id` / `data-products` / `lineage/graph` / `lineage/columns` / `lineage/impact` / `lineage/diff` / `lineage/import` / `metadata` / `quality` / `quality/report` / `quality/rules` / `ownership` / `grants` / `my-grants`。
- [ ] 左轨阶段③状态点在「发布数据集 ≥ 1」时变为 `✓`。

### IT-2 资产搜索（F1-T01）
- [ ] 关键词「宽表」命中样例 `ODS.宽表`；域/层/类型过滤组合生效。
- [ ] 结果 CompactTable 默认 10 条/页；切换每页条数后刷新并回到第 1 页。
- [ ] 过滤/排序/分页状态持久化到 URL search params；点击行跳数据集详情。

### IT-3 数据集列表+详情 / 数据产品（F1-T02、F1-T03）
- [ ] `DatasetsPage` 列表 CompactTable 渲染、过滤生效；点击进入 `DatasetDetailPage`。
- [ ] `ODS.宽表` 详情字段与 S4 画布去重/连接输出一致（PLM/ERP 合并列）；血缘、质量入口可跳 F2/F3。
- [ ] `DataProductsPage` 列出派生自 `ODS.宽表` 的数据产品并接 mock。

### IT-4 血缘图 @antv/g6 legacy（F2-T01）
- [ ] 血缘图在 legacy 构建下渲染，可平移/缩放/选中，Minimap/ToolBar 可用。
- [ ] 资产节点与任务节点视觉可区分；边显示数据流向箭头。
- [ ] `ODS.宽表` 上游图含 `PLM.订单`/`ERP.客户` 源表 + 去重/连接任务节点（连贯 S4 产出）。
- [ ] 方向（上游/下游/双向）与布局（LR/TB）切换生效。

### IT-5 血缘配套四视图（F2-T02）
- [ ] `LineageSectionNav` 在 graph/columns/impact/diff/import 间切换并路由。
- [ ] 列级血缘展示字段映射；影响分析列出下游受影响资产并可高亮联动。
- [ ] diff 区分新增/删除/变更边；导入可回显解析的节点/边预览。

### IT-6 质量闭环（F3-T01）
- [ ] 质量看板/报告/规则三页可达；列表 CompactTable 默认 10 条/页、切换刷新+回首页。
- [ ] `ODS.宽表` 有 ≥1 质量规则（如 `order_id` 唯一非空）与 1 份报告；通过/失败两态 HSL 语义色区分。
- [ ] 质量规则可新建/编辑并写入内存 fixtures。

### IT-7 权属闭环（F3-T02）
- [ ] 权属/授权/我的授权三页可达；列表 CompactTable 默认 10 条/页、切换刷新+回首页。
- [ ] `ODS.宽表` 有 owner（销售数据团队）+ ≥1 授权；授权可新建/撤销；我的授权可列出当前用户持有授权。

### IT-8 元数据（catalog 收纳项）
- [ ] `MetadataPage` 可路由访问并接 `metadata` mock service。

### IT-9 mock 契约与开关
- [ ] 所有页面 service 返回 `Promise<Result<T>>` 形状（`catalogDomainService` / `metadata` / `dataProductsService` / `lineageService` / `qualityService` / `assetGrantService` / `assetSearchService` / `datasetsService`）。
- [ ] `VITE_USE_MOCK` 开关下数据来自内存 fixtures，可注入/重置；关闭后预留真实 axios 切换路径不报错（不要求真实数据）。

### IT-10 Chrome 95 兼容
- [ ] legacy 构建产物在 Chrome 95 加载无控制台致命错误；血缘图 `@antv/g6` v4.x 可用。
- [ ] 全 sprint 页面未使用 oklch / `:has()` / 容器查询 / subgrid（CSS 源码层手写 HSL/hex token）。

### IT-11 样例项目端到端连贯（黄金主线）
- [ ] 「销售准备项目」: 阶段②产出的 `ODS.宽表` → 阶段③可搜索 → 可查血缘（上游含 PLM/ERP + 去重/连接）→ 可挂质量规则与报告 → 可标权属与授权，全链路可点可走。

## 证据建议（原型阶段，非强制 80% 覆盖）

- 关键页面截图：资产搜索、数据集详情、血缘图（含样例上游）、质量报告、权属/授权。
- mock service 契约对齐说明（导出签名 vs 现网）。
- legacy 构建产物在 Chrome 95 的加载验证记录。
