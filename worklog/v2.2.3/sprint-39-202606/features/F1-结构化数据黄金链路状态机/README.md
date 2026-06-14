# F1: 结构化数据黄金链路状态机

**优先级**: P0
**状态**: IN_PROGRESS

## 目标

定义并落地跨数据源、入湖、建模、治理、资产、权限、消费、运维的黄金链路状态机，让客户可以按一条主链路验收产品。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 黄金链路状态机与验收契约 | P0 | DONE | - |
| T02 | 链路实例与阶段快照模型 | P0 | READY | T01 |
| T03 | 统一链路聚合接口 | P0 | READY | T02 |
| T04 | 端到端证据采集脚本 | P0 | READY | T03 |

## 完成标准

- [x] 每个链路阶段有明确状态、失败分类、负责人和证据字段。
- [ ] 前端不需要跨多个中心自行拼接链路状态。
- [ ] IT 可按 chainId 或等价键追踪完整链路。

## 进度记录

- 2026-06-14: T01 已完成。代码落点：`source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/goldenchain/`；验证：`./mvnw -q -Dtest=GoldenChainContractTest test`。
