# Sprint-23: 浏览器作用域会话重构

**时间**: 2026-04
**状态**: IN_PROGRESS
**目标**: 将现有 portal token + 本地 refresh 协调改造成浏览器作用域的服务端会话架构，满足“同一浏览器多 tab 共存、跨浏览器和跨机器不共存”，并消除 401 风暴、闪屏、慢刷和“异地登录”误判。

## 背景

现网复现表明，大屏预览和 platform 主页面在双 tab 打开后，会在 5 到 6 分钟附近进入 401 和重定向风暴。问题不在 Keycloak 的 5 分钟过期配置，而在现有会话链路的结构性冲突:

- platform 使用 opaque portal token，但前端只能按固定 4 分钟策略刷新
- refresh 采用 successor session 模式，多个 tab 容易进入旧 token 失效窗口
- 登录页和路由守卫仍以本地 token 是否存在作为跳转依据，失效后会形成 remount 循环
- analytics 同时保留 forward-auth、bearer fallback、metabase session bridge 三套语义，放大了会话竞争

本 Sprint 为实施型 Sprint，直接落地浏览器作用域会话架构，并为切换、回滚和回归验证提供完整执行计划。

**约束**:
- 同一浏览器多个 tab 必须允许共存
- 不同浏览器和不同机器不得共存
- Keycloak 保留为身份源，不再作为浏览器端运行时 session 协调器
- `analytics-webapp-modern` 已删除，但 analytics backend 仍需纳入统一鉴权链路

## Feature 列表

| ID | Feature | Task 数 | 状态 | 优先级 |
|----|---------|---------|------|--------|
| F1 | 浏览器作用域会话模型 | 3 | IN_PROGRESS | P0 |
| F2 | platform 后端会话核心重构 | 3 | READY | P0 |
| F3 | 前端会话运行时重构 | 3 | READY | P0 |
| F4 | analytics 与 proxy 鉴权收敛 | 3 | READY | P0 |
| F5 | 迁移兼容与运维治理 | 3 | READY | P1 |
| F6 | 集成验证与回归防线 | 3 | READY | P1 |

## 完成标准
- [ ] `portal_session` 和 `browser_id` 的浏览器作用域模型在服务端落地，取代浏览器可见 portal access/refresh token
- [ ] platform 登录、续租、登出、冲突判断全部改为服务端 session 语义
- [ ] 前端删除固定 4 分钟 opaque token refresh 机制，改为基于 `session/current` 的会话探针
- [ ] analytics 仅接受统一 forward-auth 身份，不再参与 bearer refresh 竞争
- [ ] 同浏览器双 tab 大屏和 platform 页面可稳定共存，不再出现闪屏、慢刷和错误“异地登录”
- [ ] 发布、回滚、观测、验证文档齐备，能支撑分阶段切换
