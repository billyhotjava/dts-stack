# ELT Stability Findings And Priority

## 目标

把当前“数据接入中心 + 数据开发中心”的稳定性问题归类并分配优先级，避免后续修复失焦。

## P0

### P0-1 接入中心异步执行与状态同步语义不稳定

表现：

- `executeAsync()` / `retryExecutionAsync()` 吞掉后台执行异常
- 页面先收到 `submitted`，但失败只能靠后续轮询 `latest execution`
- 一旦 `latest execution` 没及时落库，页面就会出现“已提交但无后续状态”

范围：

- `dts-ingestion`
- `dts-platform-webapp` 接入中心页

证据：

- `IngestionTaskService.executeAsync()`
- `IngestionTaskService.retryExecutionAsync()`
- `TransformPage.tsx`
- `TransformDetailPage.tsx`

### P0-2 接入中心后端最小回归面已失效

表现：

- 关键测试先卡在 `testCompile`
- 说明接入中心当前处于“无法安全改动”的状态

范围：

- `dts-ingestion` 单测与资源层测试

证据：

- `mvn -Dtest=IngestionTaskServiceTest,IngestionTaskExecutionFilterTest,AirflowExecutionSyncServiceTest,IngestionTaskResourceTest test`

### P0-3 开发中心 dbt / Airflow 触发链错误传播失真

表现：

- 真实 Airflow 错误被包装成不准确的状态或 message
- 页面、后端和调度层对“成功触发”的定义不一致

范围：

- `dts-platform`
- `dts-platform-webapp`

证据：

- `EtlResource.waitForDagRegistration()`
- `EtlResourceTest` 中多个 502 / timeout

## P1

### P1-1 接入中心执行状态与日志回显不稳定

表现：

- 历史记录、最新状态、日志内容不同步
- 页面难以准确判断任务当前阶段

### P1-2 开发中心门禁 / 重建 / 回滚链语义不一致

表现：

- 门禁“阻断 / 告警 / 放行”的规则已经变化，但测试与文案未同步
- 回滚后二次触发 dbt rebuild 的失败仍可能被吞掉
- 产出表重建 action 的返回语义和测试预期不一致

证据：

- `DbtOutputRelationServiceTest`
- `DbtQualityGateServiceTest`
- `DbtReleaseGateServiceTest`

### P1-3 边缘入口状态不一致

表现：

- 重试
- 删除
- 重建 DAG
- 历史查看

这些入口在异常场景下容易出现状态残留或反馈不准确

## P2

### P2-1 页面易用性细节

- 表单提示
- 按钮禁用状态
- 非阻断型交互优化

### P2-2 非阻断性能问题

- 轮询频率
- 状态刷新时机
- 部分页面数据加载策略

## 本 Sprint 建议修复范围

本 Sprint 只处理：

- P0 全部
- P1 中与错误传播、状态同步、日志回显、门禁语义直接相关的部分

P2 不作为首批修复目标，只进入 backlog。
