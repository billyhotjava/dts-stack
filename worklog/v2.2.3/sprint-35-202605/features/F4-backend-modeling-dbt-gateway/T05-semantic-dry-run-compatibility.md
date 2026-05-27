# T05: 旧 semantic dry-run 与兼容迁移

**优先级**: P0
**状态**: READY
**依赖**: T04

## 目标

为旧 platform `semantic_*` 数据提供 dry-run 映射和兼容策略，不在 Sprint-35 默认执行生产迁移。

## 技术设计

- `GET /api/metrics/migration/semantic-dry-run` 输出旧 semantic 到新 metric 的映射、缺失资产、冲突指标、不可迁移原因。
- 旧 `/api/semantic/**` 只读代理到 metrics 或返回明确弃用。
- 已生成 artifact 不直接视为发布成功，必须重新通过 platform/dbt gate。
- 迁移报告保留 rollback reference。

## 影响范围

- `source/dts-metrics` migration dry-run endpoint
- `source/dts-platform` semantic compatibility route
- `worklog/v2.2.3/sprint-35-202605/it/evidence/backend-dbt-gateway/`

## 验证

- [ ] Dry-run 不写生产数据。
- [ ] 缺失 asset contract 的旧映射显示 BLOCKED。
- [ ] 旧写 API 不再创建 platform semantic 事实。

## 完成标准

- [ ] 兼容策略可解释、可回滚、不破坏历史数据。
