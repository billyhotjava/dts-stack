# Sprint-81 集成验收计划

**状态**：PLANNED
**执行纪律**：全部编码、迁移和物理删除完成后，再集中执行最终 E2E。过程中的单测、ArchUnit、Testcontainers、build 只作为 Task 证据，不替代 IT-08。

| ID | 场景 | 验收路径 | 关联 Task | 当前状态 |
|---|---|---|---|---|
| IT-01 | 模块化控制面 | ArchUnit → Spring context → 禁止跨域 repository/entity → 依赖无环 | F1/T01～T03 | PLANNED |
| IT-02 | 唯一建模状态链 | WarehousePlan → ModelSpec revision → StageGate → Lifecycle → ReleaseCandidate → Materialization | F2/T01～T03 | PLANNED |
| IT-03 | Catalog 与质量证据 | Integration identity → CatalogAssetKey → rule/version/binding/run → StageGate evidence | F3/T01～T02 | PLANNED |
| IT-04 | durable event/audit | 业务事务 → 两张 outbox → 故障/恢复/重放 → dts-admin 中央审计分类 | F3/T03～T04 | PLANNED |
| IT-05 | dbt 执行网关 | Candidate → gateway submit/query/cancel → Airflow DagRun → dbt result/relation 对账 | F4/T01～T03 | PLANNED |
| IT-06 | 旧运行面物理退役 | consumer=0 → 旧 route 普通 404 → bean/entity/repository 不存在 → table absent | F5/T01～T04 | PLANNED |
| IT-07 | 升级与恢复 | 空库升级 + 存量库升级 + manifest apply/verify + backup restore + forward recovery | F0/T03、F6/T02 | PLANNED |
| IT-08 | 最终认证 E2E | 新 UI → canonical API → quality → lifecycle/candidate → Airflow/dbt → relation/Catalog → dts-admin audit | F6/T03 | DEFERRED_UNTIL_CODE_COMPLETE |

## IT-01：模块化控制面

- dependency graph 无环，模块间仅通过公开 port。
- `integration` 不 import modeling repository；quality 不写 lifecycle；modeling 不 import Airflow client。
- Catalog、quality、AuditService 既有 owner 被复用，Spring context 中无重复 bean owner。

## IT-02：唯一建模状态链

- 创建/更新明确 revision，依赖全部 pin；并发 CAS 无 lost update。
- quality/catalog/permission/artifact 证据不足时 StageGate fail-closed。
- 生命周期不能被 resource 或 repository 绕过；candidate/version/attempt 唯一。
- Materialization 只接受已授权、证据完整的 candidate。

## IT-03：跨域身份与质量

- integration 提交外部 identity 后得到唯一 CatalogAssetKey；重放不产生重复资产。
- ruleVersion/binding/run 与相同资产键匹配；非终态、过期、失败、跨租户或 latest 漂移全部阻断。
- quality run 使用隔离测试数据，结束后精确清理，不代写客户业务事实。

## IT-04：两类 outbox 与中央审计

- 业务事务失败时两类 outbox 均不残留；业务提交成功时相应记录必存在。
- dts-admin 停止 30 秒期间审计不丢；恢复后按预算清空，中央记录按 event/audit id 幂等。
- domain event 和 audit event 分表、分 dispatcher、分 backlog/DLQ 指标。
- 审计 action code 分类正确，actor/tenant/client IP/correlationId 完整，payload 无 secret。

## IT-05：执行网关

- 同 candidate/version/attempt/idempotencyKey 重放返回同一 execution handle。
- Airflow timeout/5xx 可重试，4xx 不重试；UNKNOWN 必须先对账。
- 旧 attempt 回执不能推进新 attempt；取消使用 expected state。
- dbt 成功但 relation 不存在时 Materialization 不成功、不登记 Catalog 资产。

## IT-06：物理退役

- `/api/semantic/**`、旧 plan、`/api/modeling/vnext/**`、旧 `/etl/dbt/run` 的目标运行面没有长期 410/redirect/shim。
- old route 返回统一 404；OpenAPI、前端 client、内部作业和 source contract 均无引用。
- legacy resource/service/entity/repository/table/index/FK 均不存在；canonical 表完整。
- `modeling_legacy_object_migration_batch`、`modeling_legacy_object_migration`、`modeling_legacy_api_usage` 最后删除，归档证据可恢复。
- dts-admin 中央审计历史和 `DATABASECHANGELOG` checksum 前后不变。

## IT-07：迁移与恢复

- 空库从 master changelog 建成目标 schema。
- 具有代表存量的旧版本库升级到目标 schema，manifest 无 conflict/orphan。
- dry-run 零写入；重复 apply 幂等；verify 比较 count/key/revision/checksum/asset/audit。
- drop 后从备份恢复到隔离库，RPO=0，记录实测 RTO；forward recovery 不改历史 changelog。

## IT-08：最终认证 E2E

只在 F1～F5 全部编码和定向验证完成后执行：

1. 一次性认证身份进入 Sprint-80 `/data-modeling/**`。
2. 选择 WarehousePlan，创建/更新 ModelSpec v2 revision。
3. 绑定统一 CatalogAssetKey 和 quality rule/version/binding，运行质量任务。
4. StageGate 读取真实 run evidence，Lifecycle 进入 release-ready。
5. 创建 ReleaseCandidate，完成职责分离的审核/发布命令。
6. Materialization 经 DbtExecutionGateway 触发 Airflow/dbt。
7. 核验真实 relation、artifact、Catalog 资产/lineage 与 pipeline 状态。
8. 在 dts-admin 核验分类正确、无重复、字段完整的中央审计。
9. 验证旧 URL 为普通 404、旧表不存在、outbox backlog 为 0。
10. 删除一次性身份、隔离模型/质量运行/目标 relation，断言无凭据或业务数据残留。

## 证据规范

每个 `IT-xx` 目录至少包含：

- `commands.md`：命令、时间、exit code、环境/数据库标识、commit；
- `result.md`：PASS/FAIL、断言、遗留风险、回滚结果；
- 涉及迁移时附脱敏 manifest 摘要和 backup SHA-256；
- 涉及 API/DB/UI 时附脱敏响应、SQL 断言或截图。

`PENDING`/`PLANNED` 不放占位截图，不写“应该通过”。任何 token、口令、dbt profile、数据源 secret、客户原始行不得进入证据目录。
