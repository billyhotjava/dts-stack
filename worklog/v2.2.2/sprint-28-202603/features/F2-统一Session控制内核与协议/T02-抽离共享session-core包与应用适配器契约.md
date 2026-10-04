# T02: 抽离共享session-core包与应用适配器契约

**优先级**: P0  
**状态**: READY  
**依赖**: T01

## 目标

定义可被 `platform`、`admin`、`modern(迁移期)` 复用的共享 session core 形态，避免继续复制 `session-manager.tsx` / `apiClient.ts` / `platformSession.ts` 的逻辑分支。

## 技术设计

- 新增独立前端内部包，例如 `source/dts-frontend-session-core`，以明确依赖方式提供共享实现。
- core 只负责与应用无关的能力：
  - token refresh single-flight
  - session activity/idle timeout
  - cross-tab broadcast
  - logout reason
  - redirect intent 生成
  - token persistence adapter
- 每个应用只保留薄适配层，负责：
  - 当前路由读取
  - 登录页 URL 生成
  - userStore 读写
  - toast 文案/展示

## 影响范围

- `source/dts-platform-webapp/package.json`
- `source/dts-admin-webapp/package.json`
- `source/dts-analytics-webapp/modern/package.json`
- `source/dts-frontend-session-core/**`

## 验证

- [ ] 共享包导出的接口能同时覆盖 platform/admin 两套路由系统
- [ ] 应用层只需要传入 adapter，而不再自行实现 refresh 锁和存储协议

## 完成标准

- [ ] session-core 包边界明确
- [ ] app adapter 契约明确且可落地
