# T03: Login response 返回 expires_in 替代 localStorage 时间戳

**优先级**: P1
**状态**: READY
**依赖**: 无

## 问题

commit `9b721c0d7` 加的 120s `isRecentLogin()` 宽限期依赖 `localStorage.getItem("dts.session.loginTs")`。
这个方案有边界问题：清除 localStorage / 跨标签页 / 系统时间不准 都会导致逻辑失效。
根因：前端无法判断 opaque token 何时过期，因为后端 login response 没有返回 `expires_in`。

## 技术设计

### 后端改动

| 文件 | 改动 |
|------|------|
| `KeycloakAuthResource.java` login/pki-session 端点 | response data 增加 `expiresIn`（秒）和 `expiresAt`（ISO 时间戳） |
| `KeycloakAuthResource.java` refresh 端点 | response data 同样增加 `expiresIn` 和 `expiresAt` |

```java
data.put("expiresIn", session.ttlSeconds());
data.put("expiresAt", session.expiresAt().toString());
```

### 前端改动

| 文件 | 改动 |
|------|------|
| `userStore.ts` | 新增 `tokenExpiresAt: number` state，login/refresh 成功后写入 |
| `login-auth-guard.tsx` | `isTokenExpired()` 改为：先尝试 JWT exp 解析，失败则用 `tokenExpiresAt` |
| `login/index.tsx` | 同步改动 |
| `session-manager.tsx` | 心跳刷新延迟改为基于 `tokenExpiresAt` 计算 |

### 删除
- 移除 `dts.session.loginTs` localStorage 写入/读取
- 移除 `isRecentLogin()` 函数和 `LOGIN_GRACE_MS` 常量

## 验证

- [ ] 密码登录后前端能正确读取 expiresIn
- [ ] PKI 登录后前端能正确读取 expiresIn
- [ ] token 过期检测不再依赖 localStorage 时间戳
- [ ] 清除 localStorage 后刷新页面，过期检测仍正常工作（基于 store 持久化）

## 影响范围

- login / pki-session / refresh response 增加字段（向后兼容，前端不读也不报错）
- 前端删除 workaround 代码，替换为正规方案
