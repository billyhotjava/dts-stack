# Platform 承载 Analytics 菜单与路由统一设计

**日期**: 2026-03-30  
**状态**: READY  
**范围**: `source/dts-platform-webapp` / `source/dts-admin` / `source/dts-analytics-webapp/modern`

## 背景

当前 `dts-platform-webapp` 已经尝试把 `analytics` 页面整体迁入主应用，但运行态仍保留两套导航系统：

- `dts-platform-webapp` 继续使用平台菜单树、权限裁剪、收藏、最近访问、多标签与工作台外壳；
- 迁入的 `src/analytics/**` 又保留了独立 `AppLayout`、独立侧边栏、独立路径假设和一批旧的绝对路径跳转；
- `dts-admin` 种子菜单虽然已开始补 BI 菜单，但前端实际导航主导权仍不统一。

这导致当前系统出现以下结构性问题：

- 登录后默认跳转与真实可达路由不一致；
- 页面内部仍存在大量 `/questions`、`/screens`、`/data` 这类旧根路径跳转；
- 公开分享页被错误纳入登录守卫；
- BI 菜单没有真正纳入 `dts-admin` 统一管理。

## 目标

将 `dts-platform-webapp` 作为唯一前端壳，BI 功能作为普通业务模块并入平台导航体系，实现：

- 平台左侧菜单成为唯一导航真源；
- `dts-admin` 成为 BI 菜单定义、排序、显隐和权限配置的唯一管理端；
- `analytics` 仅保留页面、组件、API 与业务能力，不再拥有应用级菜单和外壳；
- 收藏、最近访问、多标签、面包屑、默认首页与权限裁剪全部复用平台现有能力。

## 非目标

- 本次不重写 `analytics` 业务页面本身的功能逻辑；
- 本次不调整 `dts-analytics` 后端 API 语义；
- 本次不重构公开分享页的后端授权模型，只修正前端路由归属；
- 本次不要求一次性删除所有 `/analytics/*` 路径，兼容跳转层允许短期存在。

## 方案对比

### 方案 A: 保留 analytics 独立布局，仅共享菜单数据源

优点：
- 表面改动较小；
- 可以复用现有 `src/analytics/layouts/AppLayout.tsx`。

缺点：
- 实际仍是双导航系统；
- 收藏、最近访问、多标签与面包屑仍然割裂；
- 页面内部旧路径问题不会自然消失。

### 方案 B: 推荐方案，Platform 成为唯一应用壳，Analytics 页面并入平台菜单体系

优点：
- 与“一个应用、一套菜单、一套权限”的目标完全一致；
- `dts-admin` 可以统一管理 BI 菜单；
- 长期维护成本最低。

缺点：
- 需要系统清理 analytics 页面内部路径与布局假设；
- 需要一次规范化 canonical route。

### 方案 C: 继续以 `/analytics/*` 为正式主路径，仅做更多兼容修补

优点：
- 短期迁移阻力最低。

缺点：
- 双系统并存的问题不会消失；
- 后续每次菜单、权限、收藏、默认首页都会重复踩坑。

## 采用方案

采用 **方案 B**。

## 架构设计

### 1. 应用壳职责

- `dts-platform-webapp` 是唯一前端应用壳；
- 平台主布局负责左侧导航、顶部区域、多标签、面包屑、收藏、最近访问；
- `src/analytics/layouts/AppLayout.tsx` 不再承担应用级导航职责，最终退役或降级为局部布局组件。

### 2. 菜单管理职责

- `dts-admin` 是菜单定义与权限配置的唯一真源；
- BI 菜单节点通过种子菜单与菜单管理界面维护；
- `dts-platform` / `dts-platform-webapp` 只消费菜单树，不再硬编码 BI 一级导航。

### 3. 路由职责

- BI 页面拥有新的 canonical route，统一纳入平台业务路由体系；
- 旧 `/analytics/*` 仅保留兼容跳转层，不再作为正式菜单路径；
- 公开分享页保持独立公共路由，不进入业务菜单。

## 路由设计

### Canonical Routes

建议将 BI 正式路径统一到 `/dashboard/bi/*`，例如：

- `/dashboard/bi/home`
- `/dashboard/bi/questions`
- `/dashboard/bi/dashboards`
- `/dashboard/bi/data`
- `/dashboard/bi/models`
- `/dashboard/bi/metrics`
- `/dashboard/bi/screens`
- `/dashboard/bi/project-cockpit`
- `/dashboard/bi/search`

### Compatibility Routes

短期保留：

- `/analytics` -> `/dashboard/bi/home`
- `/analytics/questions/*` -> `/dashboard/bi/questions/*`
- `/analytics/dashboards/*` -> `/dashboard/bi/dashboards/*`
- `/analytics/screens/*` -> `/dashboard/bi/screens/*`

兼容层只做 redirect，不再承载正式导航逻辑。

### Public Routes

公开分享页单独保留，不进入平台登录壳：

- `/public/card/:uuid`
- `/public/dashboard/:uuid`
- `/public/screen/:uuid`

这三类路由必须从 `LoginAuthGuard` 下剥离。

## 菜单与权限设计

### dts-admin

在 `dts-admin` 中新增并统一维护 BI 菜单，例如：

- `BI分析`
- `BI分析 / BI首页`
- `BI分析 / 分析卡片`
- `BI分析 / 分析看板`
- `BI分析 / 数据管理`
- `BI分析 / 数据模型`
- `BI分析 / 指标管理`
- `BI分析 / 大屏管理`
- `BI分析 / 项目看板`
- `BI分析 / 搜索`

每个节点绑定 canonical route，并使用既有菜单权限模型分配给角色。

### dts-platform-webapp

- 平台左侧导航直接渲染这些 BI 菜单；
- BI 页面作为普通菜单页面进入 keep-alive、多标签、收藏与最近访问体系；
- 模块入口权限由平台菜单树控制；
- Screen/Card/Dashboard 等对象级权限继续由 analytics 业务内权限控制。

## 迁移步骤

### P0: 止血

- 取消“登录默认跳转 `/analytics`”的硬编码；
- 停止继续扩展 analytics 独立应用壳；
- 明确 `dts-platform-webapp` 为唯一主入口。

### P1: 建立 canonical route

- 在平台路由层注册 `/dashboard/bi/*`；
- 建立 `/analytics/*` 到 canonical route 的兼容跳转。

### P2: 下线 analytics 应用壳

- `src/routes/sections/analytics.tsx` 由“独立应用入口”改为“普通页面路由集合”；
- 去掉 analytics 独立侧边栏与顶栏。

### P3: 清理页面内部路径

- 系统收口 `src/analytics/**` 中的旧绝对路径；
- 引入统一 route helper，禁止页面内继续手写旧根路径。

### P4: 菜单种子与权限并入

- 在 `dts-admin` 中补齐 BI 菜单树；
- 平台导航、默认首页、收藏与最近访问正式切换到 BI 菜单节点。

### P5: 清理旧入口与兼容代码

- 删除无用的 analytics 应用壳职责；
- 清理不再需要的 dev proxy / runtime compatibility 逻辑；
- 保留必要的 redirect compatibility，待后续版本再收缩。

## 风险

- 页面内部残留的旧路径数量较多，若不集中治理会持续制造回归；
- 公开分享页若仍误挂在登录壳下，会直接破坏外部访问；
- 若 `dts-admin` 菜单种子与前端 canonical route 不同步，平台菜单会出现死链；
- 合并后前端包体会变大，需要配合后续 chunk 拆分优化。

## 验证原则

- 登录后默认进入的平台首页必须由平台配置控制，而非前端硬编码；
- BI 页面必须能从平台左侧菜单进入；
- 收藏、最近访问、多标签对 BI 页面行为一致；
- `public card/dashboard/screen` 公开链接在匿名场景下仍可访问；
- `/analytics/*` 旧路径仅执行跳转，不再承载独立导航壳。
