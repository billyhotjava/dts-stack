# P0-05 可观测、兼容、性能基线

`status`: `done`  
`priority`: `P0`  
`inspiration`: `Superset(可观测查询链路) + 现场交付稳定性要求`

## 目标

确保现场遇到 500/502 时可以快速定位，且旧版浏览器可用。

## 子任务

1. 统一错误观测
- 前后端统一 `requestId` 贯穿日志。
- API 错误暴露 `code/retryable`。

2. 缓存与预热
- 发布后支持关键组件预热。
- 缓存观测面板显示命中率、驱逐数、策略状态。

3. 浏览器兼容
- 最低支持 Chrome 95，回归 109 与最新版本。

4. 性能门槛
- 100 组件大屏首屏时间与交互延迟建立基线。

## 验收标准

- 任意错误可通过 `requestId` 定位到后端日志。
- Chrome 95 下核心编辑功能可用且无明显样式错乱。
- 缓存策略修改后可立即生效并可观测。

## 风险与回滚

- 风险：兼容补丁影响现代浏览器性能。  
- 回滚：按 capability 判断启用兼容路径，不全局降级。

## 实现记录（2026-02-14）

- 错误观测：后端 `ApiError` 新增 `retryable`，并输出 `X-Error-Code/X-Error-Retryable`；请求日志补充 `requestId`。
- 前端错误模型：`HttpError` 增加 `retryable` 解析，错误信息携带 `code/retryable/requestId`。
- 健康体检：新增 `/analytics/api/screens/{id}/health`，输出草稿/发布态组件复杂度、可预热源、100组件基线判定和建议。
- 设计器入口：新增“体检”面板，展示浏览器兼容（Chrome95+基线）与性能健康报告。
- 缓存观测与发布预热：沿用既有 `缓存观测` 面板与发布 warmup 汇总链路。
- Chrome 95 兼容补强（2026-02-15）：
  - 构建目标已固定支持 `chrome95`（`vite build` 默认 `LEGACY_BROWSER_BUILD=1`）。
  - 分享链接复制统一切换到 `writeTextToClipboard`（`navigator.clipboard` + `execCommand` fallback），覆盖非安全上下文与旧浏览器策略限制。
  - Modal 层级上调到 `z-index 20000+`，避免设计器高层元素遮挡导致弹窗“点击无响应/不可见”。
