# T02: owner 分级标准质量必填门禁

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

发布前强制检查 owner、分级分类、标准映射、质量规则和质量结果。

## 技术设计

- 门禁规则按资产类型配置：ODS、DWD、DWS、ADS、BI_DATASET、API_SERVICE、DATA_PRODUCT。
- DWD 重点检查主键、标准码、维度映射。
- DWS/ADS 重点检查粒度、指标口径、质量测试和业务 owner。
- 缺项返回业务可读阻断原因。

## 影响范围

- `source/dts-platform` governance/catalog/modeling
- `source/dts-platform-webapp` 发布前校验提示

## 验证

- [ ] 缺 owner 阻断发布。
- [ ] 缺分级分类或质量规则阻断发布。

## 完成标准

- [ ] 治理字段不是展示字段，而是发布门禁输入。
