# Sprint-52: 指标工作台 & 语义建模全面整合

**时间**: 2026-06
**状态**: READY
**目标**: 构建 React Flow 三栏指标工作台，替换全部 SemanticXxxPage 重定向壳为真实页面，实现 DWS/ADS 建模 → 指标可视化 → 消费看板的端到端闭环

## 背景

Sprint-41 已完成 34 个 `/semantic/*` 后端 API 端点，`semanticModelingApi.ts` 客户端已就绪。
Sprint-44（前端整合）计划就绪但未实施，全部 SemanticXxxPage 仍是 `window.location.replace` 跳转壳。
用户要求：产品能力层最优先，指标可视化（React Flow 三栏画布）为核心，数据资产次之，保留 v2 黄金链路所有现有路由。

## 约束

- Chrome 95: 禁 oklch / :has() / container queries / subgrid；颜色用 HSL/HEX
- 不触碰 `services/dts-airflow/dags/addax-env-runner.jar`
- 不新增 `/v2` 路由命名空间
- `/modeling/semantic-center`（MetricsServiceFrame iframe）保持不动
- 每个 Task 完成后过 tsc + source-contract + 页面 smoke

## 技术基础

| 资产 | 位置 | 状态 |
|------|------|------|
| `semanticModelingApi.ts` | `src/api/semanticModelingApi.ts` | ✅ 34 个函数已就绪 |
| `@xyflow/react` | package.json `^12.10.2` | ✅ 已安装 |
| `WorkflowCanvas` | `src/components/workflow/WorkflowCanvas.tsx` | ✅ 可复用 |
| `VisualFlowCanvas` | `src/components/visual-canvas/VisualFlowCanvas.tsx` | ✅ 可复用 |
| SemanticXxxPage（6个） | `src/pages/modeling/Semantic*.tsx` | ⚠️ 全为跳转壳 |
| `dataDevelopmentWorkbench.source-contract.test.ts` | `src/pages/modeling/` | ✅ 需新增断言 |

## Feature 列表

| ID | Feature | Task 数 | 状态 |
|----|---------|---------|------|
| F1 | 指标工作台主页 | 4 | READY |
| F2 | 主题域与业务对象页 | 2 | READY |
| F3 | 指标与模型页 | 2 | READY |
| F4 | 发布与运行监控页 | 2 | READY |
| F5 | 验证收尾 | 2 | READY |

## 完成标准

- [ ] `/modeling/metric-workbench` 路由可访问，三栏画布正常渲染
- [ ] 6 个 SemanticXxxPage 壳全部替换为真实页面，不含 `window.location.replace`
- [ ] `pnpm exec tsc --noEmit` 零报错
- [ ] `pnpm build` 成功（Chrome 95 legacy bundle）
- [ ] 全部 source-contract 测试通过（baseline 失败数不增加）
- [ ] `/modeling/semantic-center` MetricsServiceFrame 路由保持不动
