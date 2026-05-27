# F2: 前后端 API 契约

**优先级**: P0
**状态**: READY

## 目标

定义 `dts-metrics-webapp`、`dts-metrics` 和 `dts-platform` 之间的 API 契约，让前端只面对稳定 metrics API，后端通过 service-auth 调用 platform internal contract 完成资产、权限、治理、RLS、dbt validation、发布和审计。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 可视化资产查询 API | P0 | READY | F1 |
| T02 | Graph draft 与 preflight API | P0 | READY | T01 |
| T03 | 候选 artifact、验证和发布 API | P0 | READY | T02 |
| T04 | DTO、错误码和 TypeScript contract | P0 | READY | T01-T03 |
| T05 | API contract 测试与兼容策略 | P0 | READY | T04 |

## 完成标准

- [ ] API 默认只返回 DWS/ADS，可通过高级模式显式请求 DWD。
- [ ] DTO 包含 warehouseLayer、grain、permissionDecision、governanceStatus 和 validationState。
- [ ] 失败场景使用明确错误码，不返回模糊 500 或静默 fallback。
- [ ] 旧 `/api/semantic/**` 有代理、只读或弃用策略。
