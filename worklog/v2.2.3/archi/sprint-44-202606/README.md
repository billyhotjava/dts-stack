# Sprint-44: 语义层整合 Phase 3 — 平台原生语义 UI 重建与治理呈现

**时间**: 2026-06
**状态**: READY（**已按现状勘察修订**——见下"现状修正"）
**类型**: Frontend / Implementation（dts-platform-webapp 原生语义建模 UI）
**实施分支**: 续 `feat/semantic-consolidation`（或 `feat/semantic-frontend`）

## 现状修正（2026-06-16，方案 A 重写）
原计划假设"平台已有原生语义页、只需加治理"。**勘察推翻该假设**：
- 平台 `src/pages/modeling/Semantic*Page` **全是 5 行跳转壳** → `SemanticModelingCenterPage` → `window.location.replace("/metrics/semantic/*")`（跳到 dts-metrics-webapp）。提交 `1648fda0d fix: isolate metrics frontend boundary` 表明这是**有意把语义前端隔离进 dts-metrics**。
- `semanticModelingApi.ts`（`/api/semantic` 客户端）**无任何页面 import = 死代码**。
- 即：**真正在用的语义 UI 是 dts-metrics-webapp（走 /api/metrics）**；平台 `/api/semantic`（`SemanticModelingService`，34 端点，成熟）**后端在、前端空**。

**结论**：要落"平台权威 + 退役 dts-metrics"，SP-3 必须**在 /api/semantic 上重建平台原生语义 UI**（替换刻意的跳转壳、移植 dts-metrics-webapp 的页面与工作台），再叠治理/工作台/收敛。这比"加开关"大得多——故重写本 sprint。

> 注意：此举**逆转**了 `1648fda0d` 的"隔离"决定，是整合"平台权威"的必然代价；已在路线图记录。

## 目标
在 `/api/semantic` 上重建平台原生语义建模 UI，替换跳转壳，成为唯一权威 UI；并承载 SP-1 受控治理。

## Feature 列表（重排）

| ID | Feature | Task 数 | 状态 | 优先级 | 依赖 |
|----|---------|---------|------|--------|------|
| **F0** | **原生语义建模页骨架与 CRUD（替换跳转壳，消费 /api/semantic）** | 4 | READY | P0 | - |
| F1 | 受控建模治理前端呈现（governanceMode + 受控 DSL 提示 + 分层诊断） | 4 | READY | P0 | **F0** |
| F2 | React Flow 可视化工作台亮点移植（复用 analytics 画布 + dts-metrics 亮点） | 4 | READY | P1 | F0；SP-2 列族 |
| F3 | 菜单/路由收敛 + iframe/跳转壳退役（配合 SP-4） | 3 | READY | P1 | F0,F1,F2；SP-4 |

**执行序**：**F0（重建原生页，前置）** → F1（治理呈现）→ F2（图形工作台）→ F3（路由收敛）。
原 F1/F2/F3 内容不变，但全部**依赖新增 F0**——没有真实原生页，治理/工作台无处附着。

## 完成标准
- [ ] F0：`Semantic*Page` 不再跳转，渲染消费 `/api/semantic` 的真实 CRUD 页（subjects/objects/dimensions/metrics/models + 评审/发布/runs）；`semanticModelingApi` 由死变活。
- [ ] F1：原生页上 governanceMode 控件 + 422/400 引导式提示（详见 F1 文档）。
- [ ] F2：原生页接入可视化画布（复用 analytics React Flow）+ 受控 DSL 构造器 + 字段树。
- [ ] F3：菜单/路由指向原生页，跳转壳/iframe 退役（配合 SP-4）。

## 非目标
- 不改后端 `/api/semantic`（SP-1 已落，34 端点就绪）。
- 不在重建（F0）完成前切路由（F3）——否则 UX 倒退。
- 不强求一次性复刻 dts-metrics-webapp 全部细节；先核心 CRUD + 治理 + 工作台，长尾增量。

## 相关材料
- 现状勘察依据: 本 README "现状修正"；提交 `1648fda0d`。
- 后端 SP-1: `worklog/v2.2.3/sprint-41-202606/README.md`（/api/semantic 受控治理）
- 平价来源（dts-metrics-webapp）: `source/dts-metrics-webapp/src`（MetricsShell + SemanticDesignerPage）
- 后端客户端（死代码，待激活）: `src/api/semanticModelingApi.ts`
- 整合路线图: `worklog/v2.2.3/sprint-41-202606/assets/semantic-consolidation-roadmap.md`
- 集成测试: `it/README.md`
