# Sprint-76 三个生产前门槛复审

**日期**：2026-07-27
**状态**：REVISE；生产启用 NO-GO
**范围**：只确认 dbt 运行凭据、统一 DAG 运行模板、生产权限与职责分离；本轮不编码。

## 结论摘要

| 门槛 | 当前结论 | 接受方向 | 未过前禁止 |
|---|---|---|---|
| PG-01 dbt 运行凭据 | REVISE / GAP | 复用 `InfraSecretService` 和数据源 secret；平台签发位于宿主机 tmpfs 的一次性 profile lease；Airflow 只按 leaseId 挂载当次只读文件 | 禁止生产构建、生产计划运行和 binding ACTIVE |
| PG-02 两类 DAG 共用执行模板 | GO WITH REVISIONS / GAP | `services/dts-airflow/extra` 中一个版本化 Python task factory；RELEASE_BUILD 与 OPERATIONAL DAG 都是薄定义；继续由 Airflow 通过 Docker socket 启动 ephemeral dbt | 禁止生成第二份 Bash/Python 执行模板，禁止新 canonical 路径继续使用 per-tag DAG |
| PG-03 生产权限、职责分离与审计 | DEPENDENCY_READY / LOCAL_INTEGRATION_PENDING | Sprint-36/F3 资产动作矩阵已交付；Sprint-76 仍需实现领域职责角色、Candidate action resolver 及发布/计划消费；两层都必须 fail-closed | 禁止 PROD 的 APPROVE/REJECT/PUBLISH/ROLLBACK/SCHEDULE_ENABLE，禁止宣称细粒度 RBAC 已完成 |

总体上，Airflow/dbt 主架构可以继续使用；生产上线不能通过。Sprint-76 保持 DRAFT，直到三项门槛均有实现和真实 IT 证据。

## PG-01：dbt 运行凭据

### 当前事实

1. `DbtTargetConnectionFactory` 已经通过 `InfraSecretService.readSecrets(...)` 在平台进程内解析数据源密码，可继续作为 target/secret owner。
2. `DbtConfigService.buildProfile(...)` 会把解密后的密码写入共享 `profiles.yml`；platform 与 Airflow 又通过宿主机目录共享该 profile。
3. 当前生成 DAG 直接执行 `-v "$DBT_PROFILES_DIR:/root/.dbt"`，因此共享明文 profile 是 canonical 运行依赖。
4. dts-platform 没有 Docker socket；Airflow scheduler/triggerer/webserver 有 Docker socket。让平台直接创建 dbt 容器不符合当前部署边界。
5. 当前内部服务认证已经支持 `X-DTS-Service + X-DTS-Service-Token`，但生成 DAG 的回写只有 service name；`ServiceDependencyAuthenticationFilter` 也没有允许 `dts-airflow` 访问 dbt 回写或计划运行端点。
6. 同步命令以 `|| true` 吞掉回写失败，可能造成 Airflow DagRun 成功但平台没有可信结果。
7. Docker socket 使 Airflow execution plane 具备宿主机高权限。tmpfs lease 只能防止静态落盘、误挂载和日志泄漏，不能抵御已攻陷的 Airflow scheduler 或 Docker daemon。

### 冻结方案

```text
InfraDataSource encrypted secret
  → dts-platform / InfraSecretService 进程内解密
  → DbtRuntimeProfileLeaseService
  → /run/dts-dbt-runtime/<leaseId>/profiles.yml
       容器内路径；底层必须 bind 到宿主机 tmpfs
  → Airflow 仅取得 leaseId、targetName、expiresAt、非敏感 checksum
  → Airflow task 由固定 host root + 严格 UUID leaseId 派生 mount source
  → docker run -v <tmpfs lease dir>:/run/dts-dbt-profile:ro ...
  → finally 调 authenticated release；TTL janitor 兜底
```

生产约束：

- 宿主机根目录固定为 tmpfs，例如 `/dev/shm/dts-dbt-runtime`；普通磁盘目录、Docker named volume 和共享 `services/dts-dbt/profiles` 均不合格。
- dts-platform 只看到容器内固定根目录，Airflow 只看到宿主机固定根目录；二者不得接受 API 返回的任意绝对路径。
- 启动时验证根目录位于 tmpfs、owner/mode 正确且不允许 symlink；失败即 readiness DOWN。
- leaseId 由服务端生成并绑定 `pipelineRunId + dagRunId + executionTargetKey + credentialVersionRef`；`CREATE_NEW`、目录 `0700`、文件 `0600`、短 TTL、单次消费。
- 内部 runtime API 不返回 profile 内容、host、username 或 password；业务数据库只存 lease 状态、非敏感 version/checksum 和过期时间。
- Airflow canonical task 不再读取 `DBT_PROFILES_DIR`，不在命令、环境变量、XCom、DagRun conf 或日志放入仓库凭据。
- P0 信任边界明确为“单一受信 Airflow 运维执行面”，禁止用户上传/编辑 dbt 生产 Python DAG；平台生成的 thin DAG 和版本化 task factory 是 dbt runtime 的唯一代码来源。
- Airflow → platform 的 prepare/open/sync/probe/finalize/release 全部使用 pairwise service token，端点同时校验 `ROLE_SERVICE_INTERNAL` 和 `authentication.name == 'service:dts-airflow'`。
- canonical 链路禁止 `|| true`；finalize 可以 `all_done` 收敛状态，但不得把上游失败改写为成功。
- 本地开发可使用 ignored fixture；任何 tracked profile 只能是无真实密码的模板，且 canonical 路径不得回退读取它。

### PASS 证据

- 宿主机与容器两侧都证明 profile 位于 tmpfs，权限为 `0700/0600`。
- build、失败、kill -9、Airflow 重启和平台重启后 lease 均释放或在 TTL 内清理。
- Git、DAG、DagRun conf、XCom、API body/response、业务 DB、审计、日志和 evidence 的 secret scan 为 0。
- 轮换数据源密码后 binding/dagId/model revision 不变，下一次运行使用新的非敏感 credential version。
- 删除 pairwise token、伪造 service header、篡改 leaseId/path、过期或重复消费均 fail-closed。
- Airflow UI/API 不能覆盖 runtime path、image、command 或 Python callable；非平台签名/受管 DAG 不得进入 dbt 生产 DAG namespace。

### 明确不承诺

当前 Docker socket + LocalExecutor 架构不提供敌对多租户隔离，也不能在 Airflow/Docker daemon 已失陷时保护 profile。若招标或客户要求“不信任调度器”“租户级容器隔离”或“无宿主机 Docker socket”，PG-01 直接 NO-GO，必须另立执行面改造（例如受控远程容器/Kubernetes executor），不能用本 Sprint 的 tmpfs lease 冒充满足。

## PG-02：RELEASE_BUILD 与 OPERATIONAL DAG 共用模板

### 当前事实

1. `DbtDagService.buildDagSource(...)` 在 Java text block 中生成完整 Bash，按 selector/tag 形成多个近似 DAG。
2. 当前 DAG 使用 `docker run` 启动 ephemeral dbt；这是已运行的现有计算方式，应继续复用。
3. 当前文件使用 `TRUNCATE_EXISTING`，DAG 没有显式 `max_active_runs=1`，同步采用 `all_done + || true`。
4. 所有 Airflow 组件已只读挂载 `services/dts-airflow/extra` 并设置 `PYTHONPATH=/opt/airflow/extra`，已有放置共享 Python runtime helper 的部署面。

### 冻结方案

只保留一份执行实现：

```text
services/dts-airflow/extra/dts_runtime/dbt_task_factory.py
  ├─ prepare_runtime
  ├─ run_dbt_container
  ├─ sync_manifest_and_probe_relation
  └─ finalize_run

RELEASE_BUILD DAG.py ─┐
                      ├─ import build_dbt_dag(...)
OPERATIONAL plan.py ──┘
```

- Python task factory 是 Docker 参数组装、内部服务认证、profile lease 挂载、失败传播和 finalize 的唯一实现。
- Java renderer 只输出薄 DAG 定义：`dagId/purpose/bindingId?/schedule/timezone/templateVersion/deploymentChecksum/tags`；不得输出第二份 shell runtime。
- Docker 调用使用参数数组和 `subprocess.run(..., check=True)`，禁止 `shell=True`、任意 host path 和包含敏感值的命令回显。
- 两类 DAG 只在 runtime-open 策略不同：
  - RELEASE_BUILD 和手工 OPERATIONAL_RUN 使用平台已持久化的 deterministic run；
  - CRON OPERATIONAL_RUN 在 `prepare_runtime` 内按 `bindingId + dagRunId + logicalDate` 幂等 open。
- 共用后续 build/sync/probe/finalize；dbt manifest 继续拥有模型级依赖图。
- 两类 DAG 均 `max_active_runs=1`；数据库 active-run 唯一约束是第二道并发兜底。
- thin DAG 使用同目录临时文件、fsync、atomic move；Airflow registration/parse/templateVersion/deploymentChecksum/effective schedule 对账完成后才 ACTIVE。
- canonical RELEASE_BUILD 使用稳定的 platform-env+target executor DAG。现有 per-tag DAG 在兼容期只读保留；旧入口迁到 gateway 后 pause，Sprint-76 不直接删除历史 DAG 文件。

### PASS 证据

- RELEASE_BUILD 与 plan DAG 源码均只 import 同一个 task factory；静态检查确认没有第二个 `docker run`/runtime builder。
- templateVersion 变化可原子部署两类 DAG；任一 DAG parse/checksum 不一致都不会 ACTIVE。
- dbt、internal prepare、sync、probe 或 finalize 回写失败时，DagRun 和 pipeline run 都不可能显示 SUCCESS。
- 100-entry candidate 仍只有一个 release DagRun；连续发布/改 CRON 不产生 per-model/per-release/per-cron DAG。
- legacy per-tag 入口迁移后触发数为 0，并有 pause/rollback 清单。

## PG-03：生产权限、职责分离与审计

### 当前事实

1. `ModelReleaseCandidateResource` 所有读写入口统一使用 `CATALOG_MAINTAINERS`。
2. `ModelSpecPlanWriteAccessAdapter.canMaintain(...)` 只允许 plan owner。
3. `ModelReleaseCandidateApplicationService` 以固定 `MODEL_MAINTAINER` 计算 `allowedActions`，尚无 authenticated authority → `DeliveryActorRole` resolver。
4. Sprint-36/F3 已 DONE：`AssetAction`、`IamAssetActionPolicy`/request、migration、审批 API、矩阵 UI 和 `AccessChecker.canPerform(...)` 已落地并通过 90 个后端聚焦测试；当前缺口已转为 Sprint-76 Candidate 发布/计划链尚未消费该端口。
5. M05 资产动作矩阵与 Candidate 的审核/发布职责不是同一件事：只完成其中一个都不能证明生产发布安全。

### 冻结方案

生产授权采用两层并集，任一层拒绝即拒绝：

1. **Sprint-76 领域职责层**
   - 在现有 Keycloak realm role/管理员分配链中登记专用 authority：`ROLE_MODEL_MAINTAINER`、`ROLE_MODEL_RELEASE_REVIEWER`、`ROLE_MODEL_RELEASE_OPERATOR`；服务端分别解析为 `MODEL_MAINTAINER`、`RELEASE_REVIEWER`、`RELEASE_OPERATOR`。
   - 不把 `CATALOG_MAINTAINERS`、`ROLE_ADMIN`、`ROLE_SYS_ADMIN`、`ROLE_AUTH_ADMIN` 或 `ROLE_SECURITY_AUDITOR` 静默映射为三类业务职责；授权管理员负责分配角色，不因此取得发布动作。
   - `START_BUILD/RETRY/RUN_QUALITY/SUBMIT_REVIEW/CANCEL` 归 maintainer。
   - `APPROVE/REJECT` 归 reviewer。
   - `PUBLISH/ROLLBACK/REGISTRATION_RETRY/SCHEDULE_ENABLE/SCHEDULE_DISABLE` 归 operator。
   - actor separation 以 immutable command ledger 校验；请求 body/header 不能声明角色。
   - `allowedActions` 与实际 command authorization 调用同一个 resolver，不能只做 UI 隐藏。
2. **Sprint-36/F3 资产动作层**
   - 发布创建或更新 CatalogDataset/字段/血缘时接入 `AccessChecker.canPerform(...)` 的 deny-by-default policy。
   - Sprint-76 不复制 `IamAssetActionPolicy`、不自建另一套资产权限表。
   - Sprint-36/F3 依赖已就绪；Sprint-76 在发布/计划链实际消费该端口并通过 IT-14 前，只能在隔离 DEV/TEST 验证构建链，不能打开 PROD 发布和调度。

审计动作必须在 dts-admin 字典中预注册，并记录 actor、resolved duty role、tenant/plan/candidate/model revision、binding/run id、reason、decision 和拒绝码。安全审计员只能读取证据，不能因审计角色获得发布动作。

### PASS 证据

- 三类真实测试账号分别只看到并只能执行自己的动作；同一 actor 提交后无法批准或发布。
- Keycloak/管理员端可分配并审计三类专用 authority；移除 authority 后异步 command/reconciler 重新鉴权并停止。
- 直接调用 API、篡改 role/action、跨租户、owner 伪造、撤销权限后的异步继续推进均被拒绝。
- Sprint-36/F3 为 DONE，相关 domain/migration/API/IT 真实存在；发布注册链实际调用 `canPerform(...)`。
- 所有 mutation 审计分类命中，未分类动作数为 0；拒绝同样留痕且不包含 secret。

## 进入实施与生产的分层条件

| 阶段 | 条件 |
|---|---|
| Sprint-76 从 DRAFT 进入 READY | 本文方案经最终复审接受；对应 Feature/Task/IT 全部回写；PG-03 明确记录为外部依赖已就绪、Sprint-76 本地双门接入待完成 |
| 开始 F1～F3 的 DEV/TEST 实现 | F0 GO；GitNexus impact 完成；不启用 PROD target、publish 或 CRON |
| 开始 F4/F7 生产路径实现 | Sprint-36/F3 的实际接口与迁移已存在并完成契约复核；domain duty role mapping 已冻结 |
| 灰度生产 | PG-01、PG-02、PG-03 全部 PASS；Gate G3/G4、IT-14/19/20 和 rollback 演练 PASS |

任何“目录权限看起来正确”“DAG 能跑一次”“页面按钮已隐藏”“同一人测试时没有点批准”都不能替代上述生产证据。
