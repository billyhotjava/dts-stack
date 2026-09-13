# F2: platform 入站每对独立 secret

**优先级**: P0
**状态**: DONE
**目标**: `trustedServices` 由"逗号分隔字符串 + 单一 sharedSecret"改为 `Map<serviceName, token>`,每对调用使用独立 secret,失陷一对只影响一对。

**依赖**: F1

## 背景

当前所有内部服务共享 `DTS_ADMIN_SERVICE_TOKEN`。任何一处泄露(日志、env dump、容器逃逸)即全集群 service-to-service 调用失守。本 Feature 让每个调用方独占一把 token,可独立轮换、独立审计。

## 任务

| Task | 状态 | 内容 |
|------|------|------|
| T01 | DONE | `PlatformInboundServiceAuthProperties.trustedServices: Map<String, String>` 已在 F1 阶段一并定义,F2 启用消费 |
| T02 | DONE | application.yml 启用 `trusted-services` Map,三键(dts-ingestion/dts-airflow/dts-analytics)各独立 env,fallback 链 `DTS_INBOUND_FROM_<X>` → `DTS_ADMIN_SERVICE_TOKEN` |
| T03 | DONE | filter 已用 `isTrustedServiceName`(F1 完成),F3 阶段补 token 强校验 |
| T04 | DONE | InfraDataSourceResource.serviceTokenMatches 已用 `resolveExpectedToken(serviceName)`(F1 完成),F2 修复 Map 命中但 value 为空时的 fallback 边界 |
| T05 | DONE | `PlatformInboundServiceAuthPropertiesTest` 9 个用例覆盖:Map 优先 / 大小写 / value 空 fallback / 未知 service / List 兼容 / canonicalServiceName / legacyMode 默认值 |

## 影响范围

- 修改: `PlatformInboundServiceAuthProperties.java`(F1 创建,F2 充实字段)
- 修改: `application.yml` `dts.platform.inbound.service-auth.trusted-services` 段
- 修改: `ServiceDependencyAuthenticationFilter.resolveServiceName`
- 修改: `InfraDataSourceResource.serviceTokenMatches`
- 测试: `PlatformInboundServiceAuthPropertiesTest`、`InfraDataSourceResourceTest` 扩充用例

## 配置示例

```yaml
dts:
  platform:
    inbound:
      service-auth:
        enabled: true
        trusted-services:
          dts-ingestion: ${DTS_INBOUND_FROM_INGESTION:${DTS_ADMIN_SERVICE_TOKEN:}}
          dts-airflow:   ${DTS_INBOUND_FROM_AIRFLOW:${DTS_ADMIN_SERVICE_TOKEN:}}
          dts-analytics: ${DTS_INBOUND_FROM_ANALYTICS:${DTS_ADMIN_SERVICE_TOKEN:}}
```

## 验证

- [ ] 启动 platform 时 trustedServices Map 至少含三个键(取决于 env)
- [ ] 用对应 token 调用 `runtime-detail` 端点 200,用错 token 403,用未知 service header 403
- [ ] 仅设旧 `DTS_ADMIN_SERVICE_TOKEN` 一个 env,行为与 Sprint-27 等价(三个 service 都能通过)

## 完成标准

- [x] Map 配置完成,sharedSecret 保留作为 fallback(运维仅设旧 env 时仍能工作)
- [x] 现有 `runtime-detail` 测试不破坏(InfraDataSourceResourceTest 4 用例继续通过)
- [x] 测试覆盖 Map 优先/value 空 fallback/未知 service/单一 List 等边界

## 实现记录

- 修改: `PlatformInboundServiceAuthProperties.resolveExpectedToken` — Map value 为空字符串时 fallback 到 sharedSecret(避免 Sprint-28 上线后只设 `DTS_ADMIN_SERVICE_TOKEN` 的旧部署立即断链)
- 修改: `application.yml` 启用 `dts.platform.inbound.service-auth.trusted-services` Map,三键预置 fallback 链
- 新增: `PlatformInboundServiceAuthPropertiesTest`(9 用例)
- 验证: 38 测试全过(F1 + F2 + F3 + 原回归)
