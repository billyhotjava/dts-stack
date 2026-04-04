# F2: 缓存层替代快照表 (Phase 2)

**优先级**: P1
**状态**: READY

## 目标
引入 `kc_user_cache` 和 `kc_org_cache` 表，替代 AdminKeycloakUser 的快照角色，按需拉取 + TTL 过期，支持 Keycloak 不可用时降级。

## 设计原则

- 缓存不是 source of truth，过期即失效
- 查询时 cache-aside 模式：先查缓存 → 未命中/过期 → 查 Keycloak → 写缓存
- TTL 可配置（默认 5 分钟），Keycloak 不可用时使用过期缓存（宽限期 30 分钟）

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | kc_user_cache 表设计与实现 | P1 | READY | F2 |
| T02 | kc_org_cache 表设计与实现 | P1 | READY | F2 |
| T03 | PlatformDirectoryResource 改用缓存层 | P1 | READY | T01, T02 |

## 完成标准
- [ ] AdminKeycloakUser 的所有读取点改为查缓存层
- [ ] Keycloak 正常时缓存命中率 > 90%
- [ ] Keycloak 挂掉后 30 分钟内查询仍可用（降级到过期缓存）
- [ ] 缓存 TTL 可通过配置调整
