# T01: 前端裸 token 整改（停止 localStorage 持久化，迁移 httpOnly cookie / 受控内存）

**优先级**: P0
**状态**: READY
**依赖**: —

## 目标

停止把 portal access/refresh 与 admin access/refresh 四套裸 token 明文持久化到 localStorage；admin token 完全不进前端，portal token 改由 httpOnly+SameSite cookie 注入或仅以短寿命内存标识保留，消除 XSS 一次失陷双端凭据的风险（dts-platform-webapp + dts-admin-webapp 双端）。

## TDD 测试先行（RED）

- 扩展既有 `dts-platform-webapp/src/store/userStore.persistence.source-contract.test.ts` 与 `dts-admin-webapp/src/store/userStore.persistence.source-contract.test.ts`：断言 persist `partialize` 不再输出 `accessToken/refreshToken/adminAccessToken/adminRefreshToken`，localStorage 序列化结果中检索不到 token 串。
- 新增行为测试 `userStore.token-storage.test.ts`（vitest + jsdom）：登录响应写入后，`localStorage.getItem(StorageEnum.UserToken)` 不含任何裸 token；admin token 字段在 store 中不存在或始终为空。
- 新增 `tokenSync.test.ts`：跨 tab 同步走内存通道（BroadcastChannel），不再向 localStorage 写 `dts.platform.session.tokenSync`。
- 运行确认全部 FAIL（当前仍持久化）。

## 技术设计（GREEN）

- 修改 `dts-platform-webapp/src/store/userStore.ts`：persist `partialize` 仅保留非凭据 UI 状态，token 移出持久化层；access token 仅存内存（zustand 非 persist 切片）。
- admin token 不再写入前端 store（后端 `PortalSessionEntity` 已持副本）；删除 `adminAccessToken/adminRefreshToken` 写入点。
- portal token 注入改为 httpOnly cookie（BFF/网关注入），`apiClient` 请求改为依赖 cookie 凭据而非内存 header 兜底。
- `dts-admin-webapp/src/store/userStore.ts` 同步整改。
- 跨 tab 同步：`components/auth/session-manager.tsx` 的 `broadcastTokenSync` 切到 `BroadcastChannel`，不落 localStorage。

## 影响范围

- `source/dts-platform-webapp/src/store/userStore.ts`（改既有 symbol，先 gitnexus_impact）
- `source/dts-admin-webapp/src/store/userStore.ts`（改既有 symbol，先 gitnexus_impact）
- `source/dts-platform-webapp/src/api/apiClient.ts`
- `source/dts-platform-webapp/src/components/auth/session-manager.tsx`（539 行，改既有 symbol，先 gitnexus_impact）
- `source/dts-platform-webapp/src/utils/portalSessionStorage.ts`

## 验证

- [ ] localStorage 中检索不到任何 access/refresh/admin token 串。
- [ ] admin token 不再出现在前端 store 与持久化层。
- [ ] 跨 tab token 同步不经 localStorage。

## 完成标准

- [ ] 前端凭据不再以明文持久化，XSS 无法从 localStorage 一次性盗取双端 token。
