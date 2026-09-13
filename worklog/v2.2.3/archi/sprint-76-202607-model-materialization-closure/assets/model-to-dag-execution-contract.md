# 模型设计到 Airflow/dbt 执行契约

**状态**：DRAFT，待 Sprint-76 架构复审
**目的**：冻结“模型设计完成后如何真正建表、发布后如何持续计算”的执行边界，并使设计严格落在当前 Airflow 2.9.3 + LocalExecutor + Docker dbt 运行架构上。

## 1. 产品结论

用户不需要在维度建模页面手工创建、选择或连接 Airflow DAG。

```text
逻辑设计
  → 数据实现
  → 验证实现
  → 构建（RELEASE_BUILD）
  → 提交上线
  → 独立审核与发布
  → 上线完成
  → 立即运行或按 CRON 持续计算（OPERATIONAL_RUN）
```

- **逻辑设计**定义业务粒度、字段、键、业务时间和模型关系；
- **数据实现**定义输入来源、字段映射、计算逻辑、目标关系和物化策略，是生成可执行 SQL 的必要上下文；
- **构建**锁定 Candidate 快照并执行一次真实 `dbt build`，负责首次计算和关系核验；
- **提交上线**只记录 Publish Intent、运行质量检查并提交人工审核；
- **发布上线**由 reviewer/operator 在 Candidate 控制面完成，发布操作不重复执行 dbt；
- **上线完成**是 `PUBLISHED + binding ACTIVE + relation healthy` 的只读投影；
- **持续计算**复用同一 dbt 物化模板，但产生独立的 `OPERATIONAL_RUN`，不重新走审核发布。

“数据实现”不是第二套物理资产台账；它是逻辑模型到 dbt 节点的执行规格。“物理资产”是发布后真实 table/view 及其字段、血缘、运行和治理证据。高级 dbt 工作台只是另一种实现编辑方式，不是物理资产本身。

## 2. 两类建模入口，一个物化内核

| 阶段 | 普通维度建模 | 高级 dbt 建模 | 统一 owner |
|---|---|---|---|
| 实现输入 | 来源、映射、JOIN、输出设置 | SQL/Jinja、schema.yml、项目文件 | `ModelImplementationRevision` |
| 可运行制品 | compiler artifact overlay | uploaded/scoped dbt project | `DbtScopedProjectService` |
| 模型依赖 | compiler 生成 `source()/ref()` | 项目已有 `source()/ref()` | dbt manifest graph |
| 构建入口 | 数据实现页“构建” | 高级页“构建” | Build Intent → `ReleaseCandidate.START_BUILD` |
| 发布构建 | 共享 RELEASE_BUILD DAG + 唯一 Python task factory | 同左 | `DbtExecutionGateway` + Airflow |
| 关系核验 | 直接探测运行目标 | 同左 | `PhysicalRelationInspector` |
| 提交上线 | Publish Intent | Publish Intent | ReleaseCandidate |
| 人工审核/发布 | 交付工作台 | 交付工作台 | reviewer/operator |
| 发布资产 | 发布结果 Tab | 发布结果 Tab | CatalogDataset + RelationObservation |
| 上线后计算 | 计划执行绑定 | 计划执行绑定 | Airflow + PlanExecutionBinding |

高级页面当前 build success 后直接 review/approve/publish 的行为必须移除。有 ModelSpec 上下文时调用统一 Build/Publish Intent；无 ModelSpec 上下文时只能技术构建，先导入或绑定 canonical ModelSpec 后才能提交上线。

## 3. 三类真值与两类 DAG

### 3.1 真值所有权

| 真值 | 唯一 owner | 平台保存什么 |
|---|---|---|
| 何时应运行、下次何时运行、DagRun/TaskInstance 状态 | Airflow | 期望 schedule、稳定 dagId、部署 checksum 和 Airflow 实际状态投影 |
| 为什么运行、运行属于哪个 Candidate/Binding、业务成败 | `modeling_pipeline_run` | durable run、purpose、scope、外部 run id、修复状态 |
| 模型执行顺序 | dbt manifest graph | selector、bundle/scope checksum，不复制模型级 DAG |

`PlanExecutionBinding` 不是第二个 scheduler：不得自行计算或缓存为权威 `nextRun`，不得用本地定时任务触发 CRON。页面的实际 pause、最近/下次调度时间必须读取 Airflow API；Airflow 不可用时明确显示 UNKNOWN，不以平台估算值冒充。

### 3.2 DAG 类型

| DAG | 数量与身份 | schedule | 用途 |
|---|---|---|---|
| RELEASE_BUILD executor DAG | 每个平台环境 + 可用执行目标一个稳定 DAG | `None` | Candidate 构建；普通/高级建模共用 |
| OPERATIONAL plan DAG | 每 `(tenant,plan,environment,executionTargetKey)` 一个稳定 DAG | `None` 或一个 CRON | 已发布计划的手工/周期计算 |

两类 DAG import 同一个版本化 Python task factory，不能各自复制一份相似 Bash/Python：

```text
services/dts-airflow/extra/dts_runtime/dbt_task_factory.py
  └─ build_dbt_dag(...)
       prepare_runtime
         → dbt_build
         → sync_manifest_and_probe_relation
         → finalize_run (trigger_rule=all_done)

RELEASE_BUILD thin DAG.py ─┐
                           ├─ import build_dbt_dag
OPERATIONAL thin DAG.py ───┘
```

约束：

- Java renderer 只写 `dagId/purpose/bindingId?/schedule/timezone/templateVersion/deploymentChecksum/tags`，不得再生成 Docker/Bash runtime；
- task factory 继续通过当前 Airflow Docker socket 启动 ephemeral dbt，使用参数数组且禁止 `shell=True` 和敏感命令回显；
- `dbt_build` 或同步/关系核验失败必须使 DagRun 失败；禁止 `|| true` 吞掉同步错误；
- `finalize_run` 只负责收敛状态，不能把失败改写为成功；
- dbt `ref()/source()` 决定模型级执行顺序；Airflow 不为每个模型复制 task；
- `max_active_runs=1`；数据库再以 active-run 唯一约束防止 CRON/手工并发；
- DAG ID 不包含 cron、Candidate、release 或 scheduleKey，修改 CRON 不得更换 dagId。

## 4. 普通实现到 dbt 节点

| ModelImplementation 内容 | dbt 投影 | 构建阻断 |
|---|---|---|
| 物理来源 | `source(source_name, table_name)` | 来源、连接、schema/table 未确认 |
| 上游逻辑模型 | `ref(stable_node_name)` | 上游未在同环境发布且不在同一 Candidate |
| 字段直映射 | quoted column + alias | 字段不存在、类型不兼容 |
| 计算表达式 | 受控 SQL expression | 解析/编译失败 |
| 关联条件 | JOIN/ON | 缺键、歧义字段、非法循环 |
| `targetPhysicalName` | `config(alias=...)` | identifier 非法或 manifest 不一致 |
| FULL + table/view | `materialized=table/view` | adapter capability 不支持 |
| INCREMENTAL + KEY | `materialized=incremental, unique_key=...` | 缺 KEY 或 adapter 不支持 |

compiler 必须把 `modelSpecId/modelRevision/modelChecksum/implementationRevision/implementationChecksum` 写入 node meta。结果回收以 manifest `unique_id` 和 meta 匹配，不能按中文名称猜测。

## 5. 发布构建契约

### 5.1 Build Intent

```http
POST /api/modeling/model-specs/{modelSpecId}/build-intents
If-Match: <model-version>
Idempotency-Key: <uuid>

{
  "planId": "<uuid>",
  "environment": "DEV|TEST|PROD"
}
```

服务端必须：

1. 校验 plan membership、Implementation、artifact 和 published upstream；
2. 创建或精确复用 `SINGLE_MODEL_INTENT`；
3. 锁定 model/implementation/artifact/dependency/target 快照；
4. 在一个事务内执行 START_BUILD、写 Candidate BUILDING、每 entry 的 QUEUED `RELEASE_BUILD` run 和 dispatch outbox；
5. 提交后由 dispatcher 准备 scoped project，再触发 RELEASE_BUILD executor DAG；
6. 返回 canonical candidate/run 和工作台 deep link。

禁止先调用 Airflow 再插入 run。客户端不得提交 `dagId/selector/projectDir/target/profile/revision/checksum/externalRunId`。

### 5.2 确定性外部运行

```text
dagRunId = dts_rc_<candidateId>_v<candidateVersion>_a<attempt>
```

平台先持久化该 ID，再用既有 `AirflowClient` 触发。HTTP timeout/duplicate/conflict 时按同一 ID 查询；存在即恢复，禁止第二次触发。

Airflow `dag_run.conf` 只允许稳定业务引用和不可变 checksum，例如：

```text
pipelineRunGroupId, candidateId, candidateVersion, attempt,
runPurpose=RELEASE_BUILD, runtimeSpecToken, bundleChecksum
```

`projectDir/selector/targetName` 由 `prepare_runtime` 使用一次性 `runtimeSpecToken` 从平台内部接口解析；任意 conf 覆盖均被忽略或拒绝。内部调用必须携带 pairwise `X-DTS-Service: dts-airflow` 与 `X-DTS-Service-Token`，服务端同时校验 `ROLE_SERVICE_INTERNAL`、principal 和精确路径白名单。

凭据绝不进入 DAG 源码、DagRun conf、XCom、API body/response、业务数据库、环境变量、运行证据或日志。内部接口只返回非敏感 runtime spec 与 `profileLeaseId/targetName/expiresAt/credentialVersionRef`；Airflow 从固定 host tmpfs root 和严格 UUID leaseId 派生只读 mount source，不能接收任意路径。

## 6. 发布与 PlanExecutionBinding

### 6.1 Publish Intent

```http
POST /api/modeling/model-specs/{modelSpecId}/publish-intents
If-Match: "release-candidate:<candidateId>:<version>"
Idempotency-Key: <uuid>

{"candidateId":"<uuid>","reason":"提交上线"}
```

Publish Intent 最多自动执行 `RUN_QUALITY → SUBMIT_REVIEW`，在人工边界停止。reviewer/operator 只在交付工作台执行 Candidate APPROVE/REJECT/PUBLISH；同一 actor 不得提交并批准/发布。

生产 mutation 同时经过两层授权：

- Candidate domain duty resolver 只从专用 `ROLE_MODEL_MAINTAINER/ROLE_MODEL_RELEASE_REVIEWER/ROLE_MODEL_RELEASE_OPERATOR` 解析 maintainer/reviewer/operator；不得从 catalog/admin/auth-admin/auditor 静默提升，allowedActions 与 command authorization 必须同源；
- 发布创建/更新 CatalogDataset 等资产时，再调用 Sprint-36/F3 的 `AccessChecker.canPerform(...)` deny-by-default policy。

M05 资产动作矩阵不能替代 Candidate 审核职责，actor separation 也不能替代资产动作权限。Sprint-36/F3 已 DONE，但 Sprint-76 在 Candidate 发布/计划链实际消费其端口并通过 IT-14 前，PROD APPROVE/PUBLISH/ROLLBACK/SCHEDULE_ENABLE 仍保持 BLOCKED；Sprint-76 不复制其权限实体。

### 6.2 绑定身份与范围

P0 唯一身份：

```text
(tenantId, planId, environment, executionTargetKey)
```

`executionTargetKey` 来自服务端已配置 dbt target，不接受客户端文本。P0 只允许一个真实配置且通过安全验收的 target；请求其他 target 返回 `MODEL_EXECUTION_TARGET_UNAVAILABLE`。保留该身份维度是为了后续安全扩展，不代表本 Sprint 已支持多 target。

`modeling_plan_execution_binding` 保存：

```text
tenantId, planId, environment, executionTargetKey, version,
scheduleMode(MANUAL_ONLY|CRON_ENABLED), cron?, timezone,
desiredScopeChecksum, desiredDeploymentChecksum,
dagId, deploymentStatus, deployedChecksum,
lastDeploymentAt, lastErrorCode
```

`modeling_plan_execution_binding_entry` 保存当前 scope：

```text
bindingId, modelSpecId, publishedReleaseId, modelRevision,
dbtUniqueId, targetIdentifier, artifactChecksum, dependencySnapshotChecksum
```

规则：

- 发布 Candidate 后，服务端从该 plan/environment/target 的**全部 current PUBLISHED 模型**重建 scope；不得只引用最近 Candidate；
- entry 只保存不可变引用和 checksum，不复制 SQL/模型正文；
- 本地发布事务写入默认 `MANUAL_ONLY + DEPLOYING` binding/scope；外部 DAG 文件部署在提交后异步完成；
- 每个 binding P0 只有一个 schedule；不引入 `scheduleKey`；
- 新发布、回滚、scope drift 使 desired checksum 变化并触发同 dagId reconcile；
- `onlineReadiness=READY` 仅当 publication current、binding ACTIVE、Airflow deployment current 且 relation healthy。

## 7. OPERATIONAL_RUN 的两种触发

### 7.1 手工立即运行

```text
UI/API
  → 校验 ACTIVE/current binding
  → 事务创建 durable OPERATIONAL_RUN + deterministic dagRunId
  → 提交后触发该 binding 的 plan DAG
  → Airflow 运行统一任务模板
```

建议入口：

```http
POST /api/modeling/plans/{planId}/execution-bindings/{bindingId}/runs
If-Match: <binding-version>
Idempotency-Key: <uuid>
```

平台先建业务 run，后触发 Airflow；不能复用当前接受客户端 `airflowDagId/dbtSelector/targetTable` 的 lifecycle run 写法。

### 7.2 Airflow CRON

```text
Airflow scheduler 创建 DagRun
  → prepare_runtime 首先调用平台 scheduled-runs/open
  → 平台按 bindingId + dagRunId + logicalDate 原子创建/认领 OPERATIONAL_RUN
  → 返回 server-controlled runtime spec
  → dbt build / sync / probe / finalize
```

内部入口契约：

```http
POST /api/internal/modeling/execution-bindings/{bindingId}/scheduled-runs/open
Authorization: service identity

{
  "dagId": "<stable-dag-id>",
  "dagRunId": "<airflow-owned-id>",
  "logicalDate": "<instant>"
}
```

- 相同 `(bindingId,dagRunId)` 幂等返回同一 run；
- binding 非 ACTIVE、scope/checksum 不匹配或已有 active run 时 fail-closed；并发碰撞记 `SKIPPED_CONCURRENT`；
- 内部接口不接受 selector、projectDir、target 或 credential；
- Airflow DagRun 已存在但平台拒绝 open 时，任务明确 SKIPPED/FAILED，不得继续 dbt。

## 8. DAG 部署与 schedule 变更

`DbtDagService` 重构为 thin renderer/reconciler，不承担调度真值或 dbt runtime 实现：

1. 从 binding snapshot 渲染稳定薄 DAG，只 import `dts_runtime.dbt_task_factory.build_dbt_dag(...)`；
2. 先写同目录临时文件，`fsync` 后原子 move，禁止 truncate 正在被 scheduler 读取的文件；
3. schedule 变更时先 pause，原子替换同一个 DAG 文件；
4. 通过 `AirflowClient` 轮询 DAG registration、parse error、tags/checksum 和 effective schedule；
5. 只有实际 checksum 一致且目标 pause 状态正确时 binding 才 ACTIVE；
6. CRON 使用显式 IANA timezone 与 timezone-aware `pendulum` start date；不能依赖容器 `TZ=CST`，因为当前 Airflow `default_timezone=utc`；
7. 页面展示的 next run、last run、paused 状态来自 Airflow API。

task factory 是 prepare/Docker dbt/sync/probe/finalize 的唯一实现。当前 `ensureDagForSelector` 产生的 per-tag DAG 仅作为兼容入口：canonical Build Intent 切换到稳定 RELEASE_BUILD executor 后，先证明旧入口触发数为 0，再 pause 历史 DAG；本 Sprint 不直接删除历史文件。

调度命令使用 binding ETag、Idempotency-Key、权限和审计；修改 `scheduleMode/cron/timezone` 不修改 ModelSpec/Implementation revision，也不改变 dagId。

## 9. 凭据和目标能力边界

当前仓库 `profiles.yml` 含静态明文凭据，因此只能视为待迁移的本地基线，不能作为生产或多目标能力完成证据。

P0 安全门禁：

- dbt credential 移出 Git 和共享 DAG/scoped-project 目录；
- `executionTargetKey` 由平台映射到既有数据源 secrets，凭据只在 dts-platform 进程内解密；
- `DbtRuntimeProfileLeaseService` 只能在绑定到宿主机 tmpfs（建议 `/dev/shm/dts-dbt-runtime`）的固定容器目录签发 task-scoped profile lease；生产启动时验证 filesystem type、owner、无 symlink、目录 0700、文件 0600；
- 内部 API 只返回 `profileLeaseId/targetName/expiresAt/credentialVersionRef`，不返回 secret 或任意 host path；Airflow task 用固定 host root + UUID leaseId 派生只读 mount；
- Airflow 在 `finally` 通过 authenticated internal API release；平台 TTL janitor 兜底清理，宿主机重启时 tmpfs 自动清空；
- prepare/open/sync/probe/finalize/release 都使用 pairwise Airflow service token；缺 token、伪造 header、路径不在 allowlist 或 token 不匹配均 fail-closed；
- DAG、DagRun conf、XCom、环境变量、platform API/DB、审计和 evidence 只出现非敏感 target key/leaseId/version ref；
- P0 只适用于受信的单一 Airflow 运维执行面；Docker socket 是宿主机高权限边界，禁止用户上传/编辑 dbt 生产 Python DAG。若要求敌对多租户或不信任 scheduler，本架构直接 NO-GO；
- 无安全 target、解析失败或请求非当前 target 时 fail-closed；
- 完成 secret scan、日志脱敏和真实轮换演练前，F2/F7/F6 不得 READY。

## 10. 页面状态

### 数据实现 Tab

```text
未配置 → 配置输入/映射/输出
已配置 → 验证实现
验证通过 → 构建
构建中 → 排队/运行/核验
构建失败 → 原因 + 唯一修复动作
构建通过 → 已核验 relation + 提交上线
质量/审核/发布中 → current Candidate 状态
已发布/部署中 → 资产可见，运行计划尚未就绪
上线完成 → ACTIVE binding + relation healthy + Airflow 实际调度摘要
已发布/运行异常 → 资产保留 + 唯一修复动作
```

### 计划交付工作台

工作台提供完整 Candidate、质量、reviewer/operator、binding、Airflow 实际 schedule、最近/下次运行与修复动作。普通用户不需要理解 dagId、selector 或 projectDir。

## 11. 已冻结与待复审

已冻结：

- [x] Airflow 是唯一 CRON/next-run/DagRun 真值，平台不再造 scheduler；
- [x] 发布构建先落 durable run 再触发共享 manual executor DAG；
- [x] CRON DagRun 由 Airflow 创建，首任务原子登记平台 OPERATIONAL_RUN；
- [x] 一个 plan binding P0 只有一个 schedule，不使用 scheduleKey；
- [x] binding scope 聚合 plan 下全部 current PUBLISHED 模型；
- [x] P0 只有一个真实配置 target；多 target fail-closed；
- [x] 普通/高级实现共用唯一 Python task factory、gateway、run/probe，不共用错误的业务触发顺序；
- [x] `sync/probe` 失败必须使 DAG 失败；
- [x] Airflow 实际 next run/paused 状态只读投影。

架构复审仍需确认：

- [x] RELEASE_BUILD executor DAG 与 plan DAG 继续复用现有 Airflow Docker dbt 启动方式，但 runtime 只能存在于一个 Python task factory；
- [x] 既有数据源 secret 到 task-scoped profile lease 使用宿主机 tmpfs 固定映射，不使用普通 host 目录、共享 profiles 或 API secret body；
- [ ] pairwise Airflow service auth、tmpfs filesystem/权限、release/TTL 和轮换真实演练通过；
- [ ] Sprint-36/F3 实际完成，且 Candidate domain duty role resolver 与资产动作矩阵双门禁通过；
- [ ] 上游 `ref()` selector 是否只带必要 ancestors；
- [ ] OPERATIONAL_RUN observation 如何更新资产健康但不覆盖发布证据；
- [ ] 高级页面无 ModelSpec 上下文时的导入/绑定路径是否完整。
