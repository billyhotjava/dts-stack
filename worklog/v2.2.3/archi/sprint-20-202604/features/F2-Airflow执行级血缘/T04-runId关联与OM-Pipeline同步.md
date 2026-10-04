# T04: runId 关联与 OM Pipeline TaskLineage 同步

**优先级**: P1
**状态**: READY
**依赖**: T03

## 目标

把 OpenLineage 事件、IngestionExecution、OpenMetadata Pipeline 三者通过 runId 串起来；并把 platform 写入的 lineage 边镜像到 OpenMetadata 的 Pipeline.taskLineage，对外保持一致。

## 技术设计

### runId 来源

- **Airflow OpenLineage**：原生使用 dag run UUID
- **dts-ingestion**：在 T01 spike 期决定—— 推荐方式：在 DAG 模板中用 `Variable.get("DTS_EXECUTION_ID")` 把 IngestionExecution.id 注入为 OpenLineage `parent.run.runId`；这样事件回到 platform 时就能 join 到 IngestionExecution。
- **dbt**：dbt-airflow 集成会让 dbt run 的事件继承 parent runId，自动串联。

### IngestionExecution 关联

`OpenLineageReceiverResource` 在收到 START 事件时：

```java
String parentRunId = event.run.facets.parent?.run?.runId;
if (parentRunId != null) {
    IngestionExecution exec = ingestionExecutionRepo.findById(UUID.fromString(parentRunId));
    if (exec != null) {
        exec.setLineageEventId(event.run.runId);
        exec.setLineageSyncedAt(now());
        ingestionExecutionRepo.save(exec);
    }
}
```

### OpenMetadata 同步

新增 `OpenMetadataPipelineLineageSync`：

- 监听 `LineageEdgeUpsertedEvent`（T03 写边时发布）
- 调用 `OpenMetadataClient.upsertPipelineLineage(pipelineFqn, fromTable, toTable)`
- 失败重试 3 次

### 配置

```yaml
dts:
  lineage:
    openmetadata:
      pipeline-sync-enabled: ${DTS_LINEAGE_OM_PIPELINE_SYNC_ENABLED:true}
```

## 影响范围

- 修改 `dts-ingestion/.../service/etl/AirflowDagService.java` —— DAG 模板注入 `DTS_EXECUTION_ID`（如果 T01 决定用 parent runId 方案）
- 新增 `dts-platform/.../service/catalog/lineage/OpenMetadataPipelineLineageSync.java`
- 修改 `dts-platform/.../service/openmetadata/OpenMetadataClient.java` —— 增加 `upsertPipelineLineage` 方法
- 新增 `dts-platform/.../service/catalog/lineage/event/LineageEdgeUpsertedEvent.java`

## 验证

- [ ] 单测：parent runId 解析、execution join、OM 同步分别覆盖
- [ ] E2E：触发一次 Addax 任务 → IngestionExecution.lineage_event_id 非空 → OM Pipeline 实体 taskLineage 可查
- [ ] 关闭 OM 同步开关时不影响主流程
- [ ] OM 不可用时回退优雅（重试 3 次后告警，不阻塞）

## 完成标准

- [ ] runId 三向打通（IngestionExecution ↔ OpenLineage runId ↔ OM Pipeline）
- [ ] 同步失败有告警条目（走 dts-admin 告警通道）
- [ ] 单测+E2E 通过
