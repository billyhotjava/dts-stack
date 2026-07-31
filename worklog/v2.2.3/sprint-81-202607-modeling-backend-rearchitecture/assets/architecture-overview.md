# Sprint-81 目标架构总览

**状态**：PLANNED
**边界**：`dts-platform` 内部模块化重构；`dts-admin` 继续拥有中央审计历史；Airflow/dbt 继续拥有执行事实。

## 1. 上下文与所有权

```text
┌────────────────────────── dts-platform 模块化控制面 ──────────────────────────┐
│                                                                              │
│  integration ──AssetRegistrationPort──▶ catalog identity                     │
│                                            │                                 │
│                                            ▼                                 │
│                                    CatalogAssetKey/Type                       │
│                                            │                                 │
│                      quality evidence ◀────┘                                 │
│                      Rule→Version→Binding→Run                                │
│                                            │ immutable evidence               │
│                                            ▼                                 │
│  modeling.plan → modeling.spec → stage-gate → lifecycle → release            │
│                                                   → materialization           │
│                                                          │                   │
│                                                          ▼                   │
│                                                 DbtExecutionGateway           │
│                                                                              │
│  domain-event outbox ─▶ domain consumers     audit outbox ─▶ AuditService     │
└──────────────────────────────────────────────────────────────────────────────┘
                                                           │            │
                                                           ▼            ▼
                                                      Airflow/dbt    dts-admin
```

## 2. 模块职责与允许依赖

| 模块 | 唯一职责 | 允许依赖 | 禁止行为 |
|---|---|---|---|
| `integration` | 来源注册、外部接入结果、来源 identity hint | `catalog` 的注册端口 | 直接创建 ModelSpec、推进生命周期、写 Catalog repository |
| `catalog` | `CatalogAssetType`、`CatalogAssetKey` 解析和资产身份 | 自身 repository、公共安全/审计端口 | 接管质量运行或建模状态 |
| `quality` | Rule/Version/Binding/Run 与不可变 evidence 投影 | `catalog` identity、执行基础设施 | 新建“模型质量结果”平行表；直接 publish candidate |
| `modeling.plan` | WarehousePlan、来源/域/集市规划约束 | catalog/quality 的 query port | 继续使用旧 `modeling_plan` owner |
| `modeling.spec` | ModelSpec v2 head、immutable revision、依赖 pin | plan、catalog identity | 反向引用 business object/SQL model 为 owner |
| `modeling.gate` | 对指定 revision 汇集标准、质量、权限、artifact 证据 | query ports only | 修改 quality/catalog 数据；隐式选择 latest revision |
| `modeling.lifecycle` | 状态机与 CAS | stage-gate | resource/service 直接写状态列 |
| `modeling.release` | ReleaseCandidate、审核/发布职责、candidate version/attempt | lifecycle、materialization port | 快捷 API 复制候选状态机 |
| `modeling.materialization` | 物化命令、运行对账、relation 证据、发布交接 | `DbtExecutionGateway`、catalog port | 直接调用 `/etl/dbt/run` 或 Docker API |
| `execution.dbt` | 执行端口与 Airflow/dbt adapter | 外部系统 client、profile lease | 拥有模型/候选状态；持久化仓库凭据 |
| `audit` | 审计命令、durable outbox、dts-admin dispatcher | 公共 `AuditService`、admin adapter | 与业务事件共表；在 platform 保存中央历史副本 |

**依赖规则**：上层只依赖下层公开端口；任何模块不得 import 其他模块的 repository/entity。查询投影可跨模块组合，但必须通过只读 port，不反向成为 owner。

## 3. 唯一建模链

```text
WarehousePlan
  └─ ModelSpec v2 head
       └─ ModelSpecRevision (immutable, revision pinned)
            └─ StageGate.evaluate(targetStage)
                 └─ Lifecycle.transition(command, expectedVersion)
                      └─ ReleaseCandidate(candidateVersion, attempt)
                           └─ MaterializationCommand
                                └─ DbtExecutionGateway.submit/query/cancel
                                     └─ Airflow DagRun → dbt build → relation probe
```

### 3.1 状态推进约束

- revision 保存不等于门禁通过；门禁只评估明确的 `modelSpecId + revision`。
- StageGate 输出的是带证据引用的决定，不拥有 lifecycle 状态。
- Lifecycle 是唯一状态变更入口，所有写使用 `expectedVersion`。
- ReleaseCandidate 是构建、审核、发布的唯一控制面；facade/HTTP resource 只委托命令。
- Materialization 只接受 candidate/version/attempt 固定的命令；执行回执不匹配即进入对账，不覆盖新 attempt。
- Airflow/dbt 是执行事实，platform 保存业务运行投影与强绑定证据，不自建第二个 scheduler。

## 4. 核心端口与数据契约

### 4.1 Catalog identity

```text
CatalogAssetRef {
  assetType: CatalogAssetType,
  assetKey: CatalogAssetKey,
  tenantId: string,
  externalIdentity?: string,
  checksum?: string
}
```

`tenantId` 从服务端上下文产生；客户端不能借请求体扩大租户范围。外部 ID 只能用于解析，进入 quality/modeling 后必须转换为 `CatalogAssetKey`。

### 4.2 Quality evidence

```text
QualityEvidenceRequest {
  asset: CatalogAssetRef,
  ruleVersionIds: UUID[],
  asOf: Instant,
  maxAgeSeconds: long
}

QualityEvidence {
  ruleId: UUID,
  ruleVersionId: UUID,
  bindingId: UUID,
  runId: UUID,
  status: PASSED | FAILED | ERROR,
  finishedAt: Instant,
  evidenceChecksum: string
}
```

只有 `gov_quality_run` 终态、binding 与相同资产身份匹配、版本固定且未过期，才可进入 StageGate；模板数量不能替代运行证据。

### 4.3 StageGate

```text
StageGateRequest {
  planId: UUID,
  modelSpecId: UUID,
  revision: int,
  targetStage: DRAFT_SAVE | IMPLEMENTATION_READY | RELEASE_READY,
  actor: ServerResolvedPrincipal,
  correlationId: string
}

StageGateDecision {
  decision: PASS | BLOCKED,
  evidenceRefs: EvidenceRef[],
  violations: { code: string, message: string, resourceRef?: string }[],
  evaluatedAt: Instant,
  checksum: string
}
```

### 4.4 DbtExecutionGateway

```text
DbtExecutionRequest {
  candidateId: UUID,
  candidateVersion: long,
  attempt: int,
  mode: RELEASE_BUILD | OPERATIONAL,
  environment: string,
  executionTargetKey: string,
  projectRef: string,
  selector: string,
  artifactChecksum: string,
  profileLeaseId: UUID,
  idempotencyKey: string,
  correlationId: string
}

ExecutionHandle { executionId: UUID, dagRunId: string, acceptedAt: Instant }
ExecutionStatus { state: QUEUED|RUNNING|SUCCEEDED|FAILED|CANCELLED|UNKNOWN,
                  artifactRefs: string[], relationEvidenceRefs: string[], observedAt: Instant }
```

端口为 `submit(request) -> handle`、`query(handle) -> status`、`cancel(handle, expectedState)`。请求不含密码、profile 内容或 Docker 参数。

## 5. 事件与审计 outbox 必须分离

| 项目 | `modeling_domain_event_outbox` | `modeling_audit_outbox` |
|---|---|---|
| 用途 | 领域集成与投影更新 | 合规审计投递到 dts-admin |
| 主字段 | `event_id, aggregate_type, aggregate_id, event_type, payload, tenant_id, correlation_id, occurred_at, available_at, published_at, attempts, last_error` | `audit_id, action_code, stage, resource_type, resource_id, payload, actor, tenant_id, client_ip, correlation_id, occurred_at, available_at, delivered_at, attempts, last_error` |
| 消费者 | catalog/quality/modeling 内部 adapter | `AuditService`/dts-admin forwarder |
| 顺序键 | aggregate | tenant + resource |
| 保留 | 消费确认后按运行策略归档/清理 | 投递确认后可清理本地 outbox；中央审计历史保留 |
| 失败 | 指标、重试、DLQ，不回滚已提交业务事实 | 指标、重试、DLQ；不得静默丢弃或降级为普通日志 |

两张表都与所属业务事务原子提交，但 dispatcher 独立。任何尝试用 `eventType=AUDIT_*` 混入业务事件表的实现都不通过架构门禁。

## 6. 关键事务边界

| 事务 | 同事务内 | 事务外 |
|---|---|---|
| 保存 ModelSpec revision | head CAS、immutable revision、domain event、audit outbox | 下游投影和 dts-admin 投递 |
| 通过 StageGate/lifecycle transition | evidence checksum、状态 CAS、event、audit outbox | candidate 构建 |
| 创建/推进 Candidate | candidate version/attempt、claim、event、audit outbox | Airflow 提交 |
| 接受物化回执 | execution correlation、relation evidence、状态 CAS、event、audit outbox | Catalog 外部同步 |
| legacy 迁移 apply | 单批对象映射、目标 revision、migration manifest checkpoint、audit outbox | 下一批、备份归档 |

外部调用不得占用数据库事务。outbox dispatcher 使用有界批次、锁跳过和幂等投递。

## 7. 退役顺序

1. F0 冻结环境级 manifest、客户画像、备份/恢复和停机窗口。
2. F1 建立模块守卫与 canonical 端口，禁止新增旧 owner 调用。
3. F2/F3 让所有业务状态、资产身份、质量证据和审计进入目标控制面。
4. F4 让全部建模执行进入 `DbtExecutionGateway`。
5. F5 逐批重接、精确迁移并物理删除旧 route/service/entity/repository/table。
6. F6 仅在源码与 schema 已无旧面后集中执行 E2E 和 Go/No-Go。

详细清单见 [`backend-retirement-matrix.md`](backend-retirement-matrix.md)。

## 8. 架构适应度函数

- ArchUnit：除公开 port/api 外，模块间不得 import 对方 entity/repository；依赖图无环。
- Source contract：新前端和 platform 内部不出现 `/api/semantic`、旧 plan、`/api/modeling/vnext`、`/etl/dbt/run`。
- Schema contract：event/audit outbox 分表且各自具备待投递索引、幂等唯一键。
- State-machine IT：绕过 Lifecycle 直接更新状态必须失败。
- Quality IT：latest 漂移、非终态、过期或资产键不一致的 run 均阻断。
- Execution IT：重复 idempotency key 返回同一 handle；回执版本不匹配不推进 candidate。
- Retirement IT：旧 bean/resource/repository 不可装配，旧 route 为普通 404，旧表不存在；历史 changelog 与中央审计仍可读。
