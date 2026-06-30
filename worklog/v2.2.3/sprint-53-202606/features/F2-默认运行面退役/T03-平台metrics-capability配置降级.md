# T03: 平台 metrics capability 配置降级

**优先级**: P1  
**状态**: DONE  
**依赖**: F0/T02

## 目标

检查 `dts-platform` 中面向旧 metrics 服务的 capability 配置，避免默认 UI 或后端健康检查继续把旧服务当作必需依赖。

## 技术设计

- 审计：
  - `DtsMetricsCapabilityProperties`
  - `DtsMetricsCapabilityConfiguration`
  - `PlatformCapabilityResource`
  - service-auth 到 `/api/internal/metrics/model-validation`
- 默认状态改为 platform-native / disabled，不把 dts-metrics 健康作为平台可用性的必要条件。
- 默认 inbound trusted service names 不再包含 `dts-metrics`。
- 若内部模型校验仍需要，保留最小内部端点与测试面；legacy compose 可显式把 `dts-metrics` 加回 trusted services。

## 影响范围

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/config/metrics/**`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/capability/PlatformCapabilityResource.java`
- `source/dts-platform/src/test/java/com/yuzhi/dts/platform/**`

## 验证

- [x] 平台能力接口不要求 dts-metrics 在线才显示平台指标建模可用。
- [x] `./mvnw -q -pl dts-platform -Dtest=ServiceDependencyAuthenticationFilterTest,PlatformCapabilityResourceTest test` 通过。
- [x] `metricsRuntimeRetirement.source-contract.test.ts` 覆盖默认 trusted services 不含 `dts-metrics`，legacy compose 可显式加回。

## 完成标准

- [x] 默认平台健康与 dts-metrics 服务解耦。
