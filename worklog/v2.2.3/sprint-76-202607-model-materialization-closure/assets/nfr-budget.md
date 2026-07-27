# 非功能预算（Gate G1）

**依据**：`assets/domain-profile.md` + Sprint-69/72 发布、权限与密级约束
**适用范围**：ReleaseCandidate 构建、Build/Publish Intent、pipeline run、dbt bundle、plan execution binding、Airflow DAG、relation probe、资产注册和相关 UI

| 维度 | 预算 | 可执行适应度函数 | 归属 Task | 状态 |
|---|---|---|---|---|
| 候选规模 | 单 candidate ≤100 entries | 101 条 scope 的 contract test 返回 422 | F2/T01 | PLANNED |
| artifact 大小 | 单文件 ≤5 MiB；单 candidate bundle ≤100 MiB | bundle builder 边界测试 | F1/T03 | PLANNED |
| 幂等 | 同 candidate+version+attempt 只有一个确定性 dagRunId；HTTP timeout 后 0 次重复外部执行 | 并发/timeout IT 断言 Airflow run id 唯一 | F2/T02～T03 | PLANNED |
| 并发 | 同 model+implementation+environment 同时最多一个 active run | PostgreSQL 唯一部分索引 + 并发 IT | F2/T01 | PLANNED |
| 快捷入口 | 同 ModelSpec+plan+environment 的重复 Build Intent 返回同一 SINGLE candidate/run；BATCH 冲突 0 次自动合并 | facade 并发/重放/冲突 IT | F2/T01、F5/T01 | PLANNED |
| 活动占用 | tenant+environment+modelSpec 同时最多一个 active claim | 数据库唯一约束 + 100 并发请求 IT | F2/T01 | PLANNED |
| 服务端派生 | 客户端提交 selector/dagId/projectDir/target/profile/revision/checksum 0 次生效 | negative contract/security tests | F2/T01、F6/T01 | PLANNED |
| 执行目标 | P0 只接受唯一已配置 executionTargetKey；其他 target 100% fail-closed | target injection/missing secret contract test | F2/T01、T04 | PLANNED |
| 漂移 | 任一 revision/checksum 变化后旧证据 0 次可发布 | drift contract/IT | F2/T03、F4/T01～T02 | PLANNED |
| Publish Intent 边界 | 一次请求最多自动执行 RUN_QUALITY 与 SUBMIT_REVIEW；APPROVE/PUBLISH 自动执行次数=0 | command ledger/transition contract test | F4/T01 | PLANNED |
| 发布原子可见性 | ≤100-entry candidate 的 mandatory local failure 后可消费 entry=0；成功后一次性全部可见 | transaction/fault-injection IT | F4/T02～T03 | PLANNED |
| 外部同步隔离 | OpenMetadata/BI 故障时 Candidate/ModelSpec 保持 PUBLISHED，syncHealth=DEGRADED | outbox fault-injection IT | F4/T03 | PLANNED |
| 上线投影 | PUBLISHED 但 binding 非 ACTIVE 或 relation unhealthy 时 onlineReadiness READY 次数=0 | projection matrix test | F5/T03、F7/T01～T03 | PLANNED |
| 调度超时 | Airflow connect ≤5s，read ≤15s；未确认结果保持 UNKNOWN/RUNNING | 配置断言 + 故障注入 | F2/T02 | PLANNED |
| 构建超时 | 默认 30 分钟，可按环境配置；超时不得自动判失败或成功，进入可恢复 UNKNOWN | clock-based service test | F2/T02 | PLANNED |
| 状态恢复 | 服务重启后 60s 内重新认领 QUEUED/RUNNING job | restart IT + polling assertion | F2/T02 | PLANNED |
| DAG 数量 | 每 platform-env+target 一个 RELEASE_BUILD DAG；每 tenant+plan+environment+target 一个 plan DAG；不得按 model/candidate/release/schedule 生成 | 100-entry/cron-change IT 断言 dagId 稳定 | F2/T02、F7/T02 | PLANNED |
| DAG runtime 单一性 | RELEASE_BUILD 与 plan DAG 只 import 一个版本化 Python task factory；canonical Docker dbt runtime builder 数=1 | source-contract/static scan + 两类 DAG parse IT | F2/T02、F7/T02 | PLANNED |
| DAG 文件安全 | scheduler 3s 扫描期间 partial/truncated parse 次数=0 | concurrent atomic-write/parse IT | F7/T02 | PLANNED |
| DAG 注册 | 文件提交后 30s 内完成 registration/parse/checksum 对账；超时 binding 不得 ACTIVE | live Airflow polling IT | F7/T02 | PLANNED |
| 调度精度 | CRON 触发偏差 ≤60s；IANA timezone 显式且 Airflow actual nextRun 正确；重复调度不重复计算 | UTC/CST boundary + Airflow IT | F7/T01～T03 | PLANNED |
| 生产运行并发 | 同 binding 同时最多一个 active OPERATIONAL_RUN；碰撞记录 SKIPPED_CONCURRENT | 唯一索引 + manual/cron race IT | F7/T02 | PLANNED |
| CRON 落账 | 每个非 skipped scheduled DagRun 在首任务完成后恰有一个 OPERATIONAL_RUN；孤儿业务运行和重复 run 均为 0 | scheduled-open idempotency/restart IT | F7/T01～T02 | PLANNED |
| 调度漂移 | release/scope/version 变化后旧 binding 0 次可触发 | stale binding contract/IT | F7/T01～T02 | PLANNED |
| 计划范围 | 连续发布 N 个模型后 binding entry 精确等于 plan 下全部 current PUBLISHED 模型 | aggregate-scope PostgreSQL IT | F7/T01 | PLANNED |
| relation probe | 每 relation ≤10s；candidate ≤60s；默认不做 count(*) | timeout test + 100 entry bounded probe test | F3/T01 | PLANNED |
| 查询安全 | locator 只接受 manifest/system identifier；禁止拼接用户 SQL | 非法 identifier contract test | F3/T01 | PLANNED |
| 证据新鲜度 | observation 必须晚于当前 build startedAt，且绑定同 invocation | repository constraint/service test | F3/T02 | PLANNED |
| 数据库索引 | run 按 tenant/candidate/status 或 tenant/binding/status；observation 按 tenant/model/current 查询必须走索引 | Liquibase IT + `EXPLAIN` | F2/T01、F3/T02、F7/T01 | PLANNED |
| 事务边界 | candidate BUILDING 与 QUEUED rows 同事务；外部 submit 不在 DB 事务内 | rollback/integration test | F2/T01 | PLANNED |
| 失败模式 | Airflow、dbt、probe、mandatory Catalog 任一步失败均保留可修复状态和错误码；external registry 只降级 sync health | 故障矩阵 IT | F2～F4 | PLANNED |
| 审计 | START_BUILD/RETRY/PUBLICATION_REQUESTED/SUBMIT_REVIEW/APPROVE/REJECT/PUBLISH/REGISTER/SCHEDULE_CHANGE/OPERATIONAL_RUN 均有 actor、plan/candidate、model revision、run id | 审计 IT，禁止“未分类” | F4/T01～T02、F7/T01 | PLANNED |
| 权限/租户 | 三个专用 Keycloak authority 由同一 domain duty resolver 供 command authorize/allowedActions 使用；catalog/admin/auth-admin/auditor 隐式提升=0；同人审核/发布=0；auditor mutation=0；Sprint-36/F3 policy 缺失/DENY/过期时 PROD 资产 mutation=0；跨租户 404/403；客户端 role 0 次生效 | 三类真实账号 + M05 policy security IT | F4/T01～T02、F6/T01 | DEPENDENCY_READY / IT_PENDING |
| 凭据 | warehouse secret 在 Git/DAG/DagRun conf/XCom/API/DB/env/log/evidence 中出现数=0；profile 只存在于 host tmpfs lease，root=0700/file=0600，任务后删除 | secret scan + filesystem/permission/cleanup/rotation IT | F2/T04、F6/T01～T03 | PLANNED |
| Airflow 服务身份 | prepare/open/sync/probe/finalize/release 的 header-only 放行数=0；缺失/伪造 pairwise token 放行数=0；非 allowlist path 放行数=0 | service-auth filter contract + live negative IT | F2/T02/T04、F7/T02 | PLANNED |
| Airflow 信任边界 | dbt 生产 DAG namespace 中非平台受管 Python DAG=0；UI/API 可覆盖 runtime path/image/command/callable 次数=0；不宣称 Docker socket 下的敌对多租户隔离 | managed-DAG inventory + negative API/source-contract IT | F2/T02、F6/T03、F7/T02 | PLANNED |
| 兼容性 | Chrome95；现有 validate `valid/code` 字段和 dbt release 单模型请求保持兼容 | source-contract + Chrome95 | F1/T01、F5 | PLANNED |
| 可观测性 | build 可用 candidateId→pipelineRunId→dagRunId→invocationId→observationId 串联；生产运行可用 bindingId→pipelineRunId 串联 | runbook smoke | F2/T02、F3/T02、F7/T02 | PLANNED |
| 高可用 | N/A：当前部署为单点，Sprint-76 不承诺 HA | 不适用，继承现有平台边界 | - | N/A |

## 未达标项处置

| 缺口 | 影响 | 处置 | Task |
|---|---|---|---|
| 生产 candidate 规模未画像 | 上限可能过紧/过松 | F0 基线补实测后只允许调预算，不取消硬上限 | F0/T01 |
| 非 PostgreSQL probe 未实测 | adapter 支持声明 | capability=false，返回稳定修复码 | F3/T03 |
| 当前 tracked/shared dbt profile credential | 生产密钥泄露与虚假多目标能力 | F2/T04 复用数据源 secrets 并签发 host tmpfs-backed task-scoped profile lease；canonical 链退出共享 profiles；完成前全链 No-Go | F2/T04 |
| Sprint-36/F3 已 DONE，但 Candidate 发布/计划链尚未消费 `canPerform` | PROD 发布仍缺少本地双门禁闭环 | Sprint-76 只消费其 policy port，不复制权限表；完成 duty resolver、发布/计划接入和 IT-14 后再解除生产 NO-GO | F4/T01～T02、F6/T01 |
| 自动物理清理未设计 | 废弃关系容量 | runbook 只记录和告警，不自动 DROP | F6/T03 |

DoD 时逐条回跑适应度函数；没有命令或测试证据的预算不得改为 PASS。
