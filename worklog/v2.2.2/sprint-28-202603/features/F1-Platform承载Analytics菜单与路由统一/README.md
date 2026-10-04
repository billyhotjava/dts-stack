# F1: Platform承载Analytics菜单与路由统一

**优先级**: P0  
**状态**: READY

## 目标

将 analytics 从独立导航应用收口为 platform 内的普通业务模块，统一菜单、路由、权限、收藏与最近访问体系。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 定义BI canonical route与兼容跳转策略 | P0 | READY | - |
| T02 | 下线analytics独立应用壳并接入平台主布局 | P0 | READY | T01 |
| T03 | 统一页面内部导航与公开分享路由边界 | P0 | READY | T01,T02 |
| T04 | dts-admin统一管理BI菜单与默认入口 | P0 | READY | T01 |
| T05 | 回归验证、发布收口与兼容清单 | P1 | READY | T02,T03,T04 |

## 完成标准

- [ ] BI 页面以平台主布局承载，不再显示独立 analytics 侧边栏
- [ ] 页面内部跳转全部切到 canonical route helper
- [ ] `dts-admin` 菜单树成为 BI 菜单唯一真源
- [ ] 公开分享链接保持匿名可访问
