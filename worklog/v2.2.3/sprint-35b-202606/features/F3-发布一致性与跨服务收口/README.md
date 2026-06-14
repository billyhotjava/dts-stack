# F3: 发布一致性与跨服务收口

**优先级**: P0
**状态**: DONE（metrics 侧编排+gating+测试全绿；platform 端点本体+端到端联调=跨服务 followup）
**对应缺陷**: #3 发布无一致性/saga

## 目标

让 metrics 标记 PUBLISHED 的模型与 platform/BI/血缘侧最终一致：publish 编排具备幂等与失败补偿，接入 BI Dataset register 与 lineage register。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | publish saga/outbox 编排（幂等 + 补偿） | P0 | DONE（幂等守卫 + 有序编排；完整 outbox 重试=followup） | F1,F2 |
| T02 | 接入 BI Dataset register + lineage register | P0 | DONE（metrics 侧调用+gating；platform 端点本体=followup） | T01 |
| T03 | PUBLISH_BLOCKED 失败态落地 + 平台端点联调 | P0 | DONE（PUBLISH_BLOCKED 落地+测试；端到端联调=followup） | T02 |

## 完成标准
- [ ] publish 可重试且幂等，部分失败有补偿路径，状态最终一致。
- [ ] 发布成功后 BI Dataset 与 source→DWS/ADS→BI Dataset 血缘已注册（platform 端点就绪时联调通过）。
- [ ] 注册失败时落 PUBLISH_BLOCKED 而非伪 PUBLISHED。

## 跨服务依赖

BI/lineage/audit register 端点本体属 **dts-platform** 职责（见架构评审 #3 与非目标）。本 feature 只负责 metrics 侧编排与调用，platform 端点就绪后联调。
