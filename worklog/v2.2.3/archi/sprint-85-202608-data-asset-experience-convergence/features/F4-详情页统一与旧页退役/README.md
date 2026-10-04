# F4：详情页统一与旧页退役

**优先级**：P1
**状态**：IMPLEMENTATION_DONE

## 目标

`DatasetDetailPage` 只消费 assets-v2 事实源；旧 `AssetDetailPage` 抽屉与 `/catalog/asset-detail` 路由收敛。

## 契约

| UI | 行为 |
|---|---|
| `DatasetDetailPage` | 移除 legacy 优先解析与 `__source` 渲染分支；`getDataset` 仅用于 legacyId → 详情路由解析；治理责任等 Tab 只渲染 assets-v2 语义组件 |
| `/catalog/asset-detail` | 410 + 重定向到 `/catalog/datasets/{id}`（有可解析 id 时）或台账 |
| `AssetDetailPage.tsx` | 删除（连同其 8 路并行拉取与重复筛选持久化） |

## Task 表

| ID | Task | 验收 |
|---|---|---|
| T01 | 详情页单一事实源改造（解析与 Tab 渲染） | 组件/契约测试：无 `__source` 分支 |
| T02 | `/catalog/asset-detail` 410 收敛 + 跳转 | 路由契约测试 |
| T03 | 删除 `AssetDetailPage` 并清理其依赖（抽屉入口、旧深链、筛选 key） | 源码门禁：文件不存在 |
| T04 | F4 聚焦回归与构建 | Vitest + build |

## 完成标准

- 同一资产 URL 在任何部署下渲染同一套页面。
- 旧深链有明确跳转（410 + 目标页），无 404 死角。
