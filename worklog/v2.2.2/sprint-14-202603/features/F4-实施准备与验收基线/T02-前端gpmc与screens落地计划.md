# T02: 前端 gpmc 与 screens 落地计划

**优先级**: P1
**状态**: DONE
**依赖**: T01

## 目标
明确前端业务页和模板层的改造顺序。

## 前端改造总原则
- 先拆数据层，再改页面层，再落模板层。
- 不允许“先把 mockData 改复杂，再想办法对接真实接口”。
- 页面与模板同源，但页面先承担路由和 drill 编排。

## `gpmc` 页面侧落地顺序

### 第一步：调整页面骨架
- 删除独立 `resource` screen
- 将 `quality` screen 拆为：
  - `quality`
  - `tech-state`
- 新增执行层 drill 路由页目录

建议目录：
- `src/pages/gpmc/screens/`
- `src/pages/gpmc/drill/`
- `src/pages/gpmc/view-models/`
- `src/pages/gpmc/api/`

### 第二步：替换数据层
- 从 `mockData.ts` 迁出到 API + view model adapter
- 每个页面只接受单一 page vm：
  - `overviewVm`
  - `executionBoardVm`
  - `qualityBoardVm`
  - `techStateBoardVm`
  - `costBoardVm`
  - `riskBoardVm`

### 第三步：落执行层 drill 页面
- 先做项目执行详情页
- 再做质量 / 技术 / 风险 drill
- 最后补成本 drill

## `screens` 模板侧落地顺序

### 第一步：先做战略层总览模板
- 直接对齐 `StrategicOverviewVm`
- 验证模板变量和跳转动作

### 第二步：再做执行、质量、技术、风险模板
- 优先做数据结构清晰、交互复杂度适中的模板

### 第三步：最后补成本模板
- 等成本域字段明确后再固化组件布局

## 页面与模板的协作规则
- 页面负责：
  - 路由
  - drill 导航
  - 全局筛选同步
- 模板负责：
  - 布局
  - 组件配置
  - 数据源 responsePath 绑定

## 最小替换策略
- 第一阶段允许页面壳子仍沿用现有 `gpmc` 视觉实现
- 但数据输入必须改成 view model
- 禁止继续扩写 `mockData.ts`

## 影响范围
- `source/dts-analytics-webapp/modern/src/pages/gpmc`
- `source/dts-analytics-webapp/modern/src/pages/screens`

## 验证
- [x] 页面与模板改造顺序明确

## 完成标准
- [x] 前端落地计划完成
