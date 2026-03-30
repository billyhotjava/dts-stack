# T02: 下线analytics独立应用壳并接入平台主布局

**优先级**: P0  
**状态**: READY  
**依赖**: T01

## 目标

去除 analytics 的独立应用级侧边栏与顶栏，使 BI 页面直接由 platform 主布局承载。

## 技术设计

- 将 `src/routes/sections/analytics.tsx` 从独立壳改为普通页面路由集合；
- 停用 `src/analytics/layouts/AppLayout.tsx` 的应用导航职责；
- 让面包屑、多标签、收藏、最近访问统一由 platform 外壳提供。

## 影响范围

- `source/dts-platform-webapp/src/routes/sections/analytics.tsx`
- `source/dts-platform-webapp/src/analytics/layouts/AppLayout.tsx`
- `source/dts-platform-webapp/src/layouts/dashboard/**`

## 验证

- [ ] BI 页面进入后显示 platform 统一导航壳
- [ ] 不再出现 analytics 独立侧边栏
- [ ] 登录后默认入口不再硬跳 `/analytics`

## 完成标准

- [ ] 平台成为唯一应用壳
- [ ] analytics 不再拥有独立菜单定义权
