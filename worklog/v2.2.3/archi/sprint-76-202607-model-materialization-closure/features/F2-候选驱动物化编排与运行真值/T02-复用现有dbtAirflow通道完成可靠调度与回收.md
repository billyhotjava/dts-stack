# T02：复用现有 dbt/Airflow 通道完成可靠调度与回收

**优先级**：P0
**状态**：IN_PROGRESS
**依赖**：T01、T04

## 目标

让 QUEUED candidate build 通过普通/高级页面共用的 dbt execution gateway 和 `schedule=None` RELEASE_BUILD executor DAG 执行，并在请求超时、服务重启或轮询中断后恢复真实状态。该 DAG 不是上线后的 plan CRON DAG。

## 技术设计（Contract-first）

- **输入契约**：按 candidateId 聚合的 current QUEUED pipeline runs + `ScopedCandidateProject`。
- **输出契约**：dispatcher 先得到 `scopedBundleChecksum` 并校验输入 `artifactBundleChecksum`，一次 `submitCandidate` 返回 `status,dagId,dagRunId,selector,scopedBundleChecksum`；所有 entry 写相同 dagRunId。
- **统一 gateway**：从 `DbtReleaseSubmissionService` 抽取 `DbtExecutionGateway`；高级 SQL build、candidate RELEASE_BUILD 和 F7 OPERATIONAL_RUN 共用 runtime spec 与唯一 Python task factory，不再各自拼 Airflow conf。
- **确定性 run id**：`dagRunId=dts_rc_<candidateId>_v<candidateVersion>_a<attempt>`，只能由服务端生成并持久化后提交。
- **持久化顺序**：START_BUILD 事务同时写 QUEUED rows、deterministic dagRunId 和 dispatch outbox；绝不先触发 Airflow 再创建 pipeline run。
- **外部 conf**：只传 `pipelineRunGroupId,candidateId,candidateVersion,attempt,runPurpose,runtimeSpecToken,bundleChecksum`；不传 credential、projectDir、selector、target。
- **runtime prepare**：Airflow 首任务以一次性 runtimeSpecToken 调平台内部接口解析 server-controlled project/selector/target/scope checksum，并取得 F2/T04 的 `profileLeaseId/targetName/expiresAt/credentialVersionRef`；不返回 profile 或任意 mount path。token 绑定 dagRunId、短期有效、单次消费。
- **内部服务鉴权**：prepare/sync/probe/finalize/release 使用 pairwise `X-DTS-Service: dts-airflow` + token；服务端校验 `service:dts-airflow` 和精确 path allowlist。当前仅 header 的旧 sync 不能进入 canonical 链。
- **DAG 选择**：P0 按平台环境 + current executionTargetKey 选择稳定 RELEASE_BUILD executor DAG；Candidate/model/schedule 不生成新 DAG。
- **单一 runtime 实现**：在 `services/dts-airflow/extra/dts_runtime/dbt_task_factory.py` 提供版本化 `build_dbt_dag(...)`；prepare/Docker dbt/sync/probe/finalize 只能在该 factory 内实现。Java renderer 只生成 import factory 的 thin DAG，F7 plan DAG 调用同一 factory。
- **Docker 执行**：保留现有 Airflow Docker socket + ephemeral dbt；helper 使用参数数组和 `subprocess.run(...,check=True)`，从固定 tmpfs host root + UUID leaseId 派生只读 profile mount，禁止 `shell=True`、任意 host path、共享 `DBT_PROFILES_DIR` 和敏感命令回显。
- **信任边界**：P0 只支持受信单一 Airflow execution plane；用户不能上传/编辑 Python DAG，DagRun conf 不能覆盖 image/path/command/callable。Docker socket 不提供敌对多租户隔离，不得在完成声明中夸大。
- **失败传播**：dbt、manifest sync 或 relation probe 失败必须使 DagRun 失败；禁止 `|| true`、吞异常或由 finalize 改写成功。
- **图边界**：candidate entries 的 `ref()/source()` 形成 dbt dependency graph；Airflow 仅执行粗粒度步骤。
- **可靠性**：
  1. scheduled dispatcher 认领 QUEUED；
  2. 外部调用不包在 DB 事务；
  3. submit 成功写 SUBMITTED；
  4. 请求超时或 Airflow 返回 duplicate/conflict 时，以确定性 dagRunId 查询；存在即恢复 SUBMITTED/RUNNING，不再次触发；
  5. reconciler 读取 Airflow + manifest + run_results + probe，按 unique_id 更新 entry。
- **状态**：QUEUED→SUBMITTED→RUNNING→DBT_SUCCEEDED/FAILED/UNKNOWN；DBT_SUCCEEDED 尚不是 BUILT，必须等待 F3 probe。
- **错误路径**：DAG 未注册/paused、Airflow disabled、bundle 丢失、manifest checksum 不匹配、run_results 缺 entry 均 fail-closed。
- **复用点**：`DbtReleaseSubmissionService`、`DbtScopedProjectService`、单一 `AirflowClient`；`ExternalRunLogService` 只作运维镜像，不是 modeling run 真值。
- **legacy 迁移**：现有 `ensureDagForSelector`/per-tag DAG 在兼容期只读保留；旧 build route 委托 gateway 后统计触发数，连续观察为 0 再 pause。Sprint-76 不直接删除历史 DAG 文件，也不让其成为新模板。

## 影响范围

- ETL dbt release service
- Materialization dispatcher/reconciler
- pipeline run state projection
- scheduled configuration and tests
- shared Airflow Python runtime helper + thin DAG renderer

## 验证（RED→GREEN）

- [x] fake gateway 的 timeout/restart/duplicate callback tests。
- [x] “Airflow 已接收但 HTTP 超时”只存在一个确定性 dagRunId。
- [x] duplicate dagRunId 响应走对账，不生成第二次外部执行。
- [x] candidate 多 entry 只触发一个 DAG run。
- [ ] 普通与高级页面的构建请求落到同一 executor DAG/runtime spec。
- [ ] RELEASE_BUILD/plan DAG 静态检查只 import 同一 task factory；仓库中没有第二个 canonical Docker dbt runtime builder。
- [ ] manifest 每个 entry 必须匹配 model/implementation meta。
- [ ] sync/probe 失败时 Airflow DagRun 与 pipeline run 均失败。
- [x] projectDir/selector/target/credential 的 conf 注入被拒。
- [ ] service token 缺失/伪造、runtimeSpecToken 重放、profile lease 过期或路径穿越均在启动 dbt 前失败。
- [ ] Airflow UI/API 无法覆盖 image/path/command/callable，dbt 生产 DAG namespace 中非平台受管 Python DAG 为 0。
- [ ] 旧 per-tag DAG 迁移后触发数为 0，并完成 pause/rollback 演练。
- [ ] disabled Airflow 返回 BLOCKED，不显示 RUNNING/SUCCESS。

## Definition of Done

- [ ] candidateId 能串联到 dagRunId/invocationId。
- [x] `(candidateId,version,attempt)` 与 dagRunId 一一对应。
- [ ] 无需用户手工配置 DAG，且 DAG 数量不按模型数增长。
- [ ] 两类 DAG 的运行逻辑只有一个版本化 Python owner；Java renderer 不含 runtime shell。
- [ ] 页面刷新/服务重启不丢状态。
- [ ] DBT_SUCCEEDED 与关系核验状态严格分离。

## 当前实现证据（2026-07-28）

- START_BUILD 已先在数据库事务内写入 deterministic `dagRunId`、QUEUED
  pipeline rows 与 PENDING dispatch；scheduled dispatcher 在事务外认领并调用仓库
  唯一 `AirflowClient`。
- `DbtExecutionGateway` 现区分“只读对账”和“允许提交”：首次提交前无法证明
  DagRun 不存在时保持 UNKNOWN，不盲触发；HTTP timeout/duplicate 后只按精确
  `dagId + dagRunId` 恢复。
- 已存在 DagRun 必须同时匹配 durable conf
  `pipelineRunGroupId/candidateId/version/attempt/purpose/token/bundle`；同 run id
  不同 conf 返回 `MODEL_AIRFLOW_RUN_IDENTITY_CONFLICT`，不能冒名恢复。
- UNKNOWN/stale-claim 恢复优先使用已持久化 token digest、expiry 和 scoped
  checksum 做只读对账；即使 runtime token 已过期，只要 Airflow 已接收正确
  DagRun，仍可恢复 SUBMITTED，不会误写 BLOCKED 或再次 POST。
- 聚焦 Java 测试：`AirflowDbtExecutionGatewayTest`、`ModelMaterializationDispatchServiceTest`
  与 `DbtDagServiceTest` 全部通过；覆盖 timeout-after-accept、existing duplicate、
  reconciliation unavailable、conf identity conflict、local commit failure、expired-token
  recovery、多 entry 单次 submission 和 thin DAG 原子写。
- Python factory 4/4 通过：固定 root/opaque lease、参数数组
  `subprocess.run(check=True)`、DagRun conf override/path traversal 拒绝，且 factory
  只有一个 Docker runtime owner。
- 真实 Airflow 故障注入已通过：Airflow 接受 trigger 后客户端 timeout；同一
  runId 重放返回 409，Airflow 中始终只有一个 DagRun；dts-platform 单服务
  重建后 run 与 durable conf 仍完整且唯一。无效 runtime token 使 prepare/finalize
  failed，dbt_build/sync_probe upstream_failed，不产生 profile lease 或目标表。
- 同轮修复 thin DAG 被 Airflow discovery safe mode 静默跳过的问题；renderer
  增加无执行语义的 discovery marker，真实 Airflow 精确解析一个受管 DAG。
  证据见 `../../it/evidence/f2-airflow-exactly-once/README.md`。

## 剩余门槛

1. 高级 SQL 旧 route 仍是 legacy 兼容入口；普通与高级页面统一委托
   Candidate Build Intent、计划 DAG 复用同一 factory 分别由 F5/T01 与 F7/T02
   完成，不能在本 Task 直接把旧任意 selector/conf 接到 canonical runtime。
2. service token、profile lease、真实 sync/probe/finalize 失败传播与 secret scan
   仍等待 T04/F6 的集成证据。
