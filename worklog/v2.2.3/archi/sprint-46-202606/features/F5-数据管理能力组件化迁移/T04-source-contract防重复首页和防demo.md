# T04: source-contract 防重复首页和防 demo

**优先级**: P0  
**状态**: DONE  
**依赖**: T01/T02/T03

## 目标

用 source-contract 锁住产品边界，防止重复工作台和客户业务 demo 场景回流。

## 技术设计

契约断言：

- 静态路由、动态 resolver、菜单 seed 只暴露一个工作台首页。
- 禁止在首页注册表出现 `经营分析`、`质量管理`、`项目交付`、`客户服务` 作为内置场景。
- 首页组件按钮必须有真实目标路由或明确禁用原因。
- 首页组件不得写死演示统计。

## 影响范围

- `source/dts-platform-webapp/src/pages/workbench/*.source-contract.test.ts`
- `source/dts-platform-webapp/src/pages/services/*.source-contract.test.ts`
- `source/dts-admin/src/main/resources/config/portal-menu-seed.json`

## 验证

- [x] RED: 先写契约，确认当前重复入口或 demo 断言失败。
- [x] GREEN: 迁移完成后契约通过。
- [x] `node --test --experimental-strip-types src/pages/workbench/*.source-contract.test.ts`

## 完成标准

- [x] 重复首页和 demo 场景有自动化防线。
- [x] 后续新增组件必须进入注册表契约。
