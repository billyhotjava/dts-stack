# 工作台首页收敛与个人定制设计

日期：2026-06-16  
范围：`dts-platform-webapp`、`dts-platform`、`dts-admin` 菜单配置  
状态：设计待评审

## 背景

当前平台存在多个“首页/工作台”概念：

- `/workbench`：平台默认工作台，现为领导视角概览，包含 KPI、常用大屏、核心资产等。
- `/workbench/data-management`：数据管理工作台，承接数据源、黄金链路、治理、资产、BI、API、运维等数据中台主链路。
- `/services/consumption`：旧业务消费工作台入口，目前兼容到数据管理工作台。

这些入口在产品心智上重复。客户又提出“可以定制首页放哪些元素”的需求，因此应收敛为一个唯一工作台首页，并把原数据管理能力拆成首页可选组件。

## 能力目标

登录用户可以在唯一工作台首页中勾选自己关注的组件，并调整显示顺序。系统按用户保存个人配置；没有个人配置时使用角色默认模板。首页只渲染已有真实页面和真实接口对应的组件，不内置客户业务场景，不显示 demo 数字。

## 固定约束

1. `/workbench` 是唯一工作台首页。
2. `/workbench/data-management` 和 `/services/consumption` 不再作为独立首页，只保留兼容入口。
3. 定制方式为 checkbox 勾选组件，加“上移/下移”调整顺序。
4. 不做拖拽布局、不做自由栅格、不做复杂页面渲染器。
5. 必须兼容 Chrome 95。
6. 首页组件必须来自系统预定义注册表，用户不能创建任意组件。
7. 用户只能选择自己有权限访问的组件。
8. 个人配置以服务端为权威，localStorage 只可作为短期缓存。
9. 无真实数据时显示空态、错误态或禁用原因，不允许静态假数据。

## 路由设计

| 路由 | 目标行为 |
|------|----------|
| `/workbench` | 唯一工作台首页，按个人配置渲染组件 |
| `/workbench?customize=1` | 打开首页，同时展开“自定义工作台”抽屉 |
| `/workbench?section=data-management` | 首页滚动或定位到数据管理相关组件 |
| `/workbench/data-management` | 兼容跳转到 `/workbench?section=data-management` |
| `/services/consumption` | 兼容跳转到 `/workbench?section=consumption` |
| `/workbench/todo` | 保留独立待办中心，首页组件可链接过去 |

菜单层只保留“工作台”和“待办事项”。“数据管理工作台”菜单项应移除或隐藏，但兼容路由保留，避免旧链接断开。

## 组件注册表

首页组件必须先进入注册表。注册表定义组件 key、名称、说明、权限依赖、数据来源、默认角色和渲染组件。

第一版注册组件如下：

| key | 名称 | 数据来源/目标 | 默认角色 |
|-----|------|---------------|----------|
| `leader-kpi` | 概览指标 | `/workbench/leader-overview` | 部门负责人、所领导 |
| `top-reports` | 常用大屏 | 现有 `TopReportsBlock` | 所有人 |
| `core-assets` | 核心资产 | 现有 `CoreAssetsBlock` | 所有人 |
| `screen-strip` | 已发布大屏 | 现有 `ScreenStrip` | 所有人 |
| `todo` | 待办事项 | `/workbench/todo` 或待办摘要接口 | 所有人 |
| `data-sources` | 数据源接入 | `/foundation/data-sources` 入口和摘要 | 数据管理员 |
| `golden-chain` | 数据交付链路 | `/api/golden-chains` | 数据管理员 |
| `governance-blockers` | 治理阻断 | 治理质量/发布门禁摘要 | 数据管理员、治理人员 |
| `bi-delivery` | BI 与大屏成果 | BI 看板/大屏入口和摘要 | 所有人 |
| `api-services` | 数据 API 服务 | `/services/apis` 入口和摘要 | 数据管理员、服务管理员 |
| `ops-health` | 运行健康 | `/ops/overview` 入口和摘要 | 运维人员、数据管理员 |

组件注册表不承诺每个组件第一版都有复杂摘要。没有稳定摘要接口时，组件应先渲染“入口型卡片 + 空态/不可用原因”，不能编造统计值。

## 个人配置数据模型

服务端保存每个用户一份工作台配置：

```json
{
  "username": "zhangsan",
  "version": 1,
  "items": [
    { "key": "todo", "visible": true, "order": 10 },
    { "key": "golden-chain", "visible": true, "order": 20 },
    { "key": "core-assets", "visible": true, "order": 30 }
  ],
  "updatedAt": "2026-06-16T00:00:00Z"
}
```

后端返回配置时需要过滤掉当前用户无权限访问的组件。前端保存时也要把不可选组件禁用或隐藏，但最终权限判断以后端为准。

## API 契约

建议新增平台侧接口：

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/workbench/preferences` | 获取当前登录用户的工作台配置；无配置时返回角色默认模板 |
| `PUT` | `/api/workbench/preferences` | 保存当前登录用户的勾选和顺序 |
| `POST` | `/api/workbench/preferences/reset` | 恢复角色默认模板 |

返回结构：

```json
{
  "version": 1,
  "availableComponents": [
    {
      "key": "todo",
      "title": "待办事项",
      "description": "查看审批、阻断和异常处理项",
      "enabled": true,
      "disabledReason": null
    }
  ],
  "items": [
    { "key": "todo", "visible": true, "order": 10 }
  ]
}
```

`availableComponents` 是当前用户可见组件目录，`items` 是用户选择结果。保存时前端只提交 `items`。

## 前端交互

工作台首页顶部保留简洁操作：

- “自定义工作台”：打开右侧抽屉。
- “恢复默认”：可放在抽屉底部，二次确认后恢复角色默认模板。

自定义抽屉内容：

- 每行一个组件。
- 左侧 checkbox 控制显示。
- 中间展示组件名称和说明。
- 右侧两个按钮：“上移”“下移”。
- 不使用拖拽。
- 未授权组件不展示；暂不可用组件可展示但 checkbox disabled，并说明原因。
- 保存成功后首页立即按新顺序渲染。

首页渲染：

- 使用稳定的纵向 section 列表。
- 大屏宽度下允许简单两列，但每个组件仍是固定组件，不做用户自由尺寸配置。
- 空配置时显示角色默认模板。
- 用户取消全部组件时显示空态，并提供“自定义工作台”和“恢复默认”。

## 现有页面收敛

`LeaderOverviewPage` 应从“固定领导首页”演进为“可配置工作台容器”。现有 `KpiRow`、`TopReportsBlock`、`CoreAssetsBlock`、`ScreenStrip` 变成注册组件。

`DataManagementWorkbenchPage` 不再作为独立首页继续扩展。它的能力拆为注册组件：

- `data-sources`
- `golden-chain`
- `governance-blockers`
- `bi-delivery`
- `api-services`
- `ops-health`

兼容路由只负责把旧入口带回 `/workbench`，并定位到相关组件。

## 后端与持久化

平台侧新增用户偏好表，建议命名为 `workbench_user_preference`：

| 字段 | 说明 |
|------|------|
| `id` | 主键 |
| `username` | 登录账号 |
| `version` | 配置版本 |
| `layout_json` | JSON 配置 |
| `created_at` | 创建时间 |
| `updated_at` | 更新时间 |

唯一约束：`username`。如果后续同一账号需要按租户/组织隔离，可扩展 `tenant_id` 或 `org_code`，第一版不引入多维配置。

## 权限与安全

- 读写偏好必须绑定当前登录用户，不能通过请求体指定其他用户。
- 后端保存前校验组件 key 是否在注册表内。
- 后端返回前按菜单权限、角色和组件权限过滤。
- 操作记录写入普通审计日志，至少记录保存和恢复默认。
- 前端不能依赖隐藏按钮作为权限控制。

## Chrome 95 兼容

第一版避免以下能力：

- HTML5 拖拽库。
- `structuredClone` 等新 API。
- CSS container query。
- Masonry/grid 自由布局。
- 复杂动画和依赖新语法的浏览器特性。

顺序调整使用普通按钮和数组重排；布局使用现有 Ant Design 组件、简单 flex/grid 和断点。

## 测试与验收

必须覆盖：

1. `/workbench` 是唯一首页入口。
2. `/workbench/data-management` 和 `/services/consumption` 兼容跳转到 `/workbench`。
3. 用户可以勾选组件并保存。
4. 用户可以上移/下移调整顺序。
5. 刷新后从服务端恢复个人顺序。
6. 恢复默认后回到角色模板。
7. 无权限组件不会出现在可选列表中。
8. 后端拒绝未知组件 key。
9. 无数据组件显示空态或不可用原因，不显示 demo 值。
10. Chrome 95 目标构建通过，Playwright smoke 验证工作台可渲染。

## 非目标

- 不做拖拽门户。
- 不做用户自定义组件开发。
- 不做自由栅格、自由尺寸和复杂布局保存。
- 不重写 BI、资产、治理、运维页面。
- 不为了首页配置新增假统计接口。
- 不把客户业务主题内置到产品。

## 实施切分建议

建议作为新的 UI 收敛 sprint 执行：

1. 契约与路由收敛：唯一首页、兼容入口、菜单隐藏重复入口。
2. 后端偏好 API：用户偏好表、接口、权限过滤、默认模板。
3. 前端工作台容器：组件注册表、个人配置加载、顺序渲染。
4. 自定义抽屉：checkbox、上移/下移、保存、恢复默认。
5. 数据管理组件拆分：把原数据管理工作台能力拆为可选组件。
6. 验收与兼容：契约测试、构建、Chrome/Playwright smoke、文档更新。

## 设计自检

- 无占位需求：所有第一版行为均已明确。
- 无内部矛盾：唯一首页、兼容入口、个人配置和 Chrome 95 约束一致。
- 范围可控：第一版只做勾选与顺序，不做拖拽门户。
- 风险明确：旧入口需要兼容跳转，组件数据必须来自真实接口或明确空态。
