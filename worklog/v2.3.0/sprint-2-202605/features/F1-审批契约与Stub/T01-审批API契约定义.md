# T01: 审批 API 契约定义

**优先级**: P0
**状态**: READY
**依赖**: F0

## 目标
定义 dts-approval 对外 REST + Kafka 契约，发布 openapi.yaml 与事件规范；契约一旦定义**不再破坏性变更**。

## 技术设计
详细方案在 F1 brainstorming 后续细化，本文件为 spec 就绪后的执行占位。

## 影响范围
- 新增 `docs/approval-api-contract.md`（或放 Feature 目录下）
- openapi.yaml 加入 dts-shared-approval 工件

## 验证
- [ ] 契约 review 通过，至少 MDM/填报两侧确认能据此集成

## 完成标准
- [ ] REST 契约 + Kafka 事件 + bizContext 字段定义发布
