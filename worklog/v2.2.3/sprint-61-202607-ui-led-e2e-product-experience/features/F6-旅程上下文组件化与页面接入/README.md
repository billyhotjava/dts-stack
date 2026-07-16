# F6: 旅程上下文组件化与页面接入

**优先级**: P0
**状态**: DONE

## 目标

把 `journey=e2e-data-product` 从工作台跳转参数升级为可复用的页面上下文能力，让数据源、标准、建模、指标、服务、运维页面都能显示“当前阶段、返回工作台、继续下一步”。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | JourneyContextBar 共享组件与 query 解析 | P0 | DONE | F1/T01 |
| T02 | 核心页面接入上下文条 | P0 | DONE | T01 |
| T03 | 返回与继续动作的上下文保持 | P0 | DONE | T02 |

## 完成标准

- [x] 子页面识别 `journey=e2e-data-product` 后展示统一上下文条。
- [x] 上下文条显示当前阶段、来源对象、下一步动作和返回工作台入口。
- [x] 页面跳转保留 `sourceId`、`standardDraftId`、`modelId`、`metricId`、`serviceId` 等上下文。
- [x] 没有 journey 参数时不干扰页面原有使用方式。

## 2026-07-10 收口

- T02 的核心页面接入和 source-contract 已完成；可登录浏览器 smoke、Chrome 95 窄屏截图仍由 F9 统一补证据。
