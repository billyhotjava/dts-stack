# T04: 前端 idle 软锁（与后端 30min inactivity 对齐，无操作锁屏需重认证）

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

补齐前端闲置防御：当前会话由后端单一把控（`session-manager.tsx` 注释「Browser-side inactivity only stops proactive refresh, backend remains single source of truth」），无操作时前端不锁屏。新增 idle 软锁，与后端 `timeout-minutes=30` 对齐，无操作超时弹锁屏并强制重新认证，满足机密级"无操作 N 分钟自动锁"。

## TDD 测试先行（RED）

- 新增 `idleSoftLock.test.ts`（vitest + jsdom + fake timers）：无操作达 30min 阈值触发锁屏状态，断言路由/UI 进入 locked、业务请求被拦截；解锁需重新认证回调。
- 边界用例：hidden→visible 长睡眠唤醒（`lastActivity` 陈旧）应立即判定超时锁屏，而非等下一次 401。
- 阈值用例：软锁阈值与后端 `timeout-minutes` 单一常量对齐，下限/无最大值兜底回归。
- 运行确认 FAIL（当前无前端软锁）。

## 技术设计（GREEN）

- 新增 `useIdleSoftLock` hook + `IdleLockOverlay` 组件：监听用户活动（pointer/key/visibility），`requestAnimationFrame`/timer 计算 idle，达阈值置 locked。
- 阈值常量与后端对齐：读取 `application.yml:340 timeout-minutes` 对应的前端配置常量，单一来源，避免 magic number。
- 锁屏期间冻结业务请求，解锁走重新认证（PKI/口令）后恢复；与 T01 受控存储联动，不在锁屏态保留可用裸 token。
- 接入 `components/auth/session-manager.tsx` 与根路由 guard。

## 影响范围

- `source/dts-platform-webapp/src/components/auth/session-manager.tsx`（539 行，改既有 symbol，先 gitnexus_impact）
- `source/dts-platform-webapp/src/routes/components/login-auth-guard.tsx`（改既有 symbol，先 gitnexus_impact）
- `source/dts-platform-webapp/src/utils/sessionExpiry.ts`
- 新增 `source/dts-platform-webapp/src/components/auth/IdleLockOverlay.tsx` 与 `src/hooks/useIdleSoftLock.ts`
- 后端基线（只读对齐，不改契约）：`source/dts-platform/.../security/session/PortalSessionInactivityFilter.java`

## 验证

- [ ] 无操作 30min 触发软锁，业务请求被拦截。
- [ ] 长睡眠唤醒立即判定超时，不依赖业务 401。
- [ ] 解锁需重新认证，锁屏态无可用裸 token。

## 完成标准

- [ ] 前端 idle 软锁与后端 inactivity 对齐，满足机密级无操作自动锁要求。
