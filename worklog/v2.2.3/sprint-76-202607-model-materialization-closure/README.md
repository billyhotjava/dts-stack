# Sprint-76：模型真实物化与物理资产闭环

**时间**：2026-07
**状态**：IN_PROGRESS（架构已冻结，允许 DEV/TEST 实施；三个生产门槛仍须以真实证据关闭，PROD NO-GO）
**类型**：Model Materialization / dbt Runtime / DAG Workflow / Release Governance / Physical Asset / Full-stack
**目标**：让普通维度建模和高级 dbt 建模都以“构建、提交上线”进入同一条受治理执行链：系统自动把已验证的 ModelImplementation 转成可运行 dbt 节点，纳入 ReleaseCandidate，通过现有 Airflow/dbt 通道在目标数据库生成并核验 current revision 的 table/view；经独立审核和发布后原子登记本地物理资产，部署默认 `MANUAL_ONLY` 计划绑定。Airflow 是唯一调度真值；绑定 ACTIVE 且关系健康时才显示“上线完成”，后续由同一 dbt 任务模板完成手工或 CRON 计算。

**当前实施快照（2026-07-28）**：F5/T02 已完成服务端工作台证据投影和模型详情页接入，构建状态、dbt/Airflow run 与真实关系核验分栏展示，且候选深链严格按请求 ID fail-closed；后端定向测试 30/30、前端契约测试 34/34、TypeScript 与 Biome 均通过。真实 Airflow 联调、F7 计划 DAG/持续计算和 Chrome95 验收仍未关闭。

## 1. 背景与问题定义

Sprint-74 已完成逻辑设计、数据实现和发布结果三阶段边界纠偏，但其正确验收边界是“编译证据可见、没有真实物理资产时不伪造结果”。本轮复查确认，当前产品还不能把普通逻辑模型可靠地变成关联表：

1. `ModelLifecycleService.compile` 只生成并保存 SQL/schema artifact，不执行 dbt；
2. 普通实现保存的 `targetPhysicalName/loadStrategy/partitionFields/retentionDays` 与 `ModelingDbtCompiler` 的允许设置不一致；
3. `targetPhysicalName` 尚未稳定映射到 dbt relation identifier；
4. 模型详情主动作跳转 SQL 工作台，不代表开始构建；
5. 高级 dbt 通道具备真实 `dbt build` 能力，但 ReleaseCandidate、ModelSpec、pipeline run、manifest 和物理资产引用尚未形成同一条闭环；
6. `physicalAssetRef` 在普通编译路径可能误指输入资产，在 dbt artifact 导入路径又为空；
7. 当前所谓 `physicalAssetVerified` 主要由 manifest + run_results 推断，没有独立查询目标数据库确认关系仍然存在；
8. 当前高级页面会在 dbt build 成功后直接提交审核、批准和发布，绕过 ReleaseCandidate 的职责分离；
9. 当前动态 dbt DAG 固定 `schedule=None`；`/lifecycle/runs` 只登记客户端提交的外部运行上下文，不会形成可信调度闭环；
10. 当前 `DbtDagService` 直接覆盖共享 DAG 文件，scheduler 扫描时存在读取半文件风险；
11. 当前同步步骤使用 `all_done` 和 `|| true`，可能把 manifest sync/probe 失败伪装成 DagRun 成功；
12. 当前业务服务存在“先触发 Airflow、后写 pipeline run”的路径，HTTP/数据库故障会留下无法认领的外部孤儿运行；
13. 当前 Airflow `default_timezone=utc`，不能用容器 `TZ=CST` 推断 CRON 语义；
14. 当前 dbt profile 仅有一个 Postgres target 且包含 tracked 明文凭据；platform/Airflow/dbt 通过宿主机共享 profile 目录，在 secret 迁移前不能声称生产或多目标能力；
15. 当前 dbt DAG 回写只带 `X-DTS-Service`、不带 service token，入站过滤器又没有开放 dts-airflow 对应路径；失败最终被 `|| true` 吞掉，可能出现 DagRun 成功但平台证据缺失；
16. 当前 `DbtDagService` 在每个动态 DAG 内复制整段 Bash/Docker 逻辑，不能作为 RELEASE_BUILD 与计划 DAG 的长期单一模板；
17. 当前 Candidate API 仍统一使用 CATALOG_MAINTAINERS、plan owner 与固定 MODEL_MAINTAINER 投影；Sprint-36/F3 已交付 deny-by-default `AccessChecker.canPerform` 与资产动作策略，但 Candidate 发布/计划链尚未消费该端口，生产职责分离和双门禁仍未闭合。

本 Sprint 的“物化完成”定义为：

```text
当前 ModelSpec revision + 当前 ModelImplementation revision
  → 可重现的 dbt runnable bundle
  → ReleaseCandidate START_BUILD
  → Airflow/dbt build SUCCESS
  → 目标数据库关系实时核验 EXISTS
  → 强绑定的 RelationObservation
  → 提交上线意图、质量检查、独立审核/批准/发布
  → 本地 CatalogDataset + physicalAssetRef + lineage 全有或全无地可见
  → PlanExecutionBinding ACTIVE + relation healthy
```

仅 compile 成功、仅生成 SQL、仅存在 run_results、仅写入 catalog 标签，均不算物化完成。

本 Sprint 同时区分两个运行目的：

- **发布构建（RELEASE_BUILD）**：上线前针对候选版本执行一次 `dbt build`，负责首次物化、质量与发布证据；
- **生产计算（OPERATIONAL_RUN）**：上线后由计划级执行绑定按手工/周期策略再次触发同一 dbt selector，刷新已发布关系，不重新走审核发布。

“构建”已经完成首次数据计算和关系生成；“提交上线/发布上线”只消费 current 构建证据，不默认重复执行 dbt。发布后第一次手工或周期刷新才产生 `OPERATIONAL_RUN`。

## 2. 目标用户旅程

```text
普通维度建模
  → 完成逻辑设计（DESIGNED）
  → 在“数据实现”选择输入来源/上游模型、字段映射、目标表名、物化与装载策略
  → 验证实现（IMPLEMENTATION_READY）
  → 点击“构建”
  → 系统自动创建/复用当前计划的单模型 ReleaseCandidate，并执行唯一 START_BUILD
  → 系统生成可运行 dbt bundle，并提交现有 Airflow dbt build
  → 当前页面显示排队/运行/失败/成功；计划交付工作台提供批量与审计视图
  → SUCCESS 后显示“已核验关系”：database.schema.identifier、table/view、核验时间、run id
  → 点击“提交上线”；系统持久化 Publish Intent，运行质量检查并在通过后提交审核
  → 独立 reviewer 在交付工作台批准/拒绝；独立 operator 点击“发布上线”
  → 系统以 candidate 为唯一 owner，全有或全无地提交本地发布事实、CatalogDataset、输出字段、血缘和 physicalAssetRef
  → 创建默认 MANUAL_ONLY 的计划执行绑定，聚合计划内全部 current PUBLISHED 模型并异步部署 DAG
  → candidate PUBLISHED 但 binding 未 ACTIVE 时显示“已发布，运行计划部署中/异常”；binding ACTIVE 且关系健康后显示“上线完成”
模型详情“发布结果”
  → 只读查看真实物理资产、DDL/artifact、计划 DAG、最近/下次计算和发布时间线
```

高级 dbt 建模仍可“上传 → 构建 → 提交上线”，但其构建和提交上线必须改为调用同一 Build/Publish Intent facade。普通配置和高级 dbt 只在“runnable bundle 如何产生”上不同；候选、DAG、运行、关系核验、审核、发布、持续计算和资产登记完全共用。完整映射见 `assets/model-to-dag-execution-contract.md`。

## 3. 架构决策记录（ADR）

| ID | 决策点 | 选择 | 理由 | 影响 |
|---|---|---|---|---|
| ADR-76-01 | “编译”与“物化”的定义 | compile 只生成制品；materialized 必须同时满足 build SUCCESS 和目标库关系 EXISTS | 消除“有 SQL 就算建表”的错误完成语义 | 生命周期、UI 文案、IT |
| ADR-76-02 | 构建入口 owner | `ReleaseCandidate.START_BUILD` 是唯一状态迁移；模型详情和高级 SQL 页调用同一 Build Intent facade，facade 只创建/精确复用 `SINGLE_MODEL_INTENT` 并委托该命令 | 保留“点构建即可”的体验，同时禁止页面各自维护状态机或静默合并批量范围 | ReleaseCandidate、BuildIntent、两类建模页 |
| ADR-76-03 | 运行真值 | 扩展既有 `modeling_pipeline_run`，不新增第二张运行台账 | 复用已有 CAS、修复路径和运行查询 | migration、runtime service |
| ADR-76-04 | dbt 执行通道 | 抽取两类页面共用的 `DbtExecutionGateway`，复用 `DbtReleaseSubmissionService`、`DbtScopedProjectService`、单一 `AirflowClient` 和现有 Docker dbt 方式 | 已有通道和历史成功证据，不能再造普通模型执行器、Airflow client 或 HTTP dbt runner | ETL/dbt service、Airflow |
| ADR-76-05 | 普通实现的 runnable bundle | compiler artifact 作为 overlay 注入 `DbtScopedProjectService` 的临时 scoped project；不强制镜像成 `ModelingSqlModel` owner | 避免普通模型再造 SQL 模型台账；临时制品仍可重现 | compiler、scoped project |
| ADR-76-06 | 逻辑 node 与物理关系名 | `dbtUniqueId/nodeName` 保持系统稳定标识；`settings.targetPhysicalName` 映射到 dbt `alias`/relation identifier | 改物理名不应改变模型身份，且必须精确生成用户确认的关系 | compiler config、manifest 校验 |
| ADR-76-07 | 设置能力与降级 | FULL table/view 和有 KEY 的 INCREMENTAL 纳入 P0；SNAPSHOT、目标适配器不支持的分区能力 fail-closed，不静默忽略 | 不能把未实现设置伪装为成功 | validation response、UI |
| ADR-76-08 | 真实关系核验 | 新增 `PhysicalRelationInspector` port；P0 以 PostgreSQL 真实集成关闭，其他适配器通过 capability registry 明确支持状态 | run_results 是必要证据但不足以证明关系当前存在 | relation observation、NFR |
| ADR-76-09 | 核验证据 | 新增 append-only `modeling_physical_relation_observation`；强绑定 candidate、pipeline run、model/implementation revision/checksum 和 relation locator | 防止旧 run、旧 relation 或同名表冒充当前产物 | migration、repository |
| ADR-76-10 | 发布前关系的可见性 | build 后保存 observation，但生命周期绑定模型在 PUBLISHED 前不登记为可消费 CatalogDataset | 构建证据不等于发布资产 | dbt sync、catalog registration |
| ADR-76-11 | 资产标识与引用 | 发布时复用 `CatalogAssetType.DATASET` + `CatalogAssetKey.dataset(...)`；artifact 的 `physicalAssetRef` 指向输出 dataset，绝不指向输入 binding | 遵守 DTS 统一资产标识和单一台账不变量 | catalog、artifact、lineage |
| ADR-76-12 | 多模型候选 | 一个 candidate 只允许一个 environment/executionTargetKey；P0 仅接受当前唯一安全 Postgres target。形成一个 dbt selector；每 entry 写独立 pipeline run，共享 Airflow run id | 保留依赖图的一次性构建，避免暗含多执行边界或虚构多目标能力 | candidate validator、dispatcher |
| ADR-76-13 | 漂移与幂等 | ModelSpec/Implementation/artifact/dependency snapshot 漂移立即 STALE；相同 Idempotency-Key 重放同一结果，外部 dagRunId 由 candidate/version/attempt 确定生成 | 防止旧实现建表、请求超时后二次触发 Airflow | ReleaseCandidate、pipeline run、gateway |
| ADR-76-14 | 回滚/清理 | 回滚恢复发布事实和资产可见性；本 Sprint 不自动 DROP 目标关系，物理清理由显式、审计、影响分析后的后续动作承担 | 自动 DROP 风险不可接受，尤其是 DBT_MANAGED/外部表 | release plan、non-goal |
| ADR-76-15 | 信息架构 | 不新增菜单/页面；复用模型详情和 `/modeling/plans/:planId/implementation` | 遵守 Sprint-48/49 与 domain-dts B1 | 现有路由内改造 |
| ADR-76-16 | dbt DAG 与 Airflow DAG 边界 | 字段映射和上游关系生成 `source()/ref()`，由 dbt manifest 决定模型依赖顺序；Airflow 只负责编译/构建/同步/核验等粗粒度工作流 | 用户不应为每张维表手工画 Airflow 节点，且不能复制 dbt 依赖图 | compiler、manifest、DAG |
| ADR-76-17 | 快捷构建/上线 | 模型详情与高级页面只提交 Build Intent 和“提交上线”Publish Intent；Publish Intent 记录用户发布请求，只推进 MODEL_MAINTAINER 可执行的 RUN_QUALITY→SUBMIT_REVIEW，在 REVIEW_PENDING 人工边界停止，绝不 APPROVE/PUBLISH | 保留两步主路径，同时让按钮语义稳定且不按当前状态执行不可预测动作 | facade API、command ledger、UI |
| ADR-76-18 | 上线后持续计算 | 新增 `(tenant,plan,environment,executionTargetKey)` 级 `PlanExecutionBinding`；发布只创建 `MANUAL_ONLY` binding，CRON 由独立 CAS 命令修改同一 DAG | 发布治理与调度配置解耦；保留未来 target 拆分边界 | plan delivery、Airflow、runtime |
| ADR-76-19 | DAG 粒度 | P0 每 binding 一个稳定 plan DAG/一个 schedule；无 scheduleKey，不按 model/candidate/release/cron 生成 DAG | 控制 DAG 数量并保证调度 identity 稳定 | DAG id、deployment reconciler |
| ADR-76-20 | 候选来源与合并 | candidate 标记 `SINGLE_MODEL_INTENT` 或 `BATCH_WORKBENCH`；快捷构建只复用 scope 完全相同的单模型候选，遇批量 DRAFT/active candidate 一律 409 + 深链 | 防止用户点一张模型却隐式构建或改变整批范围 | candidate origin、BuildIntent、UI |
| ADR-76-21 | 不可变快照与活动占用 | START_BUILD 前由服务端锁定 model/implementation/artifact/dependency/target 快照；entry 以 nullable `active_claim_key` 唯一约束保证 tenant+environment+model 同时最多一个 active candidate | CAS 不能单独抵御并发和恶意重放，必须数据库兜底 | candidate/entry migration、state transition |
| ADR-76-22 | 服务端派生 | Build Intent 客户端只提交 planId/environment 并带 If-Match/Idempotency-Key；revision、checksum、selector、dagId、projectDir、target/profile、external run id 全由服务端派生 | 防止请求篡改执行版本、目标和成功证据 | API、security tests |
| ADR-76-23 | 上游门禁 | 每个 `ref()` 上游必须已在同环境发布且证据 current，或显式进入同一 candidate；不得靠 `+selector` 隐式构建未审核上游 | 防止下游构建绕过上游发布治理 | compiler graph、candidate validation |
| ADR-76-24 | 活动占用释放 | DRAFT 不占数据库 active claim；START_BUILD 原子获取 claim；BUILD_FAILED/QUALITY_FAILED 保留 claim 供重试，并新增审计化 `CANCEL_CANDIDATE→CANCELLED` 释放；STALE replacement 原子换 claim | 防止 DRAFT 恶意占坑和失败候选永久锁定，同时保留恢复证据 | DeliveryStatus/Action、claim transition、UI |
| ADR-76-25 | 唯一发布 owner 与兼容入口 | ReleaseCandidate 是审核、批准、发布的唯一状态 owner；现有单模型 `/lifecycle/reviews*`、`/lifecycle/publish`、rollback/retry 路由在兼容期只校验并委托 current candidate command，页面不得直接调用，后续 Contract 阶段再移除 | 消除 Candidate 与 ModelSpec lifecycle 两套发布状态机，同时遵守 API expand/migrate/contract | candidate commands、legacy adapter、前端 |
| ADR-76-26 | 本地发布原子可见性 | candidate PUBLISHING 先准备所有 entry 的本地 ModelSpec release、CatalogDataset/field/lineage/physicalAssetRef 和 MANUAL_ONLY binding scope；全部强制步骤成功后同一数据库事务切换为 PUBLISHED/可见，否则 PARTIAL 且全部保持不可消费 | 防止“ModelSpec 已发布、candidate PARTIAL、资产缺失”的分裂真值 | publication service、registration、transaction |
| ADR-76-27 | 外部同步失败边界 | OpenMetadata/BI 等非本地主真值的同步在 PUBLISHED 提交后通过 outbox 重试；失败只标记 syncHealth=DEGRADED，不回退 candidate、不隐藏本地可消费资产 | 外部不可用不应破坏已原子提交的本地发布事实 | outbox、registration health、运维 |
| ADR-76-28 | “上线完成”投影 | `onlineReadiness` 是由 candidate、mandatory registration、binding deployment 和 relation health 派生的只读投影，不新增候选状态；只有 PUBLISHED + binding ACTIVE + relation healthy 才是 READY | 区分治理发布与可持续运行，避免把 PUBLISHED 冒充可运行 | API projection、UI、IT |
| ADR-76-29 | 发布职责与权限真实性 | maintainer/reviewer/operator action 必须由服务端 domain duty resolver 计算，禁止硬编码 MODEL_MAINTAINER 或接收客户端 role；提交者不得批准/发布。发布资产另消费 Sprint-36/F3 action policy；任一层未完成时 PROD fail-closed | actor separation 不等于资产动作授权，M05 也不替代 Candidate 职责 | security、allowedActions、DoR |
| ADR-76-30 | 调度真值 | Airflow 唯一拥有 CRON、logical date、next run、DagRun/TaskInstance；binding 只保存 desired schedule/deployment evidence，pipeline run 保存业务结果 | 当前架构已使用 Airflow LocalExecutor，平台不能再造第二 scheduler 或估算 nextRun | API projection、worker、UI |
| ADR-76-31 | 两类触发顺序 | RELEASE_BUILD/手工 OPERATIONAL_RUN 先事务写 durable run/outbox 再触发 Airflow；CRON 由 Airflow 先建 DagRun，首任务调用内部 API 原子 open OPERATIONAL_RUN | 同时消除外部孤儿运行与 CRON 无业务 run 两类错误 | dispatcher、DAG task、internal API |
| ADR-76-32 | 计划 scope | binding entry 每次从 plan/environment/target 下全部 current PUBLISHED 模型聚合，不只引用最近 Candidate | 连续单模型发布不能把先前已发布模型移出持续计算 | publication commit、binding entry |
| ADR-76-33 | DAG 部署 | `DbtDagService` 收敛为 renderer/reconciler；同目录临时文件+原子 move，轮询 Airflow parse/tags/checksum/effective schedule 后才 ACTIVE | scheduler 每 3s 扫描共享目录，直接 truncate 不可靠 | DAG deployment、AirflowClient |
| ADR-76-34 | 任务模板与失败语义 | RELEASE_BUILD executor DAG 与 plan DAG 共用 prepare→build→sync/probe→finalize 模板；sync/probe 失败必须使 DagRun 失败；`max_active_runs=1` | 普通/高级/周期计算共用物化内核且禁止伪成功 | DAG template、reconciler |
| ADR-76-35 | 时区 | CRON 使用 IANA timezone 和 timezone-aware pendulum start date；页面只显示 Airflow actual nextRun | 当前 Airflow default_timezone=utc，容器 CST 不是调度契约 | schedule API、DAG、UI |
| ADR-76-36 | dbt 凭据与 target | 复用既有数据源 secrets 进程内解密；平台把 task-scoped profile lease 写入宿主机 tmpfs 映射目录，Airflow 只按服务端 leaseId 从固定 host root 派生只读 mount；P0 只开放一个实测 target | 既不复制 secret owner，也不把 profile 经 API/DB/env 传给 Airflow；普通磁盘和共享 profiles 目录均为生产 No-Go | F2/T04、部署、安全 IT |
| ADR-76-37 | 单一 Airflow dbt runtime | 在 `services/dts-airflow/extra` 提供一个版本化 Python task factory；RELEASE_BUILD 与 OPERATIONAL DAG 都由 Java renderer 生成薄定义并 import 同一 factory | 当前 Airflow 已挂载 extra 且通过 Docker socket运行 ephemeral dbt；复用运行架构但消除每个 DAG 复制 Bash 的漂移面 | F2/T02、F7/T02、IT-20 |
| ADR-76-38 | Airflow 内部服务身份 | prepare/open/sync/probe/finalize/release 全部使用 pairwise `X-DTS-Service + X-DTS-Service-Token`，过滤器按 `service:dts-airflow` 和路径白名单收敛；canonical 链禁止 `|| true` | 当前仅 header 的回写无法通过生产鉴权且会静默失败，不能作为成功证据 | F2/T02/T04、F7/T02、IT-19/20 |
| ADR-76-39 | 生产权限双门禁 | Sprint-76 通过三个专用 Keycloak realm authority 完成 maintainer/reviewer/operator 职责解析与同人隔离，不从 catalog/admin/auditor 静默提升；Sprint-36/F3 负责资产动作矩阵，发布注册同时通过两层才允许 PROD | actor separation 不能替代资产动作授权，M05 矩阵也不能替代 Candidate 审核职责；禁止在 Sprint-76 复制权限表 | F4/T01～T02、F6/T01、IT-14 |
| ADR-76-40 | typed-column 物理契约 | F4 发布前，DESIGNER_GENERATED 必须把 current ModelSpec 的字段类型投影到 dbt artifact，并对 PostgreSQL 实际列类型做 adapter-aware 强校验；DBT_MANAGED 有声明则校验，无声明时以真实 observation 为物理 schema 事实并明确降级，不伪造 expected type | `ModelSpec.dataType` 是必填业务契约，当前投影丢失后仅核对列名会让 numeric→text 等错误进入 BUILT；但不能用 PostgreSQL 规则强迫所有高级 dbt 项目声明同一逻辑类型体系 | F3/T04、F4/T03、IT-07/10 |
| ADR-76-41 | binding 环境隔离与历史事实 | binding scope 必须同时匹配 plan、environment、executionTargetKey；任何 current PUBLISHED 模型缺少当前 release facts 时以 `MODEL_PLAN_BINDING_RELEASE_FACTS_REQUIRED` fail-closed，不跨环境聚合、不静默漏模型、不从旧命名推断目标 | 连续发布必须聚合 A+B，但不能把 TEST 模型带入 PROD；历史数据迁移属于显式治理动作，不能在发布事务中猜测 | F4/T02、F7/T01、PostgreSQL IT |
| ADR-76-42 | 物理资产唯一身份 | 输出 CatalogDataset 以 execution target 解析出的 `sourceId + lower(trim(schema)) + lower(trim(table))` 为唯一 locator；数据库 expression/partial unique index 是最终 authority，Candidate 与 generic dbt sync 共用稳定 UUID；升级前唯一 `source_id is null` 同表资产可被事务认领，多个候选一律 fail-closed | ModelSpec/revision 是发布证据而不是物理表身份；按 revision 生成资产会让重发产生新台账，也无法抵御 Candidate 与 dbt sync 并发 | F4/T03、IT-10/11 |
| ADR-76-43 | 外部同步健康语义 | local outbox 与目标消费者 ACK 分层：outbox `PENDING/SENT/FAILED` 只代表 handoff，不能证明 OpenMetadata/BI 已应用；目标消费者必须按 event/candidate/version/target 幂等处理并回写 ACK，目标失败只投影 `DEGRADED`，不回退 PUBLISHED | 当前 OpenMetadata client 只有 GET/缓存拉取，Kafka dispatcher 无消费者 ACK 且 FAILED 不会自动重试；把 SENT 当 HEALTHY 会制造假绿 | F4/T02～T03、F6/T03、IT-11 |

## 4. 对象所有权

| 对象 | 唯一 owner | 本 Sprint 职责 | 明确不得承担 |
|---|---|---|---|
| `ModelSpecRevision` | canonical 逻辑模型 | 提供字段、键、语义和 checksum | 运行状态、目标关系 observation |
| `ModelImplementationRevision` | canonical 数据实现 | 提供输入、映射、settings、materialization、checksum | 发布审批、Catalog 资产正文 |
| compiler artifact | `modeling_dbt_artifact` | 保存可重现 SQL/schema 与 bundle checksum | 证明数据库关系存在 |
| `ReleaseCandidate` | Sprint-69 发布控制面 | 保存来源、同质执行边界、不可变 snapshot 和 append-only Publish Intent，唯一拥有质量、审核、批准和发布状态 | 复制模型/实现正文、静默吸收其他候选范围、与 lifecycle route 双写发布状态 |
| Airflow | scheduler/DagRun/TaskInstance | 唯一拥有 CRON、logical date、nextRun、paused 和 task 状态 | 保存业务发布状态、替代 pipeline run |
| plan execution binding | `modeling_plan_execution_binding` + entry | 保存 plan/environment/target 的 desired schedule、全部 current PUBLISHED scope 和 DAG deployment evidence；参与 onlineReadiness | 保存权威 nextRun、运行平台 CRON、只引用最近 Candidate、保存 SQL |
| pipeline run | `modeling_pipeline_run` | 以 `RELEASE_BUILD`/`OPERATIONAL_RUN` 区分发布构建和生产计算，统一保存外部 run id 与模型级运行状态 | 资产目录、第二套调度定义 |
| relation observation | `modeling_physical_relation_observation` | 保存某次运行对真实关系的核验事实 | 代替 CatalogDataset |
| physical asset | `catalog_dataset` + `CatalogAssetKey` | PUBLISHED 后的可消费资产、字段、血缘、密级 | 承载未发布构建草稿 |

## 5. 设置与能力矩阵

| 配置 | P0 行为 | 不支持/冲突时 |
|---|---|---|
| `targetPhysicalName` | 必须映射为 dbt alias，manifest `identifier` 必须一致 | `IMPLEMENTATION_TARGET_IDENTIFIER_MISMATCH` |
| `materialization=table/view` + `loadStrategy=FULL` | 生成 table/view | adapter 不支持则 fail-closed |
| `loadStrategy=INCREMENTAL` | effective materialization=`incremental`；至少一个逻辑 KEY 字段作为 `unique_key` | `IMPLEMENTATION_INCREMENTAL_KEY_REQUIRED` |
| `loadStrategy=SNAPSHOT` | 保持旧数据可读，但本 Sprint 不执行 | `IMPLEMENTATION_SNAPSHOT_STRATEGY_REQUIRED` |
| `partitionFields` | 只在 adapter capability 明确支持时转译 | `IMPLEMENTATION_PARTITION_UNSUPPORTED` |
| `retentionDays` | 写入 dbt meta 和发布资产治理元数据；不声称自动清理 | UI 明示“保留策略元数据” |
| `ephemeral` | 只允许内部 STG/血缘技术节点，不产生 PhysicalAsset | `physicalExpected=false` |
| `scheduleMode/cron/timezone` | 不进入 ModelImplementation；本地发布创建 `MANUAL_ONLY`，CRON_ENABLED/timezone 在发布后独立配置同一 binding | 未发布、binding 非 ACTIVE、target/secret 不可用时禁止启用；actual nextRun 只读 Airflow |

`POST .../implementation/inputs/validate` 的兼容扩展：

```json
{
  "valid": true,
  "code": "MODEL_IMPLEMENTATION_VALID",
  "blockers": [],
  "executionPlan": {
    "engine": "DBT",
    "adapter": "postgres",
    "nodeUniqueId": "model.dts.model_<uuid>",
    "selector": "model_<uuid>",
    "targetIdentifier": "dwd_finance_detail",
    "effectiveMaterialization": "table",
    "physicalExpected": true,
    "capabilityCodes": ["FULL_TABLE", "RELATION_PROBE"]
  }
}
```

兼容原则：保留现有 `valid/code`；新增字段为只读扩展。任何配置无法执行时 `valid=false`，UI 不允许开始构建。

## 6. 端到端契约链（Vertical Slice）

| 层 | 契约/落点 | 签名要点 |
|---|---|---|
| 模型详情/高级建模 UI | 模型详情 implementation stage、`/studio/sql-modeling` | 显示“构建/提交上线”；均只调用 Build/Publish Intent，不直接拼装审核、批准、发布或 Airflow 调用 |
| Build Intent facade | `POST /api/modeling/model-specs/{id}/build-intents`（已落地） | headers=`If-Match,Idempotency-Key`；body=`planId,environment`；仅创建/精确复用 SINGLE_MODEL_INTENT，遇批量候选返回 409 |
| Publish Intent facade | `POST /api/modeling/model-specs/{id}/publish-intents`（已落地） | headers=`If-Match(candidate ETag),Idempotency-Key`；body=`candidateId,reason`；只接受 exact SINGLE candidate，记录 intent 后推进质量与提交审核，在人工边界停止 |
| 交付工作台 UI | `/modeling/plans/:planId/implementation?modelSpecId=:id&revision=:rev` | 提供批量 scope、完整候选、质量、reviewer APPROVE/REJECT、operator PUBLISH、DAG 与运行证据；与快捷入口读取同一 workspace |
| 候选命令 API | `POST .../{candidateId}/{lock|quality|reviews|reviews/approve|reviews/reject|publish|registration/retry|rollback|cancel}` | 所有 mutation 使用 candidate If-Match + Idempotency-Key；服务端解析 actor role，页面不得串行动作 |
| 实现校验 API | `POST /api/modeling/model-specs/{id}/implementation/inputs/validate` | 返回兼容 `valid/code` + `blockers[]` + `executionPlan` |
| compiler | `ModelLifecycleCompilerPort.compile(model, implementation)` | settings 全量受控；SQL config 含 materialized/alias/meta；artifact checksum 可重现 |
| runnable bundle | `DbtScopedProjectService.prepareCandidate(CandidateBuildRequest)` | 输入 candidate entries + current artifacts；输出 `{projectDir,selector,bundleChecksum,entries[]}` |
| durable dispatch | `MaterializationDispatchService.dispatch(candidateId)` | 从 QUEUED pipeline rows 组装一次 candidate build；调用既有 dbt release service |
| 外部执行 | `DbtExecutionGateway.submitReleaseBuild(...)` | 先持久化 dagRunId=`dts_rc_<candidateId>_v<version>_a<attempt>`；触发共享 `schedule=None` executor DAG；conf 无 project/selector/target/secret |
| 状态回收 | `MaterializationRunReconciler.reconcile(candidateId, dagRunId)` | 读取 Airflow + manifest + run_results + relation probe；逐 entry 更新 pipeline run |
| 关系核验 port | `PhysicalRelationInspector.observe(TargetContext, RelationLocator)` | 返回 `exists,relationType,columns,observedAt,metadataChecksum`；不存在即 BUILD_FAILED |
| 观察数据 | `modeling_physical_relation_observation` | append-only；按 `(pipeline_run_id,model_spec_id,implementation_revision,observation_attempt)` 保留每次尝试 |
| 发布 | Sprint-69 candidate `PUBLISH` 命令 | operator-only；只消费 current SUCCESS run + current EXISTS observation + quality/approval，进入 PUBLISHING |
| 本地发布提交 | `CandidatePublicationCommitService.commit(candidateId)`（预定） | 同事务提交 local publication、Catalog/field/lineage/physicalAssetRef、candidate PUBLISHED 和聚合全 plan scope 的 MANUAL_ONLY binding DEPLOYING |
| 外部同步 | publication outbox worker | PUBLISHED 后同步 OpenMetadata/BI；失败仅 syncHealth=DEGRADED，可幂等重试 |
| 计划执行绑定 | `PlanExecutionBindingService.reconcilePublished(planId,environment,executionTargetKey)` | 聚合 plan 下全部 current PUBLISHED releases；P0 一个 binding/一个 schedule，无 scheduleKey |
| DAG 部署 | `PlanDagDeploymentService.reconcile(bindingId)` | 原子写稳定 plan DAG，轮询 Airflow registration/parse/checksum/schedule 后才 ACTIVE |
| 手工生产计算 | `POST .../execution-bindings/{bindingId}/runs` | 平台先写 durable OPERATIONAL_RUN/outbox，再触发同一个 plan DAG |
| CRON 生产计算 | `POST /api/internal/modeling/execution-bindings/{bindingId}/scheduled-runs/open` | Airflow 首任务按 dagRunId/logicalDate 原子 open run，再读取 server-controlled runtime spec |
| 发布结果 UI | 模型详情 `activeStage=physical` | 区分未发布、发布处理中、已发布/部署中、上线完成、已发布/运行异常；build-only 不冒充已发布 |
| 迁移 | 已落地的 Sprint-76 分步 changeSet；F3 observation 为 `20260727_13_physical_relation_observation.xml` | expand `modeling_pipeline_run` + 新 observation/execution binding 表、约束、索引、rollback |

### 6.1 Publish Intent 契约

```text
PublicationIntentRequest(
  candidateId: UUID,
  reason: String
)

PublicationIntentResult(
  candidateId: UUID,
  candidateVersion: int,
  candidateStatus: DeliveryStatus,
  outcome: QUALITY_RUNNING | AWAITING_REVIEW | AWAITING_APPROVAL |
           AWAITING_OPERATOR_PUBLISH | PUBLISHING | PUBLISHED | BLOCKED,
  nextHumanAction: REVIEW | PUBLISH | RETRY_QUALITY | OPEN_WORKBENCH | NONE,
  blocker: {code,message}?,
  onlineReadiness: NOT_PUBLISHED | DEPLOYING | READY | DEGRADED,
  workbenchUrl: String,
  replayed: boolean
)
```

约束：

- `If-Match` 必须是 request 中 candidate 的强 ETag；`Idempotency-Key` 必填，重放返回同一 command receipt；
- 服务端验证 candidate origin=`SINGLE_MODEL_INTENT`、scope 仅含 path model、revision/implementation/evidence 全部 current；BATCH 一律 409 + 工作台深链；
- request 不接受 action、target status、reviewer/operator、selector、schedule 或其他技术字段；
- 从 BUILT 记录 append-only `PUBLICATION_REQUESTED` 并执行 RUN_QUALITY；质量通过后 reconciler 重新校验 actor authority/current snapshot，再以原 requester 执行 SUBMIT_REVIEW；
- QUALITY_FAILED 返回 BLOCKED 并要求显式重试；REVIEW_PENDING/APPROVED/PUBLISHING/PUBLISHED 只返回 current projection，不代替 reviewer/operator 动作；
- APPROVE/REJECT/PUBLISH 只能在交付工作台通过 candidate command 执行；Publish Intent 永不自动批准或发布；
- intent ledger 只是 Candidate command 证据，不拥有第二套 lifecycle state；candidate STALE/CANCELLED/REJECTED 后自动终止继续推进。

### 6.2 内部候选构建契约

```text
CandidateBuildRequest(
  tenantId: String,
  candidateId: UUID,
  candidateVersion: int,
  origin: SINGLE_MODEL_INTENT | BATCH_WORKBENCH,
  environment: String,
  executionTargetKey: String,
  adapter: String,
  profileKey: String,
  attempt: int,
  deterministicDagRunId: String,
  idempotencyKey: String,
  entries: List<CandidateBuildEntry>
)

CandidateBuildEntry(
  modelSpecId: UUID,
  modelRevision: int,
  modelChecksum: String,
  implementationRevision: int,
  implementationChecksum: String,
  dbtUniqueId: String,
  selector: String,
  targetIdentifier: String,
  artifactBundleChecksum: String,
  dependencySnapshotChecksum: String,
  activeClaimKey: String
)
```

一个字段缺失、candidate drift、artifact/dependency 非 current、执行目标不一致或 implementation validation 失败时，整批不得提交 Airflow。客户端不得提交或覆盖上述技术字段。

### 6.2.1 单模型 Build Intent 决策表

| 服务端现状 | 行为 |
|---|---|
| 当前 revision 不存在可复用或冲突 candidate | 创建 scope=当前模型的 `SINGLE_MODEL_INTENT` |
| 相同 revision/implementation/environment 的 SINGLE_MODEL DRAFT | 精确复用并执行 START_BUILD |
| 相同 SINGLE_MODEL 已 BUILDING 或后续非终态 | 返回同一 candidate/run 状态，不产生新执行 |
| 当前模型存在于任意 BATCH DRAFT/active candidate | 正常路径 `409 MODEL_ACTIVE_BATCH_CANDIDATE_CONFLICT` + 工作台 deep link；并发 START_BUILD 最终由 active claim 决定唯一获胜者 |
| candidate 为 BUILD_FAILED，或 current run 的 UNKNOWN 经对账确认失败 | 使用原 candidate 的 RETRY_BUILD，新 attempt 不新建候选；UNKNOWN 未完成对账前不得重试 |
| candidate STALE | `409 MODEL_CANDIDATE_STALE_REPLACEMENT_REQUIRED`，显式 replacement |
| 相同 revision 已 PUBLISHED | `409 MODEL_ALREADY_PUBLISHED_USE_OPERATIONAL_RUN`，引导“立即运行” |

`active_claim_key` 只在 START_BUILD 时写入；DRAFT 不持有数据库占用。候选进入 PUBLISHED/REJECTED/ROLLED_BACK/CANCELLED 后释放 claim；BUILD_FAILED/QUALITY_FAILED 保留 claim 供原候选 retry；STALE 只允许 replacement 事务原子换 claim。历史快照、运行和取消证据不可修改，取消不自动 DROP 关系。

### 6.3 RelationObservation 契约

```text
RelationLocator(
  adapter: String,
  database: String?,
  schema: String,
  identifier: String,
  expectedType: TABLE | VIEW
)

RelationObservation(
  id: UUID,
  candidateId: UUID,
  pipelineRunId: UUID,
  modelSpecId: UUID,
  modelRevision: int,
  modelChecksum: String,
  implementationRevision: int,
  implementationChecksum: String,
  dbtInvocationId: String,
  locator: RelationLocator,
  exists: boolean,
  actualType: TABLE | VIEW | MATERIALIZED_VIEW | UNKNOWN,
  columnsChecksum: String,
  metadataChecksum: String,
  observedAt: Instant
)
```

manifest 提供 locator 候选；inspector 必须使用运行目标的凭据直接查询数据库元数据。不得用 `run_results=success` 直接填充 `exists=true`。

### 6.4 模型到 DAG 的自动映射

```text
ModelImplementation.inputs
  ├─ PHYSICAL_SOURCE → dbt source()
  └─ UPSTREAM_MODEL  → dbt ref()
字段映射/表达式 → model SQL
targetPhysicalName/loadStrategy → dbt config(alias/materialized/unique_key)
platformEnvironment + executionTargetKey → 稳定 RELEASE_BUILD executor DAG
tenantId + planId + environment + executionTargetKey → 稳定 OPERATIONAL plan DAG
dbtUniqueId → selector（发布构建默认包含必要 ancestors）
```

- **dbt graph** 决定模型之间的依赖和执行顺序；
- **Airflow** 唯一决定 CRON、logical date、next run、DagRun/TaskInstance；DAG 串联 prepare/build/sync/probe/finalize；
- 页面不要求用户填写 dagId、projectDir、selector 或手工连线；
- plan DAG scope 聚合全部 current published releases；revision 漂移、回滚或换环境必须重新 reconcile；
- 首次“构建”一定产生 `RELEASE_BUILD`；上线后的手工/周期刷新一定产生 `OPERATIONAL_RUN`，二者不得互相冒充。

## 7. 状态机

```text
ReleaseCandidate DRAFT
  --lock/START_BUILD--> BUILDING
      ├─ bundle/dispatch failed ----------> BUILD_FAILED
      ├─ Airflow/dbt failed --------------> BUILD_FAILED
      ├─ relation missing/mismatched -----> BUILD_FAILED
      └─ all entries SUCCESS + EXISTS ----> BUILT
           --Publish Intent/PUBLICATION_REQUESTED--> QUALITY_RUNNING
               ├─ quality failed ----------> QUALITY_FAILED
               └─ quality passed ----------> QUALITY_PASSED
                    --reconciler/SUBMIT_REVIEW--> REVIEW_PENDING
                         ├─ reviewer reject --> REJECTED
                         └─ reviewer approve -> APPROVED
                              --operator PUBLISH--> PUBLISHING
                                   ├─ mandatory local commit incomplete
                                   │      └────────> PARTIAL（资产全部保持不可消费）
                                   └─ all mandatory local steps committed
                                          └───────> PUBLISHED
                                               → MANUAL_ONLY binding DEPLOYING
                                                   ├─ deployed + relation healthy -> ACTIVE / onlineReadiness READY
                                                   └─ deploy/health failed --------> FAILED / onlineReadiness DEGRADED

PUBLISHED + ACTIVE binding
  --MANUAL--> 平台先创建 OPERATIONAL_RUN，再触发 Airflow
  --CRON----> Airflow 先创建 DagRun，首任务原子 open OPERATIONAL_RUN
      ├─ dbt/probe failed -> FAILED（保留 PUBLISHED）
      └─ success ----------> SUCCEEDED + 新 RelationObservation

DRAFT / BUILD_FAILED / QUALITY_FAILED
  --CANCEL_CANDIDATE--> CANCELLED（释放 active claim，不删除关系）
```

- `BUILT` 必须包含 candidate 所有 entry 的 current pipeline run 和 observation；
- 任一 checksum 漂移后旧 evidence 立即 STALE；
- retry 生成新 pipeline run/observation，不覆盖历史；
- 构建已完成首次计算；Publish Intent/PUBLISH 不重复触发 RELEASE_BUILD，只消费 current observation；
- `PARTIAL` 下所有 mandatory local asset records 必须 disabled/不可检索，禁止按 entry 部分可见；
- OpenMetadata/BI 等外部同步在 PUBLISHED 后运行，失败只更新 syncHealth，不迁移 candidate；
- PUBLISHED 后 relation 消失时，健康检查只标记资产失效/告警，不静默重建或伪造存在；
- OPERATIONAL_RUN 失败不得把候选回退为 BUILD_FAILED，也不得撤销已发布版本；它只更新运行健康状态并提供重试。

## 8. 现状勘察账本（Context Ledger）

| # | 事实 | 证据 |
|---|---|---|
| L01 | 模型详情主动作当前只保存/校验或跳转 SQL Modeling，不执行构建 | `source/dts-platform-webapp/src/pages/modeling/ModelSpecDetailPage.tsx:676-686` |
| L02 | 生命周期 compile 只调用 compiler 并保存 artifact | `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelLifecycleService.java:556-606` |
| L03 | compiler settings 白名单仍小于 ModelImplementation settings 契约 | `ModelingDbtCompiler.java:18,289-294`; `ModelLifecycleContract.java:23-32,417-460` |
| L04 | UI 总是保存 targetPhysicalName/loadStrategy/partitionFields | `ModelSpecImplementationStage.tsx:460-464` |
| L05 | compiler 资源名由 dbtUniqueId 末段决定，未使用 targetPhysicalName 作为 alias | `ModelingDbtCompiler.java:325-330` |
| L06 | SQL Modeling 高级路径会提交 dbt release 并等待 Airflow 结果 | `SqlModelingPage.tsx:1657-1783` |
| L07 | dbt release service 使用现有 scoped project 并触发 Airflow operation=build | `DbtReleaseSubmissionService.java:54-147,218-253` |
| L08 | `DbtScopedProjectService.prepare(selector)` 当前只读取 ModelingSqlModel/工作区文件，不接受 lifecycle artifact overlay | `DbtScopedProjectService.java:51-150` |
| L09 | `/lifecycle/runs` 要求 ModelSpec 已 PUBLISHED，不能承担发布前构建 | `ModelLifecycleService.java:822-851` |
| L10 | `modeling_pipeline_run` 已有 model revision/checksum、Airflow/dbt id、selector、target 和修复路径，可扩展为构建运行真值 | 实库 `information_schema.columns`，2026-07-27 |
| L11 | ReleaseCandidate 已有 START_BUILD/RETRY_BUILD 状态与 `/lock` 命令，但尚未连接真实 dbt dispatch | `ModelReleaseCandidateContract.java:383-394`; `ModelReleaseCandidateResource.java:126-149` |
| L12 | dbt manifest 导入为 DBT_MANAGED artifact 保存的 physicalAssetRef 为 null | `DbtAssetSyncService.java:1185-1229` |
| L13 | 当前 isMaterializedRelation 依据 current SUCCESS run evidence + materialization 类型，没有实时数据库探针 | `DbtAssetSyncService.java:1558-1568` |
| L14 | 发布注册适配器要求 catalog_dataset 具有 current run/materialized/physical verified 标签 | `CanonicalModelReleaseRegistrationAdapter.java:74-116` |
| L15 | 普通编译适配器的 physicalAssetRef 可能返回输入 sourceBindingId，不是输出资产 | `CanonicalModelLifecycleCompilerAdapter.java:91-98` |
| L16 | 当前实库有 11 个 ModelSpec、4 个 Implementation、2 个 artifact、1 个 lifecycle event、0 个 pipeline run、0 个 ReleaseCandidate | 2026-07-27 PostgreSQL 实测，见 `assets/domain-profile.md` |
| L17 | 四个 Sprint-74 目标关系在 `biadmin.public` 全部 `to_regclass=NULL` | 2026-07-27 PostgreSQL 实测，见 `it/baseline.md` |
| L18 | Airflow 两个 dbt DAG 均已注册且 unpaused，并有历史 success；dbt/Airflow/PG 容器当前运行 | 2026-07-27 Airflow DB 与 compose 实测 |
| L19 | Sprint-74 已通过真实登录、Chrome95、API、dbt compile 基线，但 IT-08 明确只验收“无物理资产不伪造” | `sprint-74.../it/baseline.md`; `it/evidence/acceptance-summary.md` |
| L20 | Sprint-69 明确 ReleaseCandidate 是唯一发布控制面，SQL/dbt 页面不得自动审核发布 | `sprint-69.../README.md:28-58` |
| L21 | 当前 `DbtDagService` 按 tag/layer 生成 DAG，但频率硬编码为 manual 且 DAG `schedule=None` | `DbtDagService.java:44-56,406-414` |
| L22 | 当前 `/lifecycle/runs` 只在 PUBLISHED 后创建带外部上下文的 run 记录，不主动触发 Airflow/dbt | `ModelLifecycleResource.java:243-252`; `ModelLifecycleService.java:822-851` |
| L23 | 高级 SQL 页面在 build success 后直接 submit review、approve review、publish | `SqlModelingPage.tsx:1762-1783` |
| L24 | 现有 candidate 成功状态名为 `BUILT`，allowedAction 为 RUN_QUALITY；不存在 BUILD_PASSED 枚举 | `ModelLifecycleContract.java:67-139` |
| L25 | 现有 DeliveryStatus/WorkspaceAction 没有 CANCELLED/CANCEL_CANDIDATE，严格 active claim 需要本 Sprint 显式补齐 | `ModelLifecycleContract.java:67-139`; `ModelReleaseCandidateContract.java:383-400` |
| L26 | 当前 candidate REST 只有 create/scope/lock/retry/refresh/replacement，没有 quality/review/approve/publish/rollback/cancel 命令 | `ModelReleaseCandidateResource.java:80-226` |
| L27 | 当前 candidate workbench 固定以 MODEL_MAINTAINER 计算 allowedActions，reviewer/operator action 无法正确呈现 | `ModelReleaseCandidateApplicationService.java:336-353` |
| L28 | 旧 `/lifecycle/reviews*`、`/lifecycle/publish` 仍可直接变更单模型发布状态，且都使用 CATALOG_MAINTAINERS authority | `ModelLifecycleResource.java:191-221` |
| L29 | 旧 publish 先把 ModelSpec 迁移为 PUBLISHED，再调用 registration，存在 Candidate/ModelSpec/资产分裂窗口 | `ModelLifecycleService.java:745-786`; `ModelLifecyclePublicationService.java:36-60` |
| L30 | 当前 Airflow 2.9.3 使用 LocalExecutor，scheduler/webserver/triggerer 运行；platform 与 Airflow 共享 RW DAG 目录，scheduler 每 3s 扫描 | 2026-07-27 live compose/config 复查 |
| L31 | 当前 Airflow `core.default_timezone=utc`，容器 OS `TZ=CST` 不能替代 DAG timezone 契约 | 2026-07-27 live Airflow config |
| L32 | 当前两个 dbt DAG 均为 `schedule=None`、unpaused 且有历史 success；默认 `max_active_runs=16` | 2026-07-27 Airflow DB/DAG source 复查 |
| L33 | `DbtDagService` 直接覆盖 Python 文件；同步步骤使用 `all_done` 且命令含 `|| true` | `DbtDagService.java` 当前实现，2026-07-27 |
| L34 | `DbtScopedProjectService` 已支持 `.dts-scoped-runs` 和 host/container project path 映射，可复用给 Airflow Docker dbt | `DbtScopedProjectService.java` 当前实现，2026-07-27 |
| L35 | ingestion 的 `AirflowDagService` 已证明“Airflow 调度、Java 执行业务”的瘦 DAG 模式，并使用 `max_active_runs=1` | ingestion Airflow DAG 当前实现，2026-07-27 |
| L36 | 当前只有一个 `AirflowClient`，已支持 trigger/list run/list DAG/pause/log，应扩展而非复制 client | `AirflowClient.java` 当前实现，2026-07-27 |
| L37 | `DbtReleaseSubmissionService` 与 `/lifecycle/runs` 接受外部 dagSelector/target/projectDir 或 airflowDagId/dbtSelector/targetTable，不能作为新 canonical 契约 | 当前 resource/service 复查，2026-07-27 |
| L38 | `ModelingVNextApplicationService.createRun` 存在先触发外部 Airflow、后插入 pipeline run 的孤儿窗口 | 当前 application service 复查，2026-07-27 |
| L39 | `ExternalRunLogService`/`OpsRunSyncScheduler` 每 60s 同步 Airflow 到运维外部运行日志，但不是 modeling pipeline 真值 | 当前 ops sync 实现，2026-07-27 |
| L40 | 当前 dbt profile 只有一个 `dev` Postgres output 且含 tracked 明文凭据；`dts-dbt` 容器本身仅保持运行，Airflow 通过 Docker socket 启动 ephemeral dbt | profiles/compose/runtime 复查，2026-07-27；未记录 secret 值 |
| L41 | `DbtTargetConnectionFactory` 已复用 `InfraSecretService` 进程内解析数据源 secret；但 `DbtConfigService.buildProfile` 仍把明文密码写入共享 `profiles.yml` | 当前 source 复查，2026-07-27；未记录 secret 值 |
| L42 | dts-platform 与 Airflow 共享宿主机 `services/dts-dbt/profiles`；Airflow canonical dbt 命令直接把该目录挂到 ephemeral container | `docker-compose-app.yml` 与 `DbtDagService.java`，2026-07-27 |
| L43 | 当前 DAG sync 只有 `X-DTS-Service`，无 token；service auth filter 未允许 dts-airflow 的 dbt sync/计划端点，且命令以 `|| true` 静默失败 | `DbtDagService.java:356-420`; `ServiceDependencyAuthenticationFilter.java:125-155` |
| L44 | dts-platform 未挂 Docker socket；Airflow scheduler/triggerer/webserver 均挂载 Docker socket，LocalExecutor 任务由现有 Airflow 执行面启动 dbt | `docker-compose-app.yml`，2026-07-27 |
| L45 | `DbtDagService.buildDagSource` 是 Java 内嵌的完整 Bash 模板；当前生成 DAG 无显式 `max_active_runs=1`，文件以 `TRUNCATE_EXISTING` 写入 | `DbtDagService.java:188-420` |
| L46 | Sprint-36/F3 已 DONE；`AssetAction`、`IamAssetActionPolicy`/request、migration、审批 API、矩阵 UI 与 deny-by-default `AccessChecker.canPerform` 已存在，90 个后端聚焦测试通过；Sprint-76 Candidate 发布/计划链尚未消费该端口 | Sprint-36/F3 实现与测试复查，2026-07-27 |
| L47 | Candidate REST 全部使用 CATALOG_MAINTAINERS；plan access 仅 owner；workspace allowedActions 固定以 MODEL_MAINTAINER 计算 | `ModelReleaseCandidateResource.java`; `ModelSpecPlanWriteAccessAdapter.java`; `ModelReleaseCandidateApplicationService.java` |
| L48 | Airflow 通过宿主机 Docker socket 启动任务，属于高权限受信 execution plane；当前架构不能声明敌对多租户隔离 | `docker-compose-app.yml` Airflow volumes；PG-01 threat boundary |
| L49 | canonical `ModelSpec.ModelField.dataType` 为必填，但 `ModelSpecCompilerProjection.project` 只投影字段名到 `ModelingVNextContract.ModelSpec.dimensions/metrics`，类型在 dbt compiler 边界丢失 | `ModelSpecContract.java:407-466,1360-1380`; `ModelSpecCompilerProjection.java:91-134` |
| L50 | 普通模型 schema.yml 只输出 column name/description/test，不输出 `data_type`；最终 model SQL 也不对每个输出列做 canonical type cast | `ModelingDbtCompiler.java:105-178,373-420` |
| L51 | PostgreSQL inspector 已通过 `pg_catalog.format_type` 获取精确实际类型并计入 columns checksum，但 `RelationLocator` 只携带 expected column name，聚合层未比较字段类型 | `PostgresPhysicalRelationInspector.java:31-58,245-294`; `PhysicalRelationInspector.java:67-105`; `ModelMaterializationRunArtifactService.java:651-680` |
| L52 | F4 现有控制面尚不满足 DoR：Candidate REST 只有 create/scope/lock/retry/refresh/replacement 且统一 CATALOG_MAINTAINERS；registration 对部分普通模型仍可合成 `dts_modeling.model_<uuid>`，未消费 F3 observation 作为唯一输出 locator | `ModelReleaseCandidateResource.java:32-226`; `CanonicalModelReleaseRegistrationAdapter.java:51-158` |
| L53 | T04 已把 current ModelSpec typed fields 贯通 compiler cast/schema、manifest expected type、RelationLocator checksum 和 PostgreSQL adapter compare；numeric→text 阻断 BUILT，DBT_MANAGED 全未声明时保留空 expected-type contract | T04 40 个聚焦单测 + `ModelMaterializationStartServiceIT` 2 个真实 PostgreSQL 测试，2026-07-28 |
| L54 | F4/T02 要求发布事务写默认 binding，但原 F7/T01 又依赖 F4/T02 才创建 binding 表，形成不可实现的循环；最小 binding persistence 已改由 F4/T02 唯一拥有，F7 只做 additive runtime 扩展 | F4/T02、F7/T01 契约复审，2026-07-28 |
| L55 | Publish Intent 异步撤权复核不能依赖请求时 JWT snapshot；当前 platform 无按 actor 查询 current realm role 的端口，必须复用 dts-admin KeycloakAdminClient 提供 fail-closed current-duty adapter | dts-admin Keycloak role assignment/read 实现与 F4/T01 复审，2026-07-28 |
| L56 | 当前 catalog registration 仍可合成输出表名、使用逐 step REQUIRES_NEW，并且 catalog_dataset 无 output locator 唯一键；三者均会破坏 observation 真值与 Candidate 级原子发布 | `CanonicalModelReleaseRegistrationAdapter`、`CatalogDataset`/Liquibase 复审，2026-07-28 |
| L57 | F4 声明依赖 F2/T03，但当前 Candidate contract/resource 不存在 `CANCEL_CANDIDATE/CANCELLED`，无法证明取消只开放于允许状态、释放 active claim 且保留 run/observation；F4 READY 结论撤回 | F2/T03 contract 与当前 Candidate API 复审，2026-07-28 |
| L58 | L57 的 cancel 缺口已关闭：Candidate 终态、命令账本、REST、claim 释放、迁移约束和真实 PostgreSQL 保留证据均已落地；同一轮补齐 retry attempt 递增、UNKNOWN 对账阻断，以及 model/implementation/artifact/dependency/target 五类 drift 用原 retry 收据原子迁移 STALE。F2/T03 仍缺 replacement/retry 并发和 Airflow exactly-once 证据，因此 F4 继续 BLOCKED | F2/T03 聚焦单测与真实 PostgreSQL retry/cancel/drift 矩阵，2026-07-28 |
| L59 | replacement claim 现由事务从旧终态 Candidate 原子转移至 claim-only DRAFT，START_BUILD 再补齐 current snapshot；真实 PostgreSQL 双线程矩阵证明 replacement、START_BUILD 与 retry 在相同/不同 key 竞争下分别收敛为一个 claim、一个 durable run 和唯一 attempt 2。F2/T03 本地并发缺口关闭；F4 只继续等待 F2/T02 的 Airflow submit timeout/duplicate/reconcile exactly-once effect 证据 | `20260728_03_model_release_candidate_claim_transfer.xml`、`ModelMaterializationStartServiceIT#retrySnapshotDriftTransitionsCandidateStaleWithoutAttemptTwo`，2026-07-28 |
| L60 | F2/T02 的本地协议已补强为只读 reconcile 与 submit 分离：无法证明 DagRun 不存在时不盲触发；existing run 必须匹配 durable conf；HTTP timeout、duplicate、local commit failure 和 token 过期均复用同一 deterministic dagRunId。Java 聚焦测试与 Python factory 4/4 通过；真实 Airflow 故障注入、service token/profile lease 和页面/计划统一入口尚未完成，因此 F4 与 PROD 继续 BLOCKED | `DbtExecutionGateway`、`AirflowDbtExecutionGateway`、`ModelMaterializationDispatchService` 及聚焦测试，2026-07-28 |
| L61 | F2/T04 的 RELEASE_BUILD 本地安全链已贯通 encrypted data-source secret → platform tmpfs lease → Airflow 固定 root 只读 mount；tracked profile、本地 Docker context 与离线包泄漏路径已关闭，原 profile 已知凭据值扫描为 0。Java 4 组聚焦测试、Python factory 4/4、tmpfs/compose preflight 与 package test 通过；OPERATIONAL_RUN 共用 resolver 和真实 Airflow rotation/restart/401/403/secret-scan 证据仍待完成 | `DbtRuntimeProfileLeaseService`、`dbt_task_factory.py`、三套 compose、`test_dbt_runtime_profile_preflight.sh`、`test_dts_build_pack_contents.sh`，2026-07-28 |
| L62 | F2/T02 真实 Airflow 故障注入已证明 accept-after-timeout、duplicate 409、durable conf 精确一致、唯一 DagRun 及 Platform 重启后唯一性；无效 token 运行 fail-closed，dbt/sync 未执行。同期发现 thin DAG 被 Airflow discovery safe mode 静默跳过，renderer 已以无执行语义 marker 修复并通过 HIGH 影响范围的 renderer/dispatch 聚焦测试；F4 的唯一外部副作用阻断解除 | `it/evidence/f2-airflow-exactly-once/README.md`、`DbtDagService` 及聚焦测试，2026-07-28 |
| L63 | F4/T01 后端控制面已进入实施态：专用 duty role/resolver、Publish Intent 原子收据、现有 dbt build 质量证据 reconciler、dts-admin/Keycloak current-duty fail-closed adapter、三人隔离和构建后完整快照漂移门禁已通过聚焦测试；真实 Keycloak 账号/撤权、Sprint-36/F3 资产动作本地接入、旧 lifecycle 委托和 Chrome95 尚未关闭，因此 T01/F4 只能标记 IN_PROGRESS，PROD 继续 NO-GO | `ModelPublicationIntentServiceTest`、`ModelPublicationReviewReconcilerTest`、`ModelMaterializationStartServiceIT`、dts-admin current-duty tests，2026-07-28 |
| L64 | F4/T03 本地主链已关闭：Candidate 与 generic dbt sync 共用 source/schema/table 稳定身份，迁移前重复清单 fail-closed，current observation actual columns 投影到 Catalog，artifact 只指向输出资产；真实 PostgreSQL 6/6 进一步证明双线程 writer 收敛、DBT_MANAGED 字段/双层血缘重试幂等、rollback 按当前 Candidate 的 physicalAssetId 归档，以及 100-entry 末端故障全回滚、PARTIAL 零可见、单次 retry 后一次性可见。回滚在写入前要求 ARCHIVE，且仍不自动 DROP 关系。T03 继续等待 external sync health、页面及 PROD 真实账号证据 | `CatalogPhysicalLocator`、`CandidatePublicationAdmissionService`、`20260728_06_catalog_dataset_physical_locator.xml`、`CandidatePublicationRepositoryIT`，2026-07-28 |
| L65 | external sync 不能由现有 outbox 状态直接关闭：OpenMetadata client 只有 GET/拉取缓存，Kafka outbox 的 SENT 只证明 broker handoff；dispatcher 把首次失败写成终态 FAILED，当前没有目标消费者、ACK 或幂等 retry 契约。必须先落地 consumer/ACK truth，再投影 target sync health；否则保持 PUBLISHED 且显示同步状态未知/降级，禁止假报 HEALTHY | `OpenMetadataClient`、`PlatformEventOutboxService`、`PlatformEventKafkaDispatcher` 定向复审，2026-07-28 |
| L66 | F4 本地权限矩阵已关闭：无专用 release duty（含仅 admin/auth-admin/auditor/op-admin）时 publish/registration retry/rollback 在读取 Candidate 前 403；M05 policy 缺失、过期、DENY 返回 false，evaluator 异常转换为稳定 `MODEL_RELEASE_ASSET_POLICY_UNAVAILABLE`/403 并在任何 Candidate/Catalog/field/lineage/physicalAssetRef 写入前停止。本轮定向测试 29/29 GREEN；PROD 仍须以三类真实账号、撤权和跨租户 API 证据验收 | `ReleaseDutyResolver`、`CandidatePublicationAdmissionService`、`ModelReleaseCandidateApplicationServiceTest`，2026-07-28 |
| L67 | 发布前隔离遗留缺口已关闭：原 `DbtAssetSyncService` 对带 lifecycle pin 的成功物化节点仍会 upsert Catalog，现改为只导入 current dbt artifacts 并保留旧 PUBLISHED 资产，不创建/更新 Catalog/table/column/lineage；无 `modelSpecId` 的普通外部 dbt 同步保持不变。dbt sync 13/13 GREEN；100-entry PostgreSQL 方法定向 1/1 证明 publication 前本地可消费事实为 0、提交后一次性可见，未重复运行整套 IT | `DbtAssetSyncService`、`DbtAssetSyncServiceTest`、`CandidatePublicationRepositoryIT#oneHundredEntryPublicationIsInvisibleAfterFailureAndVisibleAfterOneRetry`，2026-07-28 |
| L68 | F5/T03 页面已停止从 artifact/source/path 猜测发布资产：只有当前最后一个 publication event 为 RELEASE/PUBLISHED 时才展示 artifact output physicalAssetRef，并深链真实 Catalog dataset；build-only 仅展示 DDL/构建产物，PARTIAL 明确本地登记已回滚，ROLLBACK 不再展示历史引用。source-contract 2/2 与 TypeScript GREEN。binding deployment、relation health 和 external ACK 尚无统一 DTO，页面不得自行推断“上线完成/运行异常/同步健康” | `ModelSpecPhysicalAssetStage.tsx`、`modelSpecThreeStageDetail.source-contract.test.ts`，2026-07-28 |
| L69 | F5/T01 已接入共享单模型交付入口：模型详情和带 canonical context 的高级 SQL 页均调用同一 Build/Publish Intent，严格匹配 current SINGLE_MODEL Candidate；未绑定 ModelSpec 的高级页只保留技术 build 并禁用上线。原高级页 build 后自动 submit review/approve/publish 旁路已删除；PUBLISHED 明确不等于上线完成。TypeScript GREEN、source-contract 14/14；交付工作台 candidate 证据消费、OPERATIONAL_RUN、统一健康 DTO 和 Chrome95 仍待完成 | `ModelDeliveryIntentActions.tsx`、`modelSpecApi.ts`、`SqlModelingPage.tsx`，2026-07-28 |

勘察到此停止。实施 Task 必须引用 Lxx，禁止重复全仓扫描；如出现新事实，只能追加账本。

## 9. Gate Registry

| Gate | 项目 | 状态 | 证据 | 未过则关联 Task |
|---|---|---|---|---|
| G0 | 交付基线 | PASS（实施入口） | `it/baseline.md`；同日已归档环境、RED 数据集与构建证据，按漂移触发复测 | - |
| G0 | 领域与真实数据画像 | PASS | `assets/domain-profile.md` | - |
| G0 | DTS 领域不变量 | PASS | ADR-76-02/03/04/11/15/20～39 | - |
| G1 | 端到端契约链 | PASS | 本文 §6～§7、`assets/model-to-dag-execution-contract.md` | - |
| G1 | 非功能预算 | PASS | `assets/nfr-budget.md` | - |
| G1 | 架构与方向复审 | GO（DEV/TEST）/ PROD NO-GO | `assets/architecture-review-agenda.md` | - |
| G1/PG-01 | dbt credential/target 安全 | LOCAL_GREEN / REAL_IT_PENDING | L40～L44、L48、L61、`assets/production-readiness-gates.md` | F2/T04、F6/T02 |
| G1/PG-02 | 两类 DAG 单一 runtime template | GAP | L33、L45、`assets/production-readiness-gates.md` | F2/T02、F7/T02 |
| G2 | 影响分析与变更范围 | PENDING | 每个 Task 编码前 GitNexus impact | 各实现 Task |
| G3/PG-03 | 生产权限、职责分离与审计 | DEPENDENCY_READY / LOCAL_INTEGRATION_PENDING | L46～L47；Sprint-36/F3=DONE | F4/T01～T02、F6/T01、IT-14 |
| G3 | 发布安全 | GAP | `assets/release-plan.md` | F4/T01～T03、F6/T03 |
| G4 | 可运维性 | PENDING | `assets/runbook.md`（F6/T03 产出） | F6/T03 |
| G4 | DoD 真实验收 | PENDING | `it/` | F6/T01～T03 |

G0 已以同日归档证据关闭实施入口：四个目标关系不存在、pipeline run 为 0、ReleaseCandidate 为 0，正是本 Sprint 的冻结 RED，而不是要求反复登录或重跑 Chrome95。后续只在相关运行环境发生漂移、证据失效，或进入 F6 真实 UI/部署验收时定点复测。PG-03 的外部资产动作依赖已就绪，但 Candidate duty resolver、发布/计划消费和 IT-14 尚未完成；不得用页面隐藏按钮或固定角色模拟通过。DEV/TEST 可以按 Feature 依赖实施；PG-01/02/03 全部关闭前，PROD target/publish/CRON 均不得启用。

## 10. Feature 列表

| ID | Feature | Task 数 | 优先级 | 状态 |
|---|---|---:|---|---|
| F0 | 架构冻结与真实验收基线 | 2 | P0 | DONE |
| F1 | 普通实现可运行 dbt 制品 | 3 | P0 | IN_PROGRESS |
| F2 | 候选驱动物化编排与运行真值 | 4 | P0 | IN_PROGRESS |
| F3 | 真实关系核验与强绑定证据 | 4 | P0 | DONE |
| F4 | 发布治理与物理资产交接 | 3 | P0 | IN_PROGRESS |
| F5 | 建模与交付页面产品闭环 | 3 | P0 | DRAFT |
| F7 | 上线后计划 DAG 与持续计算 | 3 | P0 | DRAFT |
| F6 | 真实集成验收与安全交付 | 3 | P0 | DRAFT |

**依赖顺序**：

```text
F0 → F1 → F2 → F3 → F4 → F7 → F6
             └──────→ F5 ─────────┘
```

F5 可在 F2/F3 契约冻结后并行开发，但最终 UI 验收必须等待 F4/F7；F7 复用 F2/F3 的执行与核验端口，不新建第二套 runtime。

## 11. 追溯矩阵

| 需求点 | Feature | 关键 Task | 验收证据 |
|---|---|---|---|
| 普通配置可生成 runnable dbt bundle | F1 | T01～T03 | IT-01、IT-02 |
| targetPhysicalName 精确成为真实关系名 | F1/F3 | F1-T02、F3-T01 | IT-02、IT-03 |
| ReleaseCandidate 开始构建会真实触发 dbt build | F2 | T01～T04 | IT-04 |
| 构建状态可恢复、可重试、不重复提交 | F2 | T02、T03 | IT-05、IT-06 |
| build success 后直接核验数据库关系 | F3 | T01～T03 | IT-07、IT-08 |
| 未发布构建/PARTIAL 不冒充资产 | F4 | T02、T03 | IT-09 |
| PUBLISHED 后生成 CatalogDataset/physicalAssetRef/lineage | F4 | T02、T03 | IT-10、IT-11 |
| 维度模型在当前页面一键构建且不绕过候选 | F2/F5 | F2-T01、F5-T01 | IT-12、IT-13 |
| 高级/普通建模共享 Build/Publish Intent | F5 | T01～T03 | IT-04、IT-12 |
| Publish Intent 只推进质量与提交审核，不自动批准/发布 | F4/F5 | F4-T01、F5-T01 | IT-12、IT-13、IT-14 |
| Candidate 是唯一发布 owner，旧 lifecycle route 只兼容委托 | F4 | T01、T02 | IT-09、IT-15 |
| 本地多 entry 发布全有或全无，外部同步失败仅健康降级 | F4 | T02、T03 | IT-10、IT-11、IT-15 |
| SINGLE/BATCH 不自动合并且 active claim 唯一 | F2/F5 | F2-T01、T03、F5-T01 | IT-05、IT-12 |
| 候选字段服务端派生、P0 单安全目标、确定性 Airflow run | F2 | T01～T04 | IT-05、IT-14、IT-16 |
| dbt 凭据不进 Git/DAG/conf/XCom/API/DB/env/log/evidence，profile 只落 host tmpfs lease | F2/F6 | F2-T04、F6-T01～T03 | IT-14、IT-19 |
| 两类 DAG import 单一 Python task factory，dbt 依赖图自动映射到共享 executor/计划 DAG | F1/F2/F7 | F1-T02、F2-T02、F7-T02 | IT-16、IT-20 |
| Candidate duty role + Sprint-36/F3 asset action 双门禁 | F4/F6/F7 | F4-T01～T02、F6-T01、F7-T01/T03 | IT-14 |
| 上线后可手工/周期刷新并复用同一运行真值 | F7 | T01～T03 | IT-17、IT-18 |
| Airflow 唯一调度真值、原子 DAG 部署与正确落账顺序 | F7 | T01～T03 | IT-17～IT-20 |
| 构建、错误、成功、发布与运行结果可解释 | F5/F7 | F5-T02、T03、F7-T03 | IT-12、IT-13、IT-18 |
| PostgreSQL/dbt/Airflow/Chrome95 真实闭环 | F6 | T01～T03 | IT-01～IT-20 |

## 12. 完成标准

- [ ] compile 与 materialize 的 API、状态和 UI 文案完全分离。
- [ ] 普通 `DESIGNER_GENERATED` 实现能进入现有 scoped dbt project，无第二套模型 owner。
- [ ] `targetPhysicalName` 与真实 database relation identifier 一致。
- [ ] candidate lock 同事务产生 durable pipeline run/outbox，提交后真实触发共享 RELEASE_BUILD executor DAG；不存在先触发后落库。
- [ ] 普通维度模型在“数据实现”页点击一次“构建”即可创建/精确复用 SINGLE_MODEL candidate，用户无需填写 DAG 技术字段。
- [ ] 快捷构建遇 BATCH candidate 严格 409，不自动改变批量 scope；tenant+environment+model 只有一个 active claim。
- [ ] candidate 的 revision/checksum/dependency/selector/target/profile/dagRunId 全由服务端派生；P0 只有一个实测 executionTargetKey，其他 target fail-closed。
- [ ] tracked/shared dbt credential 已移出 canonical 链；warehouse secret 不进入 DAG、DagRun conf、XCom、API/DB/env/log/evidence，profile 只落 host tmpfs lease且权限、清理和轮换可验证。
- [ ] Airflow prepare/open/sync/probe/finalize/release 全部通过 pairwise service token + principal/path allowlist，缺失或伪造时 fail-closed。
- [ ] BUILD_FAILED/QUALITY_FAILED 保留 active claim 供原候选恢复；CANCEL_CANDIDATE 审计化释放且不自动 DROP 已有关系。
- [ ] 高级 SQL 页和普通模型页调用同一 Build/Publish Intent；高级页不再自动审核、批准和发布。
- [ ] 点击“提交上线”只记录 intent、运行质量并推进到 REVIEW_PENDING；reviewer/operator 通过 Candidate 命令独立批准和发布；Candidate duty role 与 Sprint-36/F3 asset action 双门禁均通过。
- [ ] 旧单模型 review/approve/publish/rollback/retry route 在兼容期只委托 current candidate，不再拥有发布状态。
- [ ] 重放、100 并发、Airflow accepted-then-timeout 和服务重启不会重复提交同一 candidate attempt。
- [ ] 每个成功模型都有与当前 model/implementation checksum 强绑定的 RelationObservation。
- [ ] inspector 直接查询目标数据库；run_results 不能单独满足物化门禁。
- [ ] 构建成功但未发布时不生成可消费 CatalogDataset。
- [ ] mandatory local publication 全部成功后才同时切换 Candidate/ModelSpec/资产可见性；PARTIAL 不暴露任何 entry。
- [ ] PUBLISHED 后 artifact physicalAssetRef 指向输出资产，Catalog、字段、血缘和密级登记一致；外部同步失败只显示 DEGRADED。
- [ ] 发布后生成默认 MANUAL_ONLY 的 plan+environment+executionTarget binding，scope 精确包含计划下全部 current PUBLISHED 模型。
- [ ] Airflow 是 CRON/nextRun/DagRun/TaskInstance 唯一真值；平台不运行第二 scheduler、不估算实际 nextRun。
- [ ] 手工运行先落 durable OPERATIONAL_RUN 再触发 Airflow；CRON DagRun 首任务原子 open OPERATIONAL_RUN；两路 import 同一版本 Python task factory并共用 gateway/probe，且无孤儿。
- [ ] DAG 文件原子替换；Airflow registration/parse/checksum/effective schedule 对账成功后 binding 才 ACTIVE；修改 cron/timezone 不改变 dagId。
- [ ] sync/probe 失败使 DagRun 和 pipeline run 失败；不存在 `|| true` 伪成功。
- [ ] 页面仅在 PUBLISHED + binding ACTIVE + relation healthy 时显示“上线完成”，其余组合显示准确的发布/部署/健康状态。
- [ ] dbt graph 负责模型依赖，Airflow 负责工作流步骤和触发时机；不要求用户手工连 DAG。
- [ ] 四个代表模型至少生成 table/view，并用系统表、最小查询和页面同时证明。
- [ ] 失败构建、关系缺失、标识不一致、revision 漂移、越权和跨租户均 fail-closed。
- [ ] Chrome95 完成“实现→构建→核验→提交上线→独立审核/发布→上线完成→查看物理资产→再次运行”真实旅程，并可进入交付工作台查看完整候选。
- [ ] release plan、runbook、回滚演练和 Go/No-Go 证据齐全。

## 13. 非目标

- 不新增一级菜单、平行模型详情页或第二个发布中心。
- 不把 `ModelingSqlModel` 提升为普通 ModelSpec 的 canonical owner。
- 不让模型详情、SQL 页面自动批准或发布；Publish Intent 只按显式用户请求自动运行质量并提交审核。
- 不把 CRON/schedule 修改塞入 Publish Intent；发布只创建默认 MANUAL_ONLY binding。
- 不让用户手工填写 dagId/projectDir/dbtUniqueId，也不为每个模型复制一张 Airflow DAG。
- 不以 compile、manifest、run_results 或 catalog 标签单项替代真实关系核验。
- 不在本 Sprint 自动 DROP 失败、拒绝或回滚候选产生的关系。
- 不承诺未经真实环境验证的达梦/MySQL adapter 已支持；只能通过 capability registry 明示支持状态。
- 不在 P0 支持多 execution target、多个 scheduleKey、跨 target `ref()` 或 meta-DAG；只保留可扩展身份边界。
- 不新增平台 scheduler、第二个 Airflow client 或常驻 HTTP dbt runner；复用现有 Airflow/LocalExecutor/Docker dbt。
- 不在本 Sprint 实现 SNAPSHOT 策略设计、数据保留自动清理、数据集事件触发或跨环境蓝绿发布；P0 持续计算只覆盖 MANUAL/CRON。
- 不修改 ModelSpec 四类模型与 DWD/DWS/ADS 经典层映射。
