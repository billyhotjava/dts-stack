# T04: 治理缺口和血缘失败的前端处置流

**优先级**: P0
**状态**: DONE
**依赖**: Sprint-31A F2/F3

## 目标

让资产地图中的治理阻断、血缘证据缺口不只是 Alert 文本，而是可点击、可筛选、可跳转处理的工作台。

## 技术设计

1. 展示 `/api/catalog/assets-v2/governance-gaps` 明细列表；
2. 展示 `/api/catalog/assets-v2/lineage-failures` 明细列表；
3. 每条缺口提供处理动作：
   - 补 owner / ownerDept；
   - 补 classification；
   - 补 warehouseLayer；
   - 同步 lineage；
   - 打开资产详情；
   - 创建治理问题工单。
4. 处理后刷新当前筛选和统计。

## 影响范围

- `source/dts-platform-webapp/src/pages/catalog/DatasetsPage.tsx`
- `source/dts-platform-webapp/src/pages/catalog/DatasetDetailPage.tsx`
- `source/dts-platform-webapp/src/api/platformApi.ts`

## 验证

- [x] source-level test 通过：`./node_modules/.bin/tsx --test src/pages/catalog/DatasetsPage.remediation.source-contract.test.ts src/pages/catalog/DatasetDetailPage.source-contract.test.ts`。
- [x] 前端 build 通过：`pnpm build` from `source/dts-platform-webapp`。
- [x] 缺口列表、跳转和刷新已有前端源码契约：治理缺口弹窗、血缘失败弹窗、资产详情 tab 深链和单资产 lineage sync。

## 完成标准

- [x] 用户能从数据资产中心直接处理阻断项，而不是只能看到汇总数字。
