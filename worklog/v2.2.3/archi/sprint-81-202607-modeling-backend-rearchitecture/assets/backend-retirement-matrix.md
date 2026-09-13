# 建模后台退役矩阵

**状态**：SOURCE_COMPLETE / CUSTOMER_ENVIRONMENT_GATE
**总原则**：每个批次都在同一 Sprint 内完成“重接调用方 → dry-run/备份 → 迁移或证明零数据 → 停机复核 → 物理删除”。未满足条件时停止删除，不保留长期 410、tombstone、双写、隐藏 feature flag 或旧 entity 只读壳。

## 1. 全局删除门禁

| Gate | 必须满足 | 可执行证据 |
|---|---|---|
| R0 调用方 | 前端、后端、作业、测试、配置、OpenAPI 和外部清单中旧契约消费者为 0 | GitNexus upstream impact + `rg` 精确清单 + runtime route metrics/客户签字 |
| R1 数据 | 每个 tenant 的源记录为 0，或每条源记录都有唯一 target revision/运行映射 | dry-run manifest：source/target/migrated/conflict/orphan 数量与 checksum |
| R2 备份 | 待删表 schema+data 已导出，URI、size、SHA-256、数据库 ID、时间、操作者齐全 | restore 到隔离库并复跑 count/hash |
| R3 停机 | 维护窗口内冻结旧写入，再跑一次 R0/R1；结果未漂移 | write barrier 日志 + pre-drop manifest checksum |
| R4 回滚 | 回滚定义为恢复备份 + 回切当前 release，不依赖已删除的旧代码长期在线 | staging 演练记录、RTO/RPO 实测 |
| R5 物理删除 | resource/service/entity/repository/mapper/table/index/FK/seed/test fixture 全部删除 | build、context load、schema absent、route 404 |
| R6 保留 | dts-admin 中央审计历史和全部历史 Liquibase changelog 不变 | audit count/checksum + Liquibase checksum |

## 2. 批次 A：重接后立即物理删除

“立即”表示不等待长期观测版本，但仍须在本 Sprint 当前批次满足 R0～R6。

| 目标 | 当前本地 | canonical 替代 | 同批重接 | 删除内容 | 放行条件 |
|---|---:|---|---|---|---|
| 旧 `/api/semantic/**` HTTP 面 | semantic 数据 0 | ModelSpec v2、治理指标 owner、canonical stage/lifecycle | 新前端/API client/内部测试改 canonical；外部调用清单确认 | resource、route contract、只服务旧面的 facade/service、OpenAPI/test | consumer=0；涉及表按批次 C 另判，不因 route 删除自动 DROP |
| 旧 `modeling_plan` HTTP/service/entity/repository/table 面 | 0 | `WarehousePlan` `/api/modeling/warehouse-plans/**` | planId/版本/CAS/tenant 语义映射；新前端只读写 WarehousePlan | route、DTO、service、entity、repository、表/FK | 每个旧 plan 为 0 或已精确迁移到唯一 WarehousePlan；备份恢复通过 |
| `/api/modeling/vnext/**` HTTP shell | vNext runtime 数据 0 | canonical ModelSpec/StageGate/Lifecycle/ReleaseCandidate | 替换所有 webapp、测试和内部调用 | resource、HTTP DTO/mapper、route test | vNext HTTP consumer=0；vNext service/contract 若仍被内部消费，移入批次 B |

删除后的 URL 进入统一普通 404；不得新增 410 handler、redirect controller 或兼容实体。

### 2.1 当前批次执行记录（2026-07-31）

| 项目 | 当前结果 | 数据保护 |
|---|---|---|
| semantic HTTP/service/repository 运行面 | 源码已物理删除；Catalog identity 查询已改到 canonical ModelSpec/指标定义 | forward changeset 在 11 张 semantic 表任一非空时 `HALT` |
| vNext HTTP/runtime shell | resource、runtime submission、Addax/Airflow 旧 gateway 和测试已物理删除 | canonical compiler、ModelSpec、Candidate、materialization 主链保留 |
| legacy object migration runtime | resource/service/planner 与运行测试已删除 | 3 张 ledger 任一非空时 changeset `HALT`；历史 changelog 不改写 |
| legacy lifecycle release compatibility | reviews/publish/retry/rollback route、adapter、旧 registration port/ledger 已删除 | canonical ReleaseCandidate publication/retry/rollback 保留；旧路由测试断言普通 404 |
| 零消费者策略/契约 | `ModelingApiContract`、`BusinessObjectRetirementPolicy`、`ModelingDriftGate`、`ModelingDomainValidator` 已删除 | GitNexus impact 为 LOW，当前源码零生产消费者 |
| 旧 SQL-model 前端尾巴 | `/api/modeling/sql-models` client 与 ETL consumer 已删除；ETL 只读取非 ARCHIVED 的 canonical ModelSpec `implementationPolicy.physicalName` | source contract 断言旧 endpoint/function/consumer 为 0；生产构建通过 |
| ModelSpec v1 schema | `object_id/process_id/spec_json` 等旧列与 business-object/standard-binding owner 已由 forward-only changeset 删除 | E2E 夹具同步到 `plan_id/domain_id/current_checksum/snapshot_json/content_checksum`，避免测试继续暗养旧 schema |

当前环境只读复核：semantic 与 migration ledger 均为 0；`modeling_model_registration_step=0`；ModelSpec 仅 `contract_version=2`。这些事实只决定当前开发环境的 changeset 可放行，不替代客户环境升级前画像与备份。

## 3. 批次 B：先解耦，再物理删除

| 目标 | 风险/现状 | 解耦目标 | 数据迁移/备份 | 删除完成定义 |
|---|---|---|---|---|
| `modeling_sql_model` | 本地 0；可能仍被 import/project/dbt 路径引用，客户未知 | 逻辑语义迁入 ModelSpec revision；实现/artifact 进入 canonical implementation/materialization；资产引用转 CatalogAssetKey | 每条 SQL model 固定 source checksum、target modelSpecId+revision、implementation/artifact refs；冲突不自动覆盖 | 所有 caller 重接；entity/repository/service/table/FK 删除；dbt project 仍可由 canonical artifact 重建 |
| `modeling_business_object` | 本地 0；可能仍承载名称、域、过程或引用 | 规划归 WarehousePlan/domain/process；逻辑字段归 ModelSpec revision；词汇归 glossary；禁止整体复制成新 owner | 字段级 classification：MIGRATE/MAP/IGNORE/CONFLICT；每条 target ref 可追溯 | business object resource/service/entity/repository/table 与 objectId 写路径删除 |
| vNext service/contract | HTTP shell 可先删，但内部 facade/compiler/test 可能引用 | 调用 canonical application ports；复用 ModelSpec/StageGate/Lifecycle/Candidate DTO | 无独立业务数据时仍需 caller manifest；有投影数据按 target revision 对账 | vNext package/service/contract/mapper/bean/test fixture 物理删除，无 shim interface |
| 旧 `/etl/dbt/run` | 可能被建模页、ETL console、内部 job 复用 | 建模发布统一经 `DbtExecutionGateway`；非建模 ETL 若仍需要执行，使用明确的 ETL owner，不能反向成为 modeling dependency | 运行中任务先完成/取消并对账；旧 run 行映射 canonical execution/candidate 或纳入备份 | modeling 调用为 0；旧 route/service 删除；无“兼容委托到 gateway”的长期 controller |

## 4. 批次 C：旧领域与运行表

以下表族必须由 F0/T02 生成环境级精确 manifest；表名以实际 schema 为准，不能用通配 DROP。

| 表族 | 当前本地 | 必要映射 | DROP 条件 |
|---|---:|---|---|
| legacy semantic head/revision/relation | 0 | ModelSpec/indicator/catalog refs | source=0 或逐对象 target revision+checksum 完整；所有 FK/consumer=0 |
| `modeling_sql_model*` | 0 | ModelSpec revision + implementation/artifact | 批次 B 完成，备份恢复通过 |
| `modeling_business_object*` | 0 | WarehousePlan/domain/process/glossary/ModelSpec | 分类无 CONFLICT，objectId consumer=0 |
| old `modeling_plan*` | 0 | WarehousePlan | old→new 唯一映射；版本/CAS/tenant 保真 |
| old run/candidate/materialization | 0 | ReleaseCandidate + canonical pipeline/materialization execution | 不存在 RUNNING/UNKNOWN；回执、artifact、relation evidence 已映射或备份 |
| vNext projection/cache tables（如存在） | 0 | canonical query projection | 可重建且 consumer=0；不得误删 canonical revision |

## 5. 批次 D：最后删除 legacy migration/usage runtime ledger

| 表 | 用途 | 删除前保留动作 | 删除条件 |
|---|---|---|---|
| `modeling_legacy_object_migration_batch` | 历史迁移批次 | 导出所有 batch、状态、count、checksum、操作者到受控归档；生成摘要审计 | 所有旧 owner 已删、无恢复依赖、归档恢复验证和审计/运维签字完成 |
| `modeling_legacy_object_migration` | 旧对象到 target 的逐项映射 | 导出 source/target/classification/decision；与最终 manifest checksum 对账 | 上述条件 + 所有 target 可由 canonical 查询重建 |
| `modeling_legacy_api_usage` | 兼容入口使用记录 | 导出 30/90 天统计和 last_seen；纳入 release evidence | 所有旧 route 已物理删除且无需再做退役判断 |

这些 runtime ledger 删除后不新建 tombstone ledger。需要长期保留的证明进入受控交付证据和 dts-admin 中央审计历史；历史 Liquibase changelog 仍保留原 create/drop 定义，不修改 checksum。

## 6. 精确迁移 manifest

```text
MigrationManifest {
  manifestId, environmentId, databaseId, generatedAt, gitCommit,
  sourceTable, targetType,
  tenants: [{ tenantId, sourceCount, mappedCount, conflictCount, orphanCount,
              sourceChecksum, targetChecksum }],
  mappings: [{ sourcePk, sourceVersion, sourceChecksum,
               decision: MIGRATE|MAP|IGNORE|CONFLICT,
               targetId?, targetRevision?, targetChecksum?, reason }],
  backup: { uri, sizeBytes, sha256, restoreVerifiedAt },
  approvals: [{ role, actor, approvedAt }]
}
```

约束：

- dry-run 零写入；apply 必须重验 manifest checksum、数据库 ID 和源版本。
- `CONFLICT > 0`、`orphanCount > 0`、`mappedCount != sourceCount - ignoredCount` 均禁止 DROP。
- apply 以有界批次执行并保存 checkpoint；重复 apply 幂等，不产生新 revision。
- verify 同时比较 count、主键集合、revision pin、payload checksum、CatalogAssetKey 和审计记录。
- rollback 只恢复本批次精确对象；不得清空整库或回退他人新写入。

## 7. 永久保留清单

- 所有历史 Liquibase changelog 及 `DATABASECHANGELOG` 事实。
- `dts-admin` 中央审计历史、动作字典和相关合规索引。
- canonical WarehousePlan、ModelSpec/revision、DimensionDefinition/revision、quality rule/version/binding/run、ReleaseCandidate、materialization/pipeline、Catalog 资产与 lineage。
- 交付证据中的脱敏 manifest、backup checksum、restore rehearsal、Go/No-Go 签字。

## 8. 明确禁止

- 以当前本地 0 行执行客户环境无条件 DROP。
- 修改已执行 changelog 以“顺手删除”旧表。
- 先删表再处理 caller/FK，或只删 route 留 service/entity/repository。
- 用长期 410、tombstone、双写、隐藏开关延后真正退役。
- 删除 platform 本地 outbox 时连带删除 dts-admin 中央审计历史。
