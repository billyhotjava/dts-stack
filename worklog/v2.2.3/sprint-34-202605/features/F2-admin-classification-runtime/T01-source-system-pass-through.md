# T01: AuditActionRequest sourceSystem 透传

**优先级**: P0
**状态**: DONE
**依赖**: F1

## 目标

修复 `AuditV2Service` 写死 `sourceSystem=admin` 的问题。

## 技术设计

- `AuditActionRequest` 增加 `sourceSystem` 字段和 builder 方法。
- dts-admin 本地操作默认 `admin`。
- `/api/audit-events` ingest 使用 payload 中的 `sourceSystem`。

## 影响范围

- `AuditActionRequest.java`
- `AuditV2Service.java`
- `AuditIngestResource.java`

## 验证

- [x] 写失败测试：platform request 入库时 sourceSystem 为 platform。
- [x] admin 本地 request 不传 sourceSystem 时仍为 admin。

## 完成标准

- [x] 业务端审计不会再被展示为管理端审计。
