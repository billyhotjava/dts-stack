# 数据建模原型符合性复核（2026-08-03）

## 结论

`worklog/prototype/dm/dataworks-kimball/` 是数据建模前端唯一的界面事实源。截至 2026-08-03 本轮收口，旧建模页面、阶段式流程、静态 Demo 数据与模拟成功 hook 已从生产前端物理删除；七个顶级入口保留原型的页面结构，数据和动作改为 canonical owner 投影或明确的不支持态。当前状态为 `CODE_COMPLETE / BUILD_PASS / DEPLOYMENT_PENDING / E2E_PENDING`，仍不能标记为 `REAL/DELIVERED`。

## 原型固定边界

| 顶级入口 | 原型页面形态 | 产品落地边界 |
|---|---|---|
| 建模概览 | 资产摘要、最近模型、交付状态、待处理、快速入口 | 只投影 canonical owner；没有真实最近访问/任务事实时不展示对应区块 |
| 数仓规划 | 左侧目录 + 当前对象紧凑新建区 + 表格 | DTS 菜单承接原型左侧目录；每个叶子仅显示自己的对象表单，不共享建设计划模块 |
| 数据标准 | 左侧标准类型 + 搜索/筛选 + 表格 | DTS 菜单承接标准类型；CRUD/导入必须进入真实标准 owner |
| 维度建模 | 对象树 + 单页编辑器 + 短工具栏 + 弹层 | 不恢复阶段式旧流程；基本信息、字段、分区、版本、发布、物化都在同一工作台 |
| 数据指标 | 指标类型 + 目录树 + 单页编辑器 | DTS 菜单承接指标类型，页面保留“目录 + 编辑器”；不得退化为独立解释页 |
| 通用工具 | 工具入口 + 真实运行记录 | 只保留已有 owner 的深链/运行事实；不创建万能工具台账 |
| 关系图 | 过滤工具栏 + 关系画布 + 跨模块跳转 | 只读组合真实关系；节点必须深链定位真实对象 |
| 逆向建模 | 同一维度建模入口内的四步向导 | 按 Sprint-83 冻结决策仅支持 dbt ZIP；inspect/preview/apply/retry 真实执行，部分成功逐项给因 |

## 当前代码差异账本

| 页面/能力 | 原型要求 | 当前工作树证据 | 评级 | 修复方向 |
|---|---|---|---|---|
| 七入口/27 叶子 | 菜单承接原型顶部与内部菜单 | `navigation.ts` 与 `DataModelingSurface.tsx` 统一路由分发 | CODE_COMPLETE | 待部署后逐个点击真实菜单验收 |
| 建模概览 | 真实资产投影 | `OverviewPage.tsx` 通过 `planningProjectionService.ts` 并发投影计划、ModelSpec、标准和指标 | CODE_COMPLETE | 空/错只展示真实状态，待 E2E |
| 数仓规划 | 当前叶子自己的紧凑目录页 | `PlanningPage.tsx` 按叶子读写 WarehousePlan/Catalog owner；无 owner 的建模空间等显示明确不支持原因 | CODE_COMPLETE_WITH_OWNER_GAPS | 不再共享“新建建设计划”模块，待 E2E |
| 数据标准 | 真实目录、详情、CRUD、导入 | `StandardsPage.tsx` 通过 `standardsWorkbenchService.ts` 读写标准、码表和词典；词根/映射按 owner 能力只读或禁用 | CODE_COMPLETE_WITH_OWNER_GAPS | 待真实权限与导入 E2E |
| 模型工作台 | 对象树 + 单页编辑器 + 真实交付 | `ModelingWorkbenchPage.tsx` 与 `ModelWorkbenchDialog.tsx` 接 ModelSpec、维度定义、业务/技术表示、release/build intent 与物理预览 | CODE_COMPLETE | 待真实发布/物化 E2E |
| 逆向建模 | 四步向导 | `ReverseModelingPage.tsx` 已接 ZIP inspect/preview/apply/retry/forward-undo，并处理 rename、blocked 与部分成功 | CODE_COMPLETE | 待真实 ZIP 与部分成功 E2E |
| 数据指标 | 目录 + 单页编辑器 | `MetricsPage.tsx` 通过 `metricsWorkbenchService.ts` 接入指标目录、保存、校验、发布和归档 | CODE_COMPLETE | 待真实编辑/发布 E2E |
| 通用工具 | 真实 owner 入口/历史 | `ToolsPage.tsx` 仅保留已存在 owner 的深链；无统一历史的页面显示真实空态 | CODE_COMPLETE_WITH_OWNER_GAPS | 待深链点击 E2E |
| 关系图 | 真实关系投影 | `RelationshipGraphPage.tsx` 读取 WarehousePlan relationship graph，支持搜索、缩放和真实节点深链 | CODE_COMPLETE | 待真实关系与跳转 E2E |
| 状态反馈 | loading/empty/error/permission/success | `PrototypePrimitives.tsx` 提供请求状态；可见写动作按真实权限与 owner 能力禁用 | CODE_COMPLETE | 待真实权限 E2E |

## 当前验证证据

- Biome 定向检查：6 个核心新页面/契约文件通过，无需修复。
- TypeScript：`tsc --noEmit` 无错误输出。
- 聚焦测试：4 files / 47 tests 全部通过。
- 生产构建：Chrome 95 target 的 Vite 制品已于 2026-08-03 重新生成。
- 尚未验证：当前制品未部署，未执行真实菜单点击、写操作、审计抽样与 dbt 物化 E2E。

## 删除与保留规则

- 删除：旧建模页面、旧阶段式流程、旧共享建设计划模块、静态 Demo 数据、模拟成功、无 owner 的按钮/记录。
- 保留：公共 API client、权限组件、错误分类、审计由服务端 owner 完成的通用契约。
- 重写：所有生产页面组件。组件结构以原型为准，数据和动作以 canonical owner 为准。
- 禁止：恢复旧 `HomeWorkspace`、`PlanningWorkspace`、`DimensionalModelingWorkspace` 等页面作为 UI 容器；用旧页面改皮肤不算迁移。

## 实施门禁

1. 页面先通过“无 Demo、无模拟成功、无旧流程文案”源码门禁。
2. 每个可见动作必须有 handler、真实 API/路由或明确 disabled reason。
3. API 空、失败或无权限时只展示真实空/错/权限态，不得回退示例数据。
4. 维度建模和指标必须保持原型的单页工作台，不拆回多阶段页面。
5. 全部页面编码冻结后再执行一次联合 E2E；编码期间只跑当前切片聚焦测试。
