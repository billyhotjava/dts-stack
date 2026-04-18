# T02: 审批核心 API

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标
实现提单 / 审批 / 驳回 / 查询 / 历史追溯的 REST API，payload 与业务无关，支持串行多级。

## 技术设计
详细技术方案在 F1 brainstorming 阶段产出，本文件仅占位。

## 影响范围
- 新增 `dts-approval` 后端服务（Spring Boot）
- 对外 openapi 文档

## 验证
- [ ] 单元测试覆盖正常流 / 驳回 / 重新提交
- [ ] 对业务 payload 保持透明（schema-agnostic）

## 完成标准
- [ ] 核心 API 上线并通过 IT
