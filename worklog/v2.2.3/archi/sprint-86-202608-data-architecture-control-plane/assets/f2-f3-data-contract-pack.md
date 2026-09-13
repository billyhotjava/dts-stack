# F2/F3 资产纳管、来源状态与指标业务上下文决策包

**状态**：APPROVED

**形成日期**：2026-08-09

**评审入口**：IT-04、IT-07

**评审人**：xiezm（已登记，兼任全部 Accountable roles）

本文件是 F2/T01 与 F3/T01 的联合评审输入。资产范围、统计架构、来源、状态和指标上下文互相约束，必须作为一组批准，不能先实现混合字段再补语义。

## 1. 已批准结论摘要

| ADR | 已批准选择 | 状态 |
|---|---|---|
| ADR-86-04 | 发现即登记：所有稳定、可寻址的真实关系进入资产台账；治理/发布/可消费独立判定 | ACCEPTED |
| ADR-86-05 | SOURCE 是生产者/来源系统语义，不是数仓分层；DIM 是兼容层值，统一归一为 DWD + DIMENSION 资产角色 | ACCEPTED |
| ADR-86-06 | 所有指标单值、必填 `businessCategoryId`；`metricType`、`metricGroupCode` 独立 | ACCEPTED |
| ADR-86-07 | ATOMIC 固定域/过程；DERIVED 同分类同域；COMPOSITE 可同分类跨域；v1 禁止跨分类指标 | ACCEPTED |
| ADR-86-16 | 域/资产统计采用服务端增量统计投影 + 周期对账；在线请求禁止两轮全量扫描 | ACCEPTED |
| ADR-86-18 | ProducerRef 与 RegistrationEvidence 分轴；多渠道发现归并同一 CatalogAssetKey | ACCEPTED |
| ADR-86-19 | 发现、治理、发布、健康、生命周期五轴保存；消费资格按策略计算并返回 reason codes | ACCEPTED |

## 2. 资产纳管矩阵

| 对象 | 纳管 | ProducerRef | RegistrationEvidence | warehouseLayer | 初始状态与消费规则 |
|---|---|---|---|---|---|
| 外部数据库稳定表/视图 | 是 | `SOURCE_SYSTEM + sourceSystemId` | `SCANNER` 或 `MANUAL` | `null` | DISCOVERED；未治理/未授权不可消费 |
| 已落地 ODS 表 | 是 | `INGESTION_JOB + jobId` | `INGESTION_EVENT` | ODS | observation 成功后 VERIFIED；仍需治理/质量/授权 |
| 持久化 STG 表/视图 | 是 | `DBT_MODEL` 或 `MANUAL_BUILD` | `DBT_SYNC` 或 `MANUAL` | STG | 默认内部；由发布策略决定能否消费 |
| DWD/DWS/ADS 表、视图、物化视图 | 是 | `DBT_MODEL` 或 `MANUAL_BUILD` | `DBT_SYNC`/物化 observation | 对应 canonical code | 发布、健康、质量、权限共同决定消费资格 |
| 资产侧历史 DIM | 是 | 保留原生产者 | 原渠道 | 兼容读 `DIM`，canonical 投影 DWD | 展示“维度表 / DWD”；不得向 ModelSpec/dbt 新写 DIM |
| 失败构建后仍稳定存在的关系 | 是 | 对应构建 producer | 物化 observation/扫描 | 对应层或 null | discovery=DISCOVERED，serving=FAILED；BLOCKED |
| 已发布语义模型 | 是，但不是物理资产 | `MODELING + modelSpecId` | 发布投影 | 目标层仅作设计属性 | 使用 `SEMANTIC_MODEL` identity；不冒充表/视图 |
| dbt ephemeral、CTE、临时/会话表 | 否 | - | 可保留运行诊断，不登记资产 | - | 不进入物理资产台账 |
| 只有 ModelSpec 草稿、没有物理 observation | 否（物理资产） | - | - | 目标层仅作设计属性 | 留在建模控制面 |

### 纳管准入条件

物理关系只有同时满足以下条件才进入 `CatalogDataset`：

1. relationType 为 TABLE、VIEW 或 MATERIALIZED_VIEW；
2. `dataSourceId/database/schema/relationName` 可形成稳定 locator；
3. 通过既有 `CatalogAssetKey` 规范化后无身份冲突；
4. 至少一条可追溯 RegistrationEvidence；
5. 临时/ephemeral 标记为 false。

不满足条件时写入具名 resolution failure/diagnostic，不生成占位 CatalogDataset。

## 3. ADR-86-05：SOURCE 与 DIM 兼容归一化

### 3.1 SOURCE

```text
legacy warehouseLayer = SOURCE
  → canonical warehouseLayer = null
  → ProducerRef.producerKind = SOURCE_SYSTEM
  → 保留 legacyLayerCode = SOURCE 直到 Contract 阶段
```

- 新写入链禁止再把 SOURCE 当 canonical layer。
- Expand 期读取优先新 ProducerRef；缺失时兼容解释旧 SOURCE。
- 当前环境 3 条 SOURCE 先 dry-run：能解析来源系统则回填；不能解析则进入 `SOURCE_PRODUCER_UNRESOLVED` 队列，不猜测 producerId。
- 回滚只需关闭新读开关并继续读旧字段；Contract 前不得清除 SOURCE 原值。

### 3.2 DIM

```text
legacy warehouseLayer = DIM
  → canonical warehouseLayer = DWD
  → assetRole = DIMENSION_TABLE
  → UI 展示“维度表 / DWD”
```

- DIM 不升格为 canonical layer，不扩展 ModelSpec/dbt 枚举。
- 当前本地画像没有 DIM 行，但客户环境仍须 dry-run；读兼容必须覆盖未知存量。
- 有模型/物化证据时由 DIMENSION ModelSpec 判定 `assetRole`；无证据的外部 DIM 由治理人员确认，不根据表名前缀自动写事实。
- 回填失败保留 legacy DIM，并标记 `LAYER_NORMALIZATION_REQUIRED`；不得静默改 DWD。

该选择关闭 RF-86-01：维度是模型/资产角色，DWD 是技术加工层，两者不再竞争同一个枚举。

## 4. ADR-86-18：生产者与登记证据

### 4.1 逻辑契约

```text
ProducerRef {
  producerKind: SOURCE_SYSTEM | INGESTION_JOB | DBT_MODEL | MANUAL_BUILD | MODELING
  producerId: string
  producerVersion?: string
  validFrom: instant
  validTo?: instant
}

RegistrationEvidence {
  assetType: CatalogAssetType
  assetKey: CatalogAssetKey
  channel: SCANNER | INGESTION_EVENT | DBT_SYNC | MATERIALIZATION_OBSERVATION | MANUAL
  evidenceRef: string
  firstObservedAt: instant
  lastObservedAt: instant
  status: ACTIVE | STALE | FAILED
}
```

- ProducerRef 回答“谁生成/提供”；RegistrationEvidence 回答“平台如何知道”。
- 同一 `assetType + assetKey + channel + evidenceRef` 幂等刷新 `lastObservedAt`，不同 evidenceRef 保留独立历史。
- producer 变化采用有效期历史，不覆盖旧 producer；上游 lineage 仍使用既有资产关系，不塞进 producer 字段。
- 冲突的 producer 声明进入治理队列，消费资格可降级，但不能创建第二个资产 ID。

## 5. ADR-86-19：正交状态轴与消费资格

| 状态轴 | 枚举 | owner | 说明 |
|---|---|---|---|
| DiscoveryState | DISCOVERED / VERIFIED / MISSING | 资产发现 | 是否观测到稳定关系 |
| GovernanceReadiness | UNASSIGNED / INCOMPLETE / GOVERNED | 资产治理 | 归域、责任、分级等治理完备度 |
| PublicationState | UNPUBLISHED / PUBLISHED / WITHDRAWN | 发布控制面 | 是否获准对外使用；不表达健康或退役 |
| ServingHealth | UNKNOWN / HEALTHY / STALE / FAILED | 物化/探测 | 当前物理关系是否可服务 |
| LifecycleState | ACTIVE / DEPRECATED / RETIRED | 资产 owner | 业务生命周期；RETIRED 不删除历史 |

派生结果：

```text
ConsumptionEligibility {
  decision: ELIGIBLE | CONDITIONAL | BLOCKED
  reasonCodes: string[]
  evaluatedAt: instant
}
```

首版策略：

- `ELIGIBLE`：VERIFIED + GOVERNED + PUBLISHED + HEALTHY + ACTIVE，且质量/权限门禁通过。
- `CONDITIONAL`：允许内部诊断或待治理场景，例如 DISCOVERED + UNASSIGNED；必须返回原因且不能伪装发布。
- `BLOCKED`：MISSING、FAILED、WITHDRAWN、RETIRED、质量门禁失败或权限拒绝中的任一项。
- 质量结果和权限判定是计算输入，不覆盖上述五轴事实。

推荐 reason codes：`DOMAIN_UNASSIGNED`、`OWNER_UNASSIGNED`、`CLASSIFICATION_REQUIRED`、
`QUALITY_EVIDENCE_MISSING`、`QUALITY_FAILED`、`RELATION_STALE`、`RELATION_FAILED`、
`NOT_PUBLISHED`、`ASSET_RETIRED`、`ACCESS_DENIED`。

## 6. ADR-86-16：资产统计架构

### 6.1 已批准方案

采用**服务端增量统计投影 + 周期全量对账**：

```text
CatalogDataset / governance / state axis 变更
  → durable event/outbox
  → AssetDomainStatsProjection 增量更新
  → GET overview/tree 只读投影
  → 周期 reconciliation 校验并修复漂移
```

- 在线请求禁止扫描全部 CatalogDataset，也禁止一个页面触发两轮全量扫描。
- 投影按 `domainId/null + warehouseLayer + assetType + governance/state buckets` 保存可索引计数。
- API 必须返回 `asOf`、`freshness=FRESH|STALE|REBUILDING`、`isApproximate` 与 `count`。
- 新投影就绪后不再用 5000 作为统计上限；兼容 fallback 触顶时只能显示 `≥5000` 并标记 approximate。
- 投影漂移不阻断资产台账读取；只降级统计和导航计数，并暴露重建动作/审计。

### 6.2 已批准设计预算

| 项目 | 已批准设计值 | Fitness function |
|---|---:|---|
| 首版设计容量 | 100,000 物理资产、50 个域节点 | `AssetStatsCapacityIT` 构造边界分布并核对计数 |
| 在线统计查询 | P95 ≤ 1.5 秒；单请求 ≤ 3 次 SQL | 基准 + SQL 计数断言 |
| 增量新鲜度 | 变更后 5 分钟内反映 | 时钟可控 IT 断言 `asOf/freshness` |
| stale 表达 | 超过 10 分钟未推进即 STALE | UI/API 契约测试 |
| 全量对账 | 每 24 小时至少一次，可人工触发 | reconciliation IT + 审计记录 |
| 重建期间 | 台账可用；计数标 `REBUILDING/isApproximate=true` | 故障注入，不返回伪精确值 |
| 分页 | 默认 10 条；服务端必须有界 | Chrome/UI contract + API 上限测试 |

这些值是产品设计容量，须由 xiezm 在 IT-07 接受或替换；客户/生产画像仍是下一实施 Sprint G0 证据。

## 7. 指标业务上下文契约

### 7.1 新逻辑字段

```text
MetricBusinessContextV1 {
  businessCategoryId: UUID
  dataDomainId?: UUID
  businessProcessId?: UUID
  metricType: ATOMIC | DERIVED | COMPOSITE
  metricGroupCode?: ASCII string
  sourceRefs: MetricSourceRef[]
}

MetricSourceRef {
  sourceType: SEMANTIC_MODEL_REVISION | PHYSICAL_ASSET | INDICATOR_VERSION
  sourceId: string
  sourceVersion: string
}
```

`metricGroupCode` 只用于展示分组，采用稳定 ASCII code；业务分类、密级和任意标签不得写入该字段。

### 7.2 必填与继承矩阵

| 指标类型 | businessCategoryId | dataDomainId | businessProcessId | sourceRefs | 跨范围规则 |
|---|---|---|---|---|---|
| ATOMIC | 必填、单值 | 必填 | 必填 | 至少一个模型 revision 或物理资产 | source 必须落在同域/过程上下文 |
| DERIVED | 从上游校验并固化，必填 | 所有上游同域时固化且必填 | 上游同过程时固化，否则为空 | 至少一个固定 IndicatorVersion | v1 要求同分类、同域；禁止跨分类/跨域 |
| COMPOSITE | 所有上游同分类，固化且必填 | 同域则固化，否则为空 | 同过程则固化，否则为空 | 至少两个固定 IndicatorVersion | v1 允许同分类跨域；禁止跨分类 |

- 指标版本冻结业务上下文和 sourceRefs；上游后续改名/新版本不改写历史版本。
- 跨分类复合指标首版失败关闭，错误码 `INDICATOR_CROSS_CATEGORY_NOT_SUPPORTED`；后续如需支持须另立 ADR 和审批机制。
- businessCategoryId 永远单值且必填，回答“该指标由哪类业务负责”；不承担类型或分组语义。

## 8. 指标兼容迁移

| 旧字段 | Expand/回填 | 读取切换 | Contract 门禁 |
|---|---|---|---|
| `category: String` | 新增 `businessCategoryId`; 先按唯一 code，再按唯一规范化名称匹配 | 新 ID 优先，旧文本只作展示 fallback | 连续观测期无旧-only 写入；未匹配清单为 0 或获批准豁免 |
| `domain: String` | 新增 `dataDomainId`; 校验其父分类 | 新 ID 筛选，兼容旧 query 参数并转译 | 全部创建/模板/派生/发布消费者切换 |
| `datasetId: String` / `sourceTable` | 形成版本化 `sourceRefs`；能唯一解析才回填 | sourceRefs 优先，旧字段只读兼容 | 无 unresolved source 或明确阻断发布 |
| `isDerived` | 新增 `metricType`; false→ATOMIC，true 需结合依赖判定 DERIVED/COMPOSITE | metricType 为权威 | 旧写入口全部停用 |
| 自由文本分组/tags | 可映射 `metricGroupCode`，不能映射密级 | 新分组独立展示 | 无业务分类/密级混写 |

回填失败统一进入 `indicator_context_migration_issue`（逻辑名称，最终 schema 由实施 Sprint 冻结），至少记录：
`indicatorId`、`field`、`legacyValue`、`reasonCode`、`candidateIds`、`status`、`resolvedBy/At`。失败项可查看，但不得发布新版本。

## 9. 逐消费者实施测试矩阵

| 消费面 | 必测内容 |
|---|---|
| 指标创建/编辑 | 新 ID 选择、父子联动、旧记录回显、越权拒绝 |
| 指标模板应用 | 模板业务上下文不覆盖用户已确认值；无效 ID 失败关闭 |
| 版本/回滚 | 版本快照固定上下文/sourceRefs；回滚不漂移到当前名称 |
| 派生/复合校验 | 同分类/同域矩阵、跨分类拒绝、来源版本不可变 |
| 发布预览/发布 | migration issue、serving/quality/permission blockers 可见 |
| dbt 生成与运行 | 使用固定来源引用，不再从中文 category/domain 拼表名 |
| 列表/查询/统计 | 新 ID 查询与旧参数兼容；分页默认 10；不存在 N+1 |
| 质量绑定 | 继续绑定物理 `datasetId + ruleVersion`，不改用指标 category |

`GovIndicatorDefinition` 当前为 HIGH 风险；实施前必须刷新 GitNexus 并逐符号报告影响，不得在本架构 Sprint 宣称已完成迁移。

## 10. IT-04/IT-07 通过清单

- [x] 接受发现即登记与排除范围（ADR-86-04）。
- [x] 接受 SOURCE/ProducerRef 和 DIM→DWD+assetRole 归一化（ADR-86-05，关闭 RF-86-01）。
- [x] 接受 ProducerRef、RegistrationEvidence 和五轴状态（ADR-86-18/19）。
- [x] 接受增量统计投影与 §6.2 预算（ADR-86-16，关闭 RF-86-08）。
- [x] 接受所有指标单业务分类及三类指标矩阵（ADR-86-06/07）。
- [x] 接受逐消费者兼容迁移与失败关闭规则。
- [x] 确认批准的是架构契约，不是运行时实现/容量证明。
