# T03: 数据产品成员配置 UI

**优先级**: P0
**状态**: DONE
**依赖**: Sprint-31A F1/F4

## 目标

把数据产品页面从“名称、代码、说明”的基础 CRUD，升级为能打包数据集、指标、负责人、SLA 和消费入口的数据产品配置页。

## 技术设计

1. 新建/编辑数据产品时支持选择：
   - datasetIds；
   - indicatorCodes；
   - ownerDept；
   - freshness SLA；
   - lifecycle status；
   - consumer visibility。
2. 产品卡片展示成员数量、核心指标数、密级和最近刷新状态。
3. 保存前执行资产权限检查和治理缺口提示。

## 影响范围

- `source/dts-platform-webapp/src/pages/catalog/DataProductsPage.tsx`
- `source/dts-platform-webapp/src/api/platformApi.ts`
- 如后端 `DataProductsResource` 缺字段，需要补 DTO 与持久化。

## 验证

- [x] 数据产品页面已挂入数据资产门户菜单：`sys.nav.portal.dataPortalProducts` -> `/catalog/data-products`，角色默认菜单同步授权。
- [x] source-level test 通过：`pnpm exec tsx --test src/pages/catalog/DataProductsPage.source-contract.test.ts`。
- [x] source-level test 通过：`./node_modules/.bin/tsx --test src/pages/catalog/DataProductsPage.source-contract.test.ts`。
- [x] 前端 build 通过：`pnpm build` from `source/dts-platform-webapp`。
- [x] 后端编译通过：`./mvnw -q -pl dts-platform -DskipTests compile` from `source`。
- [x] 数据产品创建、编辑、删除和成员显示均可操作；成员资产、核心指标、密级、SLA、生命周期、可见性和消费入口已进入表单与卡片展示。

## 完成标准

- [x] 数据产品真正能把资产和指标打包，而不是仅保存文字信息。
