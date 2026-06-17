# F0: 原生语义建模页骨架与 CRUD（替换跳转壳）

**优先级**: P0（全 SP-3 前置）
**状态**: READY

## 目标
把平台 `src/pages/modeling/Semantic*Page` 从"跳转 dts-metrics 的 5 行壳"重建为**消费 `/api/semantic` 的真实原生页**（subjects/objects/dimensions/metrics/models + 评审/发布/runs），激活死代码 `semanticModelingApi.ts`。这是 F1（治理）/F2（工作台）/F3（收敛）的前置——没有真实原生页，后续无处附着。

## 现状（勘察）
- `SemanticModelingCenterPage` 现 `window.location.replace("/metrics/semantic/*")`（跳 dts-metrics）；`SemanticModelsPage` 等是其 5 行壳。
- `semanticModelingApi.ts`（34 端点对应的客户端）**无人 import**——本 feature 激活它。
- 后端 `/api/semantic`（`SemanticModelingResource`，34 端点）成熟可用。

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
