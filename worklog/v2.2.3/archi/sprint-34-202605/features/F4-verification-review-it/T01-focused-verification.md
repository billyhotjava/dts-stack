# T01: focused 自动化验证

**优先级**: P0
**状态**: DONE
**依赖**: F1-F3

## 目标

跑最小但足够覆盖本 Sprint 风险的自动化验证。

## 验证

- [x] `source/dts-admin`: `./mvnw -q -pl dts-admin -Dtest=AuditV2ServiceTest test`
- [x] `source/dts-platform`: `./mvnw -q -pl dts-platform -Dtest=CatalogDomainResourceAuditTest,ReportsResourceWebMvcTest test`
- [x] 必要时执行模块 compile。

## 完成标准

- [x] 结果写入 `it/evidence/`。
