# F6: 前端验收口径收口

**优先级**: P0
**状态**: IN_PROGRESS

## 目标

把 Sprint-31A / Sprint-31 / Sprint-32 的验收口径从“后端 API 和契约存在”提升为“前端有可操作入口、能看到状态、能处理异常、能形成闭环”。后续数据资产、语义指标、BI 消费相关 Feature，前端没有入口或入口仍是 demo，则不得标记 DONE。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 资产解析失败报告前端闭环 | P0 | DONE | F1/T05 |
| T02 | assets-v2 资产详情工作台替换旧 dataset 详情 | P0 | DONE | T01 |
| T03 | 数据产品成员配置 UI | P0 | DONE | Sprint-31A F1/F4 |
| T04 | 治理缺口和血缘失败的前端处置流 | P0 | READY | Sprint-31A F2/F3 |
| T05 | dts-metrics 页面真实功能验收 | P0 | READY | Sprint-32 F5 |

## 完成标准

- [x] 资产地图能查看 resolver failure，用户可定位资产事实源解析失败原因。
- [x] `/catalog/datasets/:id` 优先读取 assets-v2 contract/schema/governance/lineage，而不是旧 dataset API。（2026-05-18 已切为 assets-v2 优先读取，增加顶部资产合同摘要，并补 source-level UI contract test）
- [x] 数据产品页面能配置成员资产、指标、负责人、SLA 与状态，不只是名称说明。
- [ ] 治理缺口和血缘失败报告能从列表跳转到对应处置动作。
- [ ] dts-metrics 独立服务页面具备真实 API 调用和操作路径，platform-webapp 只保留菜单链接。

## 验收规则

每个数据域 Feature 必须同时满足：

1. API 或后端契约存在；
2. 前端页面可见；
3. 页面可执行至少一个核心业务动作；
4. 错误态、空态、无权限态可见；
5. 验证证据包含前端构建或页面级测试。
