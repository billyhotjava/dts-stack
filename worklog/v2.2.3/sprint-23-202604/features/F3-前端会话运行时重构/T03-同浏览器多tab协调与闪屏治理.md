# T03: 同浏览器多 tab 协调与闪屏治理

**优先级**: P0
**状态**: READY
**依赖**: T01,T02

## 目标
保留同浏览器多 tab 共存能力，同时消除 storage 广播误伤、页面慢刷和闪屏重挂载。

## 技术设计
- 重新定义 tab 级协调，只同步展示层状态，不同步认证凭据
- 对 `logoutTs`、`sessionId` 等旧广播键进行兼容清理或删除
- 为大屏预览、首页、仪表板页增加一次性错误边界和节流保护，避免失败后瞬时重挂载
- 对 `fetch` 层加入 reason-aware 处理，避免 401 导致级联重复请求
- 在必要位置增加前端观测点，记录 redirect 次数、remount 次数、401 次数

## 影响范围
- `source/dts-platform-webapp/src/components/auth/session-manager.tsx`
- `source/dts-platform-webapp/src/api/apiClient.ts`
- `source/dts-platform-webapp/src/analytics/pages/screens/ScreenPreviewPage.tsx`
- `source/dts-platform-webapp/src/analytics/api/analyticsApi.ts`
- `source/dts-admin-webapp/src/components/auth/session-manager.tsx`

## 验证
- [ ] 同浏览器双 tab 不再出现错误“异地登录”
- [ ] 大屏预览 30 分钟稳定运行，不出现闪屏或极慢刷新
- [ ] 前端观测数据能证明 redirect 和 remount 次数回落

## 完成标准
- [ ] 多 tab 协调只影响 UI，不再影响底层认证链路
- [ ] 401 失败不会演变成页面级抖动
