# T02: assets-v2 资产详情工作台替换旧 dataset 详情

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

把 `/catalog/datasets/:id` 从旧 dataset 详情页升级为企业级资产详情工作台，优先读取 Sprint-31A 的 assets-v2 事实源。

## 技术设计

页面至少包含 6 个 Tab：

1. 资产概览：名称、类型、assetKey、grantAssetType、grantAssetId、owner、生命周期、密级、分层；
2. 字段契约：`/api/catalog/assets-v2/{id}/schema-contract` 字段、类型、注释、敏感级别、标准绑定；
3. 治理信息：`/api/catalog/assets-v2/{id}/contract` 与 governance update；
4. 质量与 SLA：freshness、最近质量运行、失败分类；
5. 血缘与影响：`/api/catalog/assets-v2/{id}/lineage` 与下游消费；
6. 权限与审批：现有 grant、申请入口、审批状态。

## 影响范围

- `source/dts-platform-webapp/src/pages/catalog/DatasetDetailPage.tsx`
- `source/dts-platform-webapp/src/api/platformApi.ts`
- 可能复用 `AssetDetailPage.tsx` 中已有治理健康与脱敏联动组件。

## 验证

- [ ] 前端 build 通过。
- [ ] 至少一个 assets-v2 mock/source-level test 覆盖 contract/schema 渲染。
- [ ] 无权限资产显示友好错误，不进入 403 白页。

## 完成标准

- [ ] 资产详情页不再只展示旧 `catalog/datasets` 信息。
- [ ] 用户能从页面看到资产事实源、字段契约、治理缺口和权限状态。
