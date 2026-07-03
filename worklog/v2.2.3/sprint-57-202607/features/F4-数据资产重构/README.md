# F4: 数据资产重构 —— 资产地图与台账分离

**优先级**: P0
**状态**: IN_PROGRESS

## 目标

资产地图（`/catalog/assets`）与资产台账（`/catalog/assets?view=table`）目前由 DatasetsPage（1422 行）内一个 `viewMode` 开关承载，除标题外体验高度趋同。重构为两个职责清晰的体验：**地图 = 主题域×数仓分层的可视化导航与治理健康总览**，**台账 = 登记/权属/密级/治理/消费出口的核验工作台**。

## 约束

- URL 兼容：`/catalog/assets` 与 `?view=table` 均不能失效（菜单 seed 已引用）。
- TDD：每个任务先补 source-contract / 单测（RED），再实现（GREEN）。
- DatasetsPage 拆分后单文件 ≤800 行。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 现状审计与拆分设计（地图/台账职责边界、组件抽取清单） | P0 | DONE (6f095535c) | - |
| T02 | 资产地图重构（分层×主题域矩阵、就绪度染色、点击下钻） | P0 | DONE (bdd355cee) | T01 |
| T03 | 资产台账重构（核验列组、筛选/导出、批量操作） | P0 | DONE (2c49a7e8d，批量操作并入 F5-T04 联动) | T01 |
| T04 | 拆分收尾（DatasetsPage 瘦身、路由兼容验证、既有契约测试全绿） | P1 | READY | T02,T03 |

## 完成标准

- [ ] 地图与台账为两套明显不同的信息架构（截图对比留档 assets/）
- [ ] 既有 `DatasetsPage.asset-map-visual.source-contract.test.ts` 等契约测试全绿或按新契约更新
- [ ] 拆分后各文件 ≤800 行；`?view=table` 深链仍可直达台账
