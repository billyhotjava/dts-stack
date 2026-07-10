# T02: 主题域页规划卡片与 DWD 维度入口

**优先级**: P0  
**状态**: READY  
**依赖**: T01

## 目标

在选中主题域后显示规划状态，并提供进入数据标准和维度建模的明确动作。

## 技术设计

- `SubjectAreasPage` 读取当前主题域和规划上下文。
- 第一阶段固定规划为 `DWD` + `维度建模`，不新增 DIM 层。
- 进入数据元页携带 `planningId/domainId/warehouseLayer/modelingMode`。

## 影响范围

- `source/dts-platform-webapp/src/pages/governance/SubjectAreasPage.tsx`
- `SubjectAreasPage.source-contract.test.ts`

## 验证

- [ ] 选中主题域后规划卡片可见。
- [ ] 跳转数据元页保留规划上下文。
