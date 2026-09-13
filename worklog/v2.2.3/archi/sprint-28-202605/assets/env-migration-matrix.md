# Sprint-28 环境变量迁移矩阵

把 service-to-service 鉴权配置从混淆的 `dts.admin.*` / `dts.platform.service-token` / `dts.analytics.platform.*` 拆分为概念清晰的出站/入站配置。本文件提供旧→新映射、fallback 优先级、deprecated 计划。

## 关键概念

- **出站(outbound)**:某服务作为客户端调别人时使用的 baseUrl + token + 自报身份
- **入站(inbound)**:某服务作为被调方期望对方携带的 token 与白名单
- **每对调用独立 secret**:platform 入站使用 `Map<service, token>`,失陷一对只影响一对
- **fallback 链**:旧 env 仍可用,但启动日志会输出 deprecated 提示;Sprint-29 移除 fallback

## platform 侧

### 出站 platform → admin

| 旧 env(deprecated) | 新 env | fallback 链 | 默认值 |
|---|---|---|---|
| `DTS_ADMIN_ENABLED` | `DTS_PLATFORM_OUTBOUND_ADMIN_ENABLED` | 新 → 旧 → true | `true` |
| `DTS_ADMIN_BASE_URL` | `DTS_PLATFORM_OUTBOUND_ADMIN_BASE_URL` | 新 → 旧 → 默认 | `http://dts-admin:8081` |
| `DTS_ADMIN_API_PATH` | `DTS_PLATFORM_OUTBOUND_ADMIN_API_PATH` | 新 → 旧 → 默认 | `/api` |
| `DTS_ADMIN_ADMIN_API_PATH` | `DTS_PLATFORM_OUTBOUND_ADMIN_ADMIN_API_PATH` | 新 → 旧 → 默认 | `/api/admin` |
| `DTS_ADMIN_SERVICE_TOKEN` | `DTS_PLATFORM_TO_ADMIN_TOKEN` | 新 → 旧 → 空 | (空,部署需配) |
| `DTS_ADMIN_SERVICE_NAME`(语义错误,本是入站白名单) | `DTS_PLATFORM_OUTBOUND_SERVICE_NAME` | 新 → 默认 | `dts-platform`(F1 修复 bug) |

**Bug fix**:Sprint-27 之前,出站调 admin 时 `X-DTS-Service` header 被错误填入入站白名单字符串(`dts-admin,dts-ingestion,dts-airflow,dts-analytics`)。F1 修复后填正确的 `dts-platform`。

### 入站 platform 接收来自其他服务

| 旧 env | 新 env | 用途 | 默认值 |
|---|---|---|---|
| (无对应) | `DTS_PLATFORM_INBOUND_AUTH_ENABLED` | 是否启用入站 filter | `true` |
| `DTS_ADMIN_SERVICE_TOKEN`(共用) | `DTS_PLATFORM_INBOUND_SHARED_SECRET` | 单一 fallback secret | (空) |
| `DTS_ADMIN_SERVICE_NAME` | `DTS_PLATFORM_INBOUND_TRUSTED_SERVICES` | 受信服务名列表 | `dts-admin,dts-ingestion,dts-airflow,dts-analytics` |
| (无) | `DTS_INBOUND_FROM_INGESTION` | ingestion 调 platform 的独立 token | (空,fallback 到 sharedSecret) |
| (无) | `DTS_INBOUND_FROM_AIRFLOW` | airflow 调 platform 的独立 token | (空,fallback 到 sharedSecret) |
| (无) | `DTS_INBOUND_FROM_ANALYTICS` | analytics 调 platform 的独立 token | (空,fallback 到 sharedSecret) |
| (无) | `DTS_PLATFORM_LEGACY_HEADER_ONLY_MODE` | 紧急回滚开关:仅看 header,不校验 token | `false` |

**安全升级**:Sprint-27 仅看 X-DTS-Service header 即注入 OP_ADMIN(白名单即权限);Sprint-28 F3 起必须同时带正确 X-DTS-Service-Token,关闭潜在越权面。

## ingestion 侧

### 出站 ingestion → platform

| 旧 env(deprecated) | 新 env | fallback 链 | 默认值 |
|---|---|---|---|
| `DTS_PLATFORM_BASE_URL` / settings.baseUrl | `DTS_INGESTION_OUTBOUND_PLATFORM_BASE_URL` | 新 env → 旧 env → 默认 | `http://dts-platform:8081` |
| `DTS_PLATFORM_API_PATH` / settings.apiPath | `DTS_INGESTION_OUTBOUND_PLATFORM_API_PATH` | 新 env → 旧 env → 默认 | `/api` |
| `DTS_PLATFORM_SERVICE_TOKEN` / `DTS_ADMIN_SERVICE_TOKEN` | `DTS_INGESTION_TO_PLATFORM` | 新 env → `DTS_PLATFORM_SERVICE_TOKEN` → `DTS_ADMIN_SERVICE_TOKEN` → settings.serviceToken | (空) |
| (无,硬编码 "dts-ingestion") | `DTS_INGESTION_OUTBOUND_SERVICE_NAME` | 新 env → settings.serviceName → 默认 | `dts-ingestion` |

**Settings 表 seed**:Sprint-28 起 `IngestionSettingsSeeder.buildPlatformSettings()` 把启动时 yml/env 解析到的 serviceToken 与 serviceName 同步到 settings 表,运维 UI 可见可改;运行时仍走 settings 优先 fallback 链。

## analytics 侧

### 出站 analytics → platform

| 旧 env(deprecated) | 新 env | fallback 链 | 默认值 |
|---|---|---|---|
| `DTS_ANALYTICS_PLATFORM_BASE_URL` | `DTS_ANALYTICS_OUTBOUND_PLATFORM_BASE_URL` | 新 → 旧 → 默认 | `http://dts-platform:8081` |
| `DTS_ANALYTICS_PLATFORM_API_PATH` | `DTS_ANALYTICS_OUTBOUND_PLATFORM_API_PATH` | 新 → 旧 → 默认 | `/api` |
| `DTS_ANALYTICS_PLATFORM_SERVICE_NAME` | `DTS_ANALYTICS_OUTBOUND_SERVICE_NAME` | 新 → 旧 → 默认 | `dts-analytics` |
| `DTS_ANALYTICS_PLATFORM_TIMEOUT_SECONDS` | `DTS_ANALYTICS_OUTBOUND_PLATFORM_TIMEOUT_SECONDS` | 新 → 旧 → 默认 | `10` |
| (无,之前 analytics 不带 token) | `DTS_ANALYTICS_TO_PLATFORM` | 新 → `DTS_ADMIN_SERVICE_TOKEN` → 空 | (空,F3 上线后必填) |

**关键变更**:Sprint-27 之前,analytics 调 platform 不带 X-DTS-Service-Token 也能通过(白名单即权限)。F3 上线后必须带 token,**部署时必须配置 `DTS_ANALYTICS_TO_PLATFORM` 或 `DTS_ADMIN_SERVICE_TOKEN`**,否则 analytics 调 platform 全部 403。

### 出站 analytics → admin(本 sprint 不动)

`AdminAuditHttpHeadersFactory` 与 `UserResource:216` 仍走 `dts.admin.*` 配置(`DtsAdminProperties`),admin 端无 token 强校验。Sprint-29 起视 admin filter 升级情况再决定是否独立 outbound bean。

## 部署最小配置(零代码改动启动)

仅需配置一个共享 secret,通过 fallback 链所有服务都能正常启动:

```bash
# 仅配此一个 env,所有服务通过 fallback 自动取到同一值
export DTS_ADMIN_SERVICE_TOKEN=<32 字节随机值>
```

**适用场景**:Sprint-28 上线第一天,运维不希望大改 env;通过 fallback 链所有服务调用都用同一 secret。
**风险**:任一服务泄露即全网失守;建议 Sprint-29 切换到每对独立 secret(下方)。

## 部署推荐配置(每对独立 secret)

```bash
# 每对调用一个独立 secret,失陷只影响一对
SECRET_FROM_INGESTION=<32B random>
SECRET_FROM_AIRFLOW=<32B random>
SECRET_FROM_ANALYTICS=<32B random>

# platform 侧(被调方)
export DTS_INBOUND_FROM_INGESTION=$SECRET_FROM_INGESTION
export DTS_INBOUND_FROM_AIRFLOW=$SECRET_FROM_AIRFLOW
export DTS_INBOUND_FROM_ANALYTICS=$SECRET_FROM_ANALYTICS

# ingestion 侧(出站)
export DTS_INGESTION_TO_PLATFORM=$SECRET_FROM_INGESTION

# analytics 侧(出站)
export DTS_ANALYTICS_TO_PLATFORM=$SECRET_FROM_ANALYTICS

# airflow 侧(暂未由 java 实现,占位)
# DTS_AIRFLOW_TO_PLATFORM=$SECRET_FROM_AIRFLOW
```

## Deprecated 时间线

| Env | Deprecated since | Removal target |
|---|---|---|
| `DTS_ADMIN_SERVICE_NAME`(用作 platform 入站白名单) | Sprint-28 | Sprint-29 |
| `DTS_ADMIN_SERVICE_TOKEN`(用作 platform 入站 sharedSecret) | Sprint-28 | Sprint-29 |
| `DTS_PLATFORM_SERVICE_TOKEN`(ingestion 出站) | Sprint-28 | Sprint-29 |
| `dts.admin.*` 整段(platform yml) | Sprint-28 | Sprint-29 |
| `dts.platform.*` 段(ingestion yml,仅 service-token 子项) | Sprint-28 | Sprint-29 |
| `dts.analytics.platform.*` 段 | Sprint-28 | Sprint-29 |
| `legacy-header-only-mode` 紧急回滚开关 | (引入即 deprecated) | Sprint-29 强制移除 |

## 紧急回滚

**仅 dev/紧急情况使用**,production 严禁:

```bash
# 退回 Sprint-27 行为(仅看 X-DTS-Service header,不校验 token)
export DTS_PLATFORM_LEGACY_HEADER_ONLY_MODE=true
```

启动日志会输出:
```
WARN  SECURITY: dts.platform.inbound.service-auth.legacy-header-only-mode=true detected in PRODUCTION profile.
      This bypasses X-DTS-Service-Token validation and exposes any OP_ADMIN endpoint to header forgery.
      Disable immediately unless this is an explicit emergency rollback.
```
