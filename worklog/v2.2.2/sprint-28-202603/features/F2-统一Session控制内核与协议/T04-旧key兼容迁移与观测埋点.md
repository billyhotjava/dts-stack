# T04: 旧key兼容迁移与观测埋点

**优先级**: P1  
**状态**: READY  
**依赖**: T01,T02,T03

## 目标

为从旧 `dts.session.*` 迁移到新命名空间提供平滑兼容方案，并补上可以定位刷新、登出和回跳问题的前端观测点。

## 技术设计

- 启动阶段尝试一次性读取旧 key，迁移到新 namespace 后仅写新 key。
- 兼容期内保留旧 key 只读桥接，避免旧 bundle 与新 bundle 混用时直接丢会话。
- 统一埋点与日志字段：
  - session domain
  - logout reason
  - refresh attempt/result
  - redirect target
  - conflict source
- 为回滚保留“切回旧协议”的开关与步骤。

## 影响范围

- `source/dts-platform-webapp/src/auth/**`
- `source/dts-admin-webapp/src/auth/**`
- `source/dts-analytics-webapp/modern/src/api/platformSession.ts`
- `docs/release/**`

## 验证

- [ ] 新旧 key 共存时不会导致立即登出或顶号风暴
- [ ] 线上日志能区分 refresh 失败与 session conflict

## 完成标准

- [ ] 兼容迁移步骤可执行
- [ ] 观测字段足够支撑现场排障
