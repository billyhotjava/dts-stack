# Sprint-46: 工作台首页收敛与个人定制

**时间**: 2026-06  
**状态**: DONE  
**类型**: UI Productization / Workbench Personalization / Backend Preference Contract（dts-platform-webapp + dts-platform + dts-admin menu seed）

## 目标

把“工作台”“数据管理工作台”“业务消费工作台”收敛为唯一 `/workbench` 首页，并允许每个登录用户通过简单勾选选择首页组件、通过“上移/下移”调整顺序。首页只展示真实页面和真实接口对应的组件，不内置客户业务场景，不做拖拽门户，兼容 Chrome 95。

## 背景

Sprint-45 已把页面链路串起来，但仍留下三个工作台心智：

- `/workbench`：平台默认首页，现为领导视角概览。
- `/workbench/data-management`：数据管理工作台，承接数据源、黄金链路、治理、资产、BI、API、运维。
- `/services/consumption`：旧业务消费工作台入口，兼容到数据管理工作台。

客户现场提出“首页放哪些元素可以定制”的需求。架构上应保留一个工作台首页，把数据管理能力拆成可选组件，由登录用户按需勾选。

## 设计来源

- 设计文档: `docs/superpowers/specs/2026-06-16-workbench-home-personalization-design.md`
- 上游 UI 收敛: `worklog/v2.2.3/sprint-45-202606/README.md`
- 历史工作台基础: `worklog/v2.2.3/sprint-15-202604/README.md`
- 数据管理主题看板: `worklog/v2.2.3/sprint-42-202606/README.md`

## Feature 列表

| ID | Feature | 优先级 | Task 数 | 状态 | 阶段目标 |
|----|---------|--------|---------|------|----------|
| F1 | 唯一工作台路由与菜单收敛 | P0 | 3 | DONE | `/workbench` 成为唯一首页，旧入口兼容不再重复展示 |
| F2 | 个人工作台偏好后端契约 | P0 | 4 | DONE | 服务端按登录用户保存勾选与顺序，并过滤权限 |
| F3 | 前端工作台容器与组件注册表 | P0 | 3 | DONE | 注册真实组件并按个人配置渲染 |
| F4 | 自定义工作台抽屉 | P0 | 3 | DONE | checkbox 勾选、上移/下移、保存、恢复默认 |
| F5 | 数据管理能力组件化迁移 | P0 | 4 | DONE | 数据管理能力进入唯一工作台 section 和组件入口 |
| F6 | 验收兼容与发布材料 | P0 | 3 | DONE | 契约、构建、Chrome/Playwright 和文档闭环 |

**统计**: READY=0, IN_PROGRESS=0, DONE=20, BLOCKED=0

## 实施摘要

- `/workbench/data-management` 和 `/services/consumption` 已兼容跳转到唯一工作台，不再作为独立首页渲染。
- `dts-platform` 新增 `workbench_user_preference` 持久化模型和 `/api/workbench/preferences` GET/PUT/reset API，按当前登录用户读写并拒绝未知组件 key。
- `/workbench` 新增个人化容器，读取用户偏好后按勾选结果渲染；`customize=1` 自动打开“自定义工作台”抽屉。
- 自定义抽屉使用 checkbox、上移、下移、保存、取消、恢复默认，未引入拖拽、自由栅格、`structuredClone` 或 container query。
- 数据管理能力通过 `section=data-management` 嵌入唯一工作台，并保留“待现场定义业务主题”空态，不内置客户业务场景。

## 验证证据

- 前端契约与模型: `node --test --experimental-strip-types src/pages/workbench/WorkbenchPersonalization.source-contract.test.ts src/pages/workbench/workbenchPersonalizationModel.test.ts src/pages/workbench/DataManagementWorkbenchPage.source-contract.test.ts src/pages/services/BusinessConsumptionPage.source-contract.test.ts src/pages/foundation/DataSourcesPage.sprint45-actions.source-contract.test.ts`
- 领导工作台回归: `pnpm exec vitest run src/pages/workbench/LeaderOverviewPage.test.tsx src/pages/workbench/LeaderOverviewPage.integration.test.tsx`
- 前端构建: `pnpm build`
- 后端偏好服务: `./mvnw -q -DskipITs -Dtest=WorkbenchPreferencesServiceTest test`
- 后端 REST IT: `./mvnw -q -DskipITs -Dtest=WorkbenchResourceIT test`
- Chrome/Playwright smoke:
  - `#/workbench` 渲染唯一工作台和自定义按钮。
  - `#/workbench?section=data-management&customize=1` 渲染数据管理 section 并自动打开抽屉。
  - `#/workbench/data-management` 跳转到 `#/workbench?section=data-management`。
  - `#/services/consumption` 跳转到 `#/workbench?section=consumption`。
  - 抽屉内点击“下移”后组件顺序实时变化。
- 限制说明: 本地 preview 未启动后端服务，Chrome 控制台出现 `/api/*` 代理 500/ECONNREFUSED；页面使用本地默认工作台配置和数据管理空态正常展示。

## 页面与路由规则

| 路由 | 目标行为 |
|------|----------|
| `/workbench` | 唯一工作台首页，按个人配置渲染组件 |
| `/workbench?customize=1` | 打开工作台并展开“自定义工作台”抽屉 |
| `/workbench?section=data-management` | 定位到数据管理相关组件 |
| `/workbench/data-management` | 兼容跳转到 `/workbench?section=data-management` |
| `/services/consumption` | 兼容跳转到 `/workbench?section=consumption` |
| `/workbench/todo` | 保留独立待办中心 |

## 完成标准

- [x] 菜单只暴露一个“工作台”首页入口，保留“待办事项”，不再并列展示“数据管理工作台”。
- [x] `/workbench/data-management` 和 `/services/consumption` 不 404、不白屏，兼容跳回 `/workbench`。
- [x] 每个登录用户可以勾选首页组件并调整顺序。
- [x] 用户配置保存到服务端，刷新和换浏览器后仍能恢复。
- [x] 无个人配置时按角色默认模板渲染。
- [x] 用户只能选择自己有权限访问的组件。
- [x] 不使用拖拽、自由栅格、复杂门户渲染器，兼容 Chrome 95。
- [x] 组件无真实数据时展示空态/错误态/不可用原因，不显示 demo 数字。
- [x] TDD 覆盖后端偏好 API、前端组件注册表、路由兼容、自定义抽屉行为。
- [x] `pnpm build`、后端单测、Playwright smoke 和 `git diff --check` 通过。

## 非目标

- 不做拖拽门户。
- 不做自由栅格、自由尺寸和低代码页面渲染。
- 不允许用户自定义开发组件。
- 不重写 BI、资产、治理、运维页面。
- 不为了首页配置新增假统计接口。
- 不内置客户业务主题或演示场景。

## 资产

- 组件注册表: `assets/workbench-component-registry.md`
- 按钮组件矩阵: `assets/workbench-ui-control-matrix.md`
- API 契约: `assets/workbench-preferences-api-contract.md`
- 实施计划: `assets/implementation-plan.md`
- IT 验收: `it/README.md`
