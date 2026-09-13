# T03: OpenLineage Listener Receiver 接入

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

在 dts-platform 暴露 OpenLineage 事件接收端点，把 Airflow / dbt 发来的 RunEvent 解析为 lineage 边并落库。

## 技术设计

### 端点

新增 `com.yuzhi.dts.platform.web.rest.internal.OpenLineageReceiverResource`：

```java
@PostMapping("/api/internal/lineage/openlineage")
@PreAuthorize("hasAuthority('AUTH_INTERNAL')")
public ResponseEntity<Void> receive(@RequestBody OpenLineageEvent event)
```

### 事件类型处理

| eventType | 处理 |
|-----------|------|
| START | 创建/更新 `lineage_run` 行；如 IngestionExecution 关联（runId match），写 `lineage_event_id`、状态 RUNNING |
| RUNNING | 心跳，更新 `last_heartbeat_at` |
| COMPLETE | 解析 `inputs[]`/`outputs[]`，对每个 (input,output) 对 upsert `catalog_dataset_lineage`；relationType=`AIRFLOW`；状态 SUCCESS |
| FAIL | 同 COMPLETE 但状态 FAILED；不写正式边，写 `catalog_lineage_pending` |
| ABORT | 同 FAIL |

### 数据建模

新增表 `lineage_run`：

```sql
CREATE TABLE lineage_run (
  run_id VARCHAR(64) PRIMARY KEY,
  job_namespace VARCHAR(128),
  job_name VARCHAR(256),
  ingestion_execution_id UUID,
  status VARCHAR(32),
  started_at TIMESTAMPTZ,
  completed_at TIMESTAMPTZ,
  last_heartbeat_at TIMESTAMPTZ,
  raw_event JSONB
);
CREATE INDEX idx_lineage_run_execution ON lineage_run(ingestion_execution_id);
```

新增 Liquibase：`20260430_03_lineage_run.xml`。

`catalog_dataset_lineage` 增列（Liquibase `20260430_04_lineage_run_link.xml`）：

| 列名 | 类型 | 说明 |
|---|---|---|
| `last_run_id` | varchar(64) | 最近一次执行的 OpenLineage runId |
| `last_run_status` | varchar(16) | SUCCESS/FAILED/RUNNING |
| `last_run_at` | timestamptz | 最近一次完成时间 |

### 解析逻辑

新增 `OpenLineageEventParser`：

```java
class OpenLineageEventParser {
    LineageGraph parse(OpenLineageEvent event) {
        // event.inputs[].name -> catalog dataset
        // event.outputs[].name -> catalog dataset
        // 笛卡尔积构造边集
        // facets.columnLineage（如有）→ F3 列级血缘
    }
}
```

dataset name 解析：约定 `<service>.<database>.<schema>.<table>`，与 OM FQN 一致；解析不到落 `catalog_lineage_pending`。

### 鉴权

- 内部 token：`Authorization: Bearer <DTS_OPENLINEAGE_INTERNAL_TOKEN>`
- 网关层 IP 白名单（仅 Airflow 容器网段）
- 限速：每 IP 每秒 100 个事件（避免事件风暴）

## 影响范围

- 新增 `dts-platform/.../web/rest/internal/OpenLineageReceiverResource.java`
- 新增 `dts-platform/.../service/catalog/lineage/OpenLineageEventParser.java`
- 新增 `dts-platform/.../service/catalog/lineage/LineageRunService.java`
- 新增 `dts-platform/.../domain/lineage/LineageRun.java`
- 新增 Liquibase 迁移 ×2
- 修改 `audit-action-catalog.json` 增 `LINEAGE_OPENLINEAGE_RECEIVE`

## 验证

- [ ] 单测：`OpenLineageEventParserTest` 覆盖 START/COMPLETE/FAIL/无 inputs/无 outputs/未知 dataset 6 个分支
- [ ] 集成测试：用 Testcontainers 跑端到端（mock event → DB 落 lineage 边）
- [ ] 性能：100 EPS 持续 5 分钟无积压；P99 延迟 < 200ms
- [ ] 重复事件幂等：同 runId 多次 COMPLETE 只更新一次
- [ ] 限速触发返回 429

## 完成标准

- [ ] 端点实现并通过鉴权测试
- [ ] Liquibase 迁移加入 master.xml
- [ ] 单测覆盖率 ≥ 85%
- [ ] 集成测试通过
- [ ] 审计日志条目齐全
