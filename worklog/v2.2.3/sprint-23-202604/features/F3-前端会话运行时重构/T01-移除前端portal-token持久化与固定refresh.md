# T01: 移除前端 portal token 持久化与固定 refresh

**优先级**: P0
**状态**: READY
**依赖**: F2/T01

## 目标
彻底删除前端对 portal access/refresh token 的依赖，去掉 opaque token 的固定 4 分钟 refresh 机制。

## 技术设计
- 从 `dts-platform-webapp`、`dts-admin-webapp`、`dts-session-core` 中移除 portal token 持久化逻辑
- 下线 `nextRefreshDelayMs()` 驱动的定时 refresh 调度，改为 session 探针或用户操作驱动的续租
- 重新定义认证 store，仅保存用户展示信息和会话状态，不保存可用作认证的 portal token
- 梳理与 `localStorage`、`BroadcastChannel`、storage event 相关的旧会话同步逻辑
- 对 admin 和 platform 两个前端统一收口，避免共享库残留旧行为

## 影响范围
- `source/dts-session-core/src/token.ts`
- `source/dts-platform-webapp/src/components/auth/session-manager.tsx`
- `source/dts-admin-webapp/src/components/auth/session-manager.tsx`
- `source/dts-platform-webapp/src/store/`
- `source/dts-admin-webapp/src/store/`

## 验证
- [ ] 浏览器存储中不再出现 portal access/refresh token
- [ ] 前端启动后不再注册固定 4 分钟 refresh 定时器
- [ ] 同浏览器两个 tab 长时间驻留不触发互相踢下线

## 完成标准
- [ ] 前端会话状态与认证凭据彻底解耦
- [ ] 共享库不再输出 opaque token 的刷新策略
