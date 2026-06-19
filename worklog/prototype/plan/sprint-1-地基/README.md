# Sprint-1: 地基（脚手架/设计系统/mock/外壳）

**时间**: 2026-06
**状态**: READY
**目标**: 立起原型工程地基——能在 Chrome 95 跑通的 React19+AntD5 骨架、Swiss 设计系统、mock 契约层、项目外壳+阶段轨+项目门户，让后续阶段 sprint 有可落地的承载面。

## 背景

dts-platform 现网（`source/dts-platform-webapp`）按"职能筒仓"组织约 60 页面，缺统一入口与"下一步"引导。本原型把它重构为 **项目/工作空间 + 阶段向导**（连接→集成→资产→指标）的统一 ELT 旅程。

Sprint 1 是整条关键路径的**地基**：S2 连接 / S3 画布内核 / S5 资产 都在 S1 完成后才能并行展开。本 sprint 不实现任何业务阶段页面，只交付四块可复用基础设施：

1. **工程脚手架**——与现网同栈（React19+TS+Vite+AntD5），搬现网 legacy 工具链保证 Chrome 95 可跑。
2. **Swiss 设计系统**——HSL/hex token（禁 oklch）、8px 基线、12 列网格、原子组件、CompactTable。
3. **Mock 框架**——分域 `*Service.ts` 返回 `Promise<Result<T>>`，`VITE_USE_MOCK` 开关，可一键切真实 axios；"销售准备项目"贯穿 4 阶段的种子数据。
4. **项目外壳 + 阶段轨 + 项目门户**——Shell 布局、项目切换器、阶段状态点（状态由项目数据派生）、"你的下一步"引导卡。

设计依据：[../2026-06-19-dts-platform-unified-elt-redesign-design.md](../2026-06-19-dts-platform-unified-elt-redesign-design.md)（§4 技术架构、§6 项目外壳、§8 Swiss 设计系统、§9 Mock 数据策略）。

## 约束基线（本 sprint 全程适用）

- **Chrome 95**：禁用 oklch / `:has()` / 容器查询 / subgrid；构建必开 `@vitejs/plugin-legacy`（`chrome >= 95`）+ 搬现网 `tools/postcss/legacy-css-fallbacks`；browserslist 含 `Chrome >= 95`。
- **mock 契约**：分域 `*Service.ts` 返回 `Promise<Result<T>>`（`Result = { status: ResultStatus; message: string; data: T }`，复刻现网 `src/types/api.ts`），`VITE_USE_MOCK` 开关；不接真后端，但保留一键切真实 axios 的适配层。
- **设计系统**：Swiss 网格、HSL/hex token（禁 oklch）、8px 基线、12 列网格、CompactTable 默认 10 条/页、tabular-nums。
- **可回植**：组件 / token / service 命名对齐现网 `source/dts-platform-webapp`（如 `Result`、`ResultStatus`、`CompactTable`、`contextStore`、`*Service.ts`），降低未来回植成本。

## Feature 列表

| ID | Feature | Task 数 | 状态 |
|----|---------|---------|------|
| F1 | 工程脚手架 | 3 | READY |
| F2 | Swiss 设计系统 | 3 | READY |
| F3 | Mock 框架 | 4 | READY |
| F4 | 项目外壳+阶段轨+项目门户 | 4 | READY |

**Task 合计**: 14

## Feature 依赖

```
F1 工程脚手架 ──┬─▶ F2 Swiss 设计系统 ──┐
                ├─▶ F3 Mock 框架 ───────┤─▶ F4 项目外壳+阶段轨+项目门户
                └───────────────────────┘
```

- F1 是其余三者的前置（先有工程才能放 token / mock / 外壳）。
- F2、F3 在 F1 之后可并行。
- F4 消费 F2（设计系统组件）与 F3（项目种子数据派生阶段状态）。

## 完成标准

- [ ] `worklog/prototype/app` 工程可 `dev` / `build`，产物在 Chrome 95 加载并渲染外壳无报错（F1）。
- [ ] 构建开启 `@vitejs/plugin-legacy`（`chrome >= 95`），browserslist 含 `Chrome >= 95`，legacy-css-fallbacks postcss 接入；源码层无 oklch / `:has()` / 容器查询 / subgrid（F1）。
- [ ] Swiss token（HSL/hex 色彩 + 字号层次 + 8px 间距 + 12 列网格）落地为 CSS 变量；原子组件（Button/Card/Surface/StatusDot/SectionTitle）+ CompactTable 可用（F2）。
- [ ] CompactTable 默认 10 条/页、切换条数刷新、tabular-nums 数字对齐（F2）。
- [ ] mock `apiClient` 返回 `Promise<Result<T>>` + 可调延迟；`VITE_USE_MOCK` 开关；保留切真 axios 适配层；"重置样例数据"开发入口可用（F3）。
- [ ] "销售准备项目"种子数据骨架贯穿 4 阶段（PLM 订单+ERP 客户→去重/连接→ODS 宽表→销售达成率指标），可被 service 读取（F3）。
- [ ] Shell 布局（顶栏项目上下文 + 左侧阶段轨 + 内容区，flex/grid，不用 `:has`/容器查询）渲染；项目切换器 + 项目上下文 store（zustand）工作（F4）。
- [ ] 阶段状态点（✓完成/●进行中/○待开始）由项目种子数据派生；项目门户"你的下一步"引导卡指向当前阶段待办动作（F4）。
- [ ] `it/README.md` 端到端验证项全部通过，证据归档到 `it/evidence/`。

## 交付物清单

| 类别 | 路径（相对 `worklog/prototype/app`） |
|------|----------|
| 工程配置 | `package.json` · `vite.config.ts` · `tsconfig.json` · `.browserslistrc` · `tools/postcss/legacy-css-fallbacks.ts` |
| 设计系统 | `src/ui/tokens.css` · `src/ui/atoms/*` · `src/components/table/CompactTable.tsx` |
| mock 层 | `src/mock/apiClient.ts` · `src/mock/fixtures/*` · `src/mock/services/*Service.ts` · `.env`（`VITE_USE_MOCK`） |
| 外壳 | `src/shell/AppShell.tsx` · `src/shell/StageRail.tsx` · `src/shell/ProjectSwitcher.tsx` · `src/store/contextStore.ts` · `src/shell/ProjectPortal.tsx` |
