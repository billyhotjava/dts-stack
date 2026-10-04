# PMS-007

## 标题

实现全局筛选状态与 URL 同步，保证统一入口系统的上下文稳定。

## 范围

- `source/dts-analytics-webapp/modern/src/pages/project-cockpit/`
- `source/dts-analytics-webapp/modern/src/api/analyticsApi.ts`

## 目标

- 保证项目群、重大项目、时间周期、责任科室、风险等级在主题切换时不丢失
- 保证分享链接可以复现当前视角

## 交付

- `ProjectCockpitContext`
- query state hook
- URL query 编解码逻辑

## 验收

- 切换 `总览趋势`、`计划执行`、`重大项目树` 时筛选保持一致
- 浏览器刷新后仍能恢复当前筛选

## 当前进度

- 状态：DONE

## 风险

- 若 query contract 频繁变化，会影响演示链接与联调稳定性
