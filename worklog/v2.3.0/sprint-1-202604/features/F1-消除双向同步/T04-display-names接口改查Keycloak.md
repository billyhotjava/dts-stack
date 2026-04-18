# T04: display-names 接口改为查 Keycloak

**优先级**: P1
**状态**: READY
**依赖**: T03

## 目标
`resolveDisplayNames()` 方法从查本地 AdminKeycloakUser 表改为查 Keycloak Admin API + 缓存。

## 技术设计

### 当前逻辑 (AdminUserService.resolveDisplayNames())

```
1. 查 AdminKeycloakUser 表 (批量 findByUsernameInIgnoreCase)
2. 缺失的 → 逐个查 Keycloak Admin API
```

### 目标逻辑

```
1. 查内存/缓存 (ConcurrentHashMap 或 Caffeine，TTL 5min)
2. 缺失的 → 批量查 Keycloak Admin API
3. 写入缓存
```

### 分阶段实现策略

引入 `UserDisplayNameResolver` 接口作为稳定抽象，调用点（审计、审批等）永远只依赖接口，不触碰具体实现：

```java
public interface UserDisplayNameResolver {
    Map<String, String> resolve(Collection<String> usernames);
}
```

- **Phase 1（本 Task）**: 实现 `InMemoryKeycloakDisplayNameResolver` — 查 Keycloak Admin API + Caffeine 本地缓存（TTL 5min）
- **Phase 2（F2/T03）**: 新增 `CachedDisplayNameResolver` — 查 `kc_user_cache` 表；通过 Spring 配置切换 primary bean，**调用点零改动**

这样避免 Phase 1 → Phase 2 时需要改所有调用处的"双改"。

## 影响范围

| 文件 | 改动 |
|------|------|
| `dts-admin/.../AdminUserService.java` | resolveDisplayNames 改实现 |
| 新增 `UserDisplayNameResolver.java` | 接口定义 |

## 验证
- [ ] 审计日志中用户显示名称正确
- [ ] 批量解析 100 个用户 displayName 响应时间 < 2s
- [ ] Keycloak 不可用时返回 username 作为 fallback

## 完成标准
- [ ] resolveDisplayNames 不再查 AdminKeycloakUser 表
- [ ] 引入内存缓存降低 Keycloak API 调用频率
