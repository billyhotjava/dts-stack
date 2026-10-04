# F4: 任务运维中心产品化

**优先级**: P1
**状态**: DONE

## 目标

把已有 `/api/ops` 后端能力接成客户可用的任务运维中心页面，覆盖运行概览、实例监控、告警记录和补数管理。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 运维菜单路由与页面壳 | P1 | DONE | F1-T03 |
| T02 | 实例监控告警补数页面接入 | P1 | DONE | T01 |
| T03 | 运行日志错误分类与重试入口 | P1 | DONE | T02 |
| T04 | 黄金链路运维视角联动 | P1 | DONE | T03 |

## 完成标准

- [x] 任务运维中心四个菜单入口都有真实页面。
- [x] 运维页面能定位链路、任务、失败原因、日志和补数入口。

## 进度记录

- 2026-06-14: T01 已完成。复用已有 ops 页面并补齐 `/ops/overview`、`/ops/instances`、`/ops/alerts`、`/ops/backfill` 静态路由和 dynamic override；验证：`pnpm exec tsx src/routes/sections/dashboard/opsRoutes.source-contract.test.ts`。
- 2026-06-14: T02 已完成。新增 `GoldenChainOpsWorkCenterService`，按 `chainKey/taskId` 关联实例、告警和补数动作；验证：`./mvnw -q -Dtest=GoldenChainOpsWorkCenterServiceTest test`。
- 2026-06-14: T03 已完成。新增 `GoldenChainOpsErrorClassifier`，覆盖连接、凭据、质量、模型、血缘、权限和系统异常分类与恢复动作；验证：`./mvnw -q -Dtest=GoldenChainOpsErrorClassifierTest test`。
- 2026-06-14: T04 已完成。新增 `GoldenChainOpsImpactService`，汇总最近运行、失败次数、MTTR、补数状态、日志入口和受影响资产/报表/API；验证：`./mvnw -q -Dtest=GoldenChainOpsImpactServiceTest test`。
