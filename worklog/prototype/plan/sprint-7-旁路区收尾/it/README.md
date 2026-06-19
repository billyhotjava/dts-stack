# Sprint-7 集成测试 / 端到端走查（IT）

> 本 sprint 是收尾 sprint，IT 不止覆盖旁路区，还承担**端到端黄金主线走查**与 **Chrome 95 全量回归冒烟**。所有走查均在 `VITE_USE_MOCK=1` + legacy 构建产物下进行，样例项目固定为「销售准备项目」。

## 走查环境

- 构建：`@vitejs/plugin-legacy`（chrome>=95）legacy 产物；目标浏览器 Chrome 95（或等效 UA / legacy chunk 加载路径）。
- 数据：`VITE_USE_MOCK=1`，开发入口「重置样例数据」回到干净基线。
- 证据位置：截图与走查记录归档于 `worklog/prototype/plan/sprint-7-旁路区收尾/it/evidence/`（按 `golden-line/`、`chrome95/`、`global-search/`、`sidebar/` 分目录存放）。

## 1. 端到端黄金主线走查（核心收尾项）

证据位置：`it/evidence/golden-line/`

- [ ] 从项目门户进入「销售准备项目」，「你的下一步」引导卡指向当前阶段待办。
- [ ] 阶段① 连接：≥ 2 个数据源已连通（PLM 订单、ERP 客户），左轨 `①` 状态点为 `✓`。
- [ ] 阶段② 集成：画布上去重/连接节点跑通 ≥ 1 个转换作业，左轨 `②` 状态点为 `✓`；「查看生成的 dbt」抽屉可只读查看。
- [ ] 阶段③ 资产：ODS 宽表数据集已发布，血缘图（G6）可见上游来源，左轨 `③` 状态点为 `✓`。
- [ ] 阶段④ 指标：销售达成率指标已发布，左轨 `④` 状态点为 `✓`。
- [ ] 四阶段经左轨 + 引导卡可**连贯无断点**走完，项目上下文（顶栏切换器）全程 scoped 一致。

## 2. 旁路区入口与骨架走查

证据位置：`it/evidence/sidebar/`

- [ ] 左轨分隔线下「平台」折叠区可展开，五大域（服务/治理/安全/运维/设置）入口全部可点。
- [ ] 服务：`ApiServices`/`Tokens`/`BiLinks`/`BusinessConsumption`/`DataProducts`(服务) 均有 mock 列表骨架（CompactTable 10 条/页）。
- [ ] 治理：`GovernanceCenter`/`PermissionAudit`/`QualityRules`(管理) 入口可达、有骨架。
- [ ] 安全：`data-security`/`DatasetAccessApproval` 入口可达、有骨架。
- [ ] 运维：`OpsOverview`/`Instances`/`Backfill`/`AlertLog`/`LogCenter`/`ReleaseGovernance`/`PlatformEventObservability`/`AuditEvidence` 入口全部可达、有骨架。
- [ ] 设置：`settings`/`sys` 入口可达、有骨架。
- [ ] 每个旁路页面均标注其使用的 mock service，且经 `*Service.ts` 取数（无组件内硬编码业务数据）。

## 3. 全局搜索 ⌘K 走查

证据位置：`it/evidence/global-search/`

- [ ] ⌘K（mac）/ Ctrl+K（win）唤起搜索面板，Esc 关闭。
- [ ] 索引覆盖四阶段（连接/集成/资产/指标）+ 五旁路域（服务/治理/安全/运维/设置）。
- [ ] 输入关键词命中分组结果（按阶段/旁路域分组）。
- [ ] 命中项回车 / 点击可跳转到目标页面或阶段，并保持当前项目上下文。
- [ ] 键盘上下导航 + 回车选中可用（a11y 兜底，不依赖鼠标）。

## 4. Chrome 95 全量回归冒烟（核心收尾项）

证据位置：`it/evidence/chrome95/`

- [ ] legacy 构建产物在 Chrome 95 路径下可加载，无白屏 / 控制台致命报错。
- [ ] 全量页面（四阶段 + 五旁路域）渲染无 CSS 新特性导致的布局崩坏。
- [ ] 静态核查：源码无 `oklch(` / `:has(` / 容器查询（`@container`/`container-type`）/ `subgrid`（`legacy-css-fallbacks` 仅作安全网，不依赖兜底）。
- [ ] 画布（`@xyflow/react` v12）平移/缩放/连线在 legacy 产物下正常；面板→画布拖放有键盘 a11y 兜底（选节点→「添加到画布」）。
- [ ] CompactTable 分页（默认 10 条/页、切换条数刷新、回第 1 页）在旁路区与主线一致工作。

## 5. 视觉一致性核查

证据位置：`it/evidence/golden-line/`（与主线截图对照）

- [ ] 旁路区沿用 Swiss 设计 token（HSL/hex、Inter、8px 基线、12 列网格）。
- [ ] 状态点语言（阶段/节点/运行/连接状态）跨主线与旁路区统一。
- [ ] 数据区数字列统一 `tabular-nums` 等宽对齐。
- [ ] 动效仅 `transform/opacity`，克制一致。

## 退出标准

- [ ] 第 1～5 节全部勾选。
- [ ] 端到端黄金主线走查 + Chrome 95 全量回归冒烟两项核心收尾项无 BLOCKER。
- [ ] 旁路区允许「先占位」，但占位页不得报错、不得断路由。
