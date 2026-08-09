# F2：血缘展示收敛与图谱体验

**优先级**：P0
**状态**：IMPLEMENTATION_DONE

## 目标

血缘只讲一个故事（DTS 本地影响链）；图谱节点身份稳定、筛选 URL 化、深链可回；契约下沉解除组件→页面反向依赖。

## 契约

| UI | 行为 | 数据源 |
|---|---|---|
| 资产详情"血缘与影响"Tab | 只渲染 DTS 本地影响链（mini 图 + 统计）；OM 血缘缓存改为"同步状态与证据说明"（来源/时间/条数 + 同步按钮），不再渲染第二张图 | `/catalog/lineage/impact` + `/assets-v2/{id}/lineage`（仅证据） |
| 血缘图谱 | 关键词输入即过滤（移除"高亮但不过滤"双语义）；列节点 id 规范化 `{dsid}:{col}`；作业/源节点 id 使用可稳定复现形式（服务端已可复现，前端不再自行拼接） | `/catalog/lineage/impact` |
| 筛选状态 | 血缘五子页筛选（dataset/direction/depth/layers/withJobs/withColumns/at）进入 URL，刷新恢复；支持 `?datasetId=` 深链（详情 Tab → 图谱） | 前端 URL 协议 |
| 契约层 | 血缘 DTO 类型迁至 `src/features/catalog/lineageContracts.ts`；`LineageGraph` 与各血缘页面均从契约层导入 | 前端 |

## Task 表

| ID | Task | 验收 |
|---|---|---|
| T01 | 详情血缘 Tab 收敛为单引擎 + OM 证据说明组件 | 组件测试：不再渲染 OM 图组件 |
| T02 | 血缘契约下沉（`lineageContracts.ts`），`LineageGraph`/页面改引用 | 源码门禁：`components/lineage` 不再 import `pages/**` |
| T03 | 图谱关键词即过滤；列节点 id 规范化 | `LineageGraph` 测试 |
| T04 | 血缘筛选 URL 化 + `datasetId` 深链消费 | URL 协议契约测试 |
| T05 | F2 聚焦回归与构建 | Vitest + build |

## 完成标准

- 一个资产在血缘 Tab 只看到一种血缘语义。
- 图谱 URL 可分享、可刷新恢复；详情 Tab"查看完整图谱"深链能定位到同一数据集。
- 组件层与页面层无反向依赖。
