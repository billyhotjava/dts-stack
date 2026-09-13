# T04: Session 刷新增加乐观锁防竞态

**优先级**: P1
**状态**: READY
**依赖**: 无

## 问题

`KeycloakAuthResource.refreshSession()`（platform）和 `KeycloakApiResource` refresh（admin）均无版本号或乐观锁。
前端虽有 `refreshingPromise` 防单标签页竞速，但**跨标签页**场景仍可能并发：
标签 A 和 B 同时发 refresh → 都成功 → 生成两个不同 token → 前一个被覆盖 → 后续请求用错 token → 401。

## 技术设计

### 方案：JPA @Version 乐观锁

| 文件 | 改动 |
|------|------|
| `PortalSession` 实体 / `portal_sessions` 表 | 新增 `version BIGINT NOT NULL DEFAULT 0` 列 |
| `AdminSessionEntity` / `admin_sessions` 表 | 新增 `version BIGINT NOT NULL DEFAULT 0` 列 |
| Liquibase changelog | 新增两张表的 `ALTER TABLE ADD COLUMN version` |
| `PortalSessionRegistry.refreshSession()` | 使用 `@Version` 字段，`OptimisticLockException` 时返回当前有效 token 而非新 token |
| `AdminSessionRegistry.refresh()` | 同上 |

### 并发处理逻辑
```
请求 A: refresh → 读 session(version=1) → 更新 token → save(version=2) ✓
请求 B: refresh → 读 session(version=1) → 更新 token → save(version=1→2) → OptimisticLockException
  → catch → 重新读 session(version=2) → 返回请求 A 刷新后的 token
```

失败方不是报错，而是返回赢方已刷新的 token，保证前端拿到的始终是最新的。

## 验证

- [ ] 单标签页 refresh 不受影响
- [ ] 开两个标签页同时触发 refresh，两个都成功且拿到相同 token
- [ ] PKI 登录的 session refresh 同样受乐观锁保护

## 影响范围

- 仅改动 session 续期路径
- 不改动 createSession / login / pki-login
