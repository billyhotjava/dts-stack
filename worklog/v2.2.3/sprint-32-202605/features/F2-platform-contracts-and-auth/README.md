# F2: platform 契约、服务鉴权与事实源边界

**优先级**: P0
**状态**: DONE
**目标**: 让 `dts-metrics` 只通过 platform 明确 API 使用资产、权限、审计、数据源和 dbt 发布能力。

**Sprint-31A 依赖**: 本 Feature 的能力发现、Catalog 只读、权限校验、审计和发布网关全部以 Sprint-31A 的 platform capability 为准。

## 任务

| Task | 内容 | 验收 |
|---|---|---|
| T01 | 服务鉴权接入 | `dts-metrics` 使用 `X-DTS-Service` / `X-DTS-Service-Token` 调用 platform 内部 API |
| T02 | Catalog 只读契约 | metrics 能读取 dataset/table/column/schema/classification，不直接访问 platform 表 |
| T03 | asset_grant 权限契约 | 预览、发布、BI 注册前必须调用 platform 权限检查 |
| T04 | dbt 发布网关契约 | metrics 只提交候选 artifact，最终门禁和发布由 platform/dbt gateway 执行 |
| T05 | 审计和审批契约 | 指标创建、导入、预览、提交、发布、撤销均写入 platform 审计事件 |

## 完成标准

- [x] `dts-metrics` 不持有数据源密码。
- [x] `dts-metrics` 不实现本地用户/角色/权限事实源。
- [x] platform 内部 API 有服务鉴权失败用例。
- [x] `dts-metrics` 只能访问 internal capabilities 和 asset-permission 只读/校验端点，不能写 grant 或读取数据源运行密钥。
- [x] `dts-metrics` 可读取 platform Catalog 稳定契约，并通过 platform/dbt release gate 提交候选发布。

## 证据

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/security/ServiceDependencyAuthenticationFilter.java`
- `source/dts-platform/src/test/java/com/yuzhi/dts/platform/security/ServiceDependencyAuthenticationFilterTest.java`
- `source/dts-metrics/src/main/java/com/yuzhi/dts/metrics/service/PlatformContractClient.java`
- `source/dts-metrics/src/test/java/com/yuzhi/dts/metrics/service/PlatformContractClientTest.java`
- `worklog/v2.2.3/sprint-32-202605/it/evidence/platform-contracts/README.md`
