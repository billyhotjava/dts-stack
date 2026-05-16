# F2: platform 契约、服务鉴权与事实源边界

**优先级**: P0
**状态**: IN_PROGRESS
**目标**: 让 `dts-metrics` 只通过 platform 明确 API 使用资产、权限、审计、数据源和 dbt 发布能力。

## 任务

| Task | 内容 | 验收 |
|---|---|---|
| T01 | 服务鉴权接入 | `dts-metrics` 使用 `X-DTS-Service` / `X-DTS-Service-Token` 调用 platform 内部 API |
| T02 | Catalog 只读契约 | metrics 能读取 dataset/table/column/schema/classification，不直接访问 platform 表 |
| T03 | asset_grant 权限契约 | 预览、发布、BI 注册前必须调用 platform 权限检查 |
| T04 | dbt 发布网关契约 | metrics 只提交候选 artifact，最终门禁和发布由 platform/dbt gateway 执行 |
| T05 | 审计和审批契约 | 指标创建、导入、预览、提交、发布、撤销均写入 platform 审计事件 |

## 完成标准

- [ ] `dts-metrics` 不持有数据源密码。
- [ ] `dts-metrics` 不实现本地用户/角色/权限事实源。
- [x] platform 内部 API 有服务鉴权失败用例。
- [x] `dts-metrics` 只能访问 internal capabilities 和 asset-permission 只读/校验端点，不能写 grant 或读取数据源运行密钥。
