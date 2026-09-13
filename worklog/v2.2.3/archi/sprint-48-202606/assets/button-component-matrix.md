# DTS 按钮与组件矩阵基线

**版本**: 2026-06-18
**原则**: 每个按钮或组件必须能说明用户意图、实现位置、状态、验证方式。不可执行的动作必须禁用并说明原因，不能做假实现。

## 控件矩阵

| 页面/领域 | 控件 | 类型 | Owner 组件 | 用户意图 | Handler/Route/API | 必备状态 | 测试入口 |
|-----------|------|------|------------|----------|-------------------|----------|----------|
| 工作台 | 自定义工作台 | 主按钮 | `WorkbenchCustomizeDrawer` | 勾选首页组件并调整顺序 | preferences API + local fallback | loading/error/local fallback/save/cancel | `WorkbenchPersonalization.source-contract.test.ts` |
| 工作台 | 刷新 | 次按钮 | `pages/workbench/index.tsx` | 重新加载首页组件数据 | block reload handlers | loading/error/disabled | workbench tests + Playwright `/workbench` |
| 工作台 | 恢复默认 | 次按钮 | `WorkbenchCustomizeDrawer` | 恢复系统默认组件顺序 | default registry | confirm/save/error | workbench personalization tests |
| 工作台 | 查看资产/运行健康/查看成果 | 跳转按钮 | workbench blocks | 进入资产、运维、BI 等真实页面 | `resolveAppHref` route | target exists/disabled reason | source-contract + browser smoke |
| 连接器目录 | 创建数据源 | 主按钮 | `ConnectorRegistryPage` | 从连接器创建数据源 | route/modal to datasource create | default/loading/permission | `ConnectorRegistryPage.source-contract.test.ts` |
| 连接器目录 | 配置 | 行按钮 | `ConnectorRegistryPage` | 配置连接器参数 | config route/modal | disabled reason/error | source-contract + table screenshot |
| 连接器目录 | 查看模板 | 行按钮 | `ConnectorRegistryPage` | 查看连接器模板 | template drawer/modal | empty/error | source-contract |
| 连接器目录 | 启用/停用 | 行按钮 | `ConnectorRegistryPage` | 切换连接器状态 | backend action if available | disabled reason/loading | source-contract |
| 数据源管理 | 创建数据源 | 主按钮 | `DataSourcesPage` | 新建可用数据源 | datasource API | loading/error/success | `DataSourcesPage.sprint45-actions.source-contract.test.ts` |
| 数据源管理 | 测试连接 | 行按钮 | `DataSourcesPage` | 验证连接参数 | test connection API | loading/success/fail | focused test + browser |
| 驱动管理 | 上传驱动 | 主按钮 | `JdbcDriversPage` | 上传 JDBC 驱动 | upload API | uploading/error/success | source-contract |
| 驱动管理 | 校验/启用/禁用 | 行按钮 | `JdbcDriversPage` | 管理驱动可用状态 | currently limited | disabled title required | source-contract |
| 数据入湖 | 新建任务 | 主按钮 | `TransformPage` | 创建入湖配置 | route `/explore/etl/transform/new` | route exists | Sprint45 ingestion test |
| 数据入湖 | 运行/查看详情/执行历史 | 行按钮 | `TransformPage` | 运行和追踪任务 | transform API/routes | loading/error/history empty | unit + browser |
| 任务编排 | 保存/运行/调度 | 工具栏按钮 | `OrchestrationPage` | 编排并执行数据任务 | orchestration API | dirty/loading/error | orchestration tests |
| 即席查询 | 执行 SQL | 主按钮 | `SqlIdePage` | 运行查询 | query API | running/error/result empty | SQL IDE tests |
| SQL 建模 | 构建/发布/治理检查 | 主按钮组 | `SqlModelingPage` | 完成模型发布门禁 | modeling + governance API | loading/error/gated | modeling helper tests |
| 项目文件浏览 | 上传/运行/删除 | 工具按钮 | `DbtFileBrowserPage` | 维护建模文件 | file API | disabled reason/confirm | dbt file tests |
| 主题域管理 | 新建/编辑/删除 | 主/行/危险按钮 | `SubjectAreasPage` | 管理客户主题域 | subject API | permission disabled/confirm | page test |
| 标准管理 | 新建/导入/映射/回滚 | 主/次/危险按钮 | standards pages | 管理术语、数据元、码表 | standard API | permission/error/history | standards tests |
| 质量管控 | 新建规则/试运行/运行/发布/下线/删除 | 主/行按钮 | `QualityRulesPage` | 建立质量门禁 | quality API | loading/gated/confirm | quality tests |
| 资产门户 | 筛选/资产详情/治理缺口处置 | 筛选器/跳转/抽屉 | `DatasetsPage` | 找到资产并处理治理缺口 | catalog API/routes | empty/error/permission | asset source-contract tests |
| 血缘 | Tab: 影响/图谱/字段/导入/快照 | tabs | `LineagePage` | 选择血缘视角 | lineage routes/API | route/tab sync/error | route source-contract |
| 权限申请 | 申请/审批/查看 | 主/行按钮 | `DatasetAccessApprovalPage` | 完成数据权限流程 | approval API | loading/permission/error | permission tests |
| BI 看板 | 新建/编辑/发布 | 主/行按钮 | `DashboardsPage`/editor | 交付分析看板 | analytics API | disabled reason/loading | analytics tests |
| 分析卡片 | 新建/运行/保存/图表 | 主按钮组 | `CardsPage`/editor | 创建分析问题 | analytics query API | disabled reason/loading | analytics tests |
| 数据大屏 | 新建/编辑/发布/授权 | 主/行按钮 | `ScreensPage`/designer | 交付大屏 | screen API | lock/permission/error | screens source-contract + screenshot |
| 数据 API | 创建/发布/查看调用/审计 | 主/行按钮 | `ApiServicesPage` | 发布数据服务 | service API; audit partial | disabled reason required | service source-contract |
| 共享交换 | 创建令牌/启用/停用/审计 | 主/行按钮 | `TokensPage` | 管理数据服务令牌 | token API; audit partial | disabled reason required | source-contract |
| 运维中心 | 查看实例/告警/补数 | 表格行按钮 | ops pages | 追踪运行和恢复数据任务 | ops API | loading/error/empty | ops source-contract |

## 全局组件验收

| 组件类型 | 必须检查 |
|----------|----------|
| Table | `scroll.x`、列宽、操作列宽、标签 nowrap、长中文不挤压 |
| Drawer | 1366x768 下主操作可见，关闭/保存/错误态完整 |
| Modal | title/footer 不溢出，确认按钮 loading 不改变布局 |
| Button | 长中文不换成破碎多行，disabled 必须有原因 |
| Tabs | route/search 参数与当前 Tab 一致 |
| Empty | 说明下一步配置入口，不显示假数据 |
| Error | 可重试或明确依赖服务，不吞异常 |
| Permission | 禁用/隐藏规则一致，不能出现可点但无权限 |

## 后续 task 拆分方式

每个实现型 task 至少选择一个页面和一组控件，例如：

- `ConnectorRegistryPage` 表格列宽 + 操作列回归
- `ApiServicesPage` 后端未开放动作的禁用态和文案
- `SqlModelingPage` 发布门禁按钮的 source-contract
- `ScreensPage` 大屏列表和设计器在 1366x768 下的浏览器 smoke
