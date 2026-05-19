# T01: 验证状态机

**优先级**: P0
**状态**: READY
**依赖**: F4

## 目标

定义 graph 从草稿到发布的状态机。

## 技术设计

- 状态：DRAFT、GRAPH_VALIDATED、CONTRACT_VALIDATED、DBT_VALIDATED、REVIEW_SUBMITTED、APPROVED、PUBLISHED、CONSUMED。
- 失败态：GRAPH_BLOCKED、CONTRACT_BLOCKED、DBT_BLOCKED、REVIEW_REJECTED、PUBLISH_FAILED。
- 后端强制状态迁移，前端只显示可执行动作。

## 影响范围

- `source/dts-metrics` status model
- `source/dts-metrics-webapp` action toolbar

## 验证

- [ ] 跳过验证直接发布会失败。
- [ ] 失败态能重新进入验证。

## 完成标准

- [ ] 状态机是发布按钮可用性的唯一依据。
