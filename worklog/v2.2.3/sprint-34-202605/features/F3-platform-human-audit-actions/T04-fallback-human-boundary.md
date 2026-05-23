# T04: HTTP fallback 人工操作边界收紧

**优先级**: P0
**状态**: DONE
**依赖**: T01-T03

## 目标

让 `AuditLoggingFilter` 只兜底关键人工动作，不再把支撑查询和内部探测变成人工审计。

## 技术设计

- 保留 POST/PUT/PATCH/DELETE 的未显式审计兜底。
- GET 只记录明确的详情查看、下载、导出、执行结果查看等人工动作。
- support query、dropdown、dictionary、menu、auth probe、stats polling、service actor 全部跳过。

## 影响范围

- `AuditLoggingFilter.java`
- `AuditLoggingFilterTest.java`

## 验证

- [x] support GET 不记录。
- [x] export/download/view detail 仍记录。
- [x] 已显式 auditAction 的接口不会重复记录。

## 完成标准

- [x] auditadmin 页面不再被支撑请求噪声刷屏。

## 实现记录

- `AuditLoggingFilter` 增加对 forward-auth、menu tree、stats、summary、options、dropdown、selector 等支撑 GET 的跳过规则。
- `AuditLoggingFilterTest` 补充覆盖支撑查询跳过边界，保留人工详情/导出类 GET 的兜底能力。
- Loop 5 补充 `/api/infra/screen-fonts` 与 `/api/infra/screen-images` GET 资源降噪，避免查看大屏时字体/图片加载被误记为人工查看。
