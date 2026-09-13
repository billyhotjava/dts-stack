# T01: analytics actionCode 生成与转发

**优先级**: P0
**状态**: DONE
**依赖**: F1, F2

## 目标

让 dts-analytics 的 fallback 审计事件提交稳定 actionCode，供 dts-admin 目录解析。

## 技术设计

- `AnalyticsAuditLoggingFilter` 根据资源段、HTTP 方法和关键路径生成稳定 actionCode。
- `AnalyticsAuditForwarderService` 将 actionCode 写入 `buttonCode` 和 `operationCode`，保留中文 action 作为 `operationName`/`summary`。
- dts-admin ingest 在缺少 `buttonCode` 时从 `operationCode`/`action` 补齐，避免退成 `PLATFORM_GENERIC_EVENT`。

## 影响范围

- `source/dts-analytics/.../AnalyticsAuditLoggingFilter.java`
- `source/dts-analytics/.../AnalyticsAuditForwarderService.java`
- `source/dts-admin/.../AuditIngestResource.java`

## 验证

- [x] `AnalyticsAuditLoggingFilterTest`
- [x] `AnalyticsAuditForwarderServiceTest`
- [x] `AuditIngestResourceTest`

## 完成标准

- [x] `/api/screen` fallback 生成 `SCREEN_VIEW`。
- [x] `/api/dashboard/save` fallback 生成 `VIS_DASHBOARD_EDIT`。
- [x] 转发 payload 包含 `sourceSystem=analytics`、`buttonCode`、`operationCode`。
