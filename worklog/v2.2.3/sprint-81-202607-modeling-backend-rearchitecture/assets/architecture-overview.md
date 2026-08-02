# Sprint-81 目标架构总览

**状态**：IMPLEMENTED_SOURCE_VERIFIED（独立安全/Java/数据库/TypeScript 复核通过，隔离 PostgreSQL 控制面 E2E 通过；共享环境尚未部署本次最终增量）
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
│  platform_event_outbox ─▶ domain consumers   platform_audit_outbox ─▶ admin   │
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
| `audit` | 审计命令、`platform_audit_outbox`、dts-admin dispatcher | 公共 `AuditService`、admin adapter | 与业务事件共表；在 platform 保存中央历史副本；让普通请求伪装机器主体 |

数据集成中的凭据兼容恢复采用独立 `ingestion_secret_restore_audit` 证据账本和投递 outbox：仅接受 dts-platform 服务令牌、route grant、转发用户、`ROLE_ADMIN` 四项同时成立的请求；证据字段禁止 UPDATE/DELETE/TRUNCATE。outbox 的 `delivery_attempts` 同时作为 claim fencing token，陈旧 worker 无法覆盖新领取代次。

当前版本不支持多租户，审计所有者由服务内部固定为 `SINGLE_TENANT/default`，部署不再接收审计租户参数；审计 outbox 仍保留 `tenant_id` 作为归属与重放隔离证据，已有真实租户证据时，tenant 列迁移回滚仍会锁表并失败关闭。

**依赖规则**：上层只依赖下层公开端口；任何模块不得 import 其他模块的 repository/entity。查询投影可跨模块组合，但必须通过只读 port，不反向成为 owner。

当前建模主链已将跨域读取收敛到 immutable ports：Catalog 拥有域、来源、分类、发布准入与来源可用性投影；Governance 拥有指标、码表与门禁证据投影；标准 owner 暴露数据标准/术语/数据元投影；Service 域暴露 API 资产身份。Catalog/Quality/Modeling 编排层不再成为其他域 repository/entity 的第二 owner，ArchUnit 固化该边界。

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

| 项目 | `platform_event_outbox` | `platform_audit_outbox` |
|---|---|---|
| 用途 | 跨域集成与投影更新 | 合规审计投递到 dts-admin |
| 主字段 | 以实际 `platform_event_outbox` schema 为准，事件 ID 与聚合身份必须可幂等 | `event_id, producer, payload_hash, body_json, status, dispatch_attempts, next_attempt_at, delivered_at, last_error` |
| 消费者 | catalog/quality/modeling 内部 adapter | `AuditService`/dts-admin forwarder |
| 顺序键 | aggregate | tenant + resource |
| 保留 | 消费确认后按运行策略归档/清理 | 投递确认后可清理本地 outbox；中央审计历史保留 |
| 失败 | 指标、重试、DLQ，不回滚已提交业务事实 | 指标、重试、DLQ；不得静默丢弃或降级为普通日志 |

两张表都与所属业务事务原子提交，但 dispatcher 独立。任何尝试用 `eventType=AUDIT_*` 混入业务事件表的实现都不通过架构门禁。

### 5.2 数据源回退与来源可用性栅栏

Level 1～3 回退统一进入 availability saga，不再删除 CatalogDataset、ModelSpec、实现、dbt artifact、质量运行或历史执行，也不再直接触发 `models=all/full_refresh`：

```text
platform PREPARE（同事务）
  ├─ receipt/targets = PREPARED
  ├─ source availability = INVALIDATION_PENDING
  ├─ ODS mapping fence + enabled=false
  ├─ immutable rollback command + dispatch outbox
  ├─ platform_event_outbox
  └─ platform_audit_outbox
          │
          ▼ rollbackId + idempotencyKey + payloadHash
ingestion physical rollback
  └─ operation + affected-object evidence + audit + completion outbox
          │
          ▼ authoritative completion event
platform APPLY（同事务、幂等）
  ├─ receipt = APPLIED
  ├─ availability = UNAVAILABLE, epoch + 1
  ├─ append-only availability event
  ├─ platform_event_outbox
  └─ platform_audit_outbox
```

- `AVAILABLE | INVALIDATION_PENDING | UNAVAILABLE | REVALIDATING | UNKNOWN` 中只有 `AVAILABLE` 可通过来源门禁；查询失败也必须阻断。
- 同一事件和 payload hash 重放为 no-op；同一事件不同 hash 为冲突。来源 sequence/CAS 禁止旧回退或恢复事件覆盖新 epoch。
- `resolvedVersion = sha256(schemaFingerprint + availabilityEpoch)`；因此来源恢复后，旧 WarehousePlan pin、ModelSpec revision、质量证据和 Candidate snapshot 不会自动恢复有效。
- ingestion 已成功但 platform apply 暂时失败时返回 207，receipt 保持 PREPARED，所有发布/物化仍被栅栏阻断，由 completion outbox 重试。
- 恢复只能来自新的 landing/schema revalidation 成功事件，不能由回退接口直接反置。
- platform dispatcher 使用 `SKIP LOCKED`、有界租约和稳定 idempotency key 提供 at-least-once 派发；PREPARE 提交后即使进程在首次 HTTP 前崩溃，命令仍可重领。ingestion 必须严格去重并只接受可信 completion callback。

### 5.3 物化来源 epoch 固定

物化首次取得运行租约时追加 `modeling_materialization_source_pin`，固定每个来源资产的 availability epoch、source sequence 和 event id。成功回执在同一事务中锁定并复核所有 pin；任一来源在执行期间变化，运行进入 `FAILED_STALE`，不能写成 BUILT/SUCCEEDED，也不能登记发布结果。pin 是 append-only 证据，不随恢复覆盖。

### 5.1 `platform_audit_outbox.tenant_id` 上线 preflight

`20260801_04_platform_audit_outbox_tenant.xml` 会回填全部历史行，随后增加 `NOT NULL` 和 PostgreSQL 检查约束；这是一次可能触发表扫描、行更新和 DDL 锁等待的维护窗口变更，不能按“小表在线变更”直接执行。

上线前必须先在生产只读执行并保存结果：

```sql
select count(*) as row_count,
       min(created_at) as oldest_created_at,
       max(created_at) as newest_created_at,
       count(*) filter (where status in ('PENDING', 'CLAIMED', 'RETRY')) as active_count,
       count(*) filter (where status = 'DEAD') as dead_count,
       pg_size_pretty(pg_total_relation_size('platform_audit_outbox')) as total_size
  from platform_audit_outbox;

select l.mode, l.granted, count(*) as lock_count
  from pg_locks l
  join pg_class c on c.oid = l.relation
 where c.relname = 'platform_audit_outbox'
 group by l.mode, l.granted
 order by l.mode, l.granted;
```

Go/No-Go 条件：

- 已生成数据库备份并完成可恢复性抽查，记录 `DATABASECHANGELOG` 当前版本和 outbox 行数/摘要。
- 在维护窗口暂停 outbox dispatcher 及 platform 审计写入，确认无长事务、未授予的锁等待和正在投递的 `CLAIMED` 行。
- 运维为迁移连接设置有限的锁等待上限并持续观察数据库锁、WAL、磁盘和复制延迟；超限即中止，不无限等待。
- 若画像表明一次性回填和约束校验无法落入维护窗口，则本次发布为 NO-GO，另行评审分阶段 schema 迁移；不得在现有 Liquibase 事务内临时加入应用层循环或无 checkpoint 的批处理。
- 迁移后验证 `tenant_id` 无空值、历史行统一为 `__legacy_unscoped__`、约束存在，并确认该 sentinel 不能被人工重放。

回滚边界：应用切换前可使用 changeset 自带 rollback 删除新约束和列，并核对原行数/状态；应用已开始写入真实 `tenant_id` 后不得直接回滚列，因为会不可逆丢失租户所有权。此时应停止写入，使用发布前备份恢复或执行单独评审的前向修复，再复核中央审计投递连续性。

当前版本不具备可信的请求级租户上下文，因此部署面不开放租户模式与租户标识，服务内部固定使用 `SINGLE_TENANT/default`。`MULTI_TENANT` 仍不受支持，不能通过外部配置退化成共享默认租户；审计表中的 `tenant_id` 继续承担事件归属和重放隔离职责。

人工重放只接受服务端封闭枚举中的 `reasonCode`，不接受自由文本 note。服务必须在同一事务中完成 `DEAD` 状态、租户、调用方 hash、数据库 `body_json` 重算 hash 和 CAS 校验，并使用 strict audit 写入回执；strict audit 关闭、操作者缺失或 outbox 写入失败时，重放状态必须回滚。

## 6. 关键事务边界

| 事务 | 同事务内 | 事务外 |
|---|---|---|
| 保存 ModelSpec revision | head CAS、immutable revision、业务事件、`platform_audit_outbox` | 下游投影和 dts-admin 投递 |
| 通过 StageGate/lifecycle transition | evidence checksum、状态 CAS、event、audit outbox | candidate 构建 |
| 创建/推进 Candidate | candidate version/attempt、claim、event、audit outbox | Airflow 提交 |
| 接受物化回执 | execution correlation、来源 pin 复核、relation evidence、状态 CAS、event、strict audit outbox | Catalog 外部同步 |
| 回退 PREPARE/APPLY | receipt CAS、immutable command、dispatch outbox、availability current/history、ODS fence、event、strict audit outbox | ingestion 物理回退、completion outbox 重投 |
| legacy 迁移 apply | 单批对象映射、目标 revision、migration manifest checkpoint、`platform_audit_outbox` | 下一批、备份归档 |

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
- Rollback IT：prepare/apply/restore 原子；PENDING/UNAVAILABLE/读失败均阻断；同 hash 幂等、异 hash 冲突；回退不删除目录、模型、质量和执行证据；恢复后旧证据仍 stale。
- Retirement IT：旧 bean/resource/repository 不可装配，旧 route 为普通 404，旧表不存在；历史 changelog 与中央审计仍可读。
