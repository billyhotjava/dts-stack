# 原型到 DTS 前端映射账本

## 原型事实源

| 原型区域 | 事实源 | DTS 落点 |
|---|---|---|
| 顶部 7 模块 | `worklog/prototype/dm/dataworks-kimball/app.js` 的 `workspaceNav` | “首页”收敛为直属建模概览，其余 6 个模块成为能力分组 |
| 首页内部菜单 | `renderWorkspaceRail()` | 最近访问与我的任务合并到 `/data-modeling/home/workspace` |
| 数仓规划层级 | `renderPlanningRail()` | `/data-modeling/planning/**` |
| 标准分类 | `workspaceRail.standards` | `/data-modeling/standards/**` |
| 正向/逆向建模 | `renderModelingModuleRail()` | `/data-modeling/dimensions/**` |
| 五类指标 | `metricTypes` | `/data-modeling/metrics/**` |
| 工具和记录 | `workspaceRail.tools` | `/data-modeling/tools/**` |
| 三类关系 | `workspaceRail.graph` | `/data-modeling/graphs/**` |

## 页面完成度与本 Sprint 处理

| 模块 | 原型现状 | 本 Sprint 处理 |
|---|---|---|
| 首页 | 主体完整，3 个菜单未拆页 | 收敛为“建模概览”，最近访问和我的任务保留为页内面板 |
| 数仓规划 | 8 个独立内容视图，新建为 Toast | 保留 8 个只读/可编辑外观；最终写动作禁用 |
| 数据标准 | 仅字段标准完整 | 为 5 类标准提供各自列定义和空态 |
| 维度建模 | 三栏编辑器、六类创建、弹层最完整 | 拆成可维护 React 组件；保存/发布/物化失败关闭 |
| 逆向建模 | 仅第一步真实 | 补齐 4 步 UI 外观，生成动作禁用 |
| 数据指标 | 五类表单有差异 | 保留差异；写动作禁用 |
| 通用工具 | 3 菜单复用同页 | 工具箱、导入记录、导出记录分别呈现 |
| 关系图 | 3 菜单复用同一静态图 | 共用画布，不同标题/关系类型/筛选 |

## 不迁移的原型实现

- DataWorks 黑色顶部导航和品牌区。
- 原型提示横幅。
- `window.PROTOTYPE_DATA` 全局对象。
- `innerHTML` 整页重绘。
- 无 URL 的内存菜单状态。
- 硬编码用户、客户、财务和项目业务事实。
- Toast 模拟保存、发布、物化成功。
- 在线图标和 DataWorks 商标。

## 旧 DTS 前端边界

`src/pages/modeling` 混合了三类内容，退役时分别处理：

1. **展示页面和壳层**：由新原型页面替换并删除。
2. **纯前端契约与转换 helper**：先迁到 feature/contract 目录，再更新 API、治理和工作台引用。
3. **旧 URL 兼容逻辑**：压缩为一版轻量重定向，不再渲染旧展示页面。

已知外部消费者包括：

- `src/pages/workbench/index.tsx`
- `src/pages/workbench/DataManagementWorkbenchPage.tsx`
- `src/pages/governance/DataMartWorkspace.tsx`
- `src/api/dataMartApi.ts`
- `src/api/dimensionDefinitionApi.ts`
- `src/api/modelImplementationApi.ts`
- `src/api/modelSpecApi.ts`
- `src/api/warehousePlanApi.ts`
- dashboard 静态路由和动态解析器

## 评审关注点

- 菜单深度、展开状态和当前项高亮是否符合 DTS 现有侧栏。
- 1440×900、1366×768 与 Chrome 95 下三栏编辑器是否仍可操作。
- 每个菜单是否有独立 URL 和正确空态。
- 禁用动作是否清楚说明“等待后台接入”，且不存在虚假成功。
- 新旧路由跳转是否始终进入新 UI。
- 旧展示组件删除后是否仍有 API/治理/工作台反向引用。
