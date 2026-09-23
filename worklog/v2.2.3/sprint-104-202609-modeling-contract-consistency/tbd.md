# TBD：建模版本语义梳理与改造方案（草案）

> 状态：**草案，暂存**。权限与 Keycloak 重构方案完成后，再合并完善第 5 节（交叉点）和第 7 节（待决问题），然后才能进入 DoR。
> 日期：2026-09-19　基线提交：`c40a3968d`（v2.2.3）
> 关联：[F11 权限重构架构与实施契约](assests/F11-permission-architecture.md)（§5.2 发起人反查、Q25、Q26 与本文同域，待合并）、本目录 `README.md`（F5 状态语义）、`it/20260907-authoring-version-chain-fix.md`（编写态两条版本链）、2026-09-18～19 派发收口提交 `52a74b6b9` `6f6be6a54` `8be931d29` `fa88f11d3` `ce9f13aaf` `451dfd385` `8e98ecdf8` `c40a3968d`

## 0. 结论先行

- 2026-09-18 线上"无限构建中"的根因**不是**版本管理，而是 DAG 文件权限、Airflow 错误分类、审计幂等和状态机缺少出口（已在上述提交中收口）。
- 但版本语义确实混乱：它是**放大器**，也是 09-19 自查发现的两个缺陷（`8e98ecdf8`）的直接来源，以及 09-07 编写态"两条版本链"问题的同类问题。
- 核心问题只有一句话：**同一个 `version` 字段同时承担"并发令牌"和"构建身份"两种语义；同一次构建也没有唯一主键。** 其余问题大多由此派生。
- 改造方向：把版本标识拆成三类，各管一件事——**内容版本**（是什么）、**并发令牌**（谁的写入生效）、**执行身份**（哪一次运行）。系统自动迁移只认执行身份，不再用并发令牌。

## 1. 现状：版本标识清单

一次"从编写到上线"的链路中，同时存在下面这些版本和身份标识。

### 1.1 编写态

| 标识 | 载体 | 语义 | 写入方 | 校验方 | 不一致时 |
|---|---|---|---|---|---|
| 模型 `revision` / `checksum` | `modeling_model_spec` | 模型定义的内容版本 | 保存模型定义 | 编写草稿、发布单锁定、漂移检测 | `DBT_DRAFT_BASE_MODEL_CONFLICT`、候选单 STALE |
| 实现 `revision` / `checksum` | 模型实现表（`20260724_02`） | 加工实现的内容版本 | 保存实现、提交草稿 | 编写草稿、发布单条目、物理关系观测 | `DBT_DRAFT_BASE_IMPLEMENTATION_CONFLICT` |
| 草稿 `base_model_revision` / `base_implementation_revision` / ETag | `20260802_02_modeling_dbt_implementation_draft` | 草稿基于哪个版本创建；并发令牌 | 创建或同步草稿 | 保存、校验、提交 | 09-07 两次现场冲突 |

### 1.2 发布单（候选单）

| 标识 | 载体 | 语义 | 写入方 | 校验方 | 不一致时 |
|---|---|---|---|---|---|
| 候选单 `version` | `modeling_model_release_candidate.version` | **①并发令牌**（前端 If-Match）<br>**②构建身份**（见 1.3） | 每次状态迁移 +1（`ModelReleaseCandidateRepository.java:624,762`） | `requireExpectedVersion`（用户命令），也被系统路径使用 | `MODEL_RELEASE_CANDIDATE_VERSION_CONFLICT` |
| 条目 `revision` / `checksum` / 实现版本 | `modeling_model_release_candidate_entry` | 锁定的模型和实现内容 | 创建或锁定候选单 | `scopeDriftEntries`、重试漂移门 | `MODEL_RELEASE_CANDIDATE_STALE` / `_SCOPE_STALE` / `_DRIFT_REQUIRED` |
| `artifact_bundle_checksum` | 条目、派发 | 构建产物包内容 | 锁定候选单、派发 | 派发前比对 | `MATERIALIZATION_ARTIFACT_BUNDLE_DRIFT` |
| `dependency_snapshot_checksum` / `active_claim_key` | 条目（`20260727_10`） | 依赖快照、占用键 | 锁定候选单 | 重试 | `MODEL_MATERIALIZATION_RETRY_SNAPSHOT_STALE`、`MODEL_UPSTREAM_PIN_STALE` |
| `materializationPlanChecksum` | 预览接口 → 命令参数 | 依赖构建计划的版本 | 预览 | 创建、重新构建 | `MODEL_MATERIALIZATION_PLAN_STALE` |

### 1.3 执行

| 标识 | 载体 | 语义 | 写入方 | 校验方 | 不一致时 |
|---|---|---|---|---|---|
| 派发 `candidate_version` | `modeling_materialization_dispatch` | 创建派发时候选单的 `version`，之后固定不变 | 开始构建、重试 | 判失败、超时路径的期望版本；`ModelingExecutionAuthorization.candidate` 反查发起人 | 非法迁移或事务回滚（09-19 缺陷②） |
| `attempt` | 派发、流水行 | 第几轮构建 | 开始构建（1）、重试（+1） | 重试取最新一轮 | `MODEL_MATERIALIZATION_RETRY_RECONCILIATION_REQUIRED` |
| 派发 `id` = `pipeline_run_group_id` | 派发、流水行 | 一次构建运行的主键 | 按规则生成稳定 UUID（见下） | 回调、同步、完成确认 | `MODEL_DBT_RUN_IDENTITY_MISMATCH` |
| `airflow_run_id` | 派发 | Airflow 端这一轮运行的 id | 同上 | 对账 | `MODEL_AIRFLOW_RUN_IDENTITY_CONFLICT` |
| `scoped_bundle_checksum`、运行令牌摘要和有效期 | 派发 | 实际下发的项目包、Airflow 回调凭证 | 派发准备 | 回调读取运行规格 | `MATERIALIZATION_SCOPED_BUNDLE_DRIFT`、`MODEL_RUNTIME_SPEC_TOKEN_EXPIRED` |
| `dispatch_attempts` | 派发 | 被认领的次数 | 每次认领 +1 | 09-19 起用于退避和重试上限 | `MODEL_MATERIALIZATION_DISPATCH_RETRY_EXHAUSTED` |
| 数据源可用性锚点（epoch / sequence / event_id） | `modeling_materialization_source_pin` | 数据源代际 | 派发准备时固定 | 回调、完成确认 | `FAILED_STALE`、`MODEL_SOURCE_AVAILABILITY_FENCE_ACTIVE` |
| 物理关系观测中的模型和实现版本 | `20260727_13` | 实际落表对应哪个版本 | 同步探测 | 证据投影 | 关系核验失败 |

**构建身份有两种生成规则**：
- 第一轮带候选单版本：`release-build-group:{id}:v{version}:a1`、`dts_rc_{id}_v{version}_a1`（`ModelMaterializationBuildRepository.java:107-117`）；
- 重试和重新构建不带版本：`release-build-group:{id}:a{n}`、`dts_rc_{id}_a{n}`（`:306-308`、`:370-380`）。

### 1.4 运维部署

| 标识 | 载体 | 语义 |
|---|---|---|
| DAG `MANAGED_TEMPLATE_VERSION` / `deployment_checksum` | `DbtDagService` | DAG 模板和部署内容 |
| 执行绑定 `version` / `desired_deployment_checksum` / `scope_checksum` | `20260728_05_model_plan_execution_binding`、运行派发 | 运行计划的并发令牌、部署内容、范围；手工运行按 `binding_version` + `scope_checksum` 反查发起人 |

### 1.5 前端自行比对

- `ModelPublishDialog.tsx:183,190`：`identityMatches` / `aggregateMatches` 比对模型 id、revision、checksum、环境、候选单 id 和 version。
- `useModelDeliveryStatus.ts:38`：修订号或校验和不一致时报"模型版本已变化，请刷新后继续"。
- 页面写命令：`If-Match` 取候选单 `version`。

## 2. 已证实的问题

**P1　候选单 `version` 语义过载（核心）**
- 它既是并发令牌（每次迁移 +1），又被当成构建身份：派发固定了它，发起人反查也用它。
- 系统自动迁移候选单时，期望版本的来源不一致：
  - 派发侧用派发创建时固定的版本（`ModelMaterializationDispatchService.java:770`）；
  - Airflow 回调侧用回调当时读到的当前版本（`ModelMaterializationRunRepository.java:40`，`ModelMaterializationRunArtifactService.java:539,1193`）。
- 已造成的缺陷：
  - 09-19 缺陷①：按"候选单 + 版本"查派发，漏掉了正在运行的派发；
  - 09-19 缺陷②：候选单被刷新为 STALE 后，仍按旧版本迁移，非法迁移导致事务回滚，派发重新陷入循环。
  - 两者都已在 `8e98ecdf8` 中临时修补（改为按候选单取最新一轮 + `isCandidateBuilding` 守卫），属于止血，不是治本。

**P2　系统迁移和用户迁移共用同一个 CAS 入口**
- 用户命令需要的是"我看到的版本仍然是最新的"（乐观锁）；系统观测需要的是"这一轮构建仍是候选单当前这一轮，且候选单仍处于某个状态"（条件迁移）。
- 两者都走 `ModelReleaseCandidateService.transition` 和 `expectedVersion`，系统路径只好借用当时的 version，导致 P1。

**P3　一次构建没有唯一主键**
- 同一次构建可以用 (候选单, version, attempt)、`pipeline_run_group_id`、`airflow_run_id`、`dispatch.id` 来表示，而且生成规则分两种（第一轮带版本，之后不带）。
- 不同代码路径各自选一种来定位：重试取"最新 attempt"，放弃构建原先按版本查，回调用 group id。只要选错，就会定位到错误的一轮或者找不到。

**P4　编写态存在两条版本链**
- 见 `it/20260907-authoring-version-chain-fix.md`：模型定义前进了，草稿仍绑定旧版本；首个实现由两条保存路径分别写入，身份不同。已做两轮修补，但"草稿基线 = 模型版本 + 实现版本"还不是一个原子对。

**P5　"是否过期"的判定分散**
- 至少 5 个类里有 15 个以上的漂移或过期错误码：STALE、SCOPE_STALE、DRIFT_REQUIRED、RETRY_SNAPSHOT_STALE、UPSTREAM_PIN_STALE、ARTIFACT_BUNDLE_DRIFT、SCOPED_BUNDLE_DRIFT、FAILED_STALE、FENCE_ACTIVE、PLAN_STALE、RUN_IDENTITY_MISMATCH、MANIFEST_IDENTITY_MISMATCH 等。
- 每个检查单独看都合理，但没有统一的"新鲜度"裁决：谁在什么时机校验哪个版本、结果如何呈现，都由各个调用点自行决定；页面也只能逐个错误码去翻译。

**P6　前端各自维护一套身份比对规则**
- 见 1.5。这些规则和后端判定分别维护，后端规则一变，前端容易出现"后端认为是最新、前端认为已变化"之类的错位。

**P7　执行身份绑定在候选单版本上**
- `ModelingExecutionAuthorization.candidate(tenant, candidate, version, "BUILDING")`（`:19-22`）用 `candidate_version` + `to_status='BUILDING'` 在命令表里反查发起人，而且要求恰好命中 1 条。
- 身份的正确性因此依赖 P1 中版本号的准确性；这一点也和权限、Keycloak 重构直接交叉（见第 5 节）。

## 3. 目标语义（原则）

1. **一个标识只表达一个含义**，分三类：
   - **内容版本**：`revision` + `checksum`，回答"是什么"，只在内容变化时前进；
   - **并发令牌**：ETag，回答"谁的写入生效"，只用于用户写命令，系统路径不使用；
   - **执行身份**：`build_attempt_id`，回答"哪一次运行"，一次构建一个，终身不变。
2. **系统迁移使用条件迁移**：`条件 = 执行身份仍是候选单当前这一轮，且候选单处于期望状态`。不满足时幂等忽略并记日志，不抛异常导致回滚（把 `8e98ecdf8` 的守卫做成正式契约）。
3. **新鲜度由单一服务裁决**：输出 `CURRENT | STALE(原因列表) | FENCED(原因)`，页面只展示一个主要阻断和原因列表。
4. **身份和授权绑定到执行，而不是绑定到候选单版本**：发起人在创建这一轮构建时写入 attempt 记录。
5. **前端不再自行比对版本**：后端直接返回 `matchesCurrent` 这类结论字段。

## 4. 改造方案（分阶段，每阶段可单独交付）

### 阶段 0：护栏（不改变行为）
- 把第 1 节整理成"版本标识注册表"，作为契约文档纳入 README 的契约链。
- 契约测试：系统路径（派发、对账、回调）调用候选单迁移时，禁止传入候选单 `version` 作为期望版本（静态扫描或 ArchUnit）。
- 给漂移和过期错误码加计数日志，先摸清现场出现频率，再决定阶段 3 的合并顺序。

### 阶段 1：统一构建身份（解决 P1、P2、P3）
- 引入 `build_attempt_id`，直接复用派发 `id`（= `pipeline_run_group_id`）；候选单新增 `current_build_attempt_id`。迁移脚本用每个候选单最新一轮派发回填。
- 新增系统迁移接口：`transitionForAttempt(tenant, candidateId, attemptId, expectedStatus, target, idempotencyKey, reason)`，对应 SQL 条件为 `current_build_attempt_id = ? and status = ?`。
- 以下路径全部改为调用这个接口：派发判失败 `block`、超时 `expire`、重试耗尽、回调 finalize（成功和失败）、放弃构建中对系统状态的收尾。之后删除 `isCandidateBuilding` 临时守卫，以及 `dispatch.candidateVersion()` 作为期望版本的用法。
- 生成规则统一为不带版本的 `a{n}` 形式。已有的 `_v{version}_a1` 数据只读保留，不改写（Airflow 端的 run id 不可变）。
- 待确认：候选单被刷新为 STALE 时，是否要主动封住它当前这一轮派发（目前不会，迟到回调靠状态检查拒收）。

### 阶段 2：候选单 `version` 回归纯并发令牌（解决 P1 剩余部分、P7）
- 接口层把 `version` 以 ETag 字符串的形式暴露，前端 `If-Match` 使用 ETag；`CandidateView.version` 保留，只供比较。
- 派发的 `candidate_version` 降级为历史记录字段，不再参与查找和迁移。
- 发起人反查改为按 attempt：创建这一轮构建时，把 `initiator_id` 写到派发（或 attempt）记录上，`ModelingExecutionAuthorization` 按 attempt 读取，不再查命令表。**具体方案和权限重构一起定。**

### 阶段 3：统一新鲜度裁决（解决 P5）
- 新增 `ModelDeliveryFreshnessService`，输入候选单或某一轮构建，逐项比对：模型和实现内容版本、依赖快照、构建计划、产物包、数据源代际、运行令牌有效期。输出单一裁决和原因列表。
- 现有各检查点改为调用它，错误码保留原值作为原因项，对外只暴露一个主要阻断（兼容现有页面映射）。
- 同步检查 README 中 F5 状态语义表，二者保持一致。

### 阶段 4：编写态单一版本链（解决 P4）
- 草稿基线改为不可拆分的 (模型 revision + checksum, 实现 revision + checksum) 对；只保留一条保存路径（09-07 已部分完成），同步、校验、提交统一用这个基线对做 CAS。

### 阶段 5：前端收口（解决 P6）
- 交付状态、工作区接口直接返回 `matchesCurrentModel`、`matchesCurrentImplementation`、`attemptIsCurrent`；删除 `aggregateMatches`、`identityMatches` 以及 hook 里的本地比对。

**建议顺序**：0 → 1 → 2 → 3 → 5 → 4。阶段 1 收益最大，能直接消除 09-19 那两类缺陷的根源；阶段 4 相对独立，可以并行。

## 5. 与权限、Keycloak 重构的交叉点（待合并）

| 交叉点 | 现状 | 需要一起决定的问题 |
|---|---|---|
| 执行身份恢复 | 按 `candidate_version` + BUILDING 命令反查发起人，再用目录服务（dts-admin / Keycloak）打开身份并校验权限（`ModelingExecutionAuthorization:19-28`） | 构建以谁的身份执行：发起人、服务账号，还是"发起人授权 + 服务账号执行"？权限被收回后，正在运行的构建怎么处理？ |
| 目录服务不可用 | 返回 503 时派发记为 UNKNOWN；09-19 起计入重试上限，**目录服务中断约 20 分钟，构建就会被判失败** | 目录服务中断应当"等待"还是"失败"？是否要单独的上限？ |
| 系统域 | Airflow 回调不带用户身份，依靠 `ModelingSystemExecution` 按 plan 放开数据源检查（`6f6be6a54`） | 系统身份的授权边界如何纳入新的权限模型 |
| 手工运行 | 已按 tenant + 派发 ID 读取派发表 initiator_id，附加 binding_id/version、scope_checksum、MANUAL 校验 | 复用已封存发起人并统一当前身份解析；保留绑定版本、范围与有效部署守卫，不按候选构建的命令反查问题重做此表 |
| 审计操作人 | 派发审计固定写 `scheduler`；用户放弃构建时，派发审计中的操作人也是 `scheduler`，真实用户只记在候选单迁移审计中 | 审计操作人的来源和格式跟随新的身份模型 |
| 测试 | `ModelMaterializationStartServiceIT` 整个类缺少建模身份而无法运行 | 权限重构后提供统一的测试身份夹具，恢复这组集成测试（阶段 1 的回归需要它） |

## 6. 迁移、兼容与验收

- **升级时正在运行的构建**：阶段 1 回填 `current_build_attempt_id` 时，候选单处于 BUILDING 的取最新一轮派发；需要一个验证脚本，确认每个 BUILDING 候选单恰好对应 1 个活动派发。
- **回滚**：新增的列可以为空，旧代码忽略它；阶段 2 的 ETag 需要前后端同时发布。
- **验收**：
  - 对阶段 1 中每一条系统迁移路径，分别覆盖"本轮 / 非本轮 / 状态已变"三种情况；
  - 重现 09-19 两个缺陷的场景，在去掉临时守卫之后仍然正确；
  - 现场数据只读核对第 1 节列出的每个标识的实际取值分布。

## 7. 待决问题

1. 候选单变为 STALE 时，是否主动封住当前这一轮派发？
2. 执行身份的模型（依赖第 5 节）。
3. `build_attempt_id` 放在候选单上，还是新建 attempt 表？当前倾向前者，改动最小。
4. 阶段 3 是否也覆盖指标发布、质量运行等其他链路？
5. 是否需要在 Airflow 端也按 attempt 取消过期的运行（目前只靠平台拒收）？

## 8. 证据索引

- 候选单 version 递增：`source/dts-platform/.../repository/modeling/ModelReleaseCandidateRepository.java:624,762`
- 系统迁移中期望版本来源不一致：`ModelMaterializationDispatchService.java:770` 对照 `ModelMaterializationRunArtifactService.java:539,1193`、`ModelMaterializationRunRepository.java:40`
- 构建身份的两种生成规则：`ModelMaterializationBuildRepository.java:107-117`、`:306-308`、`:370-380`
- 执行身份按版本反查发起人：`service/modeling/ModelingExecutionAuthorization.java:19-22`
- 版本号变化轨迹（1→2→3→4）：`ModelMaterializationStartServiceIT.retryRequiresReconciledFailureAndCreatesAttemptTwo`
- 前端自行比对：`dts-platform-webapp/.../ModelPublishDialog.tsx:183,190`、`useModelDeliveryStatus.ts:38`
- 临时修补：`8e98ecdf8`（按候选单取最新一轮、`isCandidateBuilding` 守卫）


## F13 / F14 编码前待核对项（2026-09-23）

用户已决定两Feature联合编码，是否新建F14不再是待决项。本轮仅文档，以下是M0技术核对，不自动要求用户重复批准。

| 项 | 责任Task | 完成证据 | 对后续影响 |
|---|---|---|---|
| 登记失败原始异常及真实数据库最小复现 | F13-T01 | 原因与复现前后结果 | 决定T02改法，不能预定删除版本号 |
| 完整错误码、绑定/权限动作与质量等待预算 | F13-T01 | 决策表与可控时钟测试设计 | F13-T03–T06 |
| 旧refresh兼容、新检查/恢复命令及共享工作台DTO | 两个T01 | 请求响应/权限/幂等样例 | 两Feature的API与页面 |
| 失败终态合法恢复、运行包保留窗口与租约重获 | F14-T01 | 状态转换/证据表与恢复拒绝案例 | F14-T03–T05 |
| 阶段字段、恢复记录及质量状态表迁移与回退 | 两个T01 | 当前表字段盘点、DDL、并发和回退方案 | 迁移与联合交付 |
| 当前双环境/旧模型数据、人员与完整工作量 | 两个T01及T08 | 基线、角色分工与重估 | 测试执行与实际交付安排 |

业务安全规则已明确：不放宽权限/密级/来源校验，不手动修改数据库任务；不具备完整证据时不自动重跑SQL。技术上无法满足时保留明确拒绝并记录限制，不能自行改成不安全的“自动恢复”。
