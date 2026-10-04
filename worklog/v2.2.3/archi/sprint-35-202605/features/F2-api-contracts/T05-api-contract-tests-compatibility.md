# T05: API contract 测试与兼容策略

**优先级**: P0
**状态**: IN_PROGRESS
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

- [x] `MetricVisualAssetResourceTest` 证明默认 DWS/ADS、DWD 需显式高级模式、ODS/STG 被拒。
- [x] `MetricGraphResourceTest` 覆盖 graph create/get/patch/preflight、invalid layer、grain mismatch、derived metric cycle。
- [x] `MetricModelLifecycleResourceTest` 覆盖 artifact、validate、publish dry-run 状态门禁和 publish 脱敏。
- [x] `MetricsFrontendResourceContractTest` 与 webapp source test 覆盖 Sprint-35 feature routes 和 API path。
- [ ] 旧 semantic 写路径兼容策略仍需单独落地，不能继续创建 platform 内部语义事实。

## 完成标准

- [ ] API 契约可作为前端和后端并行开发依据；当前新 API focused contract 已有，旧 semantic 兼容策略待补齐。
