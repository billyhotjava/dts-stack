# F1: 端到端旅程工作台与上下文保持

**优先级**: P0
**状态**: DONE

## 目标

把 `/workbench` 升级为端到端数据产品旅程入口，让用户知道当前在哪一步、缺什么、下一步去哪，并在子页面保持 journey context。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 工作台端到端旅程轨道 | P0 | DONE | - |
| T02 | 子页面旅程上下文条 | P0 | DONE | T01 |
| T03 | 阶段缺口与下一步动作收敛 | P0 | DONE | T01 |

## 完成标准

- [x] `/workbench` 首屏展示 8 阶段旅程，不靠菜单解释产品链路。
- [x] 子页面能返回工作台并保留 `journey=e2e-data-product`。
- [x] 每个阶段都有状态、缺口、下一步和证据入口。

## 2026-07-09 进展

- T01 已完成：工作台旅程轨道增加 `journey=e2e-data-product` 上下文、继续按钮、阶段主/辅动作、负责角色、当前缺口和下一步。
- 验证：focused source-contract 6/6 通过；工作台、建模、菜单三组 source-contract 16/16 通过；`pnpm build` 通过；`git diff --check` 通过。
- 浏览器 smoke：Vite dev server 已启动在 `http://localhost:3001/`，打开 `/workbench?journey=e2e-data-product` 时被登录守卫重定向到 `#/auth/login`；后端代理同时报 `platform.dts.local` DNS 解析失败，需在可登录环境复验旅程轨道视觉。

## 2026-07-10 收口

- T02/T03 已完成：工作台、数据源、标准、建模、指标、服务、运维和审计页面共享 journey context，并由统一状态模型给出缺口和下一步。
- `DataManagementWorkbenchPage`、`dataDevelopmentWorkbench`、`dataProductDeliveryJourney` 相关契约通过；浏览器视觉证据统一挂靠 F9，不在本 Feature 中虚标。
