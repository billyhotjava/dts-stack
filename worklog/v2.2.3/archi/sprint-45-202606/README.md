# Sprint-45: 数据中台 UI 产品化整改大 Sprint

**时间**: 2026-06  
**状态**: DONE  
**类型**: UI Productization / Product Architecture / Implementation（dts-platform-webapp + dts-admin menu seed + dts-analytics-webapp/modern）

## 目标

把当前“后台能力强、前端页面割裂”的状态整改为一个可演示、可验收、可持续开发的数据中台产品。整改主线不是重做视觉皮肤，而是用页面把真实功能串起来：

`数据源 -> 入湖任务 -> ODS/DWD/DWS/ADS -> 治理/权限/血缘 -> 指标/报表/API/数据产品/大屏 -> 运维审计`

所有菜单叶子都必须满足三条规则：

1. 有真实页面或明确下线，不保留假入口。
2. 页面按钮能触发真实动作、跳转到真实下一步或给出明确不可用原因。
3. 页面组件能展示空态、加载、异常、权限不足和真实数据状态，不用静态假成功数值。

## 背景

前期重点落在后台、语义层、黄金链路、指标和治理能力，后端主线已经具备产品基础；但前端仍有几个问题：

- 菜单、路由、组件映射不完整，存在 `/workbench/todo`、`/studio/projects`、`/studio/sql-modeling` 这类入口断点。
- 工作台、治理、资产、服务、BI、大屏、运维各自能看，但没有共同的业务上下文和下一步动作。
- 部分页面仍暴露工程语言或 sprint 语言，客户视角难以理解。
- 部分能力隐藏在静态路由，没有纳入产品菜单和验收闭环。
- 页面按钮和组件缺少统一状态规范，容易出现“页面有了但实现是假的”的风险。

## 执行 Skill 矩阵

| Skill | 用途 | 使用阶段 | 输出 |
|-------|------|----------|------|
| sprint-workflow | 管理大 sprint、feature、task、IT 证据 | 全程 | worklog、状态、验收记录 |
| product-capability | 区分客户可见承诺与后台实现，防止假实现 | 需求拆解、验收 | 页面能力契约、非目标 |
| frontend-design | 页面布局、按钮、组件、状态、响应式一致性 | 设计与实现 | 组件规范、交互验收 |
| gitnexus-exploring / gitnexus-impact-analysis | 修改代码前定位符号与影响面 | 实现前 | 影响面、风险说明 |
| e2e-testing / browser-use | 菜单可达性、按钮跳转、页面截图验收 | 验收 | Playwright smoke、截图证据 |
| verification-loop | 构建、source-contract、回归验证收口 | 每个 feature 完成时 | 测试命令与结果 |
| security-review | 权限、令牌、共享交换、审计页面安全检查 | F4/F6 | 安全风险清单 |

## Feature 列表

| ID | Feature | 优先级 | Task 数 | 状态 | 阶段目标 |
|----|---------|--------|---------|------|----------|
| F1 | 产品壳与全局导航闭环 | P0 | 5 | DONE | 消除假菜单、断路由和命名漂移，建立菜单-路由-组件契约 |
| F2 | 工作台与黄金链路产品化 | P0 | 4 | DONE | 把数据管理工作台升级为产品主控台，承接所有下一步动作 |
| F3 | 数据接入到开发链路贯通 | P0 | 4 | DONE | 从数据源、入湖、转换、建模到任务编排形成连续操作流 |
| F4 | 治理与资产门户闭环 | P0 | 4 | DONE | 让治理、资产、权限、数据产品成为发布门禁和消费入口 |
| F5 | 指标 BI 大屏消费体验统一 | P1 | 4 | DONE | 指标、BI、数据大屏从独立工具收口为业务消费链路 |
| F6 | 数据服务与运维验收闭环 | P1 | 5 | DONE | API、共享交换、任务运维、审计发布能反向证明链路可用 |
| F7 | 页面级视觉规范与验收体系 | P0 | 4 | DONE | 建立按钮、组件、状态、契约测试和截图验收标准 |

**统计**: READY=0, IN_PROGRESS=0, DONE=30, BLOCKED=0

## 页面主线

| 阶段 | 入口 | 必须串起的下一步 |
|------|------|------------------|
| 工作台 | `/workbench`, `/workbench/data-management`, `/workbench/todo` | 看主题状态、待办、黄金链路、下一步动作 |
| 接入 | `/foundation/data-sources`, `/foundation/connectors`, `/foundation/jdbc-drivers` | 测试连接、发现表、创建入湖任务、生成链路 |
| 开发 | `/studio/projects`, `/studio/sql-modeling`, `/explore/etl/transform`, `/explore/etl/orchestration`, `/modeling/dbt-files` | 建模、预览、运行、调度、发布 |
| 治理 | `/governance/*`, `/security/data-security` | 标准、质量、分级、权限、发布门禁 |
| 资产 | `/catalog/*`, lineage routes | 查资产、看血缘、申请权限、生成数据产品 |
| 消费 | `/bi-apps/*`, `/bi/*`, `/bi/screens`, `/services/*` | 指标、报表、API、数据产品、大屏、共享交换 |
| 运维 | `/ops/*` | 实例、告警、补数、事件、审计、发布治理 |

## 完成标准

- [x] 所有菜单叶子有路由契约：真实页面、外链、明确下线三选一。
- [x] P0 断点 `/workbench/todo`、`/studio/projects`、`/studio/sql-modeling` 修复或改为明确可验收入口。
- [x] 服务中心、指标中心、BI、大屏、治理、资产、运维页面不再出现客户不可理解的 sprint/F1/F2 工程语言。
- [x] 每个核心页面具备页面标题、业务上下文、主按钮、次按钮、危险按钮、空态、异常态、权限态、加载态。
- [x] 所有主按钮都能落到真实 API、真实路由、真实任务创建或明确禁用原因。
- [x] 工作台能从业务主题进入数据源、治理、资产、BI、API、运维等下一步，不再只是模块目录。
- [x] source-contract 覆盖菜单-路由-组件映射、按钮文案、关键 API 依赖和空态行为。
- [x] `pnpm build` / `pnpm typecheck` / Playwright smoke 按触达模块完成验收记录。

## 非目标

- 不重写后端黄金链路状态机、语义层、指标计算或调度引擎。
- 不用这个 sprint 推进现代湖仓新技术路线；本期先打牢产品闭环。
- 不把所有页面做成一个大首页；保留现有模块，但通过上下文、按钮和跳转串成产品旅程。
- 不以静态 mock 数据替代真实接口；接口缺失时必须在 task 中明确后端依赖。

## 相关材料

- 页面能力矩阵: `assets/ui-page-capability-matrix.md`
- 按钮组件矩阵: `assets/button-component-inventory.md`
- IT 验收: `it/README.md`
- 上游产品主线: `worklog/v2.2.3/sprint-39-202606/README.md`
- 工作台基础: `worklog/v2.2.3/sprint-42-202606/README.md`
- 语义前端专项: `worklog/v2.2.3/sprint-44-202606/README.md`
