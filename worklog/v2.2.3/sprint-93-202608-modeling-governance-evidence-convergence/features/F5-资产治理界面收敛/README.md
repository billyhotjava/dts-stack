# F5：资产治理界面收敛

**优先级**：P1
**状态**：IMPLEMENTATION_COMPLETE / MODULE_VERIFIED / FINAL_BROWSER_IT_PENDING

## 目标

让用户在现有模型工作台和资产治理页面中看到同一资产身份、五轴状态、服务同步、工程/治理质量、血缘和消费资格，不新增菜单或重复工作台。

## 契约定义

| 类型 | 契约 | 关键字段 |
|---|---|---|
| 目录 DTO | 兼容扩展 `AssetSummary` | statusAxes/eligibility/reasonCodes/projectionUpdatedAt/modelRefs |
| 概览 | 统一 stats projection | total/byDomain/governance/eligibility/asOf |
| 详情 | 既有 `/catalog/datasets/:id` 与 governance-health | model/candidate/materialization/sync/quality/lineage 深链 |
| 模型工作台 | 既有页面状态区 | assetId/assetKey/syncStatus/qualityEvidence/servingRef |

## UI/UX 规格

- 不改变 Sprint-88 资产概览布局，只替换统计事实和术语。
- 目录 Table 增加消费资格、服务状态和质量状态筛选/列，默认仍 10 条/页。
- 资产详情在现有 Tab 中补模型与交付证据，不新建详情页。
- 元数据入口保留两个现有路由，以清晰标题区分技术采集和业务治理。
- 全部 Catalog `domainId` 文案统一为“业务归属数据域”。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 统一概览目录详情与模型工作台状态和深链 | P1 | IMPLEMENTATION_COMPLETE | IT-09/Chrome 95 待执行 |

## Definition of Ready

- [x] 页面、路由、控件和四态已命名。
- [x] DTO 和状态 owner 已由 F1～F4 固定。
- [ ] 真实登录与 Chrome 95 基线通过。

## 完成标准

- [ ] 同一资产在概览/目录/详情/模型页状态一致。
- [ ] 所有深链可达且不出现空详情/403。
- [ ] 空/加载/错误/成功四态和 Chrome 95 有证据。
- [ ] 无新菜单、页面、前端依赖或 N+1 请求。
