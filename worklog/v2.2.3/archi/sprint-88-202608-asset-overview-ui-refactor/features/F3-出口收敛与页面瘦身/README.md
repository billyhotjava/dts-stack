# F3: 出口收敛与页面瘦身

**优先级**: P0
**状态**: IMPLEMENTATION_DONE

## 目标

资产概览页对外跳转目标收敛为**唯一一个** `/catalog/search?view=table`，删除分层矩阵、待处置 Top5、血缘图谱与权限申请入口，`AssetOverviewPage.tsx` 从 574 行降至 ≤ 260 行，并把失效的契约测试全量改写为新形态的守卫。

## 契约定义 (Contracts)

| 类型 | 契约 | 关键字段/签名 |
|------|------|---------------|
| 路由（不变） | `/catalog/assets` → `AssetOverviewPage` | `static-routes.tsx` 与 `dynamic-resolver.tsx` 映射保持不变 |
| 路由（不变） | `/catalog/assets/ledger` → `LegacyAssetLedgerRedirect` → `/catalog/search?view=table` | 账本 #11，**本 Feature 不动** |
| 深链兼容（不变） | `?tab=catalog-tags`、`?view=table` 的 replace 重定向逻辑 | `AssetOverviewPage.tsx:93-101` 原样保留 |
| 出口（收敛后唯一） | `/catalog/search?view=table[&domain=][&unclassified=1][&governance=]` | 目标页消费点见账本 #11 |
| 契约测试（改写） | `AssetOverviewPage.source-contract.test.ts` | 见下方 §新契约条款 |

### 删除清单（逐项列出，实施时对照勾除）

| 删除对象 | 位置 | 理由 |
|---|---|---|
| 分层×主题域矩阵渲染块 | `AssetOverviewPage.tsx:417-532` | ADR-88-03 |
| `matrixHeatTone` | `:30-38` | 随矩阵 |
| `MATRIX_MAX_COLUMNS` / `MERGED_DOMAIN_KEY` | `:24,:27` | 随矩阵 |
| `matrixColumns` / `matrixCellMap` / `isSingleDomainScope` memo | `:209-273,:246` | 随矩阵 |
| `drillToLedger(layer, domainKey)` | `:191-207` | 随矩阵；出口改由各图表回调直接拼装 |
| 「待处置 Top 5」块 | `:534-569` | 用户未选此模块；其数据源 `listCatalogAssetsV2` 一并停用 |
| `listCatalogAssetsV2` 调用与 `attentionRows` state | `:153-178,:81` | 随 Top5；**页面请求数由 3 降为 2** |
| `resolveAssetReadiness` import | `:10` | 随 Top5 |
| 「查看血缘图谱」按钮 | `:368-376` | ADR-88-05；血缘有一级菜单入口 |
| 「申请权限」按钮 | `:377-385` | ADR-88-05；权限有一级菜单入口 |
| 「当前范围」回显按钮 | `:356-365` | 降级为纯文本，范围信息移入页头 |
| `GitBranch` / `ShieldCheck` / `MapPin` icon import | `:3` | 随上述按钮 |

**保留不动**：`LAYER_META` / `LAYER_ORDER` 常量本身（账本 #15：台账与详情页仍在用，只停用于本页）、`truncated` Alert、页头刷新按钮、旧深链重定向。

### 新契约条款（改写后的 source-contract 测试须断言）

```
必须存在：
  - getCatalogAssetsOverview        # 数据源仍是聚合端点
  - getDomainTree({withStats:true}) # 单次取树与统计
  - DomainScopeNav                  # 复用共享导航
  - kpi-total / kpi-attention / kpi-unclassified / kpi-tag-coverage   # 四卡齐全
  - AssetGovernanceDonut / AssetDomainBars                            # 两图齐全
  - /catalog/search                 # 唯一出口
  - truncated                       # 截断提示未丢
  - searchParams.get("domain")      # 范围在 URL 不在 state
  - searchParams.get("tab") === "catalog-tags" + router.replace       # 旧深链兼容

必须不存在：
  - 分层×主题域矩阵 / asset-overview-matrix / matrixHeatTone / MERGED_DOMAIN_KEY
  - GovernanceGapPanel
  - listCatalogAssetsV2 / resolveAssetReadiness / 待处置 Top
  - /catalog/lineage/graph / dataset-access-approval          # 已删的两个出口
  - /catalog/assets/ledger                                    # 出口不再指向旧台账路由
  - <Select / Pagination / SearchOutlined / asset-ops-menu    # 零输入控件（沿用旧约束）
  - <Tree                                                     # 不自建 antd 树

数量约束：
  - <Button 出现次数 ≤ 2（1 主 CTA + 1 刷新 icon）
  - <MetricTile 出现次数 == 4
  - router.push 出现的路径字面量去重后 == 1（只有 /catalog/search）
```

最后一条是本 Feature 的核心守卫：**它把「只跳数据查询」变成机器可验证的约束**，防止后续迭代悄悄加回第二个出口。

## UI/UX 规格

### 入口与导航

页面顶部 chrome 收敛为一行：

```
资产概览                        当前范围：全部资产 · 统计更新于 23:11  ⟳
                                        [ 在数据查询中查看 → ]
```

- 「当前范围：X」为**纯文本**（原为按钮），范围切换的唯一途径是左侧导航
- 主 CTA「在数据查询中查看」：`type="primary"`，携带当前 `domain`
- 刷新为 icon-only `Button`，`aria-label="刷新统计"`
- `truncated` 时页头下方保留现有 `Alert`

### 四态

| 态 | 表现 |
|----|------|
| 加载 | 刷新按钮 `loading`；KPI 与图表各自骨架（由 F2 组件负责） |
| 空 | KPI 显 0，两图显空态文案；主 CTA **仍可点**（跳过去是空列表，但筛选条件真实） |
| 错误 | 全局 toast；页面结构保持，不白屏 |
| 成功 | 一屏完整呈现 |

### 关键交互

| 动作 | 触发契约 | 用户看到 |
|------|----------|----------|
| 点主 CTA | `/catalog/search?view=table` + 当前 `domain` | 跳数据查询表格视图，域筛选与概览一致 |
| 点刷新 | 重新调 `overview` + `domains/tree` | 按钮转圈，数字与图表更新，「统计更新于」时间刷新 |
| 从 `?view=table` 深链进入 | `router.replace('/catalog/assets/ledger?...')` → 再 302 到数据查询 | 无感跳到数据查询（行为与现网一致） |

### 操作走查 (happy path)

1. 进入 `/catalog/assets` → 页头一行，主区 KPI + 两图，全页一屏
2. 数页面上的跳转控件 → 只有主 CTA 一个显式按钮（外加 3 处图元下钻）
3. 点主 CTA → 跳 `/catalog/search?view=table`
4. 返回，左侧选「财务业务」→ 页头「当前范围」文本随之变化
5. 再点主 CTA → URL 含 `domain=<财务 uuid>`，目标页域筛选已生效
6. 直接访问 `/catalog/assets?view=table` → 无感跳到数据查询（旧深链未破）

### 可访问性/兼容

- 主 CTA 与刷新按钮均可键盘触达
- 「当前范围」为文本，不可聚焦（与其不可交互一致）
- Chrome 95：本 Feature 无新样式特性

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 页面装配、出口收敛与契约测试改写 | P0 | READY | F1/T01、F2/T01、F2/T02 |

## Definition of Ready

- [x] 契约已钉死（删除清单逐项定位到行号；新契约条款已写成可断言的正/反清单）
- [x] 竖切片已画通（页头 → 唯一出口 → 数据查询消费点，账本 #11 已验证）
- [x] UI 落点已命名（页头一行 + 主 CTA + 刷新 icon）
- [x] 依赖已就绪（F1/F2 产出的组件契约均已在各自 Task 中钉死）
- [x] 验收可验证（走查 6 步 + 契约测试正反清单 + 行数约束）

## 完成标准

- [ ] 删除清单 12 项逐项完成，全仓无残留引用
- [ ] 新契约测试全绿，含「push 路径字面量去重 == 1」这一守卫
- [ ] `AssetOverviewPage.tsx` ≤ 260 行
- [ ] 页面加载请求数由 3 降为 2（Network 面板取证）
- [ ] 走查 6 步通过，四态截图入 `it/`
