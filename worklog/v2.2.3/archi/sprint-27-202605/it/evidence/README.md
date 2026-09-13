# Sprint-27 Evidence

本目录用于归档 Sprint-27 的页面、API、截图和发布治理证据。

## 目录约定

`<YYYYMMDD>-<env>/sprint-27/`

示例：

`20260503-local/sprint-27/`

## 必备证据

- `00-summary.txt`: smoke 参数、开始时间、结束时间和失败数。
- `01-page-*.status.txt`: ELT、指标、事件、审计证据链、发布治理页面访问状态。
- `11-api-*.status.txt`: ELT 观测、指标观测、事件 outbox、治理发布门禁 API 状态。
- `*.body.json`: API 响应体；未登录环境允许归档 `401/403`，用于区分认证问题和服务错误。

## 原则

- Kafka 未启用时也必须可归档页面和 API 状态。
- 审批、权限、脱敏策略未定时，只归档预留字段和审计/事件证据，不做策略结论。
