# F2: 人工血缘登记与核验闭环

**优先级**: P0
**状态**: DRAFT（T01/T02 契约已钉死可转 READY；T03 依赖 G0 的 B1/B3）

## 目标

治理员在血缘页面就能补录采集漏掉的血缘、软失效错误的血缘、把存疑血缘显式核验为可信——血缘从"只读展示"变成"可治理资产"。

## 契约定义 (Contracts)

| 类型 | 契约 | 关键字段/签名 |
|---|---|---|
| REST | `POST /api/catalog/lineage`（**已存在**，账本#6 `:1059`） | body: `{upstreamDatasetId, downstreamDatasetId, relationType, notes, upstreamAssetType, downstreamAssetType, direction, projectName}`；resp: edge DTO（与 `toEdgeDto` 同形）。本 Sprint 补：`relationType` 缺省为 `MANUAL`、自环拒绝、重复当前有效边 409 |
| REST | `DELETE /api/catalog/lineage/{id}?force=false`（**已存在**，`:1271`） | `force=false` → 软失效（置 `valid_to = now()`）；`force=true` → 物理删除，需更高权限。resp: `{softDeleted: boolean}` |
| REST | **新增** `PATCH /api/catalog/lineage/{id}/verification` | body: `{verificationStatus: "DECLARED"\|"KNOWN_UNVERIFIED"\|"VERIFIED", note?: string(≤512)}`；resp: 更新后的 edge DTO |
| 数据 | `catalog_dataset_lineage` 新增列 | `last_verified_by varchar(64)`、`verification_note varchar(512)`；既有 `last_verified_at` 若已存在则复用，否则一并新增 |
| 迁移 | `20260811_01_catalog_lineage_verification_actor.xml` | Expand-only：仅 `addColumn`，无默认值、无回填 |
| 审计 | `CATALOG_LINEAGE_CREATE` / `CATALOG_LINEAGE_DELETE` / `CATALOG_LINEAGE_VERIFY` | 与既有 `CATALOG_LINEAGE_VIEW`（`:141`）同规格，记录操作人与前后状态 |
| 前端 API | `api/platformApi.ts` | 复活既有 `createCatalogLineage` / `deleteCatalogLineage`（账本#6 死代码），新增 `verifyCatalogLineage(id, body)` |

### 采集不覆盖人工结论（ADR-90-04）

`IngestionLineageWriter` / `CatalogAutoLineageService` / `CatalogDbtLineageService` / `OpenLineageReceiverResource` 四个写入点（账本#10）在更新既有边时，若该边 `verification_status = 'VERIFIED'` 且 `last_verified_by is not null`，**保留人工结论**，只更新 `last_observed_at` 等观测字段。

## UI/UX 规格

### 入口与导航

不新增菜单。三处具名入口：

| 入口 | 位置 | 触发 |
|---|---|---|
| 「登记血缘」 | `/catalog/lineage/impact` 卡片 `extra` 区，与既有「导出结果」并列 | 打开登记抽屉 |
| 行内「核验 / 失效」 | 影响分析页「节点与关系」卡片中**边表**新增操作列 | 打开核验弹窗 / 二次确认软失效 |
| 「待核验 N 条」 | `/catalog/lineage/import` 运营台顶部（F3/T02 装配） | 带 `?verification=KNOWN_UNVERIFIED` 跳回影响分析 |

### 布局线框

```
┌ 登记血缘（Drawer, width 520）──────────────┐
│ 上游数据集  [Select 服务端搜索 ▾]  *必填    │
│ 下游数据集  [Select 服务端搜索 ▾]  *必填    │
│ 关系类型    [MANUAL ▾]（只读说明：人工声明） │
│ 项目名      [Input 可选]                    │
│ 备注        [TextArea ≤512]                 │
│ ─────────────────────────────────────────── │
│ ⚠ 人工血缘将以 DECLARED 状态登记，需核验     │
│                        [取消] [登记]        │
└─────────────────────────────────────────────┘

┌ 边表操作列 ─────────────────────────────────┐
│ 上游 | 下游 | 关系 | 验证 | ... | [核验▾][失效]│
│                                  └ 已核验    │
│                                  └ 存疑      │
│                                  └ 撤回为声明│
└─────────────────────────────────────────────┘
```

### 四态

| 态 | 登记抽屉 | 核验操作 |
|---|---|---|
| 空 | 表单初始，两个 Select 为空，「登记」禁用 | 边表为空时不显示操作列 |
| 加载 | 「登记」loading，表单禁用 | 行内按钮 loading |
| 错误 | 409 → 表单顶部 Alert「该血缘关系已存在」；403 → 「无权限操作该数据集」 | toast + 状态回滚（乐观更新失败回退） |
| 成功 | toast + 抽屉关闭 + 影响分析自动重查 | 该行「验证」Tag 就地变色 + toast |

### 关键交互

- 登记成功后**不做乐观插入**（新边是否落入当前 depth/direction 视图不确定），直接重跑 `runSearch`，避免呈现假状态。
- 核验做**乐观更新**：先改本地 Tag 颜色，失败回滚并 toast。
- 软失效需二次确认，文案明确"软失效，历史快照仍可查到"（ADR-90-03）。

### 操作走查 (happy path)

1. 进入 数据治理 > 血缘与影响分析 > 影响分析
2. 选中数据集，发现某上游未连线
3. 点右上「登记血缘」→ 抽屉打开
4. 上游选 A、下游选当前数据集、备注填「人工补录：Addax 未覆盖」
5. 点「登记」→ toast「已登记」→ 图/表刷新，新边以虚线（MANUAL + DECLARED）呈现
6. 在边表该行点「核验 ▾ > 已核验」→ 填核验说明 → 「验证」列 Tag 变绿
7. 切「血缘图谱」→ 该边实线呈现

### 可访问性/兼容

- 抽屉支持 Esc 关闭、Tab 顺序为上游→下游→关系→项目→备注→按钮。
- 操作列按钮有 `aria-label`（如 `核验血缘关系 A→B`）。
- Chrome 95 下 Drawer + Dropdown 需实测（F5/T02）。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 人工血缘登记与软失效契约 | P0 | READY | - |
| T02 | 血缘核验状态流转契约 | P0 | READY | T01（共用迁移） |
| T03 | 登记抽屉与核验操作 UI | P0 | DRAFT | T01、T02；G0 的 B1/B3 |

## Definition of Ready

- [x] 契约已钉死（三个 REST 契约、两列迁移、四个审计事件均已写明字段与类型）
- [x] 竖切片已画通（按钮 → API → Resource → 表 → 迁移，无 TBD）
- [x] UI 落点已命名（三处入口 + 线框 + 四态 + 走查）
- [ ] 依赖已就绪 —— T03 待 G0 的 B1（运行实例/账号）与 B5（写权限，开放问题 Q4）
- [x] 验收可验证

**T03 保持 DRAFT 直至 B1/B5 关闭。**

## 完成标准

- [ ] 三个接口各有契约测试（含 409、403、自环拒绝）
- [ ] 迁移在干净库可执行，且为 Expand-only
- [ ] 采集重跑不覆盖 `VERIFIED` 人工结论——有回归测试
- [ ] 登记抽屉与核验操作四态证据齐全（IT-03、IT-04）
- [ ] 三个审计事件在审计库可查
