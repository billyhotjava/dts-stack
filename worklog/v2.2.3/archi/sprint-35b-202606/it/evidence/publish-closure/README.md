# 证据：发布闭环（幂等 + 失败补偿 PUBLISH_BLOCKED + 回滚链）

**对应缺陷**: #3 发布无 saga（BI/血缘/审计零调用点）
**测试**:
- `com.yuzhi.dts.metrics.service.MetricPublishDownstreamTest`（4 例）— 发布闭环 metrics 侧
- `com.yuzhi.dts.metrics.web.rest.MetricModelLifecycleResourceTest`（10 例）— 全生命周期含回滚

**结果**: 4 + 10 = 14 例全绿，`Failures: 0, Errors: 0`（见同目录 `*.txt` 原始报告）
**采集时间**: 2026-06-14
**运行命令**: `cd source && ./mvnw -pl dts-metrics test -Dtest='MetricPublishDownstreamTest,MetricModelLifecycleResourceTest'`

## 阻断条件 → 证明映射

| it/README 阻断条件 | 证明测试方法 | 如何证明 |
|---|---|---|
| publish 标 PUBLISHED 但 BI/lineage 未注册（闭环断裂） | `publishRegistersBiDatasetAndLineageWhenEnabled` | 开关开启时 publish commit 后调用 `registerBiDataset` + `registerLineage` |
| BI/lineage 注册失败被静默 | `registrationFailureMarksModelPublishBlocked` | 注册失败 → 持久化 `PUBLISH_BLOCKED` + 抛 503，不静默吞错 |
| （注册开关 gating 正确） | `registrationIsSkippedWhenDisabled` | 开关关闭时跳过注册（等 platform 端点就绪前默认关，不误调） |
| （重复发布幂等） | `republishingAPublishedModelIsIdempotent` | 已 PUBLISHED 的 model 重复 publish → 返回既有结果，不重复提交 release |
| 高风险动作（rollback）审计/链路缺失 | `MetricModelLifecycleResourceTest`（回滚用例） | rollback 走完整状态机：版本回退、回滚事件、consumer 锁影响、审计 |

## 余留 followup（非本 sprint 阻断项）

完整 outbox 重试/补偿（注册失败后的自动重投）是 #3 的进一步 followup；当前实现为
**幂等守卫 + 单次有序注册 + 失败落 PUBLISH_BLOCKED**，已满足"闭环不静默断裂"的阻断门槛。
