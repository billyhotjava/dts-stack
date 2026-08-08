# F1：资产台账与搜索单一事实源

**优先级**：P0
**状态**：DESIGN_APPROVED / IMPLEMENTATION_PENDING

## 目标

台账与搜索只消费 assets-v2 一个事实源；移除 legacy 双轨渲染与搜索三路并行；统一筛选状态协议，跨页共享、双向同步。

## 契约

| UI | 行为 | 数据源 |
|---|---|---|
| 资产台账（ledger） | 移除 `ASSET_PORTAL_V2_ENABLED=false` 分支；仅 `listCatalogAssetsV2`；legacy `listDatasets` 仅在深链 legacyId 解析时使用 | `/catalog/assets-v2` |
| 数据搜索 | 单路径：`listCatalogAssetsV2` + 可选 tag 精确索引合并；移除 `searchCatalog` 并行路与按 key 去重丢弃 | assets-v2 + tags |
| 统一筛选状态 | URL query 协议 `domain/layer/assetType/classification/governance/unclassified/stale/tags/query` + 单一 localStorage key `catalog.asset.filter.v2`；台账↔搜索"应用资产筛选"双向同步 | 前端状态协议 |
| 编辑态兼容 | 老 localStorage 旧 schema 读取时迁移/忽略并提示一次 | 前端 |

## Task 表

| ID | Task | 验收 |
|---|---|---|
| T01 | 台账移除 legacy 分支，强制 assets-v2；空态/错误态文案更新 | 组件测试：无 `listDatasets` 调用分支 |
| T02 | 搜索单路径化：移除三路并行与 key 去重，统一结果分组合并逻辑 | `DataSearchPage` 测试 RED→GREEN |
| T03 | 统一筛选协议落地（URL + localStorage）；台账新增 `unclassified/stale` 过滤消费（承接 F0/T03） | URL 协议契约测试 |
| T04 | 台账↔搜索双向同步筛选（共享 key + 事件/路由触发） | 契约测试 |
| T05 | 旧 key 迁移与兼容；F1 聚焦回归与构建 | Vitest + build |

## 完成标准

- 台账与搜索页面源码中无 legacy 渲染分支；同一部署下字段/过滤/结果形态一致。
- 在台账设置的筛选能一键带到搜索页（反之亦然），URL 可直接分享。
