# T02: Stub 服务 MVP

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标
实现一个最小 dts-approval 服务：REST 接单、存库、发 Kafka 事件；默认自动 approve，可通过管理页（T04）手动 reject 或延迟。

## 技术设计
详细方案在 F1 brainstorming 后续细化，本文件为 spec 就绪后的执行占位。

## 影响范围
- 新增 `services/dts-approval` + `source/dts-approval`
- 数据库 `apr_request` 表（最小：id / bizType / bizPayload / status / history / classification / requestedBy）
- 依赖 F0 的 shared-kafka starter

## 验证
- [ ] 提单后自动 approve 事件到达 MDM demo
- [ ] 管理员手动 reject 后，rejected 事件到达

## 完成标准
- [ ] Stub 独立容器可启停，走通三态（PENDING / APPROVED / REJECTED）
