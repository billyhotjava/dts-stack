# T03: 未授权 policy 调用返回 HTTP 403

**优先级**: P0
**状态**: READY
**依赖**: T02

## 目标

`/api/internal/v1/asset-permission/policy` 当 actor 对目标 asset 无权限时必须返回 HTTP 403 + 结构化错误体；不再返回 HTTP 200 + `"1 = 0"` predicate fallback。

## 背景

`AssetPermissionInternalResource.policy(...)` 当前在 `decision.allowed=false` 时返回：

```java
return ResponseEntity.ok(new PolicyResponse(true, List.of("1 = 0"), List.of(), decision.reason()));
```

这是 silent enforcement：

| 客户端 | 解读 |
|---|---|
| dts-metrics | 「拿到 policy 了，注入 `1 = 0` 是平台想限死结果」 |
| analytics | 「拿到 policy 了，可以继续渲染」 |
| 运维 | 日志看不出区别，必须翻 audit |

如果客户端把 `1 = 0` 当成正常 predicate 拼到 SQL 上，行为正确但语义错位；如果客户端 bug 把空 predicate 当成「无策略」，会直接绕过 RLS。

## 技术设计

1. policy endpoint 改为：
   ```java
   if (!decision.allowed()) {
       auditService.recordDecision(decision, request.username(), currentActor());
       return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
           PolicyResponse.denied(decision.reason(), decision.deniedAt())
       );
   }
   ```
2. `PolicyResponse` 拆出 `PolicyDeniedResponse` 子类型 / `allowed` 字段；序列化时 `allowed=false` 不带 predicate。
3. `dts-metrics` `PlatformContractClient.resolveRlsPolicy(...)` 捕获 403：
   - `RestClient` `onStatus(403, ...)` 抛 `PolicyAccessDeniedException`
   - `MetricArtifactGenerationService` 接住后 `errors.add("not authorized to preview asset; ask platform admin for grant")`，错误信息不带 asset name
4. analytics 等其他消费方同步处理 403。

## 影响范围

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/internal/AssetPermissionInternalResource.java`
- `source/dts-metrics/src/main/java/com/yuzhi/dts/metrics/service/PlatformContractClient.java`
- `source/dts-metrics/src/main/java/com/yuzhi/dts/metrics/service/MetricArtifactGenerationService.java`
- analytics 消费方（如已接入 policy contract）

## 验证

- [ ] `AssetPermissionInternalResourceTest.policy_denied_returns403`
- [ ] `PlatformContractClientTest.resolveRlsPolicy_403_throwsAccessDenied`
- [ ] `MetricArtifactGenerationServiceTest.unauthorized_failsPreviewWithoutAssetName`

## 完成标准

- [ ] 未授权 policy 调用返回 HTTP 403。
- [ ] 客户端区分「拒绝」与「空策略」。
- [ ] 错误信息不暴露资产元数据。
