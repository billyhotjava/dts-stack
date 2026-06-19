# Sprint-3: ② 集成 · 画布内核

**时间**: 2026-06
**状态**: READY
**目标**: 搭出可视化 ELT 画布的可拖拽、可连线、可缩放内核——5 类节点 + 节点面板拖入（含键盘 a11y 兜底）+ reactflow 原生交互 + zustand 状态 + 节点卡片渲染与连接校验，跑通"销售准备项目"样例的端到端画布骨架。

## 背景

这是整个统一 ELT 旅程原型的**重头戏**。现网把 ELT 能力切碎成 Transform / Orchestration / ScriptStudio / EltConsole 散落在 `explore/etl` 下，没有一张统一的可视化画布。本 sprint 在阶段② 集成里建立**可视化 ELT 画布内核**：用户从节点面板把"源表 / 清洗 / 连接 / 聚合 / 输出"五类节点拖到画布上，连线表达数据流向，底层（后续 sprint）自动生成 dbt model，普通用户全程不接触 dbt。

设计依据见 [`../2026-06-19-dts-platform-unified-elt-redesign-design.md`](../2026-06-19-dts-platform-unified-elt-redesign-design.md) §7「可视化 ELT 画布」。

本 sprint 只做**画布内核**（节点/边/拖拽/缩放/状态/卡片/连接校验）；属性配置抽屉、运行 dock、画布⇄列表双视图在 **Sprint 4** 完成。

### 技术约束基线（继承自 sprint-queue 全局基线）

- **画布库**: `@xyflow/react` v12（现网同款，已在 legacy 构建跑通，参考现网 `src/components/visual-canvas/VisualFlowCanvas.tsx`）。
- **Chrome 95**: 画布布局**禁用** `:has()` / 容器查询 / subgrid / oklch；改用 flex/grid 固定栏 + `ResizeObserver` 感知尺寸。每个入口模块顶部 `import "@/polyfills/legacy-browser"`（对齐现网）。
- **拖拽**: 节点面板→画布用 `@dnd-kit`；**必须有键盘 a11y 兜底**（选中面板节点 → "添加到画布"按钮，无需鼠标拖拽即可落点）。连线 / 平移 / 缩放 / 网格对齐 / 小地图走 reactflow 原生。
- **状态**: 画布节点/边/选中态用 `zustand` store；不把派生值冗余存储。
- **mock 契约**: 画布节点/边数据走 mock service（`orchestrationService` / `transformService`），返回 `Promise<Result<T>>`，`VITE_USE_MOCK` 开关。
- **性能**: 节点多时启用 reactflow 虚拟化（`onlyRenderVisibleElements`）；原型阶段把样例 demo 节点数控制在 ~12 以内。
- **命名对齐现网**: 页面 `TransformPage` / `OrchestrationPage`，组件参考 `VisualFlowCanvas` / `WorkflowCanvas`。

## Feature 列表

| ID | Feature | Task 数 | 状态 |
|----|---------|---------|------|
| F1 | 画布基础 | 4 | READY |
| F2 | 节点卡片 | 2 | READY |

## 依赖

- **Sprint 1（地基）**: 外壳/阶段轨、Swiss 设计系统 token、mock 框架（`Result<T>` + `VITE_USE_MOCK`）。本 sprint 全部 Task 依赖 S1。

## 完成标准

- [ ] 5 类节点类型（源表/清洗/连接join/聚合/输出）已在 reactflow 自定义 nodeTypes 注册并可渲染。
- [ ] 节点面板可通过 **dnd-kit 拖拽** 或 **键盘 a11y 兜底（选中→"添加到画布"）** 两种方式把节点落到画布。
- [ ] 画布支持 reactflow 原生连线 / 平移 / 缩放 / 网格对齐（snap-to-grid）/ 小地图。
- [ ] 画布状态（nodes / edges / selectedId）由 zustand store 管理，节点/边数据来自 mock service 的 `Result<T>`。
- [ ] 节点卡片显示 类型图标 + 名称 + 状态点 + 行数徽标；边按数据流向渲染，**非法连接被拦截并给出可见反馈**。
- [ ] "销售准备项目"样例画布可加载（PLM.订单 + ERP.客户 → 去重/连接 → ODS.宽表），端到端可点可拖。
- [ ] 画布布局无 `:has()`/容器查询/subgrid/oklch；legacy（chrome>=95）构建可跑通。
- [ ] `it/README.md` 的端到端验证项全部可执行，含拖拽与键盘 a11y 兜底两条路径。

## 交付物路径

- 画布封装与节点：`worklog/prototype/app/src/canvas/`
- 集成阶段页面：`worklog/prototype/app/src/stages/integrate/`
- mock 契约：`worklog/prototype/app/src/mock/services/orchestrationService.ts`、`transformService.ts`
