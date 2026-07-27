# Sprint-76 集成验收

**状态**：PLANNED
**规则**：以下 IT 必须在真实 PostgreSQL、Airflow、dbt、Spring Security 和 Chrome95 环境执行。单元测试、mock、手工补库或仅截图不能替代端到端证据。

| IT | 场景 | 必须断言 | 证据位置 |
|---|---|---|---|
| IT-01 | 普通实现 compile | artifact 包含 alias/meta/settings，bundle checksum 可重现；不产生 relation observation | `it/evidence/it-01-compile.md` |
| IT-02 | DIMENSION FULL table | `dwd_s74_project_dimension` 真实存在、字段匹配、最小查询成功 | `it/evidence/it-02-dimension-table.md` |
| IT-03 | FACT INCREMENTAL | `dwd_s74_finance_detail` 使用 KEY unique_key；第二次构建不产生重复键 | `it/evidence/it-03-fact-incremental.md` |
| IT-04 | SUMMARY view/APPLICATION dbt managed | `dws_s74_finance_summary` 或配置的 view 类型正确；高级 dbt 走同一 observation/发布链 | `it/evidence/it-04-dual-mode.md` |
| IT-05 | candidate 幂等与活动占用 | 100 并发 Build Intent 只有一个 active claim/candidate；确定性 dagRunId 唯一；Airflow 已接收但 HTTP 超时不重复触发 | `it/evidence/it-05-idempotency.md` |
| IT-06 | 失败、对账、重试与取消 | dbt 失败、超时 UNKNOWN、服务重启后状态可恢复；UNKNOWN 未对账不可 retry；确认失败后 retry 保留历史并产生新 run；CANCELLED 释放 claim 且不 DROP 关系 | `it/evidence/it-06-retry.md` |
| IT-07 | 实时关系核验 | 删除/改名隔离测试关系后 inspector 返回 exists=false，不能被 run_results success 绕过 | `it/evidence/it-07-relation-probe.md` |
| IT-08 | 漂移门禁 | ModelSpec 或 Implementation revision/checksum 改变后旧 bundle/run/observation 全部 STALE | `it/evidence/it-08-drift.md` |
| IT-09 | 发布前与 PARTIAL 隔离 | BUILT、REVIEW_PENDING、APPROVED、PUBLISHING、mandatory PARTIAL 均不可检索到可消费目标资产；100-entry fault injection 后可见 entry=0 | `it/evidence/it-09-prepublish.md` |
| IT-10 | Candidate 原子发布 | operator PUBLISH 后 Candidate/ModelSpec/CatalogDataset/field/lineage/physicalAssetRef/MANUAL_ONLY binding scope 同批提交；asset key 唯一；成功后一次性全部可见 | `it/evidence/it-10-catalog.md` |
| IT-11 | 血缘、密级与外部同步隔离 | 输入资产/上游模型 → 输出 dataset 血缘正确，密级按 Sprint-72 传播；OpenMetadata/BI 故障保持 PUBLISHED，syncHealth=DEGRADED 并可重试 | `it/evidence/it-11-lineage-classification.md` |
| IT-12 | 单模型快捷构建与提交上线 | Build 只精确复用 SINGLE；BATCH 返回 409；Publish Intent 只产生 PUBLICATION_REQUESTED、RUN_QUALITY、SUBMIT_REVIEW，自动 APPROVE/PUBLISH=0；已发布 revision 引导 OPERATIONAL_RUN | `it/evidence/it-12-model-detail.md` |
| IT-13 | 双入口与交付工作台旅程 | 普通/高级页面共享 Build/Publish Intent；Chrome95 显示质量检查、等待审核、等待发布、已发布/部署中、上线完成、已发布/异常；reviewer/operator 只在工作台操作 | `it/evidence/it-13-workbench-chrome95.md` |
| IT-14 | 权限、职责、租户与请求篡改 | 三个专用 Keycloak authority 对应的 maintainer/reviewer/operator 真实账号与 command authorize/allowedActions 同源；catalog/admin/auth-admin/auditor 无隐式提升；同人批准/发布=403；auditor 只读；Sprint-36/F3 policy DENY/缺失/过期均阻断资产发布；跨租户不可读写；role/action/selector/dagId/target/checksum/schedule 注入被拒 | `it/evidence/it-14-security.md` |
| IT-15 | 兼容发布与回滚 | migration upgrade/rollback、旧 lifecycle route 仅委托 current Candidate、kill switch 和发布事实回退有演练；不自动 DROP 外部关系 | `it/evidence/it-15-release.md` |
| IT-16 | 模型依赖、单目标与 DAG 身份 | source/ref/manifest graph 一致；未发布上游/非当前 target 阻断；100-entry candidate 共用一个 RELEASE_BUILD DAG；plan DAG 不随 model/candidate/release/cron 增长 | `it/evidence/it-16-model-dag.md` |
| IT-17 | 上线完成与正确运行落账 | 连续发布 A/B 后 binding scope=A+B；默认 MANUAL_ONLY 从 DEPLOYING 到 ACTIVE；MANUAL 先建 run 后触发；CRON 先建 DagRun 再幂等 open；两路各产生一次真实 OPERATIONAL_RUN | `it/evidence/it-17-operational-run.md` |
| IT-18 | 调度真值、健康失效与恢复 | Airflow actual schedule/nextRun/paused 与页面一致；release drift/rollback、DAG missing/paused、binding FAILED、relation missing、并发触发均 fail-closed，不撤销发布 | `it/evidence/it-18-schedule-recovery.md` |
| IT-19 | dbt credential 与 target 安全 | tracked secret=0；Git/DAG/conf/XCom/API/DB/env/log/evidence warehouse secret=0；host/container 证明 runtime root=tmpfs、0700、profile=0600/read-only；release/TTL/kill-restart 清理；symlink/path traversal/unknown target/missing secret/伪造 Airflow token 阻断；轮换后运行成功且 dagId 不变 | `it/evidence/it-19-dbt-secret.md` |
| IT-20 | Airflow DAG 部署与失败传播 | RELEASE_BUILD/plan thin DAG 只 import 同一版本 task factory；静态检查第二 runtime builder=0；scheduler 扫描期间原子更新无 partial parse；30s 内 registration/templateVersion/checksum/schedule 对账；UTC/IANA CRON 正确；prepare/sync/probe/service-auth 故障使 DagRun FAILED；`max_active_runs=1`；legacy per-tag 触发=0且已 pause | `it/evidence/it-20-airflow-deployment.md` |

## DoD 证据链

```text
README 需求
  → Feature/Task
  → unit/contract/PostgreSQL IT
  → Airflow dag_run + dbt invocation
  → RelationObservation
  → CatalogDataset/physicalAssetRef/lineage
  → PlanExecutionBinding + OPERATIONAL_RUN
  → Chrome95 screenshot
  → release plan/runbook/Go-No-Go
```

任何一层缺失，Sprint 不得标记 DONE。

## 实施中增量证据

| Feature/Task | 当前证据 | 边界 |
|---|---|---|
| F1/T01 | `evidence/f1-t01-execution-plan/README.md` | canonical execution plan；不代表 dbt 已运行 |
| F1/T02 | `evidence/f1-t02-compiler-artifacts/README.md` | 真实 dbt compile + manifest graph；不代表目标 relation 存在 |
| F1/T03 | `evidence/f1-t03-scoped-dbt-project/README.md` | immutable scoped bundle、fail-closed 与可清理 runtime；不代表 Airflow 已调度 |
