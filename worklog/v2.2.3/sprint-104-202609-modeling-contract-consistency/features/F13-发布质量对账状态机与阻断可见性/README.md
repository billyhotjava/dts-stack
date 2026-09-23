# F13：发布质量处理与失败恢复

**优先级**：P1（核心恢复任务P0）；**状态**：IN_PROGRESS（设计，尚未编码）。
**日期/基线**：2026-09-24，`v2.2.3@576aba566`。
保留原目录与任务文件名以兼容已有链接；本标题替代“仅阻断可见性”的旧目标。

## 用户目标与范围

构建完成后，用户能够完成资产登记、工程验证结果确认、治理质量处理并继续发布；遇到失败能看见真实原因、责任和合法操作，平台恢复后不重复执行已经完成的SQL。

F13只负责构建之后的发布质量阶段；[F14](../F14-模型构建与失败恢复/README.md)负责构建本身和分阶段恢复。用户已确认后续一起编码，共用[联合设计与实施顺序](../../assests/F13-F14-联合设计与实施顺序.md)。本次只完善文档。

[主设计](../../assests/F13-release-quality-reconcile-contract.md)与[F13验收](../../it/F13-发布质量对账验收.md)取代原草案；新增/修订用例全部NOT_RUN。T01 READY，其余任务在M0共享设计核对完成后方可进入编码。

## 本轮纠正的设计

1. 候选版本变化与登记失败是时间关联，尚未证明因果；先真实复现，再决定幂等改法。保留不同构建的历史证据。
2. 保留旧`/refresh`使发布单失效的含义；查询刷新只读，新的立即检查命令持久化唤醒，不在HTTP中运行长检查。
3. 并发不只发生于多实例：定时、手动和事件都可能竞争。加入版本/领取校验、唤醒generation与重启恢复，不能只用upsert。
4. 等待有期限且超时可诊断；旧行及时结束，单张异常隔离；读取失败不显示“正常运行”。
5. 错误分类结合规则绑定和权限生成动作；后台与真实状态回写使用一致的受限身份。
6. 页面独立展示工程验证与治理质量；未经真实通知不能写“已通知管理员”。
7. 增加真实数据库、真实身份回写和联合链路故障验证；不只依赖mock与源码字符串断言。

## 历史观测账本（F13-C01–C16）

以下保留原文的2026-09-23观测，代码行号基于当时版本；本轮没有重连现场，数量/样本必须在T01重新核对，不能当现况或根因证明。


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


## 设计与页面

持久化qualityReconcile是调度与诊断记录，不替代候选和质量运行的业务事实。保留总状态和通过时冻结质量结论的规则；具体字段、并发、超时、权限及API见主设计。必要工程/资产/安全条件仍拒绝非法发布；治理业务问题按ADVISORY/BLOCKING决定提示或阻止，不自动补规则或放宽密级。

现有发布面板显示：构建结果→工程验证→治理质量→发布登记。工程验证已有通过记录即勾选；治理需处理时展示具体资产、原因和处理入口；系统故障显示关联ID；等待显示真实运行与时间。加载、空、读取失败和成功四态完整，Chrome95与窄屏可用。重跑/重新检查失败保留已有信息，禁止无依据清空或自动重建模型。

可验收示例：SQL与关系核验已经成功，BLOCKING下质量规则过期；用户看到“构建完成、工程验证通过、治理质量已过期”，按合法动作重跑后进入发布步骤，整个过程中dbt_build调用次数不增加。

## 本轮架构与测试补充（2026-09-24）

按联合设计A01–A05明确：既有命令服务统一修改候选状态；F14提交可重读的完成引用，F13幂等消费；原失败执行与恢复operation独立；质量检查使用qualityRoundId识别同版本重跑；ADVISORY仅提示、BLOCKING必须通过，不修改现场策略。保留通过时冻结结果，策略变化不静默改写已冻结候选。

- [单元测试用例](单元测试用例.md)：本Feature16项，明确输入、独立预期、替身与任务映射。
- [联合系统测试用例](../../it/F13-F14-系统测试用例.md)：12个详细场景，覆盖两策略、恢复/旧回调/重启、交接事务、乱序质量结果及正式回退。
- 原IT编号保留，单元/集成/系统证据分别记录；ST细化IT，不能重复累计同一业务覆盖。全部NOT_RUN，未编写测试源码、未执行。

## Task 列表

| ID | Task | 状态 | 依赖 |
|---|---|---|---|
| T01 | [根因复现与联合设计核对](T01-阻断复现与对账契约冻结.md) | READY | 无；与F14-T01分别探索，在M0共同确定共享规则 |
| T02 | [资产登记修复与根因保留](T02-质量资产登记幂等与根因透出.md) | DRAFT | F13-T01的真实复现及M0；与F14-T03共用执行证据 |
| T03 | [质量检查状态、并发与超时恢复](T03-对账状态持久化分类与退避.md) | DRAFT | M0；F13-T01完整错误目录、状态与DDL确定 |
| T04 | [只读状态查询与立即检查命令](T04-发布工作台读模型与即时对账.md) | DRAFT | M0、F13-T03；共享工作台DTO与F14-T06一次集成 |
| T05 | [发布面板阶段与处理动作](T05-发布流程面板阻断呈现重构.md) | DRAFT | F13-T04接口；与F14-T06统一组件修改 |
| T06 | [治理质量配置与重跑恢复](T06-治理质量缺失与过期的用户闭环.md) | DRAFT | 后端依赖F13-T03/T04（M2）；页面验收依赖T05（M3）；复用F8 |
| T07 | [存量卡单处置与运维说明](T07-存量卡单治愈与可运维性.md) | DRAFT | F13-T02–T06、F14阶段状态可读 |
| T08 | [F13/F14联合交付与质量验收](T08-正式构建部署与双环境验收.md) | DRAFT | F13-T02–T07、F14-T02–T07；共同基线M0 |

READY=1、DRAFT=7、IN_PROGRESS=0、DONE=0、BLOCKED=0（Task统计）。原11人日估算不再代表联合修订范围；M0后重估开发、集成、测试、迁移及回退工作量。联合顺序见共享设计，不能先独立完成F13再让F14重写共享链路。

## Ready / Done 与未决项

- [x] 用户范围、F13/F14职责与联合编码要求明确。
- [ ] T01取得真实登记异常与复现、完整错误目录、规则深链及当前存量画像。
- [ ] M0冻结状态表/领取/唤醒/超时、立即检查API、身份及F14交接；相关编码任务才可READY。
- [ ] F13-IT-01–21及F14联合场景按真实层次执行，原问题复验通过。
- [ ] 同一提交的正式制品、两环境部署、Chrome95页面与回退分别留证。
- [ ] 存量卡单均有原因、责任和处理路径；平台故障与待用户处理区分，不能要求所有单自动放行。

G0=GAP（历史环境需刷新），G1=GAP（M0未完成），G2/G3/G4=PENDING。评审阶段重构不在本Feature；已修复的S10DC-111/112仅回归，不重新立项。文档完成不代表开发完成。
