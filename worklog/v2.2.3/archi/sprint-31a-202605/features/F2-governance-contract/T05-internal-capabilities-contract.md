# T05: 内部 capabilities 契约

**优先级**: P1
**状态**: DONE
**依赖**: T02-T04

## 目标

提供 `/api/internal/capabilities` 风格的能力发现接口，让 dts-metrics 能确认 platform 支持的资产、权限、审计和发布能力。

## 技术设计

- 扩展 `GET /api/internal/capabilities`，输出 catalog contract version、asset types、permission actions、classification catalog、audit 和 dbt publish mode。
- 内部端点要求 `ROLE_SERVICE_INTERNAL` 且认证主体为 `service:dts-metrics`。
- 保留 `GET /api/capabilities` 作为公开概要端点。
- 缺能力时由 Sprint-32 `dts-metrics` health/readiness 直接报告。

## 影响范围

- platform internal API
- dts-metrics PlatformContractClient
- `worklog/v2.2.3/sprint-31a-202605/assets/platform-internal-capabilities-contract.md`

## 验证

- [x] service token 缺失时拒绝访问。
- [x] dts-metrics health 能读取真实 capability。
- [ ] 统一测试在 Sprint-31A/31/32 完成后执行。

## 完成标准

- [x] dts-metrics 不再只展示静态 contract 描述。
