# T03: DWD/DWS/ADS 发布门禁

**优先级**: P0
**状态**: READY
**依赖**: T02

## 目标

把 dbt compile/test/build、schema contract、质量、血缘和治理快照纳入模型发布门禁。

## 技术设计

- 复用现有 dbt release gate 能力，补齐 prod 阻断、dev/demo warning 的环境开关。
- DWD 发布检查主键、标准码、字段治理和血缘。
- DWS/ADS 发布检查粒度、指标口径、权限可消费性。
- 发布结果写入黄金链路阶段快照。

## 影响范围

- `source/dts-platform` dbt/modeling/release gate
- `source/dts-metrics` 候选 artifact 校验接口

## 验证

- [ ] dbt test 失败时 prod 发布阻断。
- [ ] 粒度不一致时 DWS/ADS 发布阻断。

## 完成标准

- [ ] 模型发布从工程动作升级为产品门禁。
