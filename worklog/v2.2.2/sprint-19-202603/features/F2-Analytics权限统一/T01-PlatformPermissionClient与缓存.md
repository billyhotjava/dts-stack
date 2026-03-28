# T01: PlatformPermissionClient 与缓存

**优先级**: P0
**状态**: READY
**依赖**: F1/T03

## 目标

在 analytics 侧实现调用 platform 权限 API 的客户端，含本地缓存。

## 技术设计

### 客户端

```java
@Component
public class PlatformPermissionClient {
    private final RestClient restClient;  // 指向 dts-platform:8081
    private final Cache<String, PermissionResult> checkCache;
    private final Cache<String, AccessibleAssetsResult> listCache;

    public PermissionResult check(String username, String roles, String deptCode, AssetRef asset);
    public Map<AssetRef, PermissionResult> batchCheck(...);
    public AccessibleAssetsResult listAccessibleAssetIds(...);
}
```

### 缓存策略

```
Caffeine:
  checkCache:  key="{username}:{assetType}:{assetId}", TTL=30s, max=10,000
  listCache:   key="{username}:{assetType}",           TTL=60s, max=1,000
```

缓存失效: TTL 到期自动失效。不采用主动推送失效（简化实现），文档注明权限变更最长 60s 延迟。

### 配置

```yaml
dts.analytics.platform-permission:
  base-url: http://dts-platform:8081
  service-name: dts-analytics
  check-cache-ttl: 30s
  list-cache-ttl: 60s
  check-cache-max-size: 10000
  connect-timeout: 2000
  read-timeout: 5000
```

### 熔断

platform 不可用时，返回 DENY（安全优先）。记录 WARN 日志。

## 影响范围

- `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/` — 新增 PlatformPermissionClient
- `source/dts-analytics/src/main/resources/config/application.yml` — 新增配置项

## 验证

- [ ] 调用 platform API 成功返回结果
- [ ] 缓存命中率测试（相同请求 30s 内不重复调用）
- [ ] platform 不可用时返回 DENY
- [ ] 配置项可通过 application.yml 覆盖

## 完成标准

- [ ] 客户端三个方法功能正确
- [ ] 缓存 TTL 可配置
