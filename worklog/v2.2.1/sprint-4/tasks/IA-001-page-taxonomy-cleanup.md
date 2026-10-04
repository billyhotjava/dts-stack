# IA-001: 收敛 alias/wrapper page，统一 reports / quality / analytics 页面边界

## 范围

- `source/dts-platform-webapp/src/pages/visualization/ReportsManagePage.tsx`
- `source/dts-platform-webapp/src/pages/governance/QualityReportPage.tsx`
- `source/dts-platform-webapp/src/pages/visualization/AnalyticsPage.tsx`
- 相关 route/menu 配置

## 目标

- 减少“名称看起来是独立功能，实际只是别名页/壳页”的情况
- 让 platform 页面命名与产品边界一致

## 交付

- Reports / ReportsManage 边界说明与代码收敛
- QualityReport 语义归位
- Analytics 入口语义归位

## 验收

- 页面名、路由名、菜单名能够互相解释
- 用户不会再进入“看起来独立但实际无内容”的页面
- 新边界不影响既有链接与面包屑

## 当前进度

- 状态：TODO
- 备注：需要在实现前先锁定页面 ownership，避免重复建设
