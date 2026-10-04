# VIS-002: AnalyticsPage / ReportsPage 变成真实平台入口并补稳定 selector

## 范围

- `source/dts-platform-webapp/src/pages/visualization/AnalyticsPage.tsx`
- `source/dts-platform-webapp/src/pages/visualization/ReportsPage.tsx`
- `source/dts-platform-webapp/src/api/platformApi.ts`
- 相关 page component / selector / Playwright case

## 目标

- 让 platform 内的 visualization 入口可直接使用
- 取消“只跳 `/analytics`”的空壳体验
- 为后续 Playwright 增加稳定测试锚点

## 交付

- Visualization 入口页真实摘要卡片
- Reports/Analytics 页面边界说明与跳转策略
- 关键区域 `data-testid`

## 验收

- 用户进入 platform 可直接看到 visualization 摘要与入口
- Reports 页面能打开已发布 BI 链接并保留访问审计
- 页面具备 loading / empty / error 三态

## 当前进度

- 状态：TODO
- 备注：需要和 `IA-001` 一起收敛页面信息架构
