# T03: ApprovalPort SDK

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标
业务方依赖的 shared 库，封装 `ApprovalPort` 接口 + HTTP 实现 + Kafka 消费端基础类；将来 F1-full 只改 adapter，业务方零改动。

## 技术设计
详细方案在 F1 brainstorming 后续细化，本文件为 spec 就绪后的执行占位。

## 影响范围
- 新增 `source/dts-shared-approval`（或并入 `dts-shared-kafka`）
- 提供 `@ApprovalCallback` 注解，简化"审批通过后落库"样板代码

## 验证
- [ ] MDM sample 使用 SDK 完成一次 submit → approved → UPSERT 全链路

## 完成标准
- [ ] SDK 发布并有 hello-world demo
