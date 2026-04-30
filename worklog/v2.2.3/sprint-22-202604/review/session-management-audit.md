# Portal Session 管理评审：架构师 + 测试工程师双视角

**评审范围**: `dts-platform-webapp` 中的 session 管理与控制
**评审时间**: 2026-04-30
**主要文件**:
- `source/dts-platform-webapp/src/components/auth/session-manager.tsx` (539 行)
- `source/dts-platform-webapp/src/api/apiClient.ts` (538 行)
- `source/dts-platform-webapp/src/routes/components/login-auth-guard.tsx`
- `source/dts-platform-webapp/src/pages/sys/login/index.tsx`
- `source/dts-platform-webapp/src/utils/portalSessionStorage.ts`
- `source/dts-platform-webapp/src/utils/sessionExpiry.ts`
- `source/dts-platform-webapp/src/store/userStore.ts`
- `source/dts-platform-webapp/src/api/services/userService.ts`
- `source/dts-platform-webapp/src/api/platformApi.ts`
- `source/dts-platform-webapp/src/components/auth/sessionLeadership.helpers.ts`
- 配套测试文件

---

## 一、架构师视角

### P0 — 安全 / 数据资产

#### 1. Access/Refresh + admin 双 Token 全部裸存 localStorage（XSS = 完全失陷）

`store/userStore.ts:88-96` 用 zustand persist 把 `userToken`（含 `accessToken / refreshToken / adminAccessToken / adminRefreshToken`）写入 localStorage。

任意 XSS 即可一次性盗走 portal + admin 双套凭据；refreshToken 在窗口期内可换发新 token，等于把"长寿命凭据"以明文托管在浏览器。

政企 / 数据治理类系统（CLAUDE.md 中涉及"密级 / IAM / classification"）这是不应接受的等级。

**关键事实**：admin token 在前端**只写不读**——从登录/refresh 响应里收下来，写到 localStorage，跨 tab 同步，但**没有任何业务请求把它当作 header 发出去**。整库 grep 没有读用点。

**建议**：
- 第一阶段：admin token 完全不进前端（后端 `PortalSessionEntity` 已经持有副本）。
- 第二阶段：portal access/refresh 改用 httpOnly+SameSite cookie 由 BFF 注入。
- 只有不可重放的短寿命标识留在内存（zustand 不 persist）。

#### 2. 生产代码里打印 Authorization 头 / 响应体

- `api/apiClient.ts:320` `console.log("API Request:", ..., config)` —— `config.headers.Authorization` 一并落入 console。
- `api/apiClient.ts:331` `console.log("API Response:", res.status, res.config.url, res.data)` —— 含敏感数据。

浏览器扩展、F12、共享浏览器、远程 debug 都能看到。属 OWASP A09「日志与监控」。

**建议**：生产构建中通过 `import.meta.env.DEV` 包一层；或用 logger 屏蔽 `Authorization`、`X-Portal-Access-Token`。

#### 3. `TEST_SESSION_ENABLED` 旁路开关随生产构建发布

- `apiClient.ts:43` `VITE_TEST_LONG_SESSION / VITE_TEST_SESSION` 决定 `shouldForceLogout && !TEST_SESSION_ENABLED && !isLoginRequest`(`apiClient.ts:503`)。
- 该开关一旦在生产 `.env` 误开，所有 session 失效（`x-session-conflict / x-session-expired`）被忽略，异地登录顶替能力被静默禁用。

**建议**：生产 build 直接 strip；或运行时校验 `location.host` 仅允许 dev domain 才生效。

#### 4. `handleDevFallback` 在前端伪造账号绕过后端认证

- `userStore.ts:328-378`：当后端 401 + `VITE_DEV_LOGIN_FALLBACK=true` + `import.meta.env.DEV` 时，前端凭 `username` 自签 `dev-access-*` 假 token，并赋予 `ROLE_OP_ADMIN` 等权限。
- DEV 构建意外发布到 staging / 内网测试环境就能"无密码登录"。

**建议**：增加 host allowlist + 大字号 banner；最好彻底删除，由后端 mock 提供。

#### 5. `/session/status` 显式无鉴权 + Token 走自定义 header

- `api/platformApi.ts:14-23` 注释"intentionally public on the backend"，把 `accessToken` 放在 `X-Portal-Access-Token` 里发出去。
- 配合 `_skipAuth: true`，请求不经 Authorization 拦截，但凡 CORS 配置宽松 + 第三方页面通过 `<img>` / `fetch` 投毒，可被用作 token 探测预热。

**建议**：服务端要求同源 / Origin 白名单 / 短期 nonce；不要让该接口能被跨站脚本无成本"问"。

---

### P1 — 一致性与并发模型

#### 6. Tab leader 选举存在 split-brain 窗口

- `session-manager.tsx:265-280` + `sessionLeadership.helpers.ts:36-45`。
- 两个 tab 同时见到 `lease=null` 时，先后 setItem 后 readBack：A 读到自己刚写、B 读到自己刚写（B 后写覆盖 A），但在跨进程 storage event 抵达前的几十毫秒里，A、B 都可能进入 leader 分支并并发触发 `/keycloak/auth/refresh`。
- 现唯一防线是 `LEADER_CONFIRM_MS=250` 和 `expiresAt`——仍是概率防御，不是互斥。

**建议**：换 `navigator.locks.request("portal-refresh-leader", { mode: "exclusive" }, …)`，浏览器原生互斥；老浏览器 fallback 才走 lease。

#### 7. 跨 Tab Token 同步走 localStorage（持久化窗口）

- `session-manager.tsx:113-118` `broadcastTokenSync` 把刚换发的 access/refresh/admin token 写入 `dts.platform.session.tokenSync`，用 storage event 通知。
- 每次刷新都把最新 token 重新写盘；XSS 不必抢时机，下次刷新点又给一份新鲜的。

**建议**：改用 `BroadcastChannel("portal-session")`，仅在内存里发；只有在 fallback 需要时才借 localStorage。

#### 8. 闲置超时只由后端把控，前端无防御

- `session-manager.tsx:447-455` 注释 `Browser-side inactivity only stops proactive refresh. The backend remains the single source of truth`。
- 如果后端 portal session 仅有"绝对过期"没有"滑动闲置过期"，则前端 30 分钟未操作仍可继续使用直到绝对过期。涉密 / 政企场景通常合规要求"无操作 N 分钟自动锁"。
- `SESSION_TIMEOUT_MINUTES = Math.max(1, …)` 下限 1 分钟，无最大值兜底。

**建议**：保留后端为准，但前端再做一次 idle 软锁（弹"会话即将过期"对话框 + 强制重新认证）。

#### 9. `onStorage` 里 setUserToken 用的是闭包旧 `token` 做 spread

- `session-manager.tsx:201-251` 的 `onStorage` 依赖数组含 `token`，每次 token 变都会重新挂监听。
- 但事件回调内部仍 `setUserToken({ ...token, accessToken: synced.accessToken, … })`，token 可能在事件触发瞬间又被另一处更新，导致 admin token 字段被旧值覆盖（因为 `token` 是闭包快照）。

**建议**：从 `useUserStore.getState()` 取最新值再 spread。

#### 10. `finishSession` 退出时不主动放掉 leader lease

- `session-manager.tsx:139-163` 直接 `window.location.replace(...)`，effect cleanup 不一定来得及执行；其它 tab 在 lease 自然到期前（最多 45s）误以为"还有 leader"。次要影响：那 45s 内没有 tab 主动刷 token。

**建议**：`finishSession` 内显式 `localStorage.removeItem(STORAGE_KEYS.REFRESH_LEADER)` 再跳走。

#### 11. 登出广播窗口大小三处不一致

- `apiClient.ts:71` 2000ms（grace window）
- `session-manager.tsx:122 / 240` 5000ms（onStorage / wasLogoutTriggeredRecently）
- `pages/sys/login/index.tsx:77` 15000ms（登录页 stale 检查）

含义都是"最近的 logout 广播是否仍有效"，但取值各异；改一处忘改另两处会出 race。

**建议**：抽常量 `PORTAL_LOGOUT_BROADCAST_TTL_MS`；或参数 + 文档化为什么登录页要 15s。

#### 12. Refresh leader race 检查后 await，存在 TOCTOU

- `session-manager.tsx:435-468` 先 `currentLease.tabId === tabIdRef.current` 校验然后 `await refreshPortalSessionIfPossible()`。await 期间 lease 可能被其它 tab 抢走，但当前 tab 已经发出 refresh 请求。
- 后端必须容忍并发 refresh（幂等 / 同 refreshToken 可重放至少 N 次），否则失败。
- 一旦上 rotating refresh token，这个并发就是被踢的根源。

**建议**：refresh 完成后再 sanity-check 一次 leader（防止上报错误"我才是 leader"）。

#### 13. 30s 的 LoginAuthGuard 周期校验偏慢

- `login-auth-guard.tsx:191-205` 每 30s 才探活一次。Token 被后端撤销后，用户在 SPA 内可继续使用最长 30s（除非业务接口先 401）。结合 `consecutiveExpiredFails >= 2` 才下线，最坏可达 ~60s。

**建议**：撤销 / 异地踢出走 SSE/WebSocket 主动通知，而不是只靠轮询。

---

### P2 — 设计与可维护性

#### 14. userStore persist 的 partialize key 与 state key 可能不对齐

- `userStore.ts:91-94` 持久化键是 `[StorageEnum.UserInfo]` / `[StorageEnum.UserToken]`，而 store state 实际字段名是 `userInfo` / `userToken`。
- 如果 `StorageEnum.UserInfo !== "userInfo"`，rehydrate 后字段挂错，selector 会读到 undefined。

**待办**：核对 `#/enum` 里 `StorageEnum` 的取值。

#### 15. `isTokenExpired` 对 opaque token 一律返回 false

- `login-auth-guard.tsx:27-37` 与登录页同款实现。意味着即便 portal session 已经无效，本地 token 在下一次 probe 之前永远"看起来有效"。配合上面 30s 周期，UX 可能让用户不知所措。

#### 16. 同一份 JWT 工具函数复制了 3 遍

- `decodeJwtExp / isTokenExpired` 在 `session-manager.tsx`、`login-auth-guard.tsx`、`pages/sys/login/index.tsx` 重复实现，逻辑略有差异（grace 与否、`-10_000` 偏差），未来一改三处。属典型 DRY 缺口。

#### 17. 403 不走 silent refresh 也不下线

- `apiClient.ts:444-515` 只对 401 做 refresh+retry，对 403 仅弹 toast。
- 后端如果在 token 已被吊销时返回 403（如 ABAC gate 收紧），前端不会清状态。

---

## 二、测试工程师视角

### P0 — "假"测试

#### 1. `session-management.source-contract.test.ts` 是源码字符串扫描

```ts
expect(source.includes("backend remains")).toBe(true)
expect(source.includes("expectedAccessToken")).toBe(true)
```

- 一改注释、一改换行、一改变量名就红，但行为可以全错而绿。
- 这种 contract test 给团队"已经测过了"的错觉。

**建议**：删 80% 改成 RTL + jsdom + fake timers 的行为测试。

#### 2. `SessionManager`(539 行) 零行为覆盖

没有任何用例验证：
- leader 切换 / heartbeat 续租 / 退出释放 lease
- probe 在 grace window 内的跳过
- `consecutiveExpiredFails ≥ 2` 才下线
- cross-tab `TOKEN_SYNC` 被采用 + admin token 不被旧值覆盖
- 登出广播在 5s 内被另一 tab 接住并跳转登录
- refresh failure 重试 backoff (`nextRefreshRetryDelayMs`)

这类用例 vitest + jsdom + fake timer 完全可写。

#### 3. `apiClient.ts` 的 silent refresh / stale 401 防护无单测

- `extractRequestAccessToken / isStillCurrentAccessToken / forceLogoutToLogin(requestToken)` 这些是为了防"旧请求迟到的 401 把新 session 干掉"，逻辑相当微妙，却没有用 nock / MSW 之类的 axios mock 去覆盖。
- 当前 `session-management.source-contract.test.ts:49-60` 仅做字符串断言，等于没测。

---

### P1 — 边界与并发缺测

#### 4. 缺并发场景的多 tab 测试

- 只有 `sessionLeadership.helpers.test.ts` 测了纯函数，但 happy path 也只验"自己抢自己"和"过期可抢"。
- 缺：A、B 同时无 lease → 应有最多一个成功；A 持有但崩溃（不 cleanup）→ B 应在 lease 到期后接管而非提前。

#### 5. JWT 解码鲁棒性缺测

- `decodeJwtExp` 没有针对 `parts.length=1`、payload 含非法 base64、`exp` 为非 number、`exp < 0`、超大 number 的用例。

#### 6. localStorage 写失败 / quota 异常缺测

- 大量 `try { localStorage.setItem(...) } catch {}` 静默吞错。
- 需要至少一个用例验证：写失败时不会让 leader 状态错误地停留在"我是 leader"。

#### 7. 从 hidden→visible 唤醒后的"长睡眠 lastActivity"未覆盖

- 用户合上电脑 30 分钟后打开，`lastActivityRef` 仍是旧值，`idleFor` 大于 `SESSION_TIMEOUT_MS + GRACE`，refresh loop 直接挂起 30s。
- 第一个业务接口 401 后才 refresh，UX 不佳。需要 fake timer + visibility 切换 用例锁定预期。

#### 8. 网络分区 / 后端 502/504 缺测

- `SERVICE_UNAVAILABLE_STATUSES = {502,503,504}` 走 toast 通道，没有断言"不要因 502 把用户踢下线"——这是 `LoginAuthGuard.verifyBackendSession` 注释里强调的语义，必须有测试守住。

#### 9. 登录页 stale logout 广播 15s 窗口缺测

- `pages/sys/login/index.tsx:77` `wasPortalLogoutBroadcastRecently(15_000, ...)` 与其它处 5s 不一致，且无单测覆盖；改动随时漂走。

---

### P2 — 流程

#### 10. 没有 e2e（Playwright）覆盖三大关键路径

- 异地登录顶替（CONCURRENT）→ 老 tab 在 ≤30s 内被踢出
- 后端服务重启 → 老 tab 不被错误踢出
- 多 tab 同时活动 → token refresh 仅发生一次

项目根目录有 `e2e-runner` agent 可用，理应有这三条 smoke。

#### 11. 测试运行成本 / 隔离

- `session-management.source-contract.test.ts` 用 `fs.readFileSync(import.meta.dirname,…)` 直接读源文件，跨平台、子模块复用、bazel/nx 抽取时都可能炸；这种"测源码本身的存在性"应转为 ESLint custom rule 而非单测。

---

## 三、Top 5 必修（按 ROI）

1. **关掉 `console.log(... config)` 与 `console.log(... res.data)`**（`apiClient.ts:320, 331`）—— 一行改动消除明文 token / PII 落 console。
2. **把 admin token 从前端剥离 + portal token 至少加 short-TTL**；中期改 httpOnly cookie + BFF。
3. **删除 / 重写 `session-management.source-contract.test.ts`**，用 vitest + jsdom 覆盖 SessionManager 行为；同时给 `apiClient` 401 → silent refresh → stale token 防护写 axios mock 测试。
4. **Leader 选举切到 `navigator.locks`**，跨 tab token 同步切到 `BroadcastChannel`。
5. **把 `TEST_SESSION_ENABLED` 旁路从 production bundle 中编译期 strip**，并给 dev fallback 加 host allowlist。

完整治理路线见 sprint-22 README。
