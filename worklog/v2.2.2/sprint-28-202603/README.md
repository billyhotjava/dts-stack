# Sprint-28: Platform承载Analytics菜单、路由与Session统一

**时间**: 2026-03  
**状态**: READY  
**目标**: 以 `dts-platform-webapp` 为唯一 analytics 入口壳，统一菜单、路由与 session 控制，并为 `dts-analytics-webapp/modern` 的退场建立清晰拆除路径

## 背景

当前 analytics 页面虽然已经大量迁入 `dts-platform-webapp`，但前端仍残留三类历史包袱：

- BI 页面在平台内和 `modern` 独立应用之间同时存在，导航壳与入口语义不一致；
- `dts-platform-webapp` 内部存在 `apiClient`、`SessionManager`、`analyticsApi` 三套并行 session 控制器，导致 refresh、logout、redirect 语义漂移；
- `dts-admin-webapp` 与 `dts-platform-webapp` 作为独立应用，却仍共用 `dts.session.*` 浏览器 key，存在跨应用误广播与会话冲突风险。

本 Sprint 的核心不是继续补单点 bug，而是把 analytics 收口到 platform 主壳的同时，建立一套可复用但隔离命名空间的 session 技术架构，并将 `modern` 从“仍被 compose/build/test 依赖的活跃前端”推进到“可拆除组件”。

## Feature 列表

| ID | Feature | Task 数 | 状态 |
|----|---------|---------|------|
| F1 | Platform承载Analytics菜单与路由统一 | 5 | READY |
| F2 | 统一Session控制内核与协议 | 4 | READY |
| F3 | Platform与Admin接入统一Session控制 | 4 | READY |
| F4 | analytics modern退场与依赖拆除 | 4 | READY |

## 完成标准

- [ ] `dts-platform-webapp` 成为唯一 analytics 入口壳
- [ ] BI 菜单由 `dts-admin` 统一管理并进入 platform 左侧菜单
- [ ] `platform` 与 `admin` 采用同一套 session 技术栈和协议，但浏览器 key 按应用域隔离
- [ ] refresh、logout、idle timeout、redirect 只保留单一控制入口，不再并行存在多套计时器和多套 401 处理链
- [ ] `dts-analytics-webapp/modern` 完成退场门禁盘点，并具备可执行的拆除顺序
- [ ] 旧 `/analytics/*` 路径、公开分享页和 drill 路由具备清晰兼容策略与回归清单
