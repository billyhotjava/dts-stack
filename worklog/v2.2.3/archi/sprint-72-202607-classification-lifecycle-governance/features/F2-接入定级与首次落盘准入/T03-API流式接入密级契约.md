# T03: API/流式接入密级契约

**优先级**: P0
**状态**: IN_PROGRESS
**编码状态**: DONE（统一验证延后）
**依赖**: F1-T03

## 目标

让 platform 到 ingestion 的 API/流式任务契约携带不可降级的密级封存信息。

## 技术设计

- 任务契约增加 seal id、snapshot version、checksum 和字段密级摘要。
- ingestion 只校验并携带，不自行发明默认密级。
- checkpoint、retry、rebuild-api 和旧 DAG 迁移保持同一 seal。
- 契约版本向后兼容；旧任务在生产写入前进入补登记门禁。

## 影响范围

`dts-platform` ingestion proxy/DTO、`dts-ingestion` connector/executor/checkpoint、API 接入 UI。

## 验证

- [ ] 契约序列化与旧版本兼容。
- [ ] seal 缺失、过期或 checksum 不一致时 fail closed。
- [ ] 重试沿用原密级。

## 完成标准

- [ ] API/流式接入与 JDBC/文件遵循同一准入规则。
- [ ] 运行日志可关联 seal 和密级版本。
