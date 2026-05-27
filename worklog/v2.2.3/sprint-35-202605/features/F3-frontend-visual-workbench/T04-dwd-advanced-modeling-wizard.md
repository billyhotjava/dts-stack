# T04: DWD 高级建模向导

**优先级**: P0
**状态**: READY
**依赖**: T02,T03

## 目标

提供受控的 DWD -> DWS 高级建模向导，用于缺少 DWS 时生成候选汇总模型，而不是让 DWD 明细直接进入看板消费。

## 技术设计

- 向导步骤：选择 DWD 明细资产 -> 选择 grain/primary keys -> 选择标准维度 -> 选择指标聚合 -> Join/fanout 检查 -> 生成 DWS 候选。
- UI 明确标识“候选 DWS，需 platform/dbt validation”。
- 向导只允许 DWD 资产，不允许 ODS/STG。
- fanout 风险、粒度缺失、标准码缺失、RLS/masking 缺失以阻断诊断展示。

## 影响范围

- `source/dts-metrics-webapp/src/pages/semantic/SemanticDesignerPage.tsx`
- `source/dts-metrics-webapp/src/features/semantic/**`
- `source/dts-metrics-webapp/test/source-contract.test.mjs`

## 验证

- [ ] DWD 向导缺 grain 时不能进入下一步。
- [ ] DWD 候选模型生成后进入 validation，不进入 publish。
- [ ] Playwright 覆盖 DWD 失败和成功路径。

## 完成标准

- [ ] DWD 只服务生成 DWS 候选，不成为默认消费资产。
