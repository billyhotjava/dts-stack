# F3: 血缘采集运营台

**优先级**: P0
**状态**: DRAFT（T01 契约可先行；T02 待 G0 的 B1 与开放问题 Q2）

## 目标

把 55 行两个按钮的「血缘导入」页（账本#3）升级为运营台，让用户能回答三个问题：**血缘是从哪来的？这次导入为什么只成功了几条？哪些血缘还没人核验？**

## 契约定义 (Contracts)

| 类型 | 契约 | 关键字段/签名 |
|---|---|---|
| REST | `POST /api/catalog/lineage/sync-addax`（**已存在**，账本#11 `:1117`） | 返回 `IngestionLineageWriter.LineageWriteResult`。本 Feature 扩展为含 `unmatched:[{sourceKey,odsTable,reason}]` 与 `truncated` |
| REST | `POST /api/catalog/lineage/import-dbt-manifest` | 消费 F1/T02 扩展后的 `skippedReasons` + `unmatched` |
| REST | `GET /api/catalog/assets-v2/lineage-failures`（**已存在**，账本#13，前端首次消费） | `CatalogLineageFailureReport`：`{items[], byReason{}, bySeverity{}, totalCount, ...}` |
| REST | **新增** `GET /api/catalog/lineage/sources/health` | 返回四路采集源状态：`[{source:"ADDAX"\|"DBT"\|"AIRFLOW"\|"AUTO_VIEW", edgeCount, lastObservedAt, jobCount, status:"ACTIVE"\|"STALE"\|"NOT_CONFIGURED"}]` |
| REST | **新增** `GET /api/catalog/lineage/pending-verification` | 分页返回 `verification_status in ('DECLARED','KNOWN_UNVERIFIED')` 且 `valid_to is null` 的边；参数 `page/size/relationType` |
| 数据 | 无新表、无迁移 | 全部为查询期聚合 |

## UI/UX 规格

### 入口与导航

沿用既有路由 `/catalog/lineage/import`，**不新增菜单项**（ADR-90-05）。菜单标题由「血缘导入」改为「采集与核验」（仅改 `portal-menu-seed.json` 的 `title`，`key`/`path`/`externalLink` 不变，避免破坏既有深链与角色授权）。

### 布局线框

```
┌ 血缘与影响分析 / 采集与核验 ─────────────────────────────┐
│ [影响分析][血缘图谱][字段血缘][采集与核验][快照对比]      │
├──────────────────────────────────────────────────────────┤
│ ┌ 采集源健康 ────────────────────────────────────────┐  │
│ │ ADDAX   1,204 边  最近 2h 前   [ACTIVE]  [立即同步] │  │
│ │ DBT       318 边  最近 1d 前   [ACTIVE]  [导入 ⬆]  │  │
│ │ AIRFLOW     0 边  —            [未接入]            │  │
│ │ AUTO_VIEW  42 边  最近 3h 前   [ACTIVE]            │  │
│ └────────────────────────────────────────────────────┘  │
│ ┌ 待核验队列 ────────┐ ┌ 血缘缺口 ───────────────────┐ │
│ │ DECLARED       128 │ │ 未匹配来源      12          │ │
│ │ KNOWN_UNVERIFIED 7 │ │ 孤立资产（无血缘） 43        │ │
│ │        [去核验 →]  │ │            [查看明细 →]     │ │
│ └────────────────────┘ └─────────────────────────────┘ │
│ ┌ 最近一次导入结果 ──────────────────────────────────┐  │
│ │ 新建 12 · 跳过 7 · 共 40                           │  │
│ │ 跳过原因: 非模型 3 · 结构非法 1 · 下游未匹配 2 ·   │  │
│ │           上游未匹配 1                             │  │
│ │ ┌ 未匹配明细（表格，可搜索）─────────────────────┐ │  │
│ │ │ uniqueId | 名称 | schema | 原因               │ │  │
│ │ └───────────────────────────────────────────────┘ │  │
│ └────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────┘
```

### 四态

| 态 | 表现 |
|---|---|
| 空 | 采集源全 0 → 卡片显示「尚未采集到任何血缘」+ 指向「立即同步」；导入结果区显示「本次会话尚未执行导入」 |
| 加载 | 各卡片独立 Skeleton；同步/导入按钮 loading 且互斥禁用 |
| 错误 | 健康接口失败 → 卡片内 Alert + 重试按钮（不整页崩）；同步失败 → 结果区显示错误摘要，不清空上次结果 |
| 成功 | 结果卡片渲染分桶 + 未匹配明细表；健康卡片自动刷新 |

### 关键交互

- 「立即同步」→ `sync-addax` → 结果写入「最近一次导入结果」区（**会话内内存态**，不落库，文案需明确"仅本次会话"）。
- 「导入 ⬆」→ 沿用既有 `Upload beforeUpload` 上传 manifest → 同一结果区渲染。
- 「去核验 →」→ 跳 `/catalog/lineage/impact?verification=KNOWN_UNVERIFIED`，由 F4/T02 的统一 URL 协议承接该参数。
- 「查看明细 →」→ 展开 `lineage-failures` 报告表格。
- AIRFLOW 为「未接入」时，卡片给出只读说明（接收端地址 + 需由 Airflow 侧配置），**不提供配置入口**（非目标）。

### 操作走查 (happy path)

1. 进入 数据治理 > 血缘与影响分析 > 采集与核验
2. 看到四路采集源状态，发现 DBT 最近观测是 7 天前
3. 点「导入 ⬆」选 manifest.json
4. 结果区显示「新建 12 · 跳过 7」与四类分桶
5. 展开未匹配明细，看到 2 个模型因 schema 不匹配未连上
6. 点「去核验 →」跳到影响分析，筛选出 KNOWN_UNVERIFIED 的边逐条核验

### 可访问性/兼容

- 卡片使用语义化 `<section aria-labelledby>`；状态徽标不单靠颜色区分（附文字）。
- 未匹配明细表遵循项目分页统一约定（默认 10 条/页、切换条数刷新）。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 采集源健康与待核验队列契约 | P0 | READY | F1/T02（导入返回体） |
| T02 | 血缘采集运营台页面重构 | P0 | DRAFT | T01、F2/T02；G0 的 B1、开放问题 Q2 |

## Definition of Ready

- [x] 契约已钉死（两个新接口的响应结构、两个既有接口的消费方式均已写明）
- [x] 竖切片已画通（卡片 → 接口 → Repository 聚合 → 无新表）
- [x] UI 落点已命名（路由复用、线框、四态、走查）
- [ ] 依赖已就绪 —— T02 待 B1；AIRFLOW 卡片形态待 Q2 实测
- [x] 验收可验证

## 完成标准

- [ ] 两个新接口有契约测试；`lineage-failures` 前端首次真实消费
- [ ] 运营台四态证据齐全（IT-05）
- [ ] 菜单标题改为「采集与核验」且 `key`/`externalLink` 未变（既有深链与角色授权零回归）
- [ ] 页面文件不超过项目 800 行上限，卡片拆为独立组件
- [ ] 「未接入」态如实呈现，不伪造 AIRFLOW 数据
