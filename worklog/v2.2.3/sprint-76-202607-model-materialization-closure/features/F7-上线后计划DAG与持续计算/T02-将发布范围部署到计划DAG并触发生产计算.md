# T02：基于现有 Airflow 部署稳定计划 DAG 并接通手工/CRON

**优先级**：P0
**状态**：DRAFT
**依赖**：T01、F2/T02、F3/T03

## 目标

复用当前 Airflow 2.9.3、共享 DAG 目录、`AirflowClient`、`DbtDagService` 和 Docker dbt 执行方式，把 binding scope 部署为稳定 plan DAG，并正确接通手工与 CRON 两种 OPERATIONAL_RUN。

## 技术设计（Contract-first）

- **DAG 身份**：由服务端对 `tenant+plan+environment+executionTargetKey` 生成稳定 ID；不包含 cron、Candidate、release、model 或 scheduleKey。
- **DAG renderer**：把 `DbtDagService` 收敛为 thin renderer/reconciler；只输出 `dagId/purpose/bindingId/schedule/timezone/templateVersion/deploymentChecksum/tags` 并 import F2/T02 的唯一 Python task factory。不新增 scheduler、不复制 `AirflowClient`，也不在 Java text block 中复制 runtime shell。
- **原子部署**：同目录临时文件 + fsync + atomic move；禁止直接 truncate 正被 scheduler 扫描的文件。
- **统一模板**：`services/dts-airflow/extra/dts_runtime/dbt_task_factory.py` 是 `prepare_runtime → dbt_build → sync_manifest_and_probe_relation → finalize_run(all_done)` 的唯一实现；RELEASE_BUILD 与 plan DAG 只传不同 purpose/open policy。
- **失败传播**：dbt、manifest sync、relation probe 任一失败均使 task/DagRun 失败；移除 `all_done + || true` 的伪成功链。
- **注册门禁**：部署后轮询 Airflow DAG registration、parse error、tags/checksum、effective schedule、pause 状态；一致后才 ACTIVE。
- **并发**：DAG `max_active_runs=1`；数据库 active-run 唯一约束兜底；CRON/手工碰撞记 `SKIPPED_CONCURRENT`。
- **手工触发**：`POST /api/modeling/plans/{planId}/execution-bindings/{bindingId}/runs` 先事务创建 OPERATIONAL_RUN + deterministic dagRunId，提交后用 `AirflowClient` 触发。
- **CRON 触发**：Airflow scheduler 先创建 DagRun；`prepare_runtime` 调用 `POST /api/internal/modeling/execution-bindings/{bindingId}/scheduled-runs/open`，按 bindingId+dagRunId+logicalDate 原子创建/认领 OPERATIONAL_RUN。
- **runtime spec**：内部接口返回 server-controlled project/selector/target/bundle/scope checksum 和 `profileLeaseId/targetName/expiresAt/credentialVersionRef`；不返回 secret 或任意 mount path，不接受客户端或任意 DagRun conf 覆盖。
- **服务身份**：scheduled-open/runtime/sync/probe/finalize/release 全部使用 pairwise Airflow service token；端点校验 `ROLE_SERVICE_INTERNAL + service:dts-airflow + exact path`。缺 token、伪造 header 或旧 header-only 模式均不能进入 production。
- **profile mount**：task factory 从固定宿主机 tmpfs root + UUID leaseId 派生只读 mount；禁止共享 `DBT_PROFILES_DIR`、普通 host 目录和路径穿越。
- **时区**：CRON 使用显式 IANA timezone 与 timezone-aware `pendulum` start date；不得依赖容器 CST，因为 Airflow `default_timezone=utc`。
- **schedule 修改**：pause 当前 DAG → 原子替换同一文件 → 等待 parse/checksum/schedule 生效 → 按期望 unpause；dagId 不变。
- **状态回收**：Airflow task/DagRun + manifest/run_results + relation observation 回写同一 pipeline run；`ExternalRunLogService` 仅作运维镜像，不是建模真值。
- **失败边界**：DAG missing/paused、binding/version/scope drift、runtime token 过期、secret/target unavailable、sync/probe 失败全部 fail-closed；不撤销 PUBLISHED。

## 影响范围

- `DbtDagService`/DAG template
- existing `AirflowClient`
- plan deployment reconciler
- operational run coordinator/internal service API
- Airflow/dbt integration tests

## 验证（RED→GREEN）

- [ ] 连续 100-entry scope 只有一个稳定 plan DAG。
- [ ] cron/timezone 修改前后 dagId 不变，Airflow 实际 schedule/next run 正确。
- [ ] scheduler 扫描期间原子替换无 partial parse error。
- [ ] plan DAG 与 RELEASE_BUILD DAG 都只 import 同一 task factory，templateVersion/checksum 可对账。
- [ ] MANUAL：平台 run 先于 DagRun；timeout 对账不重复触发。
- [ ] CRON：DagRun 先于 scheduled open；重复 open 返回同一 run。
- [ ] CRON/手工竞争只有一个 dbt build，另一个可审计 SKIPPED_CONCURRENT。
- [ ] manifest graph 按 ref 执行必要上游和目标模型。
- [ ] sync/probe 故障使 DagRun FAILED，绝不显示 SUCCESS。
- [ ] service token/lease 缺失、过期、伪造或路径越界在 dbt 前 fail-closed。
- [ ] Airflow 重启/注册延迟后 reconciler 恢复，不生成新 dagId。

## Definition of Done

- [ ] 用户无需手工连接或选择 DAG。
- [ ] Airflow 中实际 schedule、DagRun/TaskInstance 与平台业务 run 可一一对账。
- [ ] 上线后关系可再次计算并产生新的 observation。
- [ ] 平台无第二套 CRON/next-run 计算器。
- [ ] Java renderer、RELEASE_BUILD DAG 和 plan DAG 之间不存在第二份 dbt Docker runtime 实现。
