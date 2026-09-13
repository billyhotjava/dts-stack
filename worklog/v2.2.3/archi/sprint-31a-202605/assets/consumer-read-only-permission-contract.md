# Sprint-31A F4/T04 analytics / metrics 只读权限消费

## 目标

`dts-analytics` 和 `dts-metrics` 只消费 platform 的权限结论，platform `asset_grant` 成为唯一权限事实源。本地权限表只允许作为迁移期只读/应急 fallback。

## 当前落地

### dts-analytics

- `ScreenPermissionService` 已 platform-first。
- 本地 `analytics_screen_access` fallback 命中时输出 `event=analytics_permission_fallback` 告警日志。
- 新授权写入 `ScreenOwnershipService` 默认调用 platform `asset_grant`。
- `analytics.local-iam.read-only=true` 时禁止本地写 fallback。
- 调用 platform 权限检查时显式传入 `action=READ`。
- 本地 fallback 不再把空 screen classification 当公开资产。

### dts-metrics

`PlatformContractClient` 新增只读权限检查方法：

```java
checkPermission(PermissionCheckRequest request)
```

用于后续指标预览、发布、BI Dataset 注册前统一调用：

```text
POST /api/internal/asset-permission/check
```

## 后续依赖

- Sprint-32 metrics 预览、发布和注册接口必须调用 `PlatformContractClient.checkPermission`。
- Sprint-31 发布门禁必须使用 `action=PUBLISH` 检查 `MANAGE` 权限。
- F4/T05 对拒绝原因和 fallback 告警做审计/报告汇总。
