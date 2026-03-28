# F3: 侧边导航统一

**优先级**: P1
**状态**: DONE
**依赖**: F1（antd 已引入，Header 已统一）

## 目标

将 analytics-webapp 的侧边导航重构为 antd Menu 组件，视觉风格与 platform-webapp 一致。

## 技术设计

### 当前状态

| 方面 | analytics-webapp | platform-webapp |
|------|------------------|-----------------|
| 菜单配置 | 硬编码在 AppLayout.tsx | 动态 MenuStore |
| UI 组件 | 自定义 div + CSS | antd Menu |
| 布局 | 单一布局 | Mini/Vertical/Horizontal |
| 分组 | 3 个 section | 5 个 group |

### 改造方案

**analytics-webapp 侧边栏重构为 antd `Layout.Sider` + antd `Menu`**

菜单结构保持不变（硬编码，analytics 功能固定）：

```
核心功能
  ├ 首页
  ├ 智能分析
  ├ 问答
  ├ 仪表盘
  └ 收藏集
─────────
数据
  ├ 数据源
  ├ 模型
  └ 指标
─────────
工具
  ├ 大屏设计
  ├ 项目看板
  └ 搜索
```

**布局支持**:
- 折叠/展开（antd Sider `collapsible`）
- 折叠时只显示图标（与 platform Mini 模式对应）

**图标**:
- 统一使用 `@ant-design/icons`
- 如需自定义图标，使用 platform 的 `local:` SVG 图标系统

### 不做的事

- 不合并两个 app 的菜单
- 不接入 platform 的动态 MenuStore
- 不做权限过滤

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | AppLayout 侧边栏替换为 antd Sider + Menu | P1 | DONE | F1/T03 |
| T02 | 菜单图标替换为 antd icons | P1 | DONE | T01 |
| T03 | 折叠/展开功能 | P1 | DONE | T01 |

## 完成标准
- [ ] analytics-webapp 侧边导航使用 antd Menu
- [ ] 视觉风格与 platform-webapp 一致
- [ ] 支持折叠/展开
- [ ] 路由跳转正常
