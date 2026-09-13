# T02: API、数据产品、报表链接密级派生

**优先级**: P0
**状态**: IN_PROGRESS
**依赖**: F3-T01

## 目标

把 `SvcApi`、`SvcDataProduct`、`CatalogDataProduct` 和 `BiReportLink` 的密级改为来源派生加人工下限。

## 技术设计

- API 取绑定数据集/字段/查询的最高值。
- 数据产品取包含的 API、数据集、指标和报表最高值。
- 报表链接同步当前上游密级，不使用静态默认回退。
- 现有 upsert classification 字段兼容为 `manualFloor`，低于计算值时拒绝。

## 影响范围

API catalog/query、data product service、report link/screen sync、DTO/UI。

## 验证

- [ ] 多来源产品、同步重放、人工升密和降密拒绝。
- [ ] 上游缺失时 fail closed。

## 完成标准

- [ ] 消费服务不能保存低于来源的密级。
- [ ] 兼容客户端收到明确冲突响应。
