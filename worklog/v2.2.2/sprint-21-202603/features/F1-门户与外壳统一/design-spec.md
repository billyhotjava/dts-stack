# 统一产品入口与UI框架 — 设计规格

> 本文档覆盖 Sprint-21 全部 3 个 Feature 的设计决策。

## 1. 问题陈述

DTS 平台面向非专业业务人员使用，但当前：
1. 登录后默认进入 platform-webapp（专业版），BI 分析需要多步跳转
2. analytics-webapp 使用自定义 UI 组件库，platform-webapp 使用 antd，维护两套成本高
3. 两个 app 的 Header/侧边栏风格不一致，用户体验割裂

## 2. 目标

- 登录后通过门户页引导用户选择"BI分析"或"大数据平台(专业版)"
- 支持记住选择，回头用户自动跳转
- analytics-webapp 全面迁移到 antd，消除双 UI 框架
- 统一两个 app 的外壳（Header + 侧边导航）

## 3. 非目标

- 不做权限控制（数据资产已有权限管理）
- 不合并两个 app 为单一 SPA
- 不改变两个 app 的路由体系（`/` 和 `/analytics`）
- 不迁移 platform-webapp 的 MenuStore 到 analytics

## 4. 架构概览

### 4.1 当前架构

```
Traefik
  ├─ /analytics/* → analytics-webapp:3002 (自定义UI, React 18)
  └─ /* → platform-webapp:3001 (antd, React 19)

登录 → /dashboard/workbench (platform)
```

### 4.2 目标架构

```
Traefik (不变)
  ├─ /analytics/* → analytics-webapp:3002 (antd, React 18)
  └─ /* → platform-webapp:3001 (antd, React 19)

登录 → /portal → 用户选择 → /analytics 或 /dashboard/workbench
```

### 4.3 数据流

```
登录成功
  → navigate("/portal")
  → PortalPage 检查 localStorage("dts.portal.preferredApp")
     ├─ 有值 → 自动跳转到记住的 app
     └─ 无值 → 显示双卡片
          ├─ 选"BI分析" [+ 记住] → window.location.href = "/analytics"
          └─ 选"专业版" [+ 记住] → navigate("/dashboard/workbench")
```

## 5. F1: 门户与外壳统一

### 5.1 门户选择页（PortalPage）

**位置**: platform-webapp 内新路由 `/portal`

**UI 结构**:
```
┌──────────────────────────────────────────────┐
│              DTS 数据平台 Logo                │
│                                              │
│    ┌─────────────┐    ┌─────────────┐        │
│    │   📊 图标    │    │   🔧 图标    │        │
│    │             │    │             │        │
│    │  BI 分析    │    │ 大数据平台   │        │
│    │  智能分析    │    │  (专业版)   │        │
│    │  轻松洞察    │    │ 数据全链路  │        │
│    │  数据价值    │    │ 专业管控    │        │
│    └─────────────┘    └─────────────┘        │
│                                              │
│         □ 记住我的选择，下次直接进入           │
│                                              │
│              欢迎，{用户名}                    │
└──────────────────────────────────────────────┘
```

**记住选择**:
- localStorage key: `dts.portal.preferredApp`
- 值: `"analytics"` | `"platform"` | `null`
- 门户页 `useEffect` 检查：有值自动跳转，无值显示 UI
- 勾选"记住选择"才存值

**重置记住选择**:
- 两个 app 的"切换应用"按钮点击时，清除 `dts.portal.preferredApp` 后再跳转
- 这样用户点切换按钮 → 回到门户页 → 可以重新选择（而非直接跳到另一个 app）
- 如果用户在门户页再次勾选"记住选择"，则更新记住的值

**路由变更**:
- `portal-navigation.ts` 的 `DEFAULT_PORTAL_ROUTE` 常量从 `/dashboard/workbench` 改为 `/portal`（该常量被 `global-config.ts` 的 `GLOBAL_CONFIG.defaultRoute` 引用）
- 同时修改所有 fallback 硬编码位置（`login-form.tsx`、`Page404.tsx`、`dynamic-resolver.tsx`、`dashboard/index.tsx`、`use-tab-operations.ts`、`workbench/index.tsx` 等）中的 `|| "/dashboard/workbench"` 为 `|| "/portal"`
- PortalPage 包在 `LoginAuthGuard` 内（需认证）

### 5.2 analytics-webapp 引入 antd

- 安装 `antd` v5.x + `@ant-design/icons`（与 platform-webapp 同版本）
- `main.tsx` 包裹 `<ConfigProvider locale={zhCN} theme={...}>`
- 主题 token 与 platform-webapp 保持一致

### 5.3 统一 Header

**统一结构**:
```
┌─[Logo]──[应用名]────────────────────[切换⊞]──[用户头像▾]─┐
```

**analytics-webapp Header 改造**:
- 替换为 antd `Layout.Header`
- 右侧新增切换按钮 + 用户头像下拉
- 移除 ThemeToggle

**platform-webapp Header 改造**:
- 右侧添加切换应用按钮（`AppstoreOutlined` 图标）

**切换按钮行为**:
- 点击时先清除 `localStorage.removeItem("dts.portal.preferredApp")`
- 然后跳��到门户页: `window.location.href = "/portal"`
- ���户在门户页重新选择目标 app
- Tooltip: "切换应用"

**注意**: 数据流的不对称性是有意设计 — 选"BI分析"使用 `window.location.href`（跨 app 全页跳转），选"专业版"使用 `navigate()`（platform-webapp 内部 SPA 路由），因为门户页本身在 platform-webapp 内。

## 6. F2: 业务组件 antd 迁移

### 6.1 组件映射

| 自定义 UI | antd 替代 | 引用数 | 注意事项 |
|-----------|----------|--------|----------|
| ui/Card/Card | antd Card | ~32 | - |
| ui/Badge/Badge | antd Badge | ~29 | - |
| ui/Loading/Spinner | antd Spin | ~31 | 用绝对定位覆盖，不破坏高度链 |
| ui/Button/Button | antd Button | ~26 | - |
| ui/Modal/Modal | antd Modal | ~17 | isOpen → open |
| ui/Input/Input | antd Input | ~17 | - |
| ui/Input/Select | antd Select | ~9 | options 格式适配 |
| ui/Loading/Skeleton | antd Skeleton | ~3 | - |
| ui/Tabs/Tabs | antd Tabs | ~2 | - |
| ui/Dropdown/Dropdown | antd Dropdown | ~1 | - |
| ui/Drawer/Drawer | antd Drawer | ~1 | - |
| ui/Input/Checkbox | antd Checkbox | ~1 | - |
| ui/Tooltip | 未使用，直接删除 | 0 | - |
| ui/ThemeToggle | 移除 | ~1 | 统一亮色主题 |

### 6.2 迁移策略

- 逐组件迁移，不做中间适配层
- 按引用数从高到低顺序
- 每个组件替换完所有引用后删除原文件
- 最后删除整个 `ui/` 目录

## 7. F3: 侧边导航统一

### 7.1 改造方案

将 analytics-webapp 的自定义侧边栏替换为 antd `Layout.Sider` + `Menu`。

菜单项保持硬编码（analytics 功能固定，不需要动态 MenuStore）：

```
核心功能 (nav.section.core)
  ├ 首页           /
  ├ 智能分析       /analyze
  ├ 问答           /questions
  ├ 仪表盘         /dashboards
  └ 收藏集         /collections
─────────
数据 (nav.section.data)
  ├ 数据源         /data
  ├ 模型           /models
  └ 指标           /metrics
─────────
工具 (nav.section.tools)
  ├ 大屏设计       /screens
  ├ 项目看板       /project-cockpit
  └ 搜索           /search
```

### 7.2 布局特性

- antd `Sider` `collapsible` 支持折叠/展开
- 折叠时只显示图标（Mini 模式）
- 图标使用 `@ant-design/icons`

## 8. 约束与风险

| 约束/风险 | 应对 |
|-----------|------|
| 离线环境无 CDN | antd 完整打包到 bundle，不使用 CDN |
| Chrome 95 兼容 | antd v5 支持 Chrome ≥ 64 |
| React 18 vs 19 | antd v5 兼容 React 16-19 |
| Bundle 体积增加 | 内网应用可接受，antd 增量 ~200-300KB gzipped |
| Spin 高度链问题 | 使用绝对定位覆盖方式 |
| Session 跨 app | 已有 platformSession.ts 心跳机制，不受影响 |

## 9. 影响范围

### 新文件
- `platform-webapp/src/pages/portal/PortalPage.tsx`
- `platform-webapp/src/pages/portal/` 相关样式

### 修改文件
- `platform-webapp/src/global-config.ts` — 默认路由改为 `/portal`
- `platform-webapp/src/routes/sections/` — 添加 /portal 路由
- `platform-webapp/src/pages/sys/login/login-form.tsx` — fallback 路由
- `platform-webapp/src/pages/sys/Page404.tsx` — fallback 路由
- `platform-webapp/src/routes/components/dynamic-resolver.tsx` — fallback 路由
- `platform-webapp/src/routes/sections/dashboard/index.tsx` — fallback 路由
- `platform-webapp/src/layouts/dashboard/use-tab-operations.ts` — fallback 路由
- `platform-webapp/src/pages/workbench/index.tsx` — fallback 路由
- `platform-webapp/src/constants/portal-navigation.ts` — `DEFAULT_PORTAL_ROUTE` 常量（被 global-config.ts 引用）
- `platform-webapp/src/layouts/dashboard/` — Header 添加切换按钮
- `analytics-webapp/modern/package.json` — 添加 antd 依赖
- `analytics-webapp/modern/src/main.tsx` — ConfigProvider
- `analytics-webapp/modern/src/layouts/AppLayout.tsx` — Header + Sider 重构
- `analytics-webapp/modern/src/pages/**/*.tsx` — ~60 文件替换 UI 组件
- `analytics-webapp/modern/src/ui/` — 最终删除

### 不变
- 两个 app 的路由体系（`/` 和 `/analytics`）
- Traefik 配置
- 后端（零改动）
- 认证流程
