# T02: assets-v2 资产详情工作台替换旧 dataset 详情

**优先级**: P0
**状态**: DONE
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

## 当前进展

- [x] 详情页读取顺序已调整为 assets-v2 优先，旧 `catalog/datasets` 仅作为 fallback。
- [x] 页面顶部已增加“企业级资产工作台”摘要，展示授权资产、资产键、字段契约和治理状态。
- [x] Tab 已重构为企业资产工作台结构：概览、字段契约、治理责任、质量与 SLA、血缘与影响、权限申请。
- [x] 旧深链兼容：`tab=fields|technical|lineage|quality|sla` 会映射到新的字段契约、血缘与影响、质量与 SLA。
- [x] 字段契约页统一展示 assets-v2 contract/schema-contract、授权资产、字段清单和技术同步摘要。
- [x] 质量与 SLA 页显式承接 legacy 治理健康、质量运行和指标依赖；未映射资产给出阻断说明。
- [x] 血缘与影响页同时承接 OpenMetadata 血缘缓存和 DTS 本地影响分析。
- [x] 已补 source-level UI contract test，锁定 assets-v2 优先读取、旧 dataset fallback、资产合同摘要展示与资产键来源。

## 验证

- [x] 前端 build 通过：`pnpm build` from `source/dts-platform-webapp`。
- [x] source-level test 通过：`./node_modules/.bin/tsx --test src/pages/catalog/DatasetDetailPage.source-contract.test.ts`。
- [x] 2026-05-18 复核通过：`source/dts-platform-webapp/node_modules/.bin/tsx --test source/dts-platform-webapp/src/pages/catalog/DatasetDetailPage.source-contract.test.ts`。
- [x] 2026-05-18 复核通过：`pnpm exec tsc --noEmit` from `source/dts-platform-webapp`。
- [x] 无权限 / 不存在资产保留友好空态：`数据集不存在或无权访问。`

## 完成标准

- [x] 资产详情页不再只展示旧 `catalog/datasets` 信息。
- [x] 用户能从页面看到资产事实源、字段契约、治理缺口和权限状态。
