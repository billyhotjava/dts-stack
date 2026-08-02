# Catalog Serving 与受控物理预览契约

**状态**：ACCEPTED（D08、D12，2026-08-01）  
**事实所有权**：ModelSpec/Implementation 拥有设计与实现；ReleaseCandidate/Materialization/relation evidence 拥有运行事实；Catalog 只做统一资产投影。  
**复用约束**：只使用既有 `CatalogAssetType + CatalogAssetKey`、StageGate、Lifecycle、ReleaseCandidate、DbtExecutionGateway、relation evidence、权限/密级/脱敏与公共审计，不新建平行资产或预览控制面。

## 1. Catalog 双指针

同一个稳定逻辑模型资产必须同时表达两个独立版本引用：

```text
latestPublishedRef = 最新通过治理发布的 ModelSpec/Implementation/ReleaseCandidate 引用
servingRef         = 当前具备成功物化与 relation evidence、允许消费的版本引用
```

`latestPublishedRef` 与 `servingRef` 可以不同；revision 是资产属性和证据引用，不得为每个 revision 创建新的逻辑资产。

| 阶段 | Catalog 操作 | 可发现 | 可消费 | serving 规则 |
|---|---|---:|---:|---|
| 新建 DRAFT | 只存在于建模域，不创建 Catalog 资产 | 否 | 否 | 无 |
| 已发布模型的新 DRAFT | 不更新当前 Catalog 投影 | 保持 | 保持 | 保留旧 serving |
| PUBLISHED | 以稳定逻辑 `CatalogAssetKey` upsert 业务定义、域、负责人、表级密级、标准绑定和 `latestPublishedRef` | 是 | 新修订否 | 有旧 serving 时继续消费旧版本 |
| MATERIALIZING | 只记录 candidate/attempt 进度，不提前切换 | 是 | 旧版本可继续消费 | 保留旧 serving |
| MATERIALIZED | candidate 成功且 relation evidence/质量门禁成立后，关联既有物理 relation 资产并 CAS 切换 `servingRef` | 是 | 是 | 指向精确 model/implementation/candidate/evidence |
| FAILED/FAILED_STALE | 记录失败尝试，不登记失败物理结果 | 是 | 有旧版本则继续消费 | 不切换 serving |
| RETIRED | 保留历史与证据，停止新消费 | 历史可查 | 否 | 清除当前 serving 可用性但不物理删历史 |

### 身份与投影规则

- 逻辑资产身份来自稳定 ModelSpec 对应的既有 `CatalogAssetType + CatalogAssetKey`；ModelSpec revision、dbtUniqueId 均不能代替稳定资产身份。
- 物理 relation 使用既有 datasource/catalog/schema/relation 身份；不得用逻辑模型 ID 或 dbtUniqueId 伪造物理资产。
- relation observation 是不可变运行证据；Catalog 保存引用和当前投影，不能反向成为运行事实 owner。
- manifest、compiled schema、dbt build-only 或 stale callback 均不能创建可消费物理绑定或切换 serving。
- Catalog 投影通过既有 outbox 幂等 upsert；外部 Catalog 同步失败进入 `SYNC_PENDING/FAILED` 并重试，不回滚已经成立的发布/物化事实，也不得重新运行 dbt。
- 乱序或旧 callback 必须按 candidate/version/attempt 与 CAS 拒绝，不得把 serving 指针回退到非预期旧结果。

## 2. 物理结构与样例行的事实边界

- **物理结构**：来自 candidate 固定的 relation evidence，可按 evidenceChecksum 重读和缓存结构元数据。
- **样例行**：从 relation 在用户点击时实时查询，必须返回 `queriedAt`；它不是 candidate 生成时的数据快照。
- **普通可视化**：只展示当前 `servingRef` 的结构与受控样例行。
- **高级 dbt 实现**：技术维护者可预览成功但尚未成为 serving 的 candidate，必须显示“候选结果，非正式资产”。若模型尚未 PUBLISHED，只派生稳定 CatalogAssetKey 用于权限/策略/审计锚定，不创建 Catalog 资产记录。
- **历史 revision**：始终只展示固化结构与证据，不返回样例行；底层 relation 可能已被后续物化覆盖，禁止用当前数据冒充历史快照。

## 3. Physical Preview API

### 请求

```text
GET /api/modeling/model-specs/{modelSpecId}/implementations/{implementationRevision}/physical-preview
  ?modelRevision={int}
  &scope=SERVING|CANDIDATE
  &candidateId={uuid}
  &candidateVersion={int}
  &attempt={int}
  &pipelineRunId={uuid}
  &observationAttempt={int}
  &relationEvidenceId={uuid}
  &evidenceChecksum={string}
  &limit={1..500}       # 缺省 100
```

- `SERVING` 是普通可视化唯一允许的 scope；服务端校验所有引用与当前 servingRef 一致。
- `CANDIDATE` 只供现有建模维护者 authority 使用；candidate 必须成功且 evidence 完整。未发布模型只派生 CatalogAssetKey，不因预览而登记 Catalog 或获得可发现/可消费状态。
- `attempt` 是既有 materialization build 的整数 attempt counter，不是 import apply attempt，也不是 UUID；`pipelineRunId` 是 canonical pipeline run UUID；`observationAttempt` 是 relation observation 的整数 attempt。三者不得合并或互相代替。
- `relationEvidenceId` 只能由服务端读取对应的物理 relation observation；读取后必须逐项校验 tenant、modelSpecId/modelRevision、implementationRevision、candidateId/candidateVersion、attempt、pipelineRunId、observationAttempt 和 evidenceChecksum 与请求及 serving/candidate pin 完全一致，任一不一致均不得访问目标数据库。
- database/schema/identifier 必须分别取自上述 observation 的分段字段；客户端提交的 relation/schema/table、响应中的 relationRef、dbt manifest 或模型名均不得直接参与 SQL 构造。
- `limit` 只控制一次样例读取，不提供 offset/cursor 分页遍历。

### Relation 标识符与查询构造防线

1. 在打开目标数据库连接或执行任何数据库查询前，对 observation 中的 schema/identifier 重新执行现有 `PhysicalRelationInspector` 规范 identifier 白名单校验；非法值返回 `PHYSICAL_PREVIEW_IDENTIFIER_INVALID`、目标数据库查询数为 0，且错误响应不得回显原始标识符。
2. PostgreSQL 的 database 只用于与固定 datasource/catalog 做服务端等值复核，不进入 relation SQL。未来 adapter 若必须把 database 放入限定名，则 database 必须执行与 schema/identifier 相同的白名单校验并由 adapter 专属引用器逐段引用。
3. 白名单校验通过不等于可以直接拼接 SQL。进入 SQL 的 schema/identifier 必须由 adapter 专属 identifier 引用器逐段引用，再由受控 preview query builder 组合；禁止复用 `DbtPreviewService` 的手写 `quoteIdentifier`，也禁止以字符串替换、客户端转义或 manifest 可信为安全边界。
4. 样例 `SELECT` 前必须通过 adapter 的参数化 catalog/DB metadata 查询重新确认 database、schema、identifier、relation type 与固定 observation 一致；不存在返回 `PHYSICAL_PREVIEW_RELATION_NOT_FOUND`，与 evidence 不一致或已变化返回 `PHYSICAL_PREVIEW_STALE_RELATION`，不得继续读取样例行。
5. `limit` 只能使用服务端校验后的 1..500 整数生成有界查询；relation 各分段、排序、过滤或其他客户端文本不得进入查询模板。

### 响应

```text
PhysicalPreviewView {
  modelSpecId, modelRevision, implementationRevision,
  previewScope, candidateId, candidateVersion, attempt,
  pipelineRunId, observationAttempt,
  relationEvidenceId, evidenceChecksum, catalogAssetType, catalogAssetKey,
  relationRef, observedAt, queriedAt,
  columns[], maskedRows[], maskingSummary,
  returnedRows, truncated, driftStatus, correlationId,
  candidateLabel?       // CANDIDATE 时固定为“候选结果，非正式资产”
}
```

响应不得包含 SQL/Jinja、凭据、数据源连接信息、未授权列名或原始策略正文。

## 4. 权限、密级与列策略

按以下顺序在服务端 fail-closed：

1. tenant/project/model 范围和现有模型 `read`。
2. `SERVING` scope 要求已登记 Catalog 资产可读；`CANDIDATE` scope 要求现有建模维护者 authority，并以 ModelSpec 派生的稳定 CatalogAssetKey 锚定策略，不要求或创建 Catalog 资产记录。
3. 表级 `classification` 许可：SERVING 使用已发布资产/ModelSpec 一致投影，未发布 CANDIDATE 使用 ModelSpec 当前治理值；密级不得降级为业务标签。
4. 列策略：统一按稳定 CatalogAssetKey 解析，`ALLOW` 返回、`MASK` 返回脱敏值、`DENY` 从 columns 与 rows 中整列移除。
5. classification 无法解析、策略服务不可用、任何列策略为 UNKNOWN 时，整个样例行响应阻断且返回 0 行；不得回退原始值。
6. 不新增细粒度权限 action；候选预览也不能绕过普通预览的脱敏规则。

结构元数据可按 evidenceChecksum 使用 ETag；样例行必须 `Cache-Control: private, no-store`，不得进入服务端缓存、浏览器持久存储、日志或审计正文。Sprint-83 不增加样例 CSV/Excel/ZIP 导出、批量复制或分页遍历。

## 5. 稳定错误

| errorCode | HTTP/结果 | 语义 |
|---|---|---|
| `PHYSICAL_PREVIEW_NOT_MATERIALIZED` | 409 | 没有成功物化证据 |
| `PHYSICAL_PREVIEW_CANDIDATE_NOT_SUCCEEDED` | 409 | candidate 未成功 |
| `PHYSICAL_PREVIEW_EVIDENCE_MISMATCH` | 409 | revision/candidate/evidence pin 不一致 |
| `PHYSICAL_PREVIEW_STALE_RELATION` | 409 | evidence 已 stale，禁止当作当前成功结果 |
| `PHYSICAL_PREVIEW_HISTORICAL_ROWS_NOT_REPRODUCIBLE` | 409 | 历史 revision 不提供样例行 |
| `PHYSICAL_PREVIEW_RELATION_NOT_FOUND` | 404 | evidence 指向的 relation 不存在 |
| `PHYSICAL_PREVIEW_IDENTIFIER_INVALID` | 409 | observation 中的 relation 分段不符合规范 identifier 白名单；目标数据库查询数必须为 0 |
| `PHYSICAL_PREVIEW_ACCESS_DENIED` | 403 | 租户、read、资产或维护者权限不足 |
| `PHYSICAL_PREVIEW_CLASSIFICATION_UNRESOLVED` | 423/业务 BLOCKED | 表级密级无法确定 |
| `PHYSICAL_PREVIEW_MASKING_UNAVAILABLE` | 423/业务 BLOCKED | 列策略或脱敏无法安全执行 |
| `PHYSICAL_PREVIEW_LIMIT_EXCEEDED` | 400 | limit 不在 1..500 |
| `PHYSICAL_PREVIEW_TIMEOUT` | 504 | 有界查询超时 |

所有失败必须返回 0 行、稳定 errorCode 和 correlationId，不得泄露 relation/SQL/凭据或原始值。

## 6. UI、审计与验收

- “物理资产”阶段默认先显示证据固定的结构；样例区显示“加载样例”按钮，点击后才发起查询。
- 默认 100 行，可选 20/50/100/500；明确标注“实时样例，不保证排序，不是历史快照”。
- 四态：未物化/无 serving、加载中、阻断/错误、成功；CANDIDATE 使用独立非正式资产提示，历史 revision 不显示加载按钮。
- 公共审计记录 actor、tenant、asset key、model/implementation/candidate/evidence 引用、requested/returned row count、列数、mask/deny 数量、结果、errorCode、correlationId；样例值为 0。
- IT-03/06/07 必须覆盖 serving/候选隔离、旧 serving 保留、build-only/stale 不切换、显式加载、100/500/501、历史无行、ALLOW/MASK/DENY/UNKNOWN、no-store、无导出和审计零样例值；identifier 防线须覆盖引号、分号、SQL 注释符、点号、超长、Unicode 等恶意模型名均在连接/查询前阻断，保留字等合法白名单值则由 adapter 正确引用并只访问 evidence 固定 relation；任一 model/implementation/candidate/version/attempt/pipelineRunId/observationAttempt/evidence pin 被篡改时返回 `PHYSICAL_PREVIEW_EVIDENCE_MISMATCH`、0 行且目标数据库查询数为 0。

现有 `GET /api/etl/dbt/preview` 不具备上述 ModelSpec/revision/candidate/evidence pin、密级与列策略契约，不得被任何 Sprint-83 建模 UI/服务调用。它登记为 `R-DBT-LEGACY-PREVIEW`：**S3 进入 DONE 前必须先完成旁路遏制**，使普通已认证用户和建模角色直接调用时只能得到 403/404 或同一安全 physical-preview 服务的受控结果，绝不能返回旧原始行。若无非建模 owner 则物理退役；若仍有非建模消费者，必须先迁移到同一 evidence/classification/masking 服务并删除或封闭原旁路，不能原样长期保留。源文件物理清理由 S5 完成，但不得把 S5 当作 S3 继续暴露旁路的理由。
