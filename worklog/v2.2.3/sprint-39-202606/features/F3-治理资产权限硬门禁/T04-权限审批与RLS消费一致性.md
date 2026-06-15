# T04: 权限审批与 RLS 消费一致性

**优先级**: P0
**状态**: DONE
**依赖**: T03

## 目标

确保资产门户、metrics、BI、大屏、API 服务和数据产品都读取同一 platform 权限事实源。

## 技术设计

- 新增 `GoldenChainPermissionConsistencyService`，统一资产门户、metrics、BI、大屏、API 服务、数据产品的权限快照判断。
- 检查 platform policy hash、RLS hash、masking hash、asset key、user ref 是否一致。
- legacy local fallback 默认阻断；仅明确 break-glass 时放行并输出 warning。
- 无权限错误只返回泛化安全提示，不泄露资产细节。

## 影响范围

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/goldenchain/governance/`
- `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/goldenchain/governance/`

## 验证

- [x] 同一用户在资产门户可见性、metrics 预览、BI、大屏/API 消费结果一致。
- [x] 无权限错误不泄露资产细节。
- [x] legacy local fallback 默认阻断，break-glass 明确放行并输出 warning。
- [x] focused test 通过：`cd source/dts-platform && ./mvnw -q -Dtest=GoldenChainPermissionConsistencyServiceTest test`

## 完成标准

- [x] platform 成为消费权限唯一事实源。
