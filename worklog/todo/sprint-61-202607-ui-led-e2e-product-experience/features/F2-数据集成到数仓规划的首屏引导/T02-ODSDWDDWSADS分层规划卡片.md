# T02: ODS/DWD/DWS/ADS 分层规划卡片

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

在 UI 上明确数据从接入到数仓分层的规划草稿，而不是直接把用户丢到 SQL。

## 技术设计

- 在工作台或低代码向导增加分层规划卡片。
- 展示 `ODS_RAW`、`ODS_STANDARDIZED`、`DWD`、`DWS`、`ADS` 的用途、状态和下一步。
- 初期可以用前端草稿或已有状态推导；缺 API 时显示 blocker。

## 影响范围

- `DataManagementWorkbenchPage.tsx`
- `LowCodeDevelopmentPage.tsx`
- `dataDevelopmentWorkbench.source-contract.test.ts`

## 验证

- [ ] source-contract 断言分层名称和说明。
- [ ] Playwright smoke 检查窄屏不溢出。

## 完成标准

- [ ] 客户能理解数据先规划分层，再进入模型和 SQL 微调。
