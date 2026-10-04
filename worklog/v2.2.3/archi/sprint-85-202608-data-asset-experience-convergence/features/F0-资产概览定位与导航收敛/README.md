# F0：资产概览（资产地图）定位与导航收敛

**优先级**：P0
**状态**：IMPLEMENTATION_DONE

## 目标

把 `/catalog/assets` 从"叫地图但不是图"的困惑中解放出来：定位为**治理概览仪表盘**，修正误导性 drill-down，并成为资产域的导航中枢（概览 → 台账/血缘/权限）。

## 契约

| UI | 行为 | 数据源 |
|---|---|---|
| 页面标题/菜单文案 | "资产地图" → "资产概览"（菜单种子 + 页面标题同步，`portal-menu-seed.json` 与 `AssetOverviewPage.tsx`） | 静态 |
| 分层×域矩阵 | 单元格深链 `/catalog/assets/ledger?layer=&domain=`；**移除 `__OTHERS__` 伪列**，改为服务端按"其他域"聚合（`overview` 返回 `othersCount` 与可点击的"查看其他域台账"入口，不带域过滤但带明确文案） | `/catalog/assets-v2/overview`（聚合器返回结构扩展） |
| 治理缺口面板 | 缺口原因 drill-down 贯通：为台账增加 `?governance=` 过滤（已有）+ 新增 `?unclassified=1`/`?stale=1` 参数并由台账消费；不再静默丢弃 | 台账 URL 协议（F1 统一） |
| 待处置 Top5 | 卡片 → `/catalog/datasets/{id}` 详情（保持） | 现有 |
| 新增"查看血缘图谱"入口 | 概览页顶部快捷入口 → `/catalog/lineage/graph`（带默认根数据集选择器） | 血缘契约 |
| 新增"申请权限"入口 | 概览页"权限申请"按钮 → `/security/dataset-access-approval?action=new` | 静态导航 |

## Task 表

| ID | Task | 验收 |
|---|---|---|
| T01 | 文案更名与菜单同步（种子 + 页面 + 路由 title + 帮助中心） | 源码门禁：无"资产地图"旧文案残留（除历史/重定向说明） |
| T02 | 服务端 overview 增加"其他域"聚合（`othersCount`、`othersAttention`、按层）并扩展契约测试 | `CatalogAssetOverviewAggregatorTest` 扩展 RED→GREEN |
| T03 | 矩阵渲染改用"其他域"入口，移除 `__OTHERS__` 深链；缺口 drill-down 增加 `unclassified/stale` 参数贯通台账 | 组件测试断言不再产生 `__OTHERS__` 深链 |
| T04 | 概览页增加血缘图谱与权限申请快捷入口；台账/血缘/权限深链参数被目标页消费 | 契约测试 + 手动深链验证 |
| T05 | F0 聚焦回归与构建 | Vitest 该域全绿 + `LEGACY_BROWSER_BUILD=1 pnpm build` |

## 完成标准

- 页面不再出现"其他 N 个域"点击后落到全量域的误导深链。
- 治理缺口三种原因都能真实钻到台账且结果与原因一致。
- 概览页能一步到达台账、血缘图谱、权限申请，且目标页正确消费参数。
