# T02: API Runner 选型与适配实现

**优先级**: P0
**状态**: DRAFT
**依赖**: T01, F2, F3

## 目标

确定 API 执行引擎并完成适配，避免产品能力被单一 Addax HTTP reader 限制。

## 范围

- 对比 Addax HTTP reader、自研 DTS API runner；Airbyte 已明确不纳入当前方案。
- 验证鉴权注入、分页、token refresh、retry-after、cursor、schema drift 支持度。
- 确定默认 runner 和后续可切换策略。
- 输出 adapter 接口和第一版实现。

## 完成标准

- [ ] 有选型结论和理由。
- [ ] 默认 runner 覆盖正式产品最小能力集。
- [ ] runner 可以接收 execution plan 和 secretRef。
- [ ] runner 输出标准运行事件和落库结果。
