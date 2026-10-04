# Platform Gap Closure Design

## 背景

对 `dts-platform` 与 `dts-platform-webapp` 的静态 review 表明，平台不是整体未实现，而是成熟度分化明显：

- `catalog / governance / explore-etl / ops / foundation-数据源` 已有较完整的实现骨架
- `visualization / IAM 分类同步 / 任务调度中心 / API 服务试调用 / 工作台深度指标` 仍存在明显产品缺口

这些缺口的共同问题不是“没有页面”，而是页面、API、真实数据源、交互闭环之间没有真正接起来。

## 目标

在 `customer/2.2.1` 分支上，完成 platform 一期能力补全：

1. 将最明显的占位页替换为真实的业务页面
2. 将伪数据/假调用替换为真实聚合或真实代理
3. 收敛页面信息架构，减少“看起来是独立功能，实际上只是壳页/别名页”的情况
4. 为后续 Playwright 回归提供稳定页面边界和明确的验收路径

## 方案选择

### 方案 A：全部页面一次性重做

优点：
- 一次性把 platform 做“完整”

缺点：
- 范围过大
- 需要同时动数据源、权限、编排、BI 集成，风险高

### 方案 B：围绕 review 结论做“缺口补全一期”

优点：
- 只修最明显的占位能力
- 可以按工作流拆分成独立 task
- 对现有成熟模块影响最小

缺点：
- 仍会保留部分深层能力待后续迭代

### 方案 C：只做前端换皮，不改后端

优点：
- 交付快

缺点：
- 会继续放大前端假数据、后端 stub、接口未接线的问题

## 选型

采用方案 B。

这一期只处理 review 中最明确的 5 条产品线：

- Visualization
- IAM 分类同步
- Task Scheduling
- API Services Try Invoke
- Workbench

同时补一轮页面信息架构清理，收敛壳页和别名页。

## 设计原则

### 1. 平台继续做“统一入口 + 聚合层”

不把 `dts-platform` 变成 BI 引擎或调度引擎本体。
平台负责：

- 页面入口
- 元数据聚合
- 权限过滤
- 操作审计
- 对外部系统的统一代理

### 2. 先消灭假数据，再补深功能

优先级顺序：

1. 真实数据替代硬编码样例
2. 真实接口替代假调用
3. 可运维、可回放、可审计
4. 再谈高级交互与高级配置

### 3. 一个页面只表达一个产品边界

这次要重点减少：

- redirect shell
- alias page
- 嵌套页面冒充独立模块

比如：

- `AnalyticsPage` 应该成为平台内可用的可视化入口，而不是单纯跳转
- `ReportsManagePage` 与 `ReportsPage` 的边界需要收敛
- `QualityReportPage` 需要决定保留为治理视角，还是直接回收为目录质量页别名

### 4. 所有前端改动都要为 Playwright 留稳定锚点

每个新页面或关键交互都应预留：

- 稳定 `data-testid`
- 明确页面加载态
- 明确空态/错误态
- 可自动化的关键按钮与筛选项

## 分工作流设计

### Visualization

- 后端将 `/api/vis/*` 从硬编码样例改为真实聚合
- 前端将 `AnalyticsPage` 从跳转壳页改成 platform 内可用入口
- 对 `viz-spec` 做二选一：
  - 要么实现最小可用渲染链路
  - 要么明确退役，不再对外暴露为未完成能力

### IAM 分类同步

- 将 `ClassificationService` 接到 REST
- 前端去掉只读占位实现
- 同步执行要从“直接写成功日志”升级为真实同步流程，至少具备：
  - 开始
  - 结果统计
  - 失败记录
  - 重试

### Task Scheduling

- `TaskSchedulingPage` 不再只是导航页
- 提供统一调度控制台，整合：
  - 运行态
  - DAG/编排态
  - 最近执行
  - pause/resume/retry 入口

### API Services

- `tryInvoke` 改为真实代理调用
- 增加请求参数、请求头、响应体、命中策略的可见性
- 保留 API 元数据中心定位，但补上最关键的联调能力

### Workbench

- 后端输出真实历史与角色相关指标
- 前端去掉“模拟 7 天趋势”
- 工作台从“两个统计卡片 + 待办列表”提升为角色化首页

## 验证思路

### 后端

- 以 service/resource 单测和集成测试覆盖真实聚合与真实代理

### 前端

- `pnpm build` 保证编译闭环
- 对新增页面补稳定 test id

### Web 自动化

- 在现有 `tests/web-e2e` 基线之上，为 visualization、workbench、task scheduling、API services 补新的 smoke/biz case

## 输出物

- `docs/plans/2026-03-10-platform-gap-closure-plan.md`
- `worklog/v2.2.1/sprint-4/README.md`
- `worklog/v2.2.1/sprint-4/tasks/*.md`
- `worklog/v2.2.1/sprint-4/it/README.md`
