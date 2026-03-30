# Sprint-28: Platform承载Analytics菜单与路由统一

**时间**: 2026-03  
**状态**: READY  
**目标**: 以 `dts-platform-webapp` 为唯一入口，统一承载 analytics 菜单、路由与导航壳，并由 `dts-admin` 统一管理 BI 菜单

## 背景

当前 `analytics` 页面已被迁入 `dts-platform-webapp`，但仍保留独立布局、独立菜单和大量旧路径跳转，导致登录入口、页面跳转、分享页访问和平台菜单体系出现冲突。需要将 BI 功能从“嵌入的第二个应用”收口为“平台中的一组普通业务页面”。

## Feature 列表

| ID | Feature | Task 数 | 状态 |
|----|---------|---------|------|
| F1 | Platform承载Analytics菜单与路由统一 | 5 | READY |

## 完成标准

- [ ] `dts-platform-webapp` 成为唯一 BI 入口壳
- [ ] BI 菜单由 `dts-admin` 统一管理并进入平台左侧菜单
- [ ] Analytics 页面不再依赖独立应用级菜单与布局
- [ ] 旧 `/analytics/*` 路径有清晰兼容策略
- [ ] 公开分享页路由与登录守卫边界正确
