# T02: owner 分级标准质量必填门禁

**优先级**: P0
**状态**: DONE
**依赖**: T01

## 目标

发布前强制检查 owner、分级分类、标准映射、质量规则和质量结果。

## 技术设计

- 新增 `GoldenChainGovernanceGateService`，按资产类型校验发布前治理字段。
- 基础门禁：owner、分级分类、质量规则、质量结果。
- DWD 重点检查主键、标准码映射。
- DWS/ADS 重点检查粒度、指标口径和业务 owner。
- 缺项返回 `BLOCKED_GOVERNANCE` 和业务可读阻断原因。

## 影响范围

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/goldenchain/governance/`
- `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/goldenchain/governance/`

## 验证

- [x] 缺 owner 阻断发布。
- [x] 缺分级分类或质量规则阻断发布。
- [x] DWS/ADS 缺粒度或指标口径阻断发布。
- [x] focused test 通过：`cd source/dts-platform && ./mvnw -q -Dtest=GoldenChainGovernanceGateServiceTest test`

## 完成标准

- [x] 治理字段不是展示字段，而是发布门禁输入。
