# FE-002

## 标题

重构 `platform/admin` 共享控制台壳。

## 范围

- `source/dts-platform-webapp/src/layouts/dashboard/`
- `source/dts-admin-webapp/src/layouts/dashboard/`
- 相关 header、nav、search、breadcrumb 组件

## 目标

- 统一深色侧栏 + 浅色内容区
- 统一页头、面包屑、全局动作、搜索区、账号入口
- 统一主内容区宽度、卡片密度、页面留白

## 交付

- 新版 console shell 同时服务 platform/admin
- 页面切换时不再出现两套后台管理视觉

## 验收

- `pnpm -C source/dts-platform-webapp build`
- `pnpm -C source/dts-admin-webapp build`
- 平台与管理端的正常业务页都能在新壳下渲染

## 风险

- 多页签、权限导航、后端菜单模式都依赖现有布局，需要保持兼容
