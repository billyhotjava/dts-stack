# F3: 菜单/路由收敛至原生页 + iframe 退役（配合 SP-4）

**优先级**: P1
**状态**: READY（实际切换 gate 在 F2 达平价 + SP-4 退役节奏）
**依赖**: F2（原生页须先具备工作台平价）；SP-4（dts-metrics 服务下线）

## 目标
把语义建模菜单/路由从 **iframe 嵌入 dts-metrics-webapp** 收敛到**平台原生治理页**（`src/pages/modeling/`），移除 `MetricsServiceFrame`，配合 SP-4 dts-metrics 服务退役，并保旧链接兼容。

## 现状落点
- `src/routes/sections/dashboard/static-routes.tsx`：`MetricsServiceFrame`（iframe）+ 路由
  `/modeling/semantic-center`(*)、`/bi-apps/metrics`(*)、`/bi/semantic-modeling` → 现指向 iframe。
- `metricsServiceRoutes.ts`：平台 path → `/metrics/*`（dts-metrics）映射 + `metricsServiceEmbeddedHrefFromPlatformLocation`。
- 菜单 seed / `DynamicMenuResolver(base="/modeling")`。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 路由切换：iframe 路由 → 平台原生治理页 + 旧 path 兼容重定向 | P1 | READY | F2 |
| T02 | 移除 MetricsServiceFrame + metricsServiceRoutes 映射 + iframe 代码 | P1 | READY | T01, SP-4 |
| T03 | 菜单/导航文案统一 + 路由/菜单回归测试 | P1 | READY | T01 |

## 完成标准
- [ ] `/modeling/semantic-center` 等指向平台原生治理页（非 iframe）；旧链接重定向不断。
- [ ] `MetricsServiceFrame`/`metricsServiceRoutes` 及 iframe 代码移除（SP-4 dts-metrics 下线后）。
- [ ] 菜单"指标与语义中心"入口指向原生治理中心；路由/菜单回归测试通过。

## 切换时序（重要）
1. **先决条件**：F2 原生页达到工作台平价（图形建模 + 受控 DSL + 字段树）。
2. T01 切路由（可灰度：feature flag 切原生 vs iframe）。
3. SP-4 下线 dts-metrics 服务/webapp 后，T02 删除 iframe 残留。
4. 不可在 F2 未平价前切换（否则用户体验倒退）——故 F3 实际执行排在 F2 之后、与 SP-4 协同。
