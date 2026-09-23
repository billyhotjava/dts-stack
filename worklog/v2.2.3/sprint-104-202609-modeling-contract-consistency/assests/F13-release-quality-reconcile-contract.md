# F13 发布质量对账契约（草案，待 F13-T01 冻结）

**状态**：DRAFT（2026-09-23 登记；T01 完成复现与核对后改为 FROZEN）
**设计基线**：`v2.2.3@7a2072c61`
**适用范围**：发布单进入 `QUALITY_RUNNING` 之后、迁到 `QUALITY_PASSED` / `QUALITY_FAILED` 之前的对账过程，以及发布面板对这一段的呈现。

## 1. 问题陈述（证据见 F13 README 账本）

`QUALITY_RUNNING` 目前是一个**没有期限、没有归属、用户看不见原因**的等待态：

- reconciler 每 5 秒对所有 `QUALITY_RUNNING` 候选单重新对账，遇到阻断只打一条 WARN 并返回，不写任何状态（`ModelPublicationQualityReconciler.java:69-84`）。
- 阻断原因混杂了三类：平台自身故障（资产登记失败）、需要用户操作（治理质量缺失/失败/过期、密级未定）、正常等待（治理质量运行中）。三类在代码和界面上没有区分。
- 前端 `handoffText` 只在 `QUALITY_PASSED` 之后才提示治理问题，`QUALITY_RUNNING` 期间一律显示“服务端正在推进”；步骤条却已把治理质量标红“需处理”（`ModelReleaseWorkflowPanel.tsx:108-129`、`:80-89`）。
- 资产登记在同一物理产物、候选版本 v3→v4 后必然失败，异常被 `catch (RuntimeException)` 吞成通用码（`CandidateQualityAssetRegistrationService.java:133-147`）。本机一张单 2 天累计 39,811 条告警。

## 2. 状态语义（保持枚举与迁移表不变）

`DeliveryStatus` 与 `ModelLifecycleContract` 迁移表**不改**。`QUALITY_RUNNING` 的含义冻结为“正在产出工程验证结论与治理质量冻结快照”。在其内部新增一个**对账阶段投影**（持久化，见 §4），只用于调度、告警和呈现，不参与状态迁移判定：

| phase | 含义 | 进入条件 | 离开条件 |
|---|---|---|---|
| `ENGINEERING_PENDING` | 工程验证证据尚未落定 | `EvidenceState.PENDING` | 证据 PASSED / FAILED |
| `GOVERNANCE_WAITING` | 工程验证已通过，治理质量正在运行 | 工程 PASSED 且治理 `RUNNING` | 治理结论变化 |
| `ACTION_REQUIRED` | 工程验证已通过，需要人处理后才能继续 | 阻断类别 = `USER_ACTION` | 用户处理后下一次对账通过或类别变化 |
| `SYSTEM_BLOCKED` | 平台故障，用户无法自助处理 | 阻断类别 = `SYSTEM` | 故障消除 |
| `RESOLVED` | 已迁出 `QUALITY_RUNNING` | 迁到 QUALITY_PASSED / QUALITY_FAILED / STALE | —（行保留作审计） |

**不拆状态的理由**：`QUALITY_PASSED` 及之后的状态读取的是迁移时冻结的治理快照（`CandidateGovernanceQualityEvidenceService.java:134-139`、`:223-247`），发布命令用 `requirePublishableSnapshot` 校验这份快照（`ModelReleaseCandidateApplicationService.java:956`）。若工程验证先行迁到 `QUALITY_PASSED`，冻结的将是失败快照，发布永久被拒。拆状态需要同时重做快照冻结时机、发布门禁、重跑服务与前端步骤条，不在 F13 范围。

## 3. 阻断目录（blocker catalog）

每个阻断码必须且只能映射到一个类别、一个责任角色、一个用户动作。未登记的码一律按 `SYSTEM` 处理并在日志中标记 `uncatalogued=true`。

| 阻断码 | 类别 | 责任角色 | 面板动作 | 备注 |
|---|---|---|---|---|
| `MODEL_SPEC_GOVERNANCE_ASSET_REGISTRATION_FAILED` | SYSTEM | PLATFORM_OPERATOR | 显示关联 ID，提示联系平台管理员 | T02 修复主因后应只在真实故障时出现 |
| `MODEL_SPEC_GOVERNANCE_QUALITY_EVIDENCE_UNAVAILABLE` | SYSTEM | PLATFORM_OPERATOR | 同上 | 质量服务不可读 |
| `MODEL_SPEC_GOVERNANCE_QUALITY_POLICY_UNAVAILABLE` | SYSTEM | PLATFORM_OPERATOR | 同上 | 策略不可读 |
| `MODEL_RELEASE_QUALITY_EVIDENCE_MISSING` | SYSTEM | PLATFORM_OPERATOR | 同上 | 构建证据缺失 |
| `MODEL_SPEC_GOVERNANCE_QUALITY_MISSING` | USER_ACTION | DATA_ADMIN | “配置质量规则”（带资产深链）；已有绑定时“运行治理质量” | |
| `MODEL_SPEC_GOVERNANCE_QUALITY_FAILED` | USER_ACTION | DATA_ADMIN | “查看质量运行”+“重新运行治理质量” | |
| `MODEL_SPEC_GOVERNANCE_QUALITY_EXPIRED` | USER_ACTION | DATA_ADMIN | “重新运行治理质量” | openEuler 实测 631 次/小时 |
| `MODEL_SPEC_GOVERNANCE_CLASSIFICATION_REQUIRED` | USER_ACTION | DATA_ADMIN | “补充密级”（跳资产治理） | |
| `MODEL_RELEASE_CANDIDATE_STALE` | USER_ACTION | MODEL_MAINTAINER | “创建替代发布单” | `ModelReleaseCandidateContract.java:38` |
| `MODEL_SPEC_GOVERNANCE_QUALITY_RUNNING` | WAITING | NONE | 无动作，显示“治理质量运行中” | |
| （工程证据 PENDING） | WAITING | NONE | 无动作 | 不是阻断码，phase=ENGINEERING_PENDING |

T01 须用源码逐一核对上表的码值与产生位置，补齐遗漏码后冻结。

## 4. 持久化契约（新增表，仅前向 changeSet）

Liquibase：`dts-platform/src/main/resources/config/liquibase/changelog/20260924_01_release_quality_reconcile_state.xml`

```sql
create table modeling_release_quality_reconcile_state (
  tenant_id          varchar(128) not null,
  candidate_id       uuid         not null,
  candidate_version  integer      not null,
  phase              varchar(32)  not null,  -- §2 phase
  blocker_code       varchar(128),
  blocker_category   varchar(16),            -- SYSTEM | USER_ACTION | WAITING
  owner_role         varchar(32),            -- PLATFORM_OPERATOR | DATA_ADMIN | MODEL_MAINTAINER | NONE
  detail_json        jsonb        not null default '{}'::jsonb, -- failureType, causeMessage(截断512), assetKeys[], correlationId
  first_seen_at      timestamptz  not null,
  last_seen_at       timestamptz  not null,
  attempt_count      integer      not null default 1,
  next_attempt_at    timestamptz  not null,
  constraint pk_release_quality_reconcile_state primary key (tenant_id, candidate_id, candidate_version),
  constraint fk_release_quality_reconcile_candidate foreign key (tenant_id, candidate_id)
    references modeling_model_release_candidate (tenant_id, id) on delete restrict,
  constraint ck_release_quality_reconcile_phase check (phase in
    ('ENGINEERING_PENDING','GOVERNANCE_WAITING','ACTION_REQUIRED','SYSTEM_BLOCKED','RESOLVED')),
  constraint ck_release_quality_reconcile_category check (blocker_category is null or blocker_category in
    ('SYSTEM','USER_ACTION','WAITING')),
  constraint ck_release_quality_reconcile_attempts check (attempt_count > 0 and last_seen_at >= first_seen_at)
);
create index idx_release_quality_reconcile_due
  on modeling_release_quality_reconcile_state (next_attempt_at)
  where phase <> 'RESOLVED';
```

- 时间列一律 `timestamptz`，写入用 `Instant`，避免 [平台时间戳口径] 问题。
- `candidate_version` 变化即新行；旧版本行保留，供审计与“卡了多久”统计。
- `detail_json.causeMessage` 只存异常消息的前 512 字符，不含堆栈、不含 SQL 参数。

## 5. 调度与退避

| 类别 | 首次重试 | 退避 | 上限 | 立即唤醒条件 |
|---|---|---|---|---|
| WAITING / ENGINEERING_PENDING | 5 s | ×2 | 30 s | 质量运行完成事件 |
| USER_ACTION | 30 s | ×2 | 5 min | 治理重跑提交、规则绑定变更、用户点“刷新状态” |
| SYSTEM | 30 s | ×2 | 5 min | 用户点“刷新状态” |

- `findQualityRunning` 改为只取 `next_attempt_at <= now()` 的候选（无状态行的视为到期）。
- 日志：阻断码**变化时**打一次 WARN；同码持续时每小时最多一次 INFO 摘要；`SYSTEM` 持续超过 30 分钟打 ERROR（`event=model_publication_quality_system_blocked_escalated`）。
- 指标：`dts_release_quality_blocked{category,code}` 当前数量；`dts_release_quality_blocked_age_seconds` 最大时长。

## 6. 读接口契约（扩展既有，不新增路由）

`GET /api/modeling/plans/{planId}/release-candidates/workspace`（及 `/{candidateId}`）响应的 `WorkbenchView` 增加可空字段：

```json
"qualityReconcile": {
  "phase": "ACTION_REQUIRED",
  "blocker": {
    "code": "MODEL_SPEC_GOVERNANCE_QUALITY_EXPIRED",
    "category": "USER_ACTION",
    "ownerRole": "DATA_ADMIN",
    "message": "治理数据质量运行记录已超过有效期",
    "actionHint": "RERUN_GOVERNANCE_QUALITY",
    "assetKeys": ["source:.../schema:public/table:dwd_test_0921"],
    "correlationId": null
  },
  "firstSeenAt": "2026-09-21T02:48:51Z",
  "lastSeenAt": "2026-09-23T10:09:50Z",
  "attemptCount": 42,
  "nextAttemptAt": "2026-09-23T10:14:50Z"
}
```

- `actionHint` 枚举：`CONFIGURE_QUALITY_RULES` | `RUN_GOVERNANCE_QUALITY` | `RERUN_GOVERNANCE_QUALITY` | `VIEW_QUALITY_RUN` | `SET_CLASSIFICATION` | `CREATE_REPLACEMENT` | `CONTACT_OPERATOR` | `NONE`。
- `primaryBlocker` 保持兼容：`QUALITY_RUNNING` 时由 `qualityReconcile.blocker` 填充（替代当前只覆盖登记前置条件的 `previewBlocker`）。
- 非 `QUALITY_RUNNING` 时 `qualityReconcile` 为 `null`。
- `POST /{candidateId}/refresh` 语义扩展：`QUALITY_RUNNING` 时把 `next_attempt_at` 置为 now 并同步执行一次对账，返回最新 `WorkbenchView`（T04 先核对该接口现有语义，不破坏已有调用方）。

## 7. 资产登记幂等（T02）

同一物理产物（`pipelineRunId` + `metadataChecksum` 相同）重复登记必须幂等成功，不受候选版本号变化影响。具体改法（调整 `evidenceRef` 构成，或在观测台账中把同通道、同物理证据的版本变化视为同一观测）由 T01 复现拒收规则后在本节冻结。异常必须保留 `failureType` 与截断后的 `causeMessage` 写入 §4 `detail_json` 并打印一次带 `correlationId` 的 ERROR 日志。

## 8. 不在范围

- 不拆分 `QUALITY_RUNNING` / 不改快照冻结时机 / 不改发布门禁。
- 不新增菜单、不新增路由；质量规则页只增加 `assetKey` 查询参数的预筛选（如该页尚不支持，由 T05 补齐）。
- 不改变 F3 边界：发布与治理质量仍在数据模块办理，模型页只读展示。
- 评审阶段的 `ModelPublicationReviewReconciler` 存在同类“阻断只打日志”模式（openEuler 实测 `MODEL_RELEASE_PUBLICATION_REQUEST_EVIDENCE_MISSING`，产生于 `ModelPublicationReviewReconciler.java:114`）。F13 的表结构与目录设计须能直接扩展到该阶段，但本 Feature 只交付质量阶段；评审阶段的接入另立后续 task。
