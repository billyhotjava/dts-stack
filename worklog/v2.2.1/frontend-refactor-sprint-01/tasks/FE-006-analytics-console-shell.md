# FE-006

## 标题

重构 `analytics modern` 控制台壳。

## 范围

- `source/dts-analytics-webapp/modern/src/layouts/AppLayout.tsx`
- `source/dts-analytics-webapp/modern/src/layouts/layout.css`
- `source/dts-analytics-webapp/modern/src/routes.tsx`

## 目标

- analytics 的导航壳进入同一产品家族
- 保留 `/analytics` 现有路由拓扑，不改交付方式
- 页头、侧栏、面包屑、账号区和普通列表页容器与 admin/platform 对齐

## 交付

- 重做 analytics 主壳，不再保留它目前独立的一套“BI 风格”
- 保持 basename 和全屏路由行为不变

## 验收

- `pnpm -C source/dts-analytics-webapp/modern build`
- `/analytics` 常规页面在统一壳下编译通过

## 风险

- analytics 既有普通页面又有全屏页面，改壳时不能误伤 `/screens/:id/edit` 等全屏路由
