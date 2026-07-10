# F2: 数据集成到数仓规划的首屏引导

**优先级**: P0
**状态**: IN_PROGRESS

## 目标

让数据源和接入任务自然进入数仓规划，而不是让用户在数据集成、建模、SQL 页面之间猜路径。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 数据源到旅程的开始入口 | P0 | READY | F1/T01 |
| T02 | ODS/DWD/DWS/ADS 分层规划卡片 | P0 | READY | T01 |
| T03 | 接入状态到建模就绪度提示 | P1 | READY | T01 |
| T04 | API 数据源测试与运行配置一致性 | P0 | DONE | T01 |

## 完成标准

- [ ] 用户从数据源页面能进入数仓规划。
- [ ] UI 能解释 ODS_RAW、ODS_STANDARDIZED、DWD、DWS、ADS 的下一步。
- [ ] 接入未完成时不会生成误导性模型草稿。

## T04 说明

- 新建数据源测试、已保存数据源测试和实际 API 入湖共用同一份运行时配置归一化规则。
- `path/resources/requestPolicy/rateLimit/tls` 等配置不能因页面保存位置不同而失效。
- 连接测试必须覆盖鉴权、资源路径、策略配置和失败结果回传。

## T04 结果

- 已完成：normalizer、已保存数据源连接测试、新建表单 source config 和实际 HTTP 执行共享同一套归一化结果。
- 已验证：单测覆盖嵌套 `api/readerConfig` 提升、资源路径、requestPolicy、rateLimit、tls；容器内连接测试返回 `connected=true`、HTTP 200、`sampleCount=1`、`recordPathResolved=true`。
- 已知运行风险：容器启动后自动重试队列中的历史失败任务仍产生 `UnexpectedRollbackException`，不属于本 T04 的 API 配置链路失败，需单独纳入运行治理任务。
