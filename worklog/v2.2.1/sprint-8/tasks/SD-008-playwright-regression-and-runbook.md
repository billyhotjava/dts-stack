# SD-008

## 标题

将大屏设计器关键路径纳入现有 Playwright 回归体系，并补齐 runbook。

## 范围

- `tests/web-e2e`
- `tests/suites.json`
- `worklog/v2.2.1/sprint-8/it/README.md`

## 目标

- 让 screens/template/marketplace 关键路径具备最基本的 Web 自动化验证
- 明确这批场景的运行条件、工件和失败定位方式

## 交付

- analytics 侧 smoke 用例或等价回归入口
- suite 或文档接线
- sprint 级运行说明

## 验收

- 关键路径至少能 dry-run 并有明确执行命令
- 用例失败时可产出 screenshot / trace / video 或等价工件

## 当前进度

- 状态：TODO
- 备注：第一期优先 smoke，不在本 Sprint 做过深业务编排

## 风险

- 若过早追求全量真实后端编排，会显著提高自动化成本和脆弱度
