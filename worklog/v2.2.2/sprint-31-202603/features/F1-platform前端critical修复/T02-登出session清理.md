# T02: 修复 userStore 登出 session 数据未清理

**严重度**: Critical
**文件**: `source/dts-platform-webapp/src/store/userStore.ts`

## 问题

`clearUserInfoAndToken()` 只清理 `dts.session.loginTs` 和 `dts.session.lastActivity`，遗漏：
- `dts.session.id` — 残留导致下次登录误判"会话冲突"
- `dts.session.user` — 残留过期用户名
- `dts.session.logoutTs` — 残留可能触发跨 tab 登出逻辑

## 修复方案

在 `clearUserInfoAndToken()` 中补充清理：

```typescript
localStorage.removeItem("dts.session.id");
localStorage.removeItem("dts.session.user");
localStorage.removeItem("dts.session.logoutTs");
localStorage.removeItem("dts.session.loginTs");
localStorage.removeItem("dts.session.lastActivity");
```

## 验证

- 登出后 localStorage 中无 `dts.session.*` 残留
- 重新登录不触发"会话冲突"提示
