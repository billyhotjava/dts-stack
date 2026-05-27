# T05: API contract 测试与兼容策略

**优先级**: P0
**状态**: READY
**依赖**: T04

## 目标

为新 API 和旧 `/api/semantic/**` 兼容路径定义 contract 测试，防止迁移期间静默 fallback。

## 技术设计

- 后端测试覆盖 visual assets 默认层级、DWD 高级模式、invalid layer、permission denied、platform unavailable。
- 前端测试覆盖 API path、query 参数和 DTO 字段使用。
- 旧 `/api/semantic/**` 策略：只读代理、明确 410 弃用或 503 metrics unavailable，不再新增 platform semantic 写入。
- 所有兼容路径写审计或弃用日志。

## 影响范围

- `source/dts-metrics/src/test/**`
- `source/dts-platform/src/test/**`
- `source/dts-metrics-webapp/test/**`
- `worklog/v2.2.3/sprint-35-202605/it/evidence/api-contracts/`

## 验证

- [ ] Contract tests 证明默认 DWS/ADS。
- [ ] 旧 semantic 写路径不能继续创建 platform 内部语义事实。
- [ ] metrics 服务不可用时返回明确错误。

## 完成标准

- [ ] API 契约可作为前端和后端并行开发依据。
