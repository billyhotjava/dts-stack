# F3: 接入与调度

**优先级**: P1
**状态**: READY

## 目标

收纳现网接入变更（`AccessChangesPage`）与任务调度（`TaskSchedulingPage`）两个 foundation 页面进阶段①，作为连接侧的"接入审批/变更记录"与"采集调度"管理面。原型阶段以列表 + 只读详情为主，mock 驱动。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| [T01](./T01-接入变更.md) | 接入变更 | P1 | READY | S1 |
| [T02](./T02-任务调度.md) | 任务调度 | P1 | READY | F1-T01 |

## 完成标准

- [ ] `AccessChangesPage` 用 CompactTable 列出接入/变更记录（对象/类型/状态/时间），接 mock。
- [ ] `TaskSchedulingPage` 列出采集/同步调度（任务名/关联数据源/周期/状态/上次运行），接 mock，可关联到 F1 数据源。
- [ ] 两页均在阶段① slot 内可路由可访问，`VITE_USE_MOCK` 下有样例数据。
