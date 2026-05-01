# F5: 数据资产门户重构

**优先级**: P0  
**状态**: IN_PROGRESS

## 目标

让数据资产门户真正使用 OpenMetadata 技术资产能力，同时保留 DTS 治理属性和工作流入口。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 数据资产列表切换到聚合 API | P0 | DONE | F4 |
| T02 | 资产卡片/表格展示 OM 技术状态和 DTS 治理状态 | P0 | DONE | T01 |
| T03 | 资产详情技术信息、字段、profile 和 raw metadata 展示 | P0 | IN_PROGRESS | T01 |
| T04 | 认领、定级、归域、生命周期更新入口 | P0 | IN_PROGRESS | F2,T03 |
| T05 | 待治理工作台与映射异常提示 | P1 | DONE | F3,T01 |

## 完成标准

- [x] OpenMetadata 中已同步资产可在数据资产门户看到。
- [x] 用户能看出资产是否已治理、是否待定级、是否映射异常。
- [x] 资产详情不再强制 `catalog:<datasetId>` 走本地详情。
- [x] 字段和 profile 来自 OM cache，密级和权限来自 DTS extension。
- [x] 资产治理动作有清晰入口和状态反馈。
