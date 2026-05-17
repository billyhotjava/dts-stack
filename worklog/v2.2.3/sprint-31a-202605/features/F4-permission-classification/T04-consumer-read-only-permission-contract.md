# T04: analytics/metrics 只读权限消费

**优先级**: P0
**状态**: DONE
**依赖**: T01-T03

## 目标

让 dts-analytics 和 dts-metrics 只消费 platform 权限结论，不再产生新的最终权限事实源。

## 技术设计

- dts-metrics 提供 `PlatformContractClient.checkPermission`，预览、发布、BI 注册前可统一调用 platform。
- analytics 本地权限仅作为明确过渡 fallback，并有 `event=analytics_permission_fallback` 告警。
- analytics 调用 platform 权限检查时显式传入 `action=READ`。
- analytics 本地 fallback 不再把空密级 screen 当公开资产。
- 新授权默认写入 platform `asset_grant`；`analytics.local-iam.read-only=true` 时禁止本地写 fallback。

## 影响范围

- dts-metrics
- dts-analytics
- platform internal permission API

## 验证

- [x] fallback 命中有日志。
- [x] 新授权写入 platform asset_grant。
- [x] dts-metrics 具备只读权限检查客户端方法。

## 完成标准

- [x] platform 成为权限唯一事实源。

## 交付物

- `PlatformPermissionClient` 显式 action 传参
- `ScreenPermissionService` 本地 fallback 缺密级拒绝
- `PlatformContractClient.checkPermission`
- `worklog/v2.2.3/sprint-31a-202605/assets/consumer-read-only-permission-contract.md`
