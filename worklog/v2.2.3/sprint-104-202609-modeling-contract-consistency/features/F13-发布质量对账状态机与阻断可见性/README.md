# F13：发布质量对账状态机与阻断可见性

**优先级**：P1
**状态**：IN_PROGRESS（设计；T01=READY，T02–T08=DRAFT，待 T01 冻结契约）
**登记日期**：2026-09-23
**设计基线**：`v2.2.3@7a2072c61`
**主契约**：[F13 发布质量对账契约](../../assests/F13-release-quality-reconcile-contract.md)
**测试设计**：[F13 发布质量对账验收](../../it/F13-发布质量对账验收.md)（F13-IT-01–12，全部 NOT_RUN）

## 目标

发布单进入“质量校验中”后，任何一张单都不会再无声卡住：数据管理员在发布面板上能看到**卡在哪、谁来处理、点哪里处理**；平台故障会被区分出来并升级告警；同一物理产物的资产登记不再因候选版本变化而永久失败。

可验收表述：对本机与 openEuler 两套环境里现存的全部 `QUALITY_RUNNING` 发布单，部署后 10 分钟内每一张要么自动推进，要么在面板上显示归属明确的阻断原因和可执行动作；同码告警日志从“每 5 秒一条”降到“变化时一条 + 每小时摘要”。

## 背景

2026-09-23 review 发布模型流程时发现（证据见账本）：

1. 本机发布单 `d0af4a93…` 自 09-21 10:48 起停在 `QUALITY_RUNNING`，reconciler 每 5 秒报 `MODEL_SPEC_GOVERNANCE_ASSET_REGISTRATION_FAILED`，2 天累计 39,811 条 WARN。
2. openEuler 测试服务器同类卡单，阻断码是 `GOVERNANCE_QUALITY_EXPIRED`（631 次/小时）和 `GOVERNANCE_QUALITY_MISSING`。
3. 面板上治理质量标红“需处理”，“当前责任”却说“服务端正在推进，刷新后查看”；证据状态 `MISSING` 被全局标签翻成“物理对象缺失”，而表实际存在。

用户要求彻底解决，而不是零散修补。本 Feature 把“质量对账”从“只打日志的轮询”改为“有持久状态、有分类、有退避、有归属、有告警”的对账过程，并让面板如实呈现。

## 现状勘察账本（F13-C01–C16）

| # | 事实 | 证据 |
|---|---|---|
| C01 | reconciler 每 5 s 取最多 20 张 `QUALITY_RUNNING` 单全量重试，阻断只打 WARN，不落任何状态 | `ModelPublicationQualityReconciler.java:69-84` |
| C02 | 工程证据 PASSED 后，先 `ensureRegistered` 再 `evaluateLive` 治理质量；任一失败返回 BLOCKED，不迁移 | `ModelPublicationQualityReconciler.java:133-146` |
| C03 | `QUALITY_PASSED` 及之后读取迁移时冻结的治理快照；发布命令校验该快照 | `CandidateGovernanceQualityEvidenceService.java:134-139,223-247`；`ModelReleaseCandidateApplicationService.java:956` |
| C04 | 迁移表：`QUALITY_RUNNING → BUILDING/QUALITY_FAILED/QUALITY_PASSED`，无超时出口 | `ModelLifecycleContract.java:115-118,141` |
| C05 | `ensureRegistered` 的 `catch (RuntimeException)` 把异常包成通用码，只在 details 放类名，日志不打印 | `CandidateQualityAssetRegistrationService.java:96-147` |
| C06 | 登记观测的 `evidenceRef` 含候选版本：`candidate:{id}:v{version}:pipeline:{run}:metadata:{checksum}` | `CandidateQualityAssetRegistrationService.java:260` |
| C07 | 本机台账：同一资产 v3 的 `MATERIALIZATION_OBSERVATION` 已 ACTIVE（10:48:02），v4 首次对账（10:48:51）起即失败；17:43 的 SCANNER 观测晚于首次失败，不是诱因 | `catalog_asset_registration_evidence`；平台日志 |
| C08 | 候选单 `BUILT → QUALITY_RUNNING` 时版本 +1（v3→v4） | `modeling_model_release_candidate_command` 记录 |
| C09 | 读侧 `withPersistedEvidence` 只在 `QUALITY_RUNNING` 时用 `previewBlocker` 预演登记前置条件（不含 observe 和治理质量） | `ModelReleaseCandidateApplicationService.java:1368-1399` |
| C10 | 读侧对 `QUALITY_PASSED/REVIEW_PENDING/APPROVED` 治理未过时过滤掉 PUBLISH | `ModelReleaseCandidateApplicationService.java:1383-1407` |
| C11 | `ModelDeliveryStatusQueryService` 已有 `QUALITY_RUNNING` + `GOVERNANCE_QUALITY_MISSING` → `WAITING_INPUT` 的投影，但只覆盖一个码 | `ModelDeliveryStatusQueryService.java:106` |
| C12 | 面板 `stepState('quality')` 只看候选状态，`QUALITY_RUNNING` 即“处理中”，不看工程证据本身 | `ModelReleaseWorkflowPanel.tsx:54-58` |
| C13 | 面板 `handoffText` 的治理提示分支要求 `reached(QUALITY_PASSED)`，`QUALITY_RUNNING` 期间落到兜底文案 | `ModelReleaseWorkflowPanel.tsx:108-129` |
| C14 | 全局标签 `MISSING → 物理对象缺失`，被治理证据状态复用 | `customerDisplayLabels.ts:8`；`ModelReleaseWorkflowPanel.tsx:295` |
| C15 | 既有路由：`/api/modeling/plans/{planId}/release-candidates` 下 `GET /workspace`、`GET /{candidateId}`、`POST /{candidateId}/refresh`、`POST /{candidateId}/governance-quality/runs` | `ModelReleaseCandidateResource.java:45-513` |
| C16 | 评审阶段 `ModelPublicationReviewReconciler` 同样“阻断只打日志”（openEuler 实测 `PUBLICATION_REQUEST_EVIDENCE_MISSING`） | `ModelPublicationReviewReconciler.java:114` |

**影响面实测（2026-09-23）**：本机 `QUALITY_RUNNING`=3、`BUILT`=3（最早 09-10）；openEuler `QUALITY_RUNNING`=1、`BUILT`=5。

**开放问题（T01 关闭）**
- Q1：观测台账拒收 v4 观测的精确规则（同通道不同 evidenceRef？observedAt 未前进？），决定 T02 改法。
- Q2：openEuler 上 `EXPIRED` 的单为什么没有被用户重跑——面板是否给出了“重新运行治理质量”（`canRerunGovernanceQuality` 要求 ruleId/ruleVersionId/bindingId 齐全）。
- Q3：`POST /{candidateId}/refresh` 现有语义与调用方，扩展是否兼容。
- Q4：质量规则目录页是否已支持 `assetKey` 预筛选参数。
- Q5：本机另外 2 张 `QUALITY_RUNNING`、3 张 `BUILT`（最早 09-10）各自卡在什么原因。

## 架构决策（F13-ADR）

| 决策 | 选择 | 理由 | 影响 |
|---|---|---|---|
| A1 状态机 | 不拆 `QUALITY_RUNNING`，不改枚举和迁移表 | 快照冻结与发布门禁依赖现有迁移时机（C03）；拆分会把死锁挪到发布步骤 | 只新增对账阶段投影，零迁移风险 |
| A2 对账状态持久化 | 新表 `modeling_release_quality_reconcile_state`，仅前向 changeSet | 当前阻断不落库，无法退避、告警、呈现、统计时长；Sprint ADR“若证据要求迁移，先补设计”——证据见 C01/C07 | 新增 1 张表；旧版本行保留作审计 |
| A3 阻断目录 | 每个码唯一映射 类别/责任角色/动作；未登记码按 SYSTEM | 让“谁来处理”成为数据而不是文案猜测 | 前后端共用同一枚举 |
| A4 退避 | 按类别指数退避，上限 30 s / 5 min；事件与“刷新状态”立即唤醒 | 去掉每 5 s 全量重试与日志风暴，同时不让用户操作后干等 | reconciler 查询改为按 `next_attempt_at` 取到期项 |
| A5 登记幂等 | 同一物理产物重复登记必须幂等，与候选版本号无关 | C06–C08：版本号变化是正常迁移，不能让登记永久失败 | 改法待 T01 Q1 冻结 |
| A6 读接口 | 扩展既有 `WorkbenchView`，不新增路由；`primaryBlocker` 保持兼容 | Sprint ADR“不新建替代入口” | 前端向后兼容 |
| A7 模块边界 | 继承 F3：发布与治理质量在数据模块办理；模型页只读展示同一数据 | F3 ADR | 面板动作跳数据/治理模块页面 |
| A8 评审阶段 | 表与目录可扩展到评审 reconciler，但本 Feature 只交付质量阶段 | 控制范围；C16 另立后续 | 后续 task 复用 |

## 契约定义

| 类型 | 契约 | 关键字段/签名 |
|---|---|---|
| 数据 | `modeling_release_quality_reconcile_state` | pk(tenant_id,candidate_id,candidate_version)；phase/blocker_code/blocker_category/owner_role/detail_json/first_seen_at/last_seen_at/attempt_count/next_attempt_at；时间全 `timestamptz`（主契约 §4） |
| 迁移 | `20260924_01_release_quality_reconcile_state.xml` | 建表 + 部分索引 `next_attempt_at where phase<>'RESOLVED'` |
| Service | `ModelPublicationQualityReconciler.reconcile(item)` | 输出 `QualityReconcileResult` 增加 `phase`、`category`；每次对账 upsert 状态行 |
| Service | 新 `ReleaseQualityBlockerCatalog.classify(code)` | → `{category, ownerRole, actionHint}`；未登记码 → SYSTEM |
| Repository | `ModelPublicationQualityEvidenceRepository.findQualityRunning(limit)` | 改为只返回到期项（无状态行视为到期） |
| REST | `GET …/release-candidates/workspace`、`GET …/{candidateId}` | `WorkbenchView.qualityReconcile`（主契约 §6） |
| REST | `POST …/{candidateId}/refresh` | `QUALITY_RUNNING` 时立即对账并返回最新视图（Q3 核对后冻结） |
| 事件 | 治理质量运行完成 / `governance-quality/runs` 提交 | 置对应候选 `next_attempt_at = now()` |
| 前端 | `ModelReleaseWorkflowPanel` props | 新增 `qualityReconcile`；步骤、责任、动作全部由其驱动 |

## UI/UX 规格

- **入口**：不新增菜单与路由。数据模块发布入口与模型工作台“发布与构建 → 发布模型”弹窗（现有 `ModelPublishDialog` → `ModelReleaseWorkflowPanel`）。
- **布局（QUALITY_RUNNING + ACTION_REQUIRED）**：
  ```
  ┌ 发布流程 ───────────────────────────── [质量校验中·待处理] ┐
  │ (✓)构建 ── (✓)工程验证 ── (!)治理数据质量 ── ( )发布登记 ── ( )上线就绪 │
  │ ┌ 当前责任：数据管理员 ───────────────────────────────┐ │
  │ │ 治理数据质量运行记录已超过有效期（24 小时）。            │ │
  │ │ 已等待 2 天 3 小时 · 最近检查 18:09 · 下次检查 18:14    │ │
  │ │ [重新运行治理质量]  [查看质量运行]  [刷新状态]          │ │
  │ └───────────────────────────────────────────────┘ │
  │ 治理数据质量记录  发布检查                                │
  │  运行记录已过期  资产 …/table:dwd_test_0921  规则版本 …    │
  └──────────────────────────────────────────────────┘
  ```
- **SYSTEM_BLOCKED**：责任区显示“平台管理员”，文案“平台处理中遇到故障，已通知管理员；你无需操作”，附 `关联 ID`（可复制），只保留 [刷新状态]。步骤条治理节点显示为“平台故障”而不是“需处理”。
- **GOVERNANCE_WAITING / ENGINEERING_PENDING**：责任区“无需操作，系统正在运行”，显示运行开始时间，无红色。
- **四态**：空（无发布单）→ 现有“暂无发布单”；加载 → 责任区骨架；错误（读接口失败）→ 保留上次数据并提示“状态读取失败，[重试]”；成功 → 上述三种形态之一。
- **标签修正**：治理证据状态使用独立标签表：`MISSING=缺少质量运行记录`、`EXPIRED=运行记录已过期`、`FAILED=质量未通过`、`RUNNING=运行中`、`PASSED=已通过`；物理对象的 `MISSING=物理对象缺失` 仅用于物理关系观测。有效期按“24 小时 / 30 分钟”格式显示。顶部资产登记列 `PENDING_GOVERNANCE` 显示“已登记（待治理）”。
- **操作走查（EXPIRED，happy path）**：1. 数据管理员打开发布面板 → 2. 看到“当前责任：数据管理员 / 运行记录已过期” → 3. 点 [重新运行治理质量] → 4. 按钮变“正在创建新运行…”，责任区变“治理质量运行中，无需操作” → 5. 运行通过后 30 秒内面板自动（或点 [刷新状态]）进入“发布登记”步骤。
- **兼容**：Chrome 95；沿用 `PrototypePrimitives` 的 `Button`/`Status`，不引入新组件库。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 | 估算 |
|---|---|---|---|---|---|
| T01 | [阻断复现与对账契约冻结](T01-阻断复现与对账契约冻结.md) | P0 | READY | 无 | 1 人日 |
| T02 | [质量资产登记幂等与根因透出](T02-质量资产登记幂等与根因透出.md) | P0 | DRAFT（待 Q1） | T01 | 1.5 人日 |
| T03 | [对账状态持久化分类与退避](T03-对账状态持久化分类与退避.md) | P0 | DRAFT（待目录冻结） | T01 | 2 人日 |
| T04 | [发布工作台读模型与即时对账](T04-发布工作台读模型与即时对账.md) | P1 | DRAFT（待 Q3） | T03 | 1 人日 |
| T05 | [发布流程面板阻断呈现重构](T05-发布流程面板阻断呈现重构.md) | P1 | DRAFT（待 Q4） | T04 | 2 人日 |
| T06 | [治理质量缺失与过期的用户闭环](T06-治理质量缺失与过期的用户闭环.md) | P1 | DRAFT（待 Q2） | T03、T05 | 1 人日 |
| T07 | [存量卡单治愈与可运维性](T07-存量卡单治愈与可运维性.md) | P1 | DRAFT | T02、T03 | 1 人日 |
| T08 | [正式构建部署与双环境验收](T08-正式构建部署与双环境验收.md) | P1 | DRAFT | T02–T07 | 1.5 人日 |

合计约 11 人日，无人员容量或工期承诺。**执行顺序**：T01 → T02 ∥ T03 → T04 → T05 ∥ T06 → T07 → T08。

## Definition of Ready

- [x] 目标可验收（部署后 10 分钟内所有卡单要么推进、要么显示归属明确的阻断）
- [x] 端到端竖切片已画通：面板 → `GET workspace` → `WorkbenchView.qualityReconcile` → reconciler/目录 → 新表
- [x] UI 落点已命名（`ModelPublishDialog` → `ModelReleaseWorkflowPanel`，不新增路由）
- [ ] 契约冻结：阻断目录（T01）、登记幂等改法（Q1）、refresh 语义（Q3）、规则页深链（Q4）
- [x] 验收可验证：F13-IT-01–12 已逐条绑定 task

## Definition of Done

- [ ] 架构：迁移在空库可执行；reconciler/目录/登记单测 + PostgreSQL IT 通过；阻断码全部登记
- [ ] UI：Chrome 95 下 ACTION_REQUIRED / SYSTEM_BLOCKED / WAITING 三种形态截图，四态齐全
- [ ] 切片：本机与 openEuler 两套环境、同一提交 SHA 与镜像 digest，存量卡单全部推进或正确归属
- [ ] 运维：告警/日志降噪实测（同码 1 小时内 WARN ≤ 1 + INFO 摘要 ≤ 1）
- [ ] 无占位证据；未执行项标 NOT_RUN

## 与既有工作的关系

- 继承 F2（交付状态与发布面板）、F3（模块边界）、F8（质量规则运行契约）的已定决策，不重议。
- S10DC-111 修复（`7a2072c61`）与本 Feature 无代码交集。
- 工作区里 S10DC-112（双模式草稿一致性）的未提交改动涉及 `DbtImplementationDraftService`/`ModelAuthoringDraftService`/前端草稿文件，与 F13 改动文件不重叠；提交时仍须按 hunk 暂存，互不夹带。
