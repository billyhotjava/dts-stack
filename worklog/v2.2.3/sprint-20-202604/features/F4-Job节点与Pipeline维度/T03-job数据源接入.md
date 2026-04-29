# T03: job 数据源接入（dbt / Airflow / Addax）

**优先级**: P1
**状态**: READY
**依赖**: T01

## 目标

把三类来源的 job 注册到 `lineage_job` 表，并确保 lineage 边的 `via_job_id` 正确填充。

## 技术设计

### 来源 1：dbt model（最重要）

- 在 `DbtAssetSyncService.syncFromManifest()` 里：每解析一个 model node，先 upsert `lineage_job`：

```java
LineageJob job = new LineageJob();
job.setJobType(JobType.DBT_MODEL);
job.setName(model.name());
job.setFqn("dbt." + projectName + "." + model.name());
job.setProject(projectName);
job.setMetadata(Map.of(
    "materialization", model.materialized(),
    "schema", model.schema(),
    "database", model.database()
));
lineageJobRepository.upsertByFqn(job);
```

- 在写 `catalog_dataset_lineage` 时把 `via_job_id` 设为该 job

### 来源 2：Airflow DAG（执行级）

- F2.T03 的 `OpenLineageReceiverResource` 收到事件时，根据 `event.job.name` upsert 一条 `AIRFLOW_DAG` 类型的 job
- `last_run_id`、`last_run_status`、`last_run_at` 由事件填充

### 来源 3：Addax task（采集级）

- F1.T02 的 `IngestionLineageWriter.writeAddaxLineage()` 里，根据 `taskName` upsert 一条 `ADDAX_TASK` 类型的 job

### Upsert 策略

`LineageJobRepository.upsertByFqn(job)`：基于 `fqn` 唯一约束，用 PostgreSQL `ON CONFLICT (fqn) DO UPDATE` 模式写。注意：

- name / metadata 总是更新（上游名字可能改）
- last_run_* 仅当传入非 null 时更新（避免老事件覆盖新事件）

### 数据回填

在 F1.T04 的 backfill runner 里同步加 lineage_job 的回填，dbt 项目首次同步也会自动写入。

## 影响范围

- 修改 `dts-platform/.../service/etl/DbtAssetSyncService.java`
- 修改 `dts-platform/.../service/catalog/lineage/IngestionLineageWriter.java`（F1.T02 创建的）
- 修改 `dts-platform/.../web/rest/internal/OpenLineageReceiverResource.java`（F2.T03 创建的）
- 新增 `dts-platform/.../repository/lineage/LineageJobRepository.java` 的 `upsertByFqn` 方法

## 验证

- [ ] 单测：每种 jobType 都有 upsert 用例
- [ ] 集成测试：dbt sync 后 `lineage_job` 表 dbt model 数 = manifest 中 model 数
- [ ] 集成测试：触发一次 Airflow DAG → `lineage_job` 多一条 AIRFLOW_DAG 记录
- [ ] 触发一次 Addax 任务 → `lineage_job` 多一条 ADDAX_TASK 记录
- [ ] dbt model 改名后再次 sync：name 更新，fqn 不变（`fqn` 设计为稳定的）
- [ ] last_run_* null 不覆盖现有非 null 值

## 完成标准

- [ ] 三个来源都能写入 lineage_job
- [ ] lineage 边的 via_job_id 正确填充
- [ ] upsert 幂等
- [ ] 单测+集成测试通过
