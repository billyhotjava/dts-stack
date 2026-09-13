# T04：冻结预检 API 与错误语义

**优先级**: P0  
**状态**: DONE  
**依赖**: T02、T03

## 目标

为前端向导冻结 preview、run 查询和结构化问题契约。

## 技术设计

- 实现 `POST /model-spec-imports/dbt/preview` 与 `GET /model-spec-imports/{runId}`。
- issue 包含 code、severity、fieldPath、modelUniqueId、message、recoveryAction。
- 响应包含 summary、candidate action、conversion mode、proposed ModelSpec/implementation 和 previewHash。
- API 错误不泄露 SQL 密钥、连接串或跨租户标识。

## 影响范围

- 后端 REST/DTO/API 文档。
- 前端 API client 与 contract test。

## 验证

- [ ] success、blocked、conflict、permission denied 和 expired run 契约固定。
- [ ] OpenAPI/序列化与前端类型一致。
- [ ] 审计只记录摘要与 checksum，不记录敏感 SQL 正文。

## 完成标准

- [ ] F4 可以只依赖此契约开发预检页面。
- [ ] 旧 `/vnext/dbt/import` 保持兼容。
