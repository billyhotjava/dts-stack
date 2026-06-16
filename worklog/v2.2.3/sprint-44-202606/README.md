# Sprint-44: 语义层整合 Phase 3 — 语义工作台前端整合

**时间**: 2026-06
**状态**: READY
**类型**: Frontend / Implementation（dts-platform-webapp 原生语义建模页）
**实施分支**: 续 `feat/semantic-consolidation`（或 `feat/semantic-frontend`）

## 目标

把整合的语义建模能力收敛到 **dts-platform-webapp 原生语义建模页**（`src/pages/modeling/`，调 `/api/semantic`）。本 sprint 先做 **F1 受控建模治理前端呈现**——让 SP-1 的后端治理（governanceMode、受控 DSL 违规、ELT 分层闸诊断）在用户每天用的页面里可见、可用、可理解。React Flow 工作台亮点移植（F2+）随后。

## 背景

- **SP-1（Sprint-41）后端已落**：受控 DSL（拒原始 SQL）+ ELT 分层闸 + governanceMode 开关，全在 `/api/semantic`（`SemanticModelingService`），36 测试绿。但**只动后端**——前端尚无 governanceMode 控件、无 422/400 友好提示、无分层诊断展示。
- **现状歧义**：菜单 `/modeling/semantic-center` 当前指向 `MetricsServiceFrame`（iframe 嵌入待退役的 dts-metrics-webapp）；调 `/api/semantic` 的平台原生页（`SemanticModelsPage`/`SemanticMetricsPage` 等）才是整合后的权威 UI。本 sprint 让权威 UI 承载受控治理；SP-4 退役 iframe 后菜单收敛至此。
- 关联：[[dts-metrics-elt-ecosystem]]（整合决策）、Sprint-41（后端）、Sprint-43（SP-2 语义富化，解锁列族字段选择）。

## Feature 列表

| ID | Feature | Task 数 | 状态 | 优先级 |
|----|---------|---------|------|--------|
| F1 | 受控建模治理前端呈现（governanceMode + 受控 DSL 提示 + 分层诊断） | 4 | READY | P0 |
| F2 | React Flow 可视化工作台亮点移植（受控图形建模） | 4 | READY | P1 |
| F3 | 菜单/路由收敛至原生页（配合 SP-4 退役 iframe） | 3 | READY | P1 |

> 三 feature 均已细化。**执行序**：F1（治理呈现，独立）→ F2（图形工作台，依赖 SP-2 列族）→ F3（路由收敛，依赖 F2 平价 + SP-4 退役）。
> 关键洞察：平台已有 React Flow 画布（`analytics/pages/semantic`，BI 卡片用），F2 **复用而非再造**——把 dts-metrics 治理亮点（受控 DSL 构造器 / ELT 分层 UX）叠加到治理路径。

## 完成标准（F1）
- [ ] 模型创建/编辑可设 `governanceMode`（CONTROLLED|PERMISSIVE），列表可见模式。
- [ ] 受控模型定义派生指标时，422 `unsafe_expression` 渲染为内联可读提示（白名单引导，禁原始 SQL）。
- [ ] 受控模型提交评审失败时，400 分层码（invalid_layer/grain_mismatch/standard_code_required）渲染为定位到模型/层的可读诊断。
- [ ] CONTROLLED/PERMISSIVE 视觉区分 + 文案；前端契约测试覆盖。

## 非目标
- 不动后端 `/api/semantic`（Sprint-41 已落，本 sprint 只做前端呈现）。
- 不做 React Flow 工作台移植（F2，后续）与 iframe 退役（F3/SP-4）。
- 不依赖 SP-2 列族（F1 只呈现治理；字段选择列族属 F2/SP-2 联动）。

## 相关材料
- 后端 SP-1: `worklog/v2.2.3/sprint-41-202606/README.md`
- 整合路线图: `worklog/v2.2.3/sprint-41-202606/assets/semantic-consolidation-roadmap.md`
- 前端落点: `dts-platform-webapp/src/pages/modeling/`（`SemanticModelsPage`/`SemanticMetricsPage`/`components/ModelEditDrawer`）+ `src/api/semanticModelingApi.ts`
- 集成测试: `it/README.md`
