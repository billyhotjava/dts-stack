# Sprint-81：数据建模后台重构与旧运行面物理退役

**时间**：2026-07
**状态**：IMPLEMENTED_SOURCE_VERIFIED（后台编码、物理退役、迁移契约和隔离控制面 E2E 已完成；共享环境部署与客户环境画像/备份门禁待单独执行）
**类型**：Backend Architecture / Modular Monolith / Data Migration / Controlled Retirement
**目标**：把 DTS 建模后台收敛为 `dts-platform` 内的一套模块化控制面，使新前端只通过唯一建模链完成规划、设计、门禁、发布和物化，并在同一 Sprint 内重接调用方、精确迁移后物理删除旧运行面。

## 背景与价值

Sprint-80 已完成建模展示层替换，但保存、提交、发布和物化仍保持失败关闭。后台同时存在 semantic、旧 plan、vNext、SQL model、business object 与旧 dbt 运行入口，继续接线会把新前端绑定到多套 owner。

本 Sprint 不再增加兼容壳，而是先建立模块边界与唯一状态链，再按“调用方重接 → 数据迁移/备份验证 → 物理删除”关闭旧运行时。历史 Liquibase changelog 和 `dts-admin` 中央审计历史是合规证据，永久保留。

## 架构决策记录（ADR）

| ID | 决策 | 理由 | 影响 |
|---|---|---|---|
| ADR-81-01 | `dts-platform` 采用模块化单体控制面，不拆新的建模微服务 | 事务、状态机和现有部署边界均在 platform；本轮目标是消除内部耦合而非增加网络跳数 | 用包边界、端口和 ArchUnit 守卫模块依赖 |
| ADR-81-02 | 跨域关系固定为 `integration → assets/catalog identity → quality evidence → modeling stage/release/materialization` | 资产身份与质量证据必须先于建模门禁，避免 integration 直接改模型状态 | 只允许单向端口；禁止跨域 repository 调用 |
| ADR-81-03 | 唯一建模链固定为 `WarehousePlan → ModelSpec v2/revision → StageGate → Lifecycle → ReleaseCandidate → Materialization → DbtExecutionGateway → Airflow/dbt` | 消除 semantic、business object、SQL model、vNext 的平行 owner | 所有新前端写操作和内部作业均重接该链 |
| ADR-81-04 | 资产身份唯一复用 `CatalogAssetType` + `CatalogAssetKey` | 遵守 DTS A3/A4，不为模型、质量或物化另造资产键 | 质量绑定、门禁证据和发布结果均携带同一资产键 |
| ADR-81-05 | 质量事实唯一复用 `gov_rule`、`gov_rule_version`、`gov_rule_binding`、`gov_quality_run` | 模板不是运行证据，不能建立第二套“模型质量结果”表 | StageGate 只读取版本固定、运行完成的 QualityEvidence |
| ADR-81-06 | 所有业务审计继续调用公共 `AuditService`；普通用户调用 `auditAction(...)`，受信调度器/回调使用隔离的显式 machine-actor 入口；平台事务先写 `platform_audit_outbox`，再投递 `dts-admin` | 保留统一动作字典、actor 和中央历史，同时消除进程崩溃丢审计；不放宽普通请求主体过滤 | 复用公共审计与 outbox，不复制审计历史库 |
| ADR-81-07 | `platform_event_outbox` 与 `platform_audit_outbox` 物理分表、独立 dispatcher/重试/死信处置 | 两类消息的消费者、保留期、失败语义和合规要求不同 | 禁止用一个通用 outbox 混合承载 |
| ADR-81-08 | dbt/Airflow 只经 `DbtExecutionGateway`；旧 `/etl/dbt/run` 不再是建模执行入口 | 统一幂等键、候选版本、租约、超时、取消和结果对账 | ReleaseCandidate/Materialization 不直接依赖 HTTP resource 或 Docker |
| ADR-81-09 | 旧运行面不保留长期 `410 Gone`、tombstone、双写或 feature flag | 长期兼容会继续维持平行模型 | 同一 Sprint 完成重接和物理删除；未满足门禁则阻断删除而非保留壳 |
| ADR-81-10 | 本地零数据不能外推客户环境 | 当前旧表和运行表为 0，但客户环境未知 | 每个现场必须执行只读画像、精确备份和停机判定 |
| ADR-81-11 | 中央审计历史、已执行 Liquibase changelog 永不删除或改写 | 审计留存和 Liquibase checksum 是交付事实 | 只新增 forward changeset；旧 changelog 保留 |
| ADR-81-12 | 最终 E2E 只在全部编码、迁移和物理删除完成后集中执行 | 避免反复扫描和中间兼容态误验收 | F6/T03 是唯一端到端 Go/No-Go 出口 |
| ADR-81-13 | 数据源 Level 1～3 回退统一采用“platform 同步预栅栏 + durable dispatch + ingestion 权威完成 outbox + platform 幂等应用” | 跨服务物理回退无法全局 ACID；必须先阻断旧来源证据，并关闭 PREPARE 提交后、首次 HTTP 前的崩溃窗口 | 删除旧级联删除和直触 Airflow；availability epoch 进入来源版本与候选漂移判断 |
| ADR-81-14 | 物化租约固定来源 availability epoch，成功回执必须重新核验 | 执行期间发生回退/恢复时，旧运行结果不能发布到新来源纪元 | append-only source pin；漂移运行终态为 `FAILED_STALE` |
| ADR-81-15 | 跨域编排只消费 owner-side immutable ports | 防止 Catalog、Governance、Modeling 互相直接持有 repository/entity，形成第二 owner | Catalog/标准/指标/Service 资产身份均由所属域 adapter 查询，ArchUnit 固化 |

## 目标架构

详见 [`assets/architecture-overview.md`](assets/architecture-overview.md)。

```text
Integration
    │ register/resolve
    ▼
CatalogAssetType + CatalogAssetKey
    │ bind/version/run
    ▼
QualityRule → RuleVersion → Binding → QualityRun
    │ immutable evidence
    ▼
WarehousePlan → ModelSpec v2/revision → StageGate → Lifecycle
    → ReleaseCandidate → Materialization → DbtExecutionGateway → Airflow/dbt
                                      │
                                      ├─ domain-event outbox → domain consumers
                                      └─ audit outbox → AuditService adapter → dts-admin
```

## 端到端契约链（Vertical Slice）

| 层 | 唯一契约/落点 | 关键签名与约束 |
|---|---|---|
| 新前端 | `/data-modeling/**` | 只调用 canonical `/api/modeling/warehouse-plans/**`、`/api/modeling/model-specs/**`、stage/lifecycle/candidate/materialization 契约；不调用 semantic/vNext/旧 plan/旧 ETL run |
| 规划 | `WarehousePlan` | `tenantId` 服务端解析；`planId: UUID`、`expectedVersion: long`、`idempotencyKey: string` |
| 逻辑模型 | `ModelSpec v2` + immutable revision | `modelSpecId: UUID`、`revision: int`、`planId: UUID`、`modelType`、revision-pinned dependencies；禁止 `businessObjectId` 成为 owner |
| 门禁 | `StageGate.evaluate(StageGateRequest)` | 输入 `modelSpecId/revision/targetStage`；输出 `PASS|BLOCKED` 与 catalog/quality/permission/artifact evidence refs |
| 生命周期 | canonical Lifecycle command | 只允许状态机命令推进；CAS 冲突 409/412，越权 403，证据不足 422 |
| 发布 | `ReleaseCandidate` | candidate/version/attempt 唯一；审核人与操作员职责不被快捷入口绕过 |
| 物化 | `MaterializationCommand` | `candidateId/version/attempt/environment/executionTargetKey/artifactChecksum/profileLeaseId/idempotencyKey` |
| 执行 | `DbtExecutionGateway` | `submit/query/cancel`；Airflow adapter 生成确定性 `dagRunId`，dbt 回执必须与 candidate/version/attempt 对账 |
| 资产 | `CatalogAssetType` + `CatalogAssetKey` | 所有来源、质量绑定、模型引用和物化结果使用统一资产身份 |
| 质量 | `gov_rule*` + `gov_quality_run` | StageGate 只接受 pinned ruleVersion/binding/run，且 run 必须终态、未过期、资产键一致 |
| 事件 | `platform_event_outbox` | 与聚合事务同提交；at-least-once；消费者按 `eventId` 幂等 |
| 审计 | `platform_audit_outbox` → public `AuditService` → `dts-admin` | 生产者 + eventId + payload hash 幂等；payload 脱敏；不得写入业务事件表 |
| 回退失效 | `RollbackInvalidationReceipt` + durable dispatch + `CatalogAssetAvailability` | 先 `INVALIDATION_PENDING` 并提交不可变命令，再由 ingestion 成功事件推进 `UNAVAILABLE`；同 event/hash 幂等，异 hash 冲突；恢复必须来自新的来源复验事件 |
| 物化来源一致性 | `ModelMaterializationSourcePin` | 首次租约固定 epoch/sequence/event；成功回执发现漂移则 `FAILED_STALE`，禁止发布 |
| 数据迁移 | manifest + dry-run/apply/verify/rollback | 源/目标逐对象映射、count/hash、冲突清单、备份 URI/checksum；未满足即 fail-closed |
| 物理退役 | forward-only Liquibase + source deletion | 同 Sprint 先重接和迁移；删除 resource/service/entity/repository/table；历史 changelog 与中央审计不动 |

## 现状勘察账本（Context Ledger）

| # | 事实 | 证据 |
|---|---|---|
| CL-01 | Sprint-80 已替换建模展示层，后台写动作保持失败关闭 | `../sprint-80-202607-prototype-modeling-ui-replacement/README.md`“交付边界” |
| CL-02 | 当前 Compose 中 `dts-platform` 为 running/healthy | `docker compose -f docker-compose-app.yml ps --format json`，2026-07-31 |
| CL-03 | canonical 本地基线：ModelSpec 6/revision 10、DimensionDefinition 7/revision 21、WarehousePlan 4、DataMart 2、glossary 42、template 4 | `assets/domain-profile.md` §3 |
| CL-04 | legacy semantic/sql/business_object/plan 以及运行、候选、物化数据当前均为 0 | `assets/domain-profile.md` §3；只代表当前环境 |
| CL-05 | 质量模板 10，但质量运行事实为 0 | `gov_quality_template` seed 与 `assets/domain-profile.md` §3 |
| CL-06 | Catalog 统一资产标识实现已存在 | `source/dts-platform/.../service/catalog/CatalogAssetKey.java:10`、`CatalogAssetType.java:5` |
| CL-07 | 公共审计入口为 `AuditService.auditAction(actionCode, stage, resourceId, payload)` | `source/dts-platform/.../service/audit/AuditService.java` |
| CL-08 | 质量规则、版本、绑定和运行已有 canonical 表 | `20251008_01_governance_quality_tables.xml` |
| CL-09 | legacy runtime ledger 为 `modeling_legacy_object_migration_batch`、`modeling_legacy_object_migration`、`modeling_legacy_api_usage` | `20260719_07_legacy_object_retirement.xml` |
| CL-10 | 当前工作树有 Sprint-80 与其他并行改动；Sprint-81 不回退、不吸收它们 | 2026-07-31 `git status --short` |
| CL-11 | 当前数据库 `modeling_model_spec` 只有 `contract_version=2`（6 行），v1 revision、business object、standard binding、registration step 均为 0 | 2026-07-31 只读 PostgreSQL count；只代表当前环境 |
| CL-12 | semantic/vNext runtime/legacy lifecycle compatibility 已无生产入口，删除 changeset 对非空旧数据 fail-closed | `20260801_01_retire_legacy_semantic_modeling.xml` 与对应 source/route contract test |
| CL-13 | 旧回退级联会删除 SQL 模型、目录数据集和 ODS 映射，并可绕过 Candidate/Materialization 直触 Airflow full-refresh | 旧 `RollbackCascadeService`；Sprint-81 将其整体删除并改为 availability fence |

**开放问题与硬门禁**：

- 客户环境所有待退役表的数量、状态、引用和 30/90 天外部调用未画像；F5 物理 DROP 前必须逐环境关闭。
- legacy migration/usage ledger 的历史是否已被导出并获审计/运维签字，需由 F5/T04 冻结清单。
- 质量运行数据为 0，无法证明真实 StageGate 质量证据链；F6 必须创建隔离测试运行，不代写客户业务事实。
- 最终认证 UI/API/dbt/Airflow E2E 按用户约定延后到全部编码结束。

## Gate Registry

| Gate | 项目 | 状态 | 证据 | 未过则关联 Task |
|---|---|---|---|---|
| G0 | 交付基线 | GAP | `it/baseline.md`：平台健康；客户画像、备份恢复和最终 E2E 未完成 | F0/T02、F0/T03 |
| G0 | 领域与数据画像 | GAP | `assets/domain-profile.md`：当前环境已画像，客户环境未知 | F0/T02 |
| G0 | DTS 领域不变量 | PASS | ADR-81-01～12；Catalog、审计、迁移红线已纳入 | - |
| G1 | 模块与契约链 | PASS | owner ports、ArchUnit 与 canonical ModelSpec/ReleaseCandidate/Materialization 主链已落地 | - |
| G1 | 非功能预算 | GAP | `assets/nfr-budget.md`；客户量级相关阈值待画像复核 | F0/T02、F6/T01 |
| G3 | 迁移与退役安全 | PASS_SOURCE / ENV_GATE | forward-only 退役 changeset、锁内重检、不可变证据及回滚阻断已验证；客户环境画像/备份仍是部署前硬门禁 | F0/T02、F6/T02 |
| G4 | 可运维性 | PASS_SOURCE | domain/audit/rollback/secret-restore outbox 分离，具备重试代次、stale-claim fencing、DLQ/replay 与指标 | - |
| G4 | DoD 验收 | PASS_CONTROL_PLANE / DEPLOY_PENDING | `it/README.md`；真实 PostgreSQL 控制面 E2E 已通过，未把未部署的新代码冒充在线 UI/Airflow 验收 | F6/T03 部署阶段 |

## Feature 列表

| ID | Feature | Task 数 | 优先级 | 状态 |
|---|---|---:|---|---|
| F0 | 基线与退役门禁 | 3 | P0 | SOURCE_COMPLETE / CUSTOMER_GATE |
| F1 | 模块化控制面边界 | 3 | P0 | COMPLETE |
| F2 | 唯一建模状态链 | 3 | P0 | COMPLETE |
| F3 | 跨域证据与耐久消息 | 4 | P0 | COMPLETE |
| F4 | dbt 执行网关与调度 | 3 | P0 | COMPLETE |
| F5 | 精确迁移与物理退役 | 4 | P0 | SOURCE_COMPLETE / CUSTOMER_GATE |
| F6 | 集成验收与发布门禁 | 3 | P0 | CONTROL_PLANE_PASS / DEPLOY_PENDING |

**实施顺序**：F0 → F1 → F2/F3 → F4 → F5 → F6。F2 与 F3 可在 F1 的端口和模块守卫冻结后并行；F5 每个批次只可在对应调用方重接和迁移门禁通过后执行；F6/T03 只能最后执行。

## 追溯矩阵

| 需求点 | Feature/Task | 适应度/验收 | 证据位置 |
|---|---|---|---|
| dts-platform 模块化控制面 | F1/T01～T03 | ArchUnit 禁止逆向依赖和跨域 repository | `it/IT-01` |
| 唯一建模状态链 | F2/T01～T03 | state-machine、CAS、revision pin、候选唯一性 IT | `it/IT-02` |
| Catalog 与质量事实复用 | F3/T01～T02 | 资产键 round-trip；质量 evidence 版本/终态/过期测试 | `it/IT-03` |
| durable 审计到 dts-admin，且与事件分离 | F3/T03～T04 | crash/retry/replay、分表和中央审计分类断言 | `it/IT-04` |
| dbt/Airflow 统一执行网关 | F4/T01～T03 | 幂等提交、超时、取消、回执对账和故障注入 | `it/IT-05` |
| 旧运行面物理删除 | F5/T01～T04 | caller=0、manifest verify、schema absent、旧路由 404、无 410/tombstone | `it/IT-06` |
| 升级/回滚可证明 | F0/T03、F6/T02 | 空库/存量库升级，备份恢复 checksum 一致 | `it/IT-07` |
| 最终真实旅程 | F6/T03 | 认证 UI → API → quality → candidate → Airflow/dbt → catalog/audit | `it/IT-08` |

## Sprint 完成标准

- [ ] 新前端和内部作业只使用唯一建模链，旧 semantic/plan/vNext/SQL/business-object/dbt-run owner 无运行时调用方。
- [ ] 模块依赖、状态机、资产身份、质量证据、事件 outbox、审计 outbox 和执行网关均有可执行适应度测试。
- [ ] 每个客户/目标环境都有迁移 manifest、备份 checksum、apply/verify/rollback 证据；未知存量不会被 DROP。
- [ ] 旧 routes/services/entities/repositories/tables 被物理删除，不保留长期 410、tombstone、双写或隐藏开关。
- [ ] `dts-admin` 中央审计历史和历史 Liquibase changelog 完整保留。
- [ ] 全部编码完成后一次执行 IT-01～IT-08；真实认证、质量、发布、物化、Catalog 和审计链通过。

## 非目标

- 不拆分新的微服务，不迁移前端信息架构，不新增菜单。
- 不另造 Catalog 资产键、质量规则/运行、模型台账、发布候选、运行台账或审计中心。
- 不修改历史 Liquibase changelog，不删除中央审计历史。
- 不以当前环境 0 行推断客户环境安全，不替客户代写业务元数据。
- 不在中间编码批次反复跑全量 E2E，也不把 source test/build 当作最终运行验收。
