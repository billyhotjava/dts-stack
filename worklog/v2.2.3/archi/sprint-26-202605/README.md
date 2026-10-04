# Sprint-26: 语义指标与血缘真实模块拆分

**时间**: 2026-05
**状态**: DONE
**类型**: Frontend Architecture / Module Ownership / Contract Hardening
**目标**: 在 Sprint-25 IA 收敛基础上，消除“路由拆了但代码没拆”的架构债，把语义与指标中心、血缘与影响分析从巨型 section 页面推进到真实模块、真实页面和可验证边界。

## 背景

Sprint-25 完成了菜单、路由和产品口径收敛，但架构审查确认仍有三个问题：

1. 语义指标中心仍由 `SemanticModelingCenterPage` 单文件承载全部状态、API、副作用和 UI。
2. 血缘与影响分析仍由 `LineagePage` 单文件承载图谱、字段、影响、导入、diff 和导出逻辑。
3. 指标工作台 / 指标字典仍位于 `pages/governance`，与“语义与指标中心是根菜单”的所有权不一致。

本 Sprint 不继续堆新功能，优先做真实模块边界和可维护性。

## 架构原则

- **所有权优先**: `语义与指标中心` 代码归入 `pages/metrics/**`，旧治理/建模路径只保留兼容 wrapper。
- **真实拆页**: 子路由应拥有自己的页面组件、主要状态和数据加载；共享能力沉到 hooks/components，不再用一个巨型页面靠 `section` 条件渲染。
- **边界固化**: DWD 是语义建模输入，DWS/ADS 是 BI/大屏/API 消费输出；前端选择器和 API 调用都要体现这个约束。
- **血缘横向能力**: 全局血缘工作台归资产门户；语义、开发、治理只使用上下文血缘组件。
- **小步验证**: 每拆一层都保留旧路由兼容，并用 build + smoke + 截图验证。

## Feature 列表

| Feature | 优先级 | 状态 | 目标 |
|---------|--------|------|------|
| F1-指标模块所有权迁移 | P0 | DONE | 建立 `pages/metrics/**` 所有权，治理旧页面只做兼容 |
| F2-语义建模真实页面拆分 | P0 | DONE | 拆 `SemanticModelingCenterPage` 为 layout、hooks、真实页面 |
| F3-血缘工作台真实页面拆分 | P0 | DONE | 拆 `LineagePage` 为共享能力和真实页面 |
| F4-契约约束与验收 | P0 | DONE | 固化 DWD/DWS/ADS 约束、旧路由兼容和真实 smoke |

**统计**: PLANNED=0, IN_PROGRESS=0, DONE=4, BLOCKED=0

## 非目标

- 不新增指标市场、AI 问答、指标消费分析等友商功能。
- 不重写后端血缘存储模型。
- 不在本 Sprint 强制删除旧 `/governance/indicator-*`、`/modeling/semantic-center*` 路由。
- 不把业务人员指标配置放回数据开发中心或数据治理中心。

## 验收标准

- `/metrics/center`、`/metrics/dictionary`、`/metrics/semantic*` 的主实现归属 `pages/metrics/**`。
- 旧治理指标路由和旧建模路由仍可访问，但被标记为兼容入口。
- 语义建模子页面不再通过一个 1800+ 行组件集中承载全部状态。
- 血缘子页面不再通过一个 1200+ 行组件集中承载全部状态。
- `pnpm build` 通过，Sprint-26 smoke 通过。
