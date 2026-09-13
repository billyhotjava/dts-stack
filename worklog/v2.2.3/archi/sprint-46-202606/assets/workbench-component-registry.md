# 工作台组件注册表

## 设计原则

工作台首页组件必须来自系统注册表。注册表是前端渲染、后端权限过滤、默认模板和测试契约的共同来源。

## 第一版组件

| key | 名称 | 类型 | 来源 | 默认角色 | 主按钮 |
|-----|------|------|------|----------|--------|
| `leader-kpi` | 概览指标 | 数据摘要 | `/workbench/leader-overview` | 部门负责人、所领导 | 查看工作台 |
| `top-reports` | 常用大屏 | 列表 | `TopReportsBlock` | 所有人 | 查看大屏 |
| `core-assets` | 核心资产 | 列表 | `CoreAssetsBlock` | 所有人 | 查看资产 |
| `screen-strip` | 已发布大屏 | 横向列表 | `ScreenStrip` | 所有人 | 查看全部 |
| `todo` | 待办事项 | 入口/摘要 | `/workbench/todo` | 所有人 | 处理待办 |
| `data-sources` | 数据源接入 | 入口/摘要 | `/foundation/data-sources` | 数据管理员 | 配置数据源 |
| `golden-chain` | 数据交付链路 | 状态/入口 | `/api/golden-chains` | 数据管理员 | 查看链路 |
| `governance-blockers` | 治理阻断 | 入口/摘要 | 治理质量/发布门禁 | 数据管理员、治理人员 | 处理阻断 |
| `bi-delivery` | BI 与大屏成果 | 入口/摘要 | `/bi/screens`, `/bi/dashboards` | 所有人 | 查看成果 |
| `api-services` | 数据 API 服务 | 入口/摘要 | `/services/apis` | 数据管理员、服务管理员 | 发布 API |
| `ops-health` | 运行健康 | 入口/摘要 | `/ops/overview` | 运维人员、数据管理员 | 查看运行 |

## 组件契约

每个组件至少包含：

- `key`: 稳定唯一标识。
- `title`: 客户可读名称。
- `description`: 客户可读说明。
- `defaultVisibleFor`: 默认可见角色。
- `requiredRoutes`: 用于权限过滤的菜单路径。
- `render`: 前端渲染组件。
- `emptyState`: 无数据时的空态文案。
- `primaryAction`: 主按钮文案和目标路由。

## Chrome 95 约束

- 不依赖拖拽库。
- 不依赖 `structuredClone`。
- 不使用 CSS container query。
- 不使用 masonry/free layout。
- 只使用稳定的列表、按钮、checkbox 和简单响应式布局。
