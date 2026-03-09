# Frontend Unified Refactor Sprint 01

## 目标

在 `customer/2.2.1` 上完成一轮真正的前端重构，而不是继续局部修补：

- 三个 webapp 统一为一套产品级 UI 语言
- 默认采用浅色优先风格
- `analytics-webapp/modern` 完整纳入统一风格
- 直接删除生产代码中的 `faker / demo / placeholder / dummy / mock`

## 范围

- `source/dts-platform-webapp`
- `source/dts-admin-webapp`
- `source/dts-analytics-webapp/modern`

## 三个壳层

1. `Console Shell`
   - 常规控制台页面
   - 适用于 `platform`、`admin`、`analytics` 常规导航页
2. `Workspace Shell`
   - 设计器、预览、导出等高密度工作区
   - 重点覆盖 `analytics` 大屏设计器
3. `Runtime Shell`
   - 大屏公开页与运行态控制层
   - 统一控制层，不强制统一业务大屏画布主题

## 本 Sprint 不做

- 不新增后端能力
- 不重做产品信息架构
- 不引入新的 monorepo/workspace 基础设施
- 不把 `v2.2.2+` 的产品方向整包回带

## 设计与计划

- 设计文档：`docs/plans/2026-03-09-frontends-unified-refactor-design.md`
- 实施计划：`docs/plans/2026-03-09-frontends-unified-refactor-plan.md`
- 假素材清单：`worklog/v2.2.1/frontend-refactor-sprint-01/fake-material-inventory.md`
- 状态板：`worklog/v2.2.1/frontend-refactor-sprint-01/status-board.md`

## Task 顺序

1. `FE-001` 统一设计契约与 token
2. `FE-002` 重构 platform/admin 共享控制台壳
3. `FE-003` 删除 platform/admin 假素材与占位入口
4. `FE-004` 重构 admin 客户可见页面
5. `FE-005` 重构 platform 客户可见页面
6. `FE-006` 重构 analytics 控制台壳
7. `FE-007` 重构 analytics 工作区壳
8. `FE-008` 重构 analytics 运行时壳
9. `FE-009` 删除 analytics 假素材与 demo 依赖
10. `FE-010` 三端构建验证与视觉审计收口

## 通过标准

- 三个前端 `build` 全部通过
- 客户可见页面不再出现 `预留 / demo / dummy / placeholder`
- `analytics` 的导航壳、编辑器页、大屏相关页全部进入同一风格体系
- 页面切换时，不再有“三套产品”的割裂感
