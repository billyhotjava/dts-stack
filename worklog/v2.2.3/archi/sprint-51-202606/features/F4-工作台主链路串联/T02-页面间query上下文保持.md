# T02: 页面间 query 上下文保持

**优先级**: P1  
**状态**: READY  
**依赖**: T01

## 目标

页面跳转时保留 assetId、datasetId、sourceId、productId、section 等上下文。

## 技术设计

- 复用 Sprint-49/50 已有 query 兼容策略。
- 详情返回列表时保留筛选。
- 工作台入口只补 query，不新增后端聚合。

## 影响范围

- `/workbench`
- `/catalog/assets`
- `/foundation/data-sources`
- `/services/apis`
- `/services/products`

## 验证

- [ ] 从资产进入服务/API 后能识别 assetId。
- [ ] 返回时列表筛选不丢失。

## 完成标准

- [ ] 页面链路连续，用户不用重复搜索同一对象。
