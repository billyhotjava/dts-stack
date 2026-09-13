# T01: lineage_job 表与节点类型扩展

**优先级**: P1
**状态**: READY
**依赖**: F1.T02, F2.T03

## 目标

新建 `lineage_job` 表承载"加工节点"（dbt model / Airflow DAG / Addax task），并扩展 `catalog_dataset_lineage` 让边可以指向 job 而不只是 dataset。

## 技术设计

### 新表

```sql
CREATE TABLE lineage_job (
    id UUID PRIMARY KEY,
    job_type VARCHAR(32) NOT NULL,           -- DBT_MODEL / AIRFLOW_DAG / ADDAX_TASK / VIEW_DEFINITION
    name VARCHAR(256) NOT NULL,
    fqn VARCHAR(512) NOT NULL,                -- 唯一定位：service.namespace.name
    project VARCHAR(128),
    description TEXT,
    last_run_id VARCHAR(64),
    last_run_status VARCHAR(16),
    last_run_at TIMESTAMPTZ,
    metadata JSONB,                           -- 类型特有字段，如 dbt materialization、Airflow schedule
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_lineage_job_fqn UNIQUE (fqn)
);

CREATE INDEX idx_lineage_job_type ON lineage_job(job_type);
CREATE INDEX idx_lineage_job_project ON lineage_job(project);
```

### `catalog_dataset_lineage` 扩展

新增 2 个可空列：

| 列 | 说明 |
|---|---|
| `via_job_id` | UUID，指向 `lineage_job.id`；NULL = 直连边 |
| `edge_kind` | varchar(16)，`DATASET_TO_DATASET` / `DATASET_TO_JOB` / `JOB_TO_DATASET` |

### 边模型

原来一条 dataset → dataset 边，可以拆为两条：

```
ods_orders --[DATASET_TO_JOB]--> dbt_model:dwd_orders --[JOB_TO_DATASET]--> dwd_orders
```

迁移期间双写：保留原 `DATASET_TO_DATASET` 边（用 `via_job_id` 指 job），同时写两条新边。前端可任选展示形式。

### Liquibase

`20260430_07_lineage_job.xml` —— 建表
`20260430_08_dataset_lineage_job_link.xml` —— 加列

### 实体

新增：

- `LineageJob.java`
- `LineageJobRepository.java`
- `LineageJobDTO.java`

## 影响范围

- 2 个 Liquibase 迁移
- 3 个新 Java 类
- 修改 `CatalogDatasetLineage.java` 加 `viaJobId`、`edgeKind`

## 验证

- [ ] 表已建，约束、索引齐全
- [ ] 单测：写一个 dbt model job，关联两条边，查询正确
- [ ] FQN 唯一约束验证
- [ ] 旧 lineage 边读取兼容（via_job_id NULL 时仍正常工作）

## 完成标准

- [ ] 表与实体完成
- [ ] 单测通过
- [ ] 现有查询行为未破坏
