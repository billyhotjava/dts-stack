# F7：上线后 Airflow 计划 DAG 与持续计算

**优先级**：P0
**状态**：DRAFT
**依赖**：F2、F3、F4

## 目标

在现有 Airflow 2.9.3/LocalExecutor 架构上，把计划内全部 current PUBLISHED 模型部署为一个稳定执行绑定。Airflow 唯一负责 CRON、next run 和 DagRun/TaskInstance 状态；平台负责发布 scope、业务运行真值、dbt runtime spec、关系核验和资产健康。

## 冻结契约

| 类型 | 契约 | 关键边界 |
|---|---|---|
| 绑定 | `modeling_plan_execution_binding` | 唯一 `(tenant,plan,environment,executionTargetKey)`；一个 schedule |
| 范围 | `modeling_plan_execution_binding_entry` | plan 下全部 current PUBLISHED 模型的不可变引用/checksum |
| 运行 | 扩展 `modeling_pipeline_run` | `RELEASE_BUILD|OPERATIONAL_RUN`；业务运行唯一真值 |
| 调度 | Airflow | CRON、logical date、next run、DagRun/TaskInstance 唯一真值 |
| 部署 | `PlanDagDeploymentService.reconcile(bindingId)` | 稳定 dagId、原子写文件、Airflow 实际注册对账 |
| 触发 | manual API + authenticated scheduled-run internal API | 两条触发顺序不同，共用唯一 Python dbt task factory |
| UI | 交付工作台 + 模型详情摘要 | 展示 Airflow 实际状态，不让用户填写内部字段 |

## 核心语义

```text
发布构建：
平台先创建 RELEASE_BUILD run → 触发 schedule=None 的共享 executor DAG

手工生产运行：
平台先创建 OPERATIONAL_RUN → 触发该 binding 的 plan DAG

CRON 生产运行：
Airflow scheduler 创建 DagRun → 首任务调用平台原子创建/认领 OPERATIONAL_RUN
```

两类 DAG 都 import `services/dts-airflow/extra/dts_runtime/dbt_task_factory.py` 的同一个 factory：

```text
prepare_runtime → dbt_build → sync_manifest_and_probe_relation → finalize_run(all_done)
```

同步/探针失败必须使 DAG 失败；禁止 `|| true`。`max_active_runs=1`，数据库同时限制一个 active OPERATIONAL_RUN。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 接管发布绑定并建立调度部署与运行用途契约 | P0 | DRAFT | F4/T02、F2/T04 |
| T02 | 基于现有 Airflow 部署稳定计划 DAG 并接通手工/CRON | P0 | DRAFT | T01、F2/T02、F3/T03 |
| T03 | 呈现 Airflow 实际调度与运行历史并关闭失效修复 | P0 | DRAFT | T02、F5/T02 |

## Definition of Ready

- [x] Airflow 是唯一调度真值；PlanExecutionBinding 不是 scheduler。
- [x] P0 不使用 scheduleKey；每 binding 一个 schedule。
- [x] P0 单真实 target，其他 target fail-closed。
- [x] 发布构建、手工运行、CRON 运行的落账顺序已区分。
- [x] dbt graph 与 Airflow workflow 边界已冻结。
- [x] 现有 Airflow Docker socket + ephemeral dbt 方式继续复用，Java renderer 只生成 thin DAG。
- [ ] PG-01 tmpfs profile lease 和 pairwise Airflow service auth 有真实部署证据。
- [ ] PG-03 权限门禁通过，才能启用 PROD CRON。

## 完成标准

- [ ] PUBLISHED 后才生成/更新 binding，scope 覆盖 plan 下全部 current PUBLISHED 模型。
- [ ] 本地发布写 `MANUAL_ONLY + DEPLOYING`；CRON 通过独立 CAS 命令修改同一 DAG。
- [ ] Airflow 注册、checksum、schedule 和 pause 实际状态一致后 binding 才 ACTIVE。
- [ ] 手工/CRON 都产生 durable OPERATIONAL_RUN，且不存在外部运行孤儿。
- [ ] 同 binding 最多一个 active run；碰撞可解释、可审计。
- [ ] 只有 publication current + binding ACTIVE + relation healthy 才是 onlineReadiness READY。
- [ ] DAG 数量随 plan/environment/target 变化，不随模型、Candidate、release 或 schedule 变化。
- [ ] RELEASE_BUILD/plan DAG 中不存在第二份 dbt Docker runtime 实现。
