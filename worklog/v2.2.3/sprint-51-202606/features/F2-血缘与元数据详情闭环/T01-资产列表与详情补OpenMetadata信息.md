# T01: 资产列表与详情补 OpenMetadata 信息

**优先级**: P0  
**状态**: DONE  
**依赖**: F0

## 目标

让 `/catalog/assets` 和 `/catalog/datasets/:id` 展示 OpenMetadata 中已有的 tags、domain、owner、profile。

## 技术设计

- 列表页优先批量读取 `/catalog/datasets/openmetadata/batch` 或 `assets-v2` 字段。
- 详情页可懒加载 `/catalog/datasets/{id}/openmetadata`。
- 标签显示使用短标签名，hover 或详情保留完整 FQN。

## 影响范围

- `DatasetsPage`
- `DatasetDetailPage`
- `AssetDetailPage` 如仍作为现有入口使用

## 验证

- [ ] 有标签时展示 Tag；无标签时展示“暂无标签”。
- [ ] 有 profile 时展示行数/列数/采样时间。
- [ ] 关闭/重新打开详情不重复请求或至少不造成明显抖动。

## 完成标准

- [ ] 元数据不再只在技术页孤立可见。
