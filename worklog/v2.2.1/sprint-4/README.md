# Sprint-4: Platform 能力补全一期

## 目标

在 `customer/2.2.1` 分支上，针对 `dts-platform` 与 `dts-platform-webapp` 中 review 暴露出的明显缺口，完成一轮平台能力补全：

1. **Visualization 去占位** — 平台内提供真实的可视化入口与聚合数据，而不是 redirect shell + demo payload
2. **IAM 分类同步接线** — 打通后端同步能力、前端页面与失败重试闭环
3. **任务调度中心落地** — 把任务调度页从导航页升级为统一调度控制台
4. **API 服务试调用真实化** — 将 schema sample 替换为真实代理执行
5. **Workbench 首页实化** — 去掉模拟趋势，补齐角色化指标和真实历史
6. **页面信息架构收敛** — 清理 alias page / wrapper page，统一 platform 页面边界

设计文档：`docs/plans/2026-03-10-platform-gap-closure-design.md`

实施计划：`docs/plans/2026-03-10-platform-gap-closure-plan.md`

## 范围

### 后端 — dts-platform
- `web/rest` — visualization、IAM sync、scheduler、services、workbench 相关资源
- `service/visualization` — 可视化聚合服务
- `service/iam` — 分类同步与失败重试
- `service/scheduler` — 调度中心聚合服务
- `service/services` — API 真实试调用代理
- `service/workbench` — 首页指标与趋势聚合

### 前端 — dts-platform-webapp
- `src/pages/visualization/` — analytics / reports 页面重构
- `src/pages/security/` — 分类同步页面与相关入口
- `src/pages/foundation/` — 任务调度中心
- `src/pages/services/` — API 服务测试控制台
- `src/pages/workbench/` — 首页指标、趋势与待办视图
- `src/api/` — platform API 与页面 service 接线

### 自动化测试
- `tests/web-e2e/` — 为新增真实页面与关键交互补 Playwright 覆盖

## Task 列表

### 批次一：Visualization（VIS-001 ~ VIS-002）

| ID | 标题 | 类型 | 预估 | 状态 |
|----|------|------|------|------|
| VIS-001 | VisualizationResource 去硬编码，落真实聚合服务 | 后端 | 2天 | |
| VIS-002 | AnalyticsPage / ReportsPage 变成真实平台入口并补稳定 selector | 前端 | 2天 | |

### 批次二：IAM 分类同步（IAM-001 ~ IAM-002）

| ID | 标题 | 类型 | 预估 | 状态 |
|----|------|------|------|------|
| IAM-001 | ClassificationService 接 REST，补真实同步状态/失败重试闭环 | 后端 | 2天 | |
| IAM-002 | 去掉 iamService 前端占位实现，补分类同步页面与操作流 | 前端 | 2天 | |

### 批次三：任务调度中心（SCH-001 ~ SCH-002）

| ID | 标题 | 类型 | 预估 | 状态 |
|----|------|------|------|------|
| SCH-001 | 统一调度控制台后端聚合接口（overview/runs/actions） | 后端 | 2天 | |
| SCH-002 | TaskSchedulingPage 从导航壳页升级为调度控制台 | 前端 | 2天 | |

### 批次四：API 服务试调用（SVC-001 ~ SVC-002）

| ID | 标题 | 类型 | 预估 | 状态 |
|----|------|------|------|------|
| SVC-001 | ApiCatalogService 引入真实 tryInvoke 代理执行链路 | 后端 | 2天 | |
| SVC-002 | API 服务页补请求参数、请求头、响应体、策略命中展示 | 前端 | 1.5天 | |

### 批次五：Workbench 与页面边界收敛（WB-001 ~ IA-001）

| ID | 标题 | 类型 | 预估 | 状态 |
|----|------|------|------|------|
| WB-001 | WorkbenchService 输出真实趋势与角色化摘要 | 后端 | 1.5天 | |
| WB-002 | 工作台首页去模拟趋势，补真实图表与角色卡片 | 前端 | 1.5天 | |
| IA-001 | 收敛 alias/wrapper page，统一 reports / quality / analytics 页面边界 | 前端 | 1天 | |

## Task 细化

各 Task 的开发说明、交付与验收标准见：

- `tasks/VIS-001-visualization-real-data-backend.md`
- `tasks/VIS-002-visualization-pages-and-selectors.md`
- `tasks/IAM-001-classification-sync-rest-and-runtime.md`
- `tasks/IAM-002-classification-sync-ui-rewire.md`
- `tasks/SCH-001-scheduler-console-backend.md`
- `tasks/SCH-002-task-scheduling-console-frontend.md`
- `tasks/SVC-001-real-api-try-invoke-proxy.md`
- `tasks/SVC-002-api-service-test-console-ui.md`
- `tasks/WB-001-workbench-real-metrics-backend.md`
- `tasks/WB-002-workbench-real-trends-frontend.md`
- `tasks/IA-001-page-taxonomy-cleanup.md`

## 本 Sprint 不做

- 不在本期引入新的 BI 创作引擎或图表设计器
- 不重做 analytics 独立应用本身，只处理 platform 内入口与聚合
- 不实现完整 IAM 策略引擎重构，只先打通分类同步与运维闭环
- 不替换现有调度引擎，只做平台层调度控制台
- 不做 repo 全量页面视觉重构
- 不做 CI 流水线接线，自动化测试先以本地/环境可执行为目标

## 集成测试

`it/` 目录存放本 Sprint 的验证入口说明，覆盖：

- Visualization：平台入口页加载、图表摘要加载、看板跳转
- IAM：用户搜索、同步执行、失败重试、状态回显
- Scheduling：总览、最近执行、控制动作、跳转链路
- API Services：真实试调用、策略命中展示、错误态展示
- Workbench：真实趋势加载、角色摘要卡片、待办联动
- 页面边界：reports / quality / analytics 的路由与命名一致性
- Web 自动化：补入现有 `tests/web-e2e` 回归套件

## 状态跟踪

各 Task 进度在本文件的 Task 列表中更新状态标记：
- 空白 = 未开始
- WIP = 进行中
- DONE = 已完成
- BLOCK = 阻塞
