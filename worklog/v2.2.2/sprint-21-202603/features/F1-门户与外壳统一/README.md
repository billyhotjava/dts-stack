# F1: 门户与外壳统一

**优先级**: P0
**状态**: READY

## 目标

新增门户选择页，引入 antd 到 analytics-webapp，统一两个 app 的 Header，添加切换应用按钮。

## 技术设计

### 1. 门户选择页（PortalPage）

**路由**: `/portal`（platform-webapp 内新路由）

**登录后路由变更**: `portal-navigation.ts` 中 `defaultRoute` 从 `/dashboard/workbench` 改为 `/portal`

**页面结构**:
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

**记住选择逻辑**:
- localStorage key: `dts.portal.preferredApp`
- 值: `"analytics"` | `"platform"` | `null`
- 勾选"记住选择" → 存值 → 跳转
- 未勾选 → 不存值 → 跳转
- 门户页加载时检查：有值则自动跳转，无值则显示选择

### 2. analytics-webapp 引入 antd

**依赖安装**:
- `antd` v5.x（与 platform-webapp 同版本）
- `@ant-design/icons`

**ConfigProvider 配置**:
- 在 `main.tsx` 或 `App` 层包裹 `<ConfigProvider>`
- 设置中文 locale（`zh_CN`）
- 设置与 platform-webapp 一致的主题 token

### 3. 统一 Header

**共享 Header 结构（两个 app 保持一致）**:
```
┌─[Logo]──[应用名]────────────────────[切换⊞]──[用户头像▾]─┐
```

**analytics-webapp Header 改造**:
- AppLayout.tsx 的 Header 替换为 antd `Layout.Header`
- 右侧新增：切换应用按钮 + 用户头像下拉

**platform-webapp Header 改造**:
- 在现有 Header 右侧区域添加"切换应用"按钮

**切换应用按钮行为**:
- 图标：antd `AppstoreOutlined`
- 在 platform 中点击 → `window.location.href = "/analytics"`
- 在 analytics 中点击 → `window.location.href = "/"`
- Tooltip: "切换到 BI 分析" / "切换到专业版"

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 门户选择页（PortalPage） | P0 | READY | - |
| T02 | 默认路由改为 /portal | P0 | READY | T01 |
| T03 | analytics-webapp 引入 antd + ConfigProvider | P0 | READY | - |
| T04 | analytics-webapp Header 重构（antd Layout.Header） | P0 | READY | T03 |
| T05 | platform-webapp Header 添加切换按钮 | P0 | READY | - |
| T06 | analytics-webapp Header 添加切换按钮 | P0 | READY | T04 |

## 完成标准
- [ ] 登录后进入门户选择页
- [ ] 记住选择后自动跳转
- [ ] 两个 app Header 风格一致
- [ ] 切换应用按钮正常工作
