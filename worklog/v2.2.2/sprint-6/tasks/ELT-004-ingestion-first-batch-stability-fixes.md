# ELT-004：接入中心首批稳定性修复

## 目标

收口接入中心最影响稳定性的 P0 / P1 问题。

## 优先问题

- DAG 预热 / 就绪失败错误传播
- 执行中状态同步
- 日志回显与历史记录一致性
- 边缘入口状态一致性

## 交付标准

- 对应问题有明确修复
- 相关测试补齐
- 冒烟路径可回归

## 当前实现进展

- 已完成异步执行与异步重试的前置校验收口：
  - `executeTaskAsync` 不再对运行中任务直接返回 `submitted`
  - `retryExecutionAsync` 不再对缺失 execution / 非法 retry mode 直接返回 `submitted`
- 已完成异步提交拒绝兜底：
  - 线程池拒绝任务时，异步执行/异步重试不再落成泛化 500
  - 当前统一返回 `后台执行队列繁忙，请稍后重试`
- 已补充后端测试：
  - `IngestionTaskServiceTest`
  - `IngestionTaskResourceTest`
- 已确认 `dts-ingestion` 资源层错误契约为 `HTTP 200 + body.status`，测试断言已按真实契约调整
- 已确认 `dts-platform-webapp` `apiClient` 会按响应体 `status` 抛错，前端能正确消费这批后端错误语义

## 当前验证

```bash
cd source/dts-ingestion
mvn -Dtest=IngestionTaskServiceTest,IngestionTaskResourceTest,IngestionTaskExecutionFilterTest,AirflowExecutionSyncServiceTest test
```

结果：

- `Tests run: 22, Failures: 0, Errors: 0, Skipped: 0`

## 后续焦点

- 继续收口后台异步线程中的失败可观测性，避免前端只看到“已提交”但无法快速定位后台失败原因
- 把接入中心的执行历史、日志回显和异步状态同步进一步对齐
