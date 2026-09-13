# T03: 前端展示真实契约与导入状态

**优先级**: P0  
**状态**: READY  
**依赖**: T02

## 目标

把 metro-stack 前端 `数据与特征` 页从固定 demo contract 切换为真实 snapshot 状态。

## 技术设计

页面行为：

- 输入或选择 DTS snapshot 目录。
- 调用 `/api/ml/data-contract/validate` 校验真实包。
- 展示 manifest、schema、quality、lineage、data.csv 状态。
- 点击“创建训练任务”后调用 `/api/batches/from-training-snapshot`。

## 影响范围

- `/opt/prod/metro-app/sources/metro-stack/frontend/src/api.ts`
- `/opt/prod/metro-app/sources/metro-stack/frontend/src/pages/PackDashboard.tsx`

## 验证

- [ ] `pnpm build` 通过。
- [ ] 无真实包时页面显示明确空状态。
- [ ] demo 模式只作为显式“加载示例”动作。

## 完成标准

- [ ] 客户看到的是 DTS 快照路径和真实契约，不是写死演示数据。
