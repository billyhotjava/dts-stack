# F2：自助分析设计服务

**优先级**：P0
**状态**：DRAFT（依赖 F1）

## 目标

以 DTS 自有 `AnalysisQuerySpec dts.analysis/v1` 替代新建 Question/MBQL 写入，同时复用 `analytics_card`、revision 和现有可视化能力，形成可保存、可校验、可版本化的业务分析服务与单一编辑器。

## AnalysisQuerySpec v1

```text
AnalysisQuerySpec {
  apiVersion: "dts.analysis/v1",
  dataset: {id: UUID, version: int, contractVersion: string, checksum: string},
  dimensions: [{field, alias?}],
  metrics: [{code, alias?}],
  derivedMetrics: [{code, expression, format?}],          // max 20
  filters: [{field, op, values[], required?}],            // max 50
  timeRange?: {field, start, end, grain},
  orderBy: [{field, direction: ASC|DESC}],                 // max 10
  limit: int,                                               // default 5000, max 10000
  visualization: {type, settings}
}
```

表达式只允许 runtime contract 中的字段/指标和白名单函数；客户端不得提交 raw SQL、MBQL、join SQL 或数据源凭据。

## API 与持久化

| API | 语义 |
|---|---|
| `GET/POST /api/analysis` | 分页列表/创建 DRAFT；支持 idempotency key |
| `GET/PUT /api/analysis/{id}` | 读取/乐观锁更新；已发布 revision 不可原地改 |
| `POST /api/analysis/query` | 未保存 spec 预览 |
| `POST /api/analysis/{id}/query` | 已保存分析执行 |
| `POST /api/analysis/{id}/copy` | 复制为新 DRAFT，不复制受众/发布指针 |
| `POST /api/analysis/{id}/archive|restore` | 归档；恢复只进入 DRAFT |

复用 `analytics_card.dataset_query_json`，Expand-only 增加：

- `query_dataset_id uuid`
- `query_dataset_version int`
- `semantic_contract_version varchar(64)`
- `lifecycle_status varchar(32)`
- `published_revision_id bigint`

`card_type='analysis'`；增加必要索引与非空约束采用 expand/backfill/validate/contract 顺序。`AnalysisDto` 返回 id/name/description/lifecycleStatus/versionNo/publishedRevisionId/queryDatasetId/queryDatasetVersion/contractVersion/visualization/createdBy/updatedAt/permissions。

R1 期间旧 `/api/card` 只为旧镜像回切暂存；新 UI 只调用 `/api/analysis`，不得新增 legacy 写调用。错误：400 malformed，403 denied，404 missing，409 version/contract conflict，422 semantic field invalid。

## UI/UX

- canonical 路由为 `/bi/questions/new`、`/bi/questions/{id}` 与 `/bi/questions/{id}/edit`（ADR-94-13），全部由 `AnalysisEditorPage` 承接；列表只使用 Analysis API。
- `/bi/card/*` 与 legacy explore/VDS 只在 R1 作为回切面保留，F6/T03 独立 Contract 处理。
- 布局：顶部名称/保存状态/数据集版本；左侧字段与指标；中间图表/表格预览；右侧配置；底部或抽屉显示校验、查询统计和错误。
- 先从 1137 行 `SemanticCardEditorPage.tsx` 抽取职责组件，原文件不得增长。

### ⚠ 共享组件风险（ADR-94-14）

`SemanticCardEditorPage.tsx` 被 **5 条路由**复用，其中两条属于虚拟数据集创作线：

```text
bi/questions/new         ← canonical，本 Feature owner
bi/card/new              ← 冻结，行为须不变
bi/card/:id/edit         ← 冻结，行为须不变
bi/virtual-datasets/new  ← VDS 线，行为须不变
bi/virtual-datasets/:id  ← VDS 线，行为须不变
```

不得把旧 VDS 适配进新 Analysis 主线；F6/T03 删除前只需证明 R1 回切面未被意外破坏。
- loading/empty/error/success 全覆盖；409 显示契约已变化并提供“基于新版本创建草稿”，不得自动升级。
- 保存、校验、发布分开；本 Feature 只完成保存/编辑，发布由 F4 owner。

## Task

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 固化 AnalysisQuerySpec 与兼容存储 | P0 | DRAFT | F1/T01 |
| T02 | 重构自助分析编辑器与保存旅程 | P0 | DRAFT | T01、F1/T02 |

## DoR / 完成标准

- [x] v1 schema、上限、DTO、持久化和错误码已冻结。
- [x] 明确不建新业务表、不增长超大 owner 文件。
- [x] canonical editor 路由已钉死（ADR-94-13）；共享组件的 5 条路由复用关系已列明。
- [ ] VDS 两条路由的回归测试已就位（拆分组件的前置条件）。
- [ ] 旧 Card 三分类 fixture 可用。
- [ ] 新写路径可证明没有 MBQL/raw SQL；旧读兼容无回归。
- [ ] 预览/保存/复制/归档/恢复和乐观锁有聚焦测试。
- [ ] Chrome 95 编辑旅程、四态、键盘、console/Network 通过。
