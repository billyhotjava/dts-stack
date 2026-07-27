# T04：收敛 dbt 运行凭据与单目标能力边界

**优先级**：P0
**状态**：IN_PROGRESS
**依赖**：T01

## 目标

移除当前 tracked `profiles.yml` 明文凭据，在不改写现有 Airflow + Docker dbt 主架构的前提下，为 RELEASE_BUILD 和 OPERATIONAL_RUN 建立同一安全 target 解析方式；P0 只声明一个真实验收 target，不虚构多目标能力。

## 技术设计（Contract-first）

- **事实边界**：当前 profile 只有一个可用 Postgres output；P0 `executionTargetKey` 只映射该验收 target，其他 target 返回 `MODEL_EXECUTION_TARGET_UNAVAILABLE`。
- **secret owner**：复用平台既有数据源 secrets；凭据继续加密保存并只在 dts-platform 进程内解密，不复制到 Airflow metadata DB。不得继续位于 Git、DAG 目录、scoped project 或业务明文字段。
- **target resolver**：平台将非敏感 `executionTargetKey` 解析到受租户/环境约束的数据源和当前 dbt target；禁止请求直接提交 adapter/host/user/password。
- **profile lease 根目录**：新增窄职责 `DbtRuntimeProfileLeaseService`。生产只允许写入绑定到宿主机 tmpfs 的固定容器目录，例如容器 `/run/dts-dbt-runtime` ↔ host `/dev/shm/dts-dbt-runtime`；普通磁盘、Docker named volume、共享 `services/dts-dbt/profiles` 均 fail-closed。
- **启动校验**：production readiness 校验 filesystem type=tmpfs、根目录 owner/mode、无 symlink、不可被其他 uid 遍历；任一不满足则 dbt runtime readiness DOWN，binding 不得 ACTIVE。
- **lease 身份**：平台生成严格 UUID leaseId，绑定 `pipelineRunId + dagRunId + executionTargetKey + credentialVersionRef`，以 `CREATE_NEW` 写 `<leaseId>/profiles.yml`；目录 `0700`、文件 `0600`、短 TTL、单次消费。
- **路径边界**：内部 API 只返回 `profileLeaseId,targetName,expiresAt,credentialVersionRef`，不返回 profile 内容或任意绝对路径。Airflow task 只能从配置的固定 host root + 校验后的 leaseId 派生 mount source，并只读挂载给当次 ephemeral dbt container。
- **清理**：Airflow task 在 `finally` 通过 authenticated internal API release；平台 TTL janitor 兜底删除并告警。kill -9、Airflow/platform 重启和重复 release 都必须幂等清理。
- **服务身份**：prepare/open/sync/probe/finalize/release 统一使用 pairwise `X-DTS-Service: dts-airflow` + `X-DTS-Service-Token`；端点同时要求 `ROLE_SERVICE_INTERNAL` 和 `authentication.name == 'service:dts-airflow'`，并进入精确 path allowlist。
- **日志与接口**：DAG source、DagRun conf、XCom、环境变量、platform API body/response、业务数据库、audit、evidence 和 task logs 中 warehouse credential 出现次数为 0；Docker 命令只记录脱敏后的镜像、purpose、run id 和 selector checksum。
- **轮换**：数据源 secret version 变化不改 binding/dagId/model revision；下一次 lease 使用新版本，并记录非敏感 credentialVersionRef/checksum 用于对账。
- **不可用策略**：数据源 secret 不存在/不可解密、target key 不匹配、runtime root 非 tmpfs/不可写、service token 缺失、lease 过期/重复消费或 profile 生成失败时 run BLOCKED/FAILED；binding 不得 ACTIVE，且绝不回退共享 tracked profile。
- **扩展边界**：保留 executionTargetKey 是未来拆分 binding 的兼容点；多 target、跨 target ref/meta-DAG 不在 P0 完成声明。
- **开发/测试**：本地 fixture credential 通过 ignored/runtime secret 注入；文档提供占位值，不提交真实密码。

## 影响范围

- existing data-source secret resolver
- tmpfs-backed runtime profile lease/cleanup/readiness
- dbt runtime task helper/profile mounting
- platform inbound service-auth allowlist + Airflow pairwise token configuration
- compose/deployment documentation
- secret scan/security tests

## 当前进展（2026-07-28）

- canonical RELEASE_BUILD 已使用 `DbtTargetConnectionFactory` →
  `DbtRuntimeProfileLeaseService` → host tmpfs lease；Airflow 只消费
  leaseId，并从固定 root 派生只读 mount。
- `init.sh`、app/dev/legacy compose、启动 preflight 与 readiness 已统一
  tmpfs、owner、0700/0600、symlink 和固定路径 fail-closed 约束。
- Airflow → platform 已使用 pairwise token、精确 principal/method/path
  allowlist；runtime spec、lease consume/release、sync-probe/finalize 不再
  依赖 header-only 身份。
- tracked `profiles.yml` 已删除；本地 fixture 被 Git 与 Docker context
  忽略，部署打包函数强制剔除；原 profile 已知凭据值在当前 tracked
  工作树中的出现次数为 0。
- 尚未关闭：OPERATIONAL_RUN 复用同一 resolver、真实 Airflow 场景中的
  kill/restart/TTL/rotation/secret scan，以及缺失 token 的真实 HTTP
  401/403 证据。

## 验证（RED→GREEN）

- [x] tracked profile secret RED scan 可复现，迁移后已知值扫描为 0。
- [ ] RELEASE_BUILD/OPERATIONAL_RUN 使用同一 executionTargetKey/tmpfs profile lease resolver。
- [ ] DagRun conf/XCom/API/DB/audit/evidence/task logs warehouse secret scan为 0。
- [ ] target key 注入、未知 target、secret missing/decrypt denied、伪造 service header/token 均 fail-closed。
- [x] production 非 tmpfs root 启动失败；tmpfs root 0700、profile 0600、固定路径派生、只读 mount、release/TTL 清理测试通过。
- [x] path traversal、symlink、任意 mount path、过期/重复 lease 消费均被拒绝。
- [ ] prepare/open/sync/probe/finalize/release 缺 pairwise token 时返回 401/403，且不会执行 dbt 或伪装成功。
- [ ] credential rotation 后 dagId/binding/model revision 不变，新任务成功。
- [x] 本地/CI 安全 fixture 与生产数据源 secret 分离。

## Definition of Done

- [x] 原 tracked dbt profile 与其已知凭据值已从当前 tracked 工作树清除。
- [ ] 运行凭据不经过用户请求、API payload、环境变量和业务持久化，只短暂存在于平台进程与宿主机 tmpfs lease。
- [ ] P0 单目标能力有真实证据，未支持目标不显示为可选。
- [ ] 数据源 secret/lease/service auth 不可用时明确阻断，绝不回退到 tracked plaintext。
