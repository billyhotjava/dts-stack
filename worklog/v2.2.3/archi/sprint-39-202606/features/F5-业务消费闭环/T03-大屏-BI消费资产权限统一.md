# T03: 大屏/BI 消费资产权限统一

**优先级**: P1
**状态**: DONE
**依赖**: T01

## 目标

让大屏、BI、指标和资产门户对同一资产的可见性和 RLS/masking 结果一致。

## 技术设计

- 复查大屏权限 fallback、screen asset grant、BI dataset 权限、metrics platform contract。
- 大屏组件绑定数据集时记录资产 ID、字段合同和权限快照。
- 运行态读取 platform 授权，不信任本地孤立授权。
- 新增 `GoldenChainConsumptionPermissionViewService`，复用 F3 权限一致性服务，并在消费层要求资产门户、指标、BI、大屏、API、数据产品六个消费面权限快照齐全。

## 影响范围

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/goldenchain/consumption/`
- `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/goldenchain/consumption/`

## 验证

- [x] 资产门户无权限时，大屏和 BI 也不可消费。
- [x] owner/superuser/grant 优先级符合现有规则。
- [x] focused test: `cd source/dts-platform && ./mvnw -q -Dtest=GoldenChainConsumptionPermissionViewServiceTest test`

## 完成标准

- [x] 报表消费侧不再形成第二套权限事实源。
