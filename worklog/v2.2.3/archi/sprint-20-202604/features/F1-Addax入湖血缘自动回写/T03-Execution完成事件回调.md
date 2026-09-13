# T03: Execution 完成事件回调挂接

**优先级**: P0
**状态**: READY
**依赖**: T02

## 目标

在 dts-ingestion 的 execution 状态机里挂一个回调：execution 从 `RUNNING` 变 `SUCCESS` 时调用 `PlatformLineageClient`，把 lineage 边写到 platform。

## 技术设计

### 状态变迁点

定位 dts-ingestion 中所有把 `IngestionExecution.status` 设为终态的地方：

- `RealtimeTaskStatusService` — 实时任务状态轮询
- `AirflowAdapter` — Airflow callback / poll
- `IngestionTaskService.markCompleted()`（如存在）

统一收敛到一个 `IngestionExecutionStateMachine.transitionTo(executionId, newStatus)`，在内部发布 `ExecutionCompletedEvent`。

### 事件监听

新增 `LineageCallbackListener`：

```java
@EventListener
@TransactionalEventListener(phase = AFTER_COMMIT)
public void onExecutionCompleted(ExecutionCompletedEvent event) {
    if (event.status() != SUCCESS) return;
    if (!settings.lineageCallbackEnabled()) return;
    var execution = repo.findById(event.executionId()).orElseThrow();
    var request = AddaxLineageRequest.from(execution);
    platformLineageClient.write(request);
    execution.setLineageSyncedAt(Instant.now());
    repo.save(execution);
}
```

### 异步化

回调走 `@Async`（`AsyncConfig.lineageExecutor()`），不阻塞 execution 主流程；失败重试见 T02（client 层处理）。

### 配置

```yaml
dts:
  lineage:
    platform-callback:
      enabled: ${DTS_LINEAGE_PLATFORM_CALLBACK_ENABLED:true}
      base-url: ${DTS_PLATFORM_BASE_URL:http://dts-platform:8080}
      timeout-ms: 5000
      retry-count: 3
```

## 影响范围

- 新增 `dts-ingestion/.../service/etl/IngestionExecutionStateMachine.java`
- 新增 `dts-ingestion/.../service/etl/LineageCallbackListener.java`
- 新增 `dts-ingestion/.../config/AsyncConfig.java`（如不存在）
- 修改 `RealtimeTaskStatusService`、`AirflowAdapter`、`IngestionTaskService` 收敛到状态机
- 修改 `application.yml` 增 `dts.lineage.*` 配置

## 验证

- [ ] 单测：`LineageCallbackListenerTest` mock client，验证不同状态下是否触发回调
- [ ] 集成测试：开启 wiremock 模拟 platform 接口，跑一次 SUCCESS 任务 → wiremock 收到 1 次请求
- [ ] 集成测试：FAILED 任务不触发 lineage 回调
- [ ] 关闭开关 `dts.lineage.platform-callback.enabled=false` 时回调被跳过
- [ ] platform 端宕机模拟：回调失败 3 次后 `lineage_synced_at=NULL` 且日志含 `LINEAGE_CALLBACK_FAILED`

## 完成标准

- [ ] 状态机收敛点已统一
- [ ] AFTER_COMMIT 事件已生效，确保 DB 事务提交后才回调
- [ ] 异步执行不影响主流程响应时间（基准对比 < 5%）
- [ ] 单测+集成测试全绿
