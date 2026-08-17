# F1：分析数据集唯一主线

**优先级**：P0
**状态**：DRAFT（依赖 F0 关闭）

## 目标

让 BI 数据集页面与分析运行只消费平台已发布的 Query Dataset 版本。平台保持治理 owner；Analytics 通过只读投影和内部运行契约获取钉定信息，不建立 VDS/Database/SQL 副本。

## 契约定义

### Published projection

`GET /api/sql/query-datasets/published?page=0&size=20&keyword=&bizDomain=&ownerDept=&warehouseLayer=&classification=`

```text
AnalysisDatasetPage {
  items: AnalysisDatasetSummary[], page, size, totalElements, totalPages
}
AnalysisDatasetSummary {
  datasetId: UUID, version: int, name, description,
  ownerDept, sourceDatasourceId, sourceDatasourceName,
  warehouseLayer, classification, refreshStrategy,
  semanticContractVersion, semanticModelNames[], contractChecksum, updatedAt
}
```

规则：只返回 `PUBLISHED && enabled`；公开 DTO 绝不返回 `baseSql`；默认 DWS/ADS，DWD 只有 capability 允许时出现，ODS/STG 不进入创建入口。

### Published detail

`GET /api/sql/query-datasets/{id}/published/{version}` 返回字段、指标和治理策略摘要；归档、版本不存在、无 read 分别返回 409/404/403。

### Internal runtime contract

`GET /api/internal/analysis-datasets/{id}/versions/{version}`，仅 SERVICE_INTERNAL，且只能读取发布时保存的不可变 snapshot：

```text
AnalysisDatasetRuntimeContract {
  datasetId, version, status=PUBLISHED, sourceDatasourceId, baseSql,
  dimensions[{code,label,dataType,timeGrains[],classification,filterOps[]}],
  metrics[{code,label,aggregation,unit,expression}],
  joins[{fromModel,toModel,relationship,approvalRequired}],
  policyRefs[], classification, contractVersion, contractChecksum
}
```

### QueryDatasetVersion 不可变 snapshot

在现有 `query_dataset_version` 上 Expand-only 增加：

- `semantic_contract_schema varchar(64)`：固定 `dts.query-dataset-contract/v1`。
- `semantic_contract_version varchar(64)`：发布时钉定的上游契约版本。
- `semantic_contract_json jsonb`：发布时维度、指标、关系、源模型 revision、classification floor 与 policyRef ID 快照；不对 public DTO 暴露。
- `semantic_contract_checksum varchar(64)`：对 canonical JSON 与该版本既有 `sql_text` 计算的 SHA-256。
- `contract_snapshot_status varchar(32)`：`READY / UNRESOLVED`；无法无损回填的旧版本保持 UNRESOLVED，禁止进入新 Analysis 主线。

发布事务先构造并校验 snapshot，再把 version 推进 PUBLISHED；已发布 snapshot immutable。投影 service 不建新 repository/table，只从指定 `QueryDatasetVersion` 的 snapshot 组装 DTO，禁止按当前 canonical model 重新计算历史契约。

安全策略分两层：snapshot 钉定发布时的 `classification floor + policyRef IDs`；运行时仍加载当前有效策略并取更严格结果。策略收紧可拒绝运行，但不得修改历史 checksum；策略放宽不得低于 snapshot floor。

BI_DATASET 逻辑身份直接复用 `QueryDatasetAsset + CatalogAssetType.BI_DATASET + CatalogAssetKey.biDataset(id)`。不得调用只接收物理 DATASET 的 `CatalogAssetRegistrationService.observe`；现有 mapping report、权限 identity resolver、密级传播和 ReportLink 使用同一 key，详见 `../../assets/dependency-boundary.md`。

## 数据流与错误

```text
QueryDataset publish/archive
  → persist immutable semantic snapshot/checksum on QueryDatasetVersion
  → projection/cache invalidation
  → BI data list/detail
  → create Analysis pinned reference
  → runtime contract fetch by Analytics service identity
```

| 错误 | 语义 |
|---|---|
| 400 | 分页/筛选参数非法 |
| 401/403 | 未认证/无 dataset read 或内部 service auth |
| 404 | dataset/version 不存在 |
| 409 | 非 PUBLISHED、已归档或 checksum 漂移 |
| 502/503 | 平台契约依赖不可用；Analytics 不得回退裸 SQL |

## Route contract（已钉死，ADR-94-13）

| 用途 | 路由 | 说明 |
|---|---|---|
| 数据集目录 | `/bi/data` | 本 Feature 改造对象 |
| **创建分析（canonical）** | `/bi/questions/new?datasetId={uuid}&version={int}&checksum={sha}` | F1/T02 的导航目标；F2/T02 的读取来源；IT-03 断言对象 |
| 编辑分析（canonical） | `/bi/questions/{id}/edit` | F2/T02 owner |

`/bi/card/new`、`/bi/card/:id/edit`、`/bi/explore` 在本 Sprint **保持原样可用，不重定向**（重定向属退役 S2）。旧参数 `dbId/vds/base` 只做兼容解析，不用于新链接。

## UI/UX

- `/bi/data`：顶部搜索与领域/owner/分层/密级筛选；主体为数据集卡片/表格；右侧只读契约 drawer。
- 列表分页遵循前端既有约定：**UI 默认每页 10 条**，切换每页条数必须重新拉取并重置到第 1 页（API 侧 0-based，默认 20/最大 100 是服务端上限，不是 UI 默认值）。
- 卡片显示版本、DWS/ADS、owner、刷新、密级、语义模型、更新时间；不显示 JDBC/SQL/Analytics database。
- loading 使用 skeleton；empty 分“无已发布数据集”和“筛选无结果”；error 带重试/correlationId；success 分页保留 URL 状态。
- “创建分析”仅在 write + eligible 时启用，导航参数固定为 `datasetId/version/checksum`。

## Task

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 发布数据集消费契约与运行快照 | P0 | DRAFT | F0/T01、F0/T02、F0/T03 |
| T02 | 改造 BI 数据集页面与创建链路 | P0 | DRAFT | T01 |

## DoR / 完成标准

- [x] owner、DTO、状态和错误码已定义。
- [x] 明确复用 QueryDataset，不建新数据集表。
- [x] canonical 创建/编辑路由已钉死（ADR-94-13），不再留"编码前再定"的悬空项。
- [x] 逻辑 BI_DATASET 身份与物理 DATASET observation 已分开；不误用 `CatalogAssetRegistrationService`（ADR-94-15）。
- [x] 不可变 snapshot 字段、回填状态和运行时策略合并规则已定义（ADR-94-16）。
- [ ] F0 创建至少三个数据集样本并校准 NFR。
- [ ] T01 对公开 DTO、内部 DTO、缓存失效、授权、分页有测试。
- [ ] T02 四态、Chrome 95、路由参数与 Network 有证据。
- [ ] 创建分析后存储的 dataset/version/checksum 与列表完全一致。
