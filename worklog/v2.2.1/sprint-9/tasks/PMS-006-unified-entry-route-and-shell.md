# PMS-006

## 标题

新增统一入口路由与项目看板系统壳页，承载所有主题视图。

## 范围

- `source/dts-analytics-webapp/modern/src/routes.tsx`
- `source/dts-analytics-webapp/modern/src/pages/project-cockpit/`

## 目标

- 用户只进入一次系统，不再通过模板库或多页面反复点击
- 提供统一的大屏专题壳页

## 交付

- `/analytics/project-cockpit`
- 固定顶部筛选区
- 固定主题切换标签
- 统一主画布容器

## 验收

- 新路由可直接访问
- 默认显示 `总览趋势`
- 切换主题时不刷新整页

## 当前进度

- 状态：DONE

## 风险

- 若壳页与主题视图耦合过深，后续拓展视图会变得困难
