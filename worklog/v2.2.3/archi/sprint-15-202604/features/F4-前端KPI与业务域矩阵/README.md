# F4: 前端 KPI 与业务域矩阵

**优先级**: P0
**状态**: READY

## 目标

基于 F3 输出的 `WorkbenchFilterState` 和 F1 的 `leader-overview` 聚合响应，实现：

1. `KpiRow`：三角色差异化 KPI 行（员工 3 卡 / 部门领导 3 卡 / 所领导 4 卡）。
2. `DomainMatrix`：仅所领导专属的色块矩阵，点击切换全局 `bizDomain`。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | KpiRow 组件（三角色差异化） | P0 | READY | F3/T05, F1/T03 |
| T02 | KPI 环比 / 比例副标题渲染 | P1 | READY | T01 |
| T03 | DomainMatrix 色块组件 | P0 | READY | F3/T05, F1/T06 |
| T04 | 色块联动全局过滤器 | P0 | READY | T03 |

## 完成标准

- [ ] 三种角色下 KPI 卡片数量和语义正确（员工 3 / 部门领导 3 / 所领导 4）。
- [ ] "本期"文案跟随 `timeRange` 动态切换（本月 / 本季 / 本年）。
- [ ] 环比副标题（`visitsMoM`）：正数绿色 ↑、负数红色 ↓、null 隐藏。
- [ ] `assetsS1Ratio` 在所领导卡上以百分比展示，null 隐藏。
- [ ] `DomainMatrix` 在 `role=INST_LEADER` 且 `bizDomainAvailable=true` 且数据非空时渲染；否则不渲染。
- [ ] 色块颜色按访问量深浅渐变（同色系），"其他"桶用统一灰色。
- [ ] 点击色块 → 触发 `onChange(filter)` 将 `bizDomain` 设为该域并打埋点 `WORKBENCH_DOMAIN_DRILL`。
