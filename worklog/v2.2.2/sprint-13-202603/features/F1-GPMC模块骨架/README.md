# F1: GPMC 模块骨架

**优先级**: P0
**状态**: READY

## 目标
搭建 GPMC 指挥中心的基础框架：路由、布局、主题、导航、数据 hook、三层下钻状态管理

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 主题系统与 CSS Token | P0 | READY | - |
| T02 | 布局框架与路由注册 | P0 | READY | T01 |
| T03 | 三层 Drill 状态管理 | P0 | READY | - |
| T04 | 统一数据 Hook 与 API | P0 | READY | - |

## 完成标准
- [ ] `/analytics/gpmc` 渲染 GPMC 布局
- [ ] 侧边栏 + 顶栏 + 内容区 CSS Grid 布局
- [ ] Light/Dark 切换
- [ ] 三层 drill 状态（战略/管控/执行）+ 面包屑
