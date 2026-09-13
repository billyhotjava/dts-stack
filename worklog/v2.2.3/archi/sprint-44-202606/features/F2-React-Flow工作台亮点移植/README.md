# F2: React Flow 可视化工作台亮点移植（受控图形建模）

**优先级**: P1
**状态**: READY
**依赖**: F1（治理前端基座）；列族字段角色依赖 Sprint-43 SP-2

## 目标
给平台**治理建模路径**（`src/pages/modeling/`，调 `/api/semantic`）配上可视化图形建模 UX，把 dts-metrics 的图形建模亮点（语义画布 + 字段树拖拽 + **受控派生指标 DSL 构造器** + **ELT 分层准入 UX**）带到平台原生页。

## 关键设计前提（先收敛，再动手）
平台前端**已有三套**语义 UI，F2 须**复用而非再造**：
- `pages/modeling/`（治理建模，纯表单，`/api/semantic`）= **目标**。
- `analytics/pages/semantic/SemanticModelCanvas`（**已有 React Flow 画布**，`analyticsApi`，BI 卡片）= **复用候选组件**。
- dts-metrics-webapp `SemanticDesignerPage`（画布 + 受控 DSL 构造器 + ELT 分层 UX，`/api/metrics`，待退役）= **亮点来源**。

**取向**：优先把 analytics 已有的 `SemanticModelCanvas` 泛化/复用到治理路径，叠加 dts-metrics 的受控亮点；**不引入第三套画布**（与整合"模块化复用"一致）。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 画布方案收敛（复用 analytics 画布 vs 移植 dts-metrics）+ 组件接口 | P1 | READY | F1 |
| T02 | 受控派生指标 DSL 构造器（前端，白名单表单，非裸 SQL） | P1 | READY | T01 |
| T03 | 字段树拖拽 + ELT 分层准入 UX（字段按列角色分组 + 层级准入可视化） | P1 | READY | T01, SP-2 列族 |
| T04 | 画布建模保存贯通 `/api/semantic`（model + bindings + 提交评审） | P1 | READY | T01,T02,T03 |

## 完成标准
- [ ] 治理建模页有可视化画布（复用 analytics 组件，不新增第三套）。
- [ ] 受控模型在前端用 DSL 构造器生成合法受控公式，减少 422 往返（与 F1-T02 联动）。
- [ ] 字段树按列角色（dimension/metric/time）分组（依赖 SP-2 列族）；分层准入在画布有可视化提示（与 F1-T03 联动）。
- [ ] 画布建模可保存/提交评审，贯通 `/api/semantic`；与现有表单并存或替代（T01 定）。
