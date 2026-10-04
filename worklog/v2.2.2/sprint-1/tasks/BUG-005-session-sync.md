# BUG-005: Analytics 与 Platform session 不同步

- **优先级**: P0
- **状态**: TODO

## 问题

从 platform-webapp 打开 analytics-webapp 后，platform 侧 10 分钟无操作，analytics 提示 session 失效。

## 根因

- Platform: JWT + localStorage（`dts.session.*`），10 分钟超时
- Analytics: Metabase 独立 session 模型
- 两者没有共享心跳/token 续期机制

## 方案选项

1. **短期**: analytics 侧检测到 session 失效时，尝试用 platform token 重新认证
2. **中期**: 统一走 SSO（Keycloak）token，两个 webapp 共享 token 续期

## 涉及文件

- `source/dts-platform-webapp/src/components/auth/session-manager.tsx`
- `source/dts-analytics/` — session/auth 相关

## 交付标准

- [ ] 两个 webapp 间切换时 session 不中断
- [ ] 超时逻辑以最后一次操作为准（任一 webapp 操作都刷新计时）
