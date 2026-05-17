# Sprint-31A F2/T05 Platform Internal Capabilities 契约

## 目标

`dts-metrics` 启动和健康检查时，通过 platform 的能力发现接口确认可用契约，不再依赖静态描述或猜测 platform 是否支持资产、权限、审计和发布门禁。

## 当前落地

内部端点：

```text
GET /api/internal/capabilities
```

服务鉴权：

```text
X-DTS-Service: dts-metrics
X-DTS-Service-Token: {trusted token}
```

公开端点：

```text
GET /api/capabilities
```

公开端点保留平台概要；内部端点要求 `ROLE_SERVICE_INTERNAL` 且认证主体为 `service:dts-metrics`。

## 能力域

| 能力域 | 内容 |
|--------|------|
| `catalog` | contract version、资产类型、生命周期、治理状态、读取端点 |
| `permissions` | platform `asset_grant` 作为唯一权限事实源、权限动作、action-to-permission 映射、授权对象、内部权限端点 |
| `classification` | 密级等级、默认密级、缺失密级处理、执行方 |
| `audit` | 审计来源、服务鉴权方式 |
| `dbtPublish` | platform-gated 发布模式和治理缺口依赖 |
| `metrics` | dts-metrics edition、base path、service name、remote-service 模式 |

`catalog.readEndpoints` 当前包含：

```text
/api/catalog/assets-v2
/api/catalog/assets-v2/{id}/contract
/api/catalog/assets-v2/{id}/schema-contract
/api/catalog/assets-v2/governance-gaps
/api/catalog/assets-v2/lineage-failures
/api/catalog/assets-v2/migration/dry-run
```

`catalog.migrationDryRunEndpoint` 固定为：

```text
/api/catalog/assets-v2/migration/dry-run
```

## 后续依赖

- Sprint-32 `dts-metrics` 的 `PlatformContractClient` 读取该端点作为 health/readiness 依据。
- 缺少关键能力时，`dts-metrics` 应返回明确健康检查错误，而不是静默降级。
- F6 IT 脚本需要验证无 service token 时内部端点不可访问。
- F4/T01 后，`permissions.actions` 包含 `READ/PREVIEW/EDIT/PUBLISH/GRANT/MANAGE`，`permissions.actionMapping` 明确发布和授权动作需要 `MANAGE`。
