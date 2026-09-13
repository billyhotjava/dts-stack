# F0: 原生语义建模页骨架与 CRUD（替换跳转壳）

**优先级**: P0（全 SP-3 前置）
**状态**: READY

## 目标
把平台 `src/pages/modeling/Semantic*Page` 从"跳转 dts-metrics 的 5 行壳"重建为**消费 `/api/semantic` 的真实原生页**（subjects/objects/dimensions/metrics/models + 评审/发布/runs），激活死代码 `semanticModelingApi.ts`。这是 F1（治理）/F2（工作台）/F3（收敛）的前置——没有真实原生页，后续无处附着。

## 现状（勘察，cold-start 必读）
- **两条渲染路径都通 dts-metrics**（重建前必先理清菜单实际走哪条）：
  1. `src/routes/sections/dashboard/static-routes.tsx`：`/modeling/semantic-center`(*) → `<MetricsServiceFrame/>`（**iframe** 嵌 dts-metrics-webapp，经 `metricsServiceRoutes.ts` 映射到 `/metrics/*`）。
  2. `src/pages/modeling/Semantic*Page`（5 行壳）→ `SemanticModelingCenterPage` → `window.location.replace("/metrics/semantic/*")`（**整页跳转**）。
- 即同一套语义入口被 iframe + 跳转壳两种方式都导向 dts-metrics；F0 重建须同时处理这两条（flag 切原生时，iframe 路由与跳转壳都要改走原生容器）。
- `semanticModelingApi.ts`（34 端点对应的客户端）**无人 import = 死代码**——本 feature 激活它。
- 后端 `/api/semantic`（`SemanticModelingResource`，34 端点）成熟可用。
- env flag 模式：照 `src/global-config.ts`（如 `String(import.meta.env.VITE_ENABLE_SQL_IDE_V2 || "true").toLowerCase()==="true"`），新增 `VITE_SEMANTIC_NATIVE`（默认关=保持现跳转/iframe）。
- API 包装：`semanticModelingApi` 用 `api`@`@/api/apiClient` + `quiet`（_skipErrorToast）；最小验证入口 `getSemanticWorkbenchOverview()` → `/semantic/workbench`。
- 提交 `1648fda0d fix: isolate metrics frontend boundary` = 跳转壳是**有意隔离**的结果；F0 逆转它，是整合代价。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 容器重建：SemanticModelingCenterPage 真实 sectioned 容器（去跳转）+ 激活 semanticModelingApi | P0 | READY | - |
| T02 | subjects / objects(+table-mappings) / dimensions CRUD 页 | P0 | READY | T01 |
| T03 | metrics(+formula) / models(+bindings) CRUD + 列表 | P0 | READY | T01 |
| T04 | 评审/发布/runs 流（submit-review/approve/reject/publish-dbt/runs/artifacts） | P0 | READY | T03 |

## 完成标准
- [ ] `Semantic*Page` 渲染真实原生页（不再 window.location.replace 跳转 dts-metrics）。
- [ ] subjects/objects/dimensions/metrics/models CRUD 贯通 `/api/semantic`；列表/详情/创建/编辑可用。
- [ ] 评审/发布/runs 流可操作（对应后端端点）。
- [ ] `semanticModelingApi.ts` 由死变活；Chrome 95 兼容；构建（tsc + vite）通过。

## 范围控制（YAGNI）
- 先核心 CRUD + 生命周期，UI 走平台既有 antd 风格（参照其他 modeling 页如 SqlModelingPage）。
- 不一次性复刻 dts-metrics-webapp 全部细节（工作台画布属 F2，长尾增量）。
- 灰度：可用 feature flag 在"原生页 vs 跳转 dts-metrics"间切，保回退（与 F3-T01 联动）。
