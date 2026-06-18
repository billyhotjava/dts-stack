# F3: 工作台入口闭环

**优先级**: P0
**状态**: DONE

## 目标

在唯一工作台策略下复核首页组件、配置入口和兼容跳转，保证登录用户看到的是可定制、可理解、可落地的工作台。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 首页组件链接闭环复核 | P0 | DONE | F1 |
| T02 | 工作台配置降级状态复核 | P0 | DONE | T01 |

## 完成说明

- 工作台组件注册表中的 7 个首页动作均有真实路由落点：待办、BI 成果、数据源、数据交付链路、治理、数据 API、运行健康。
- 首页组件卡片和按钮补充稳定 `data-testid`，浏览器回归可逐项定位，不依赖文案模糊匹配。
- 自定义工作台抽屉保持复选框 + 上移/下移方案，不引入拖拽和复杂布局渲染，兼容 Chrome 95。
- 默认关闭个人工作台后端偏好 API 时，页面走本地偏好降级，不请求 `/api/workbench/preferences`。

## 验收证据

- Source contract：`src/pages/workbench/Sprint49WorkbenchEntryFlow.source-contract.test.ts`。
- Chrome smoke：`worklog/v2.2.3/sprint-49-202606/it/scripts/workbench-entry-smoke.mjs`。
- 截图：
  - `worklog/v2.2.3/sprint-49-202606/it/evidence/workbench-home-customize-1366x768.png`
  - `worklog/v2.2.3/sprint-49-202606/it/evidence/workbench-data-management-entry-1366x768.png`
