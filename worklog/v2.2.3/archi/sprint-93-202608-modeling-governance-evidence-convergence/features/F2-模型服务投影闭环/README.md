# F2：模型服务投影闭环

**优先级**：P0
**状态**：COMPLETE / IT-04_PASS

## 目标

让模型发布产生的 serving projection 从耐久 `SYNC_PENDING` 自动进入 `SYNCED` 或带退避的 `SYNC_FAILED`，并把语义模型、候选版本和物理资产稳定连接起来。

## 契约定义

| 类型 | 契约 | 关键字段 |
|---|---|---|
| 工作队列 | `modeling_catalog_model_serving_projection` | tenantId/modelSpecId/version/syncStatus/attempts/nextSyncAt/latestPublishedRef/servingRef |
| 外部事件 | outbox `MODELING_CATALOG_PROJECTION` | 模型/实现/候选/asset/projectionVersion/outcomeCode |
| worker | 每轮 claim ≤100，`FOR UPDATE SKIP LOCKED` | pending 或到期 failed；CAS expectedVersion |
| 回执 | `markSyncSucceeded/markSyncFailed` | 成功清 error；失败记录 errorCode/nextAttemptAt |

## UI/UX 规格

不新增页面。模型工作台和资产详情显示“目录同步：同步中/成功/失败”，失败提供重试（有权限时）和日志深链；不得把 `SYNC_PENDING` 显示为已上线。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 实现耐久同步 worker、重试与对账 | P0 | COMPLETE / IT-04_PASS | - |

## Definition of Ready

- [x] 队列、claim、CAS、重试预算和 UI 状态已定义。
- [x] 不新增 Kafka topic 或第二 projection 表。
- [x] 43 条现有 pending 可作为验收样本，但不得直接批量改状态。

## 完成标准

- [x] pending 在预算内进入 synced/failed。
- [x] 旧版本事件不能覆盖新 servingRef。
- [x] 失败五次进入明确 dead-letter/问题态并可审计。
- [x] 现有 43 条通过 worker 重放后有真实结果。
