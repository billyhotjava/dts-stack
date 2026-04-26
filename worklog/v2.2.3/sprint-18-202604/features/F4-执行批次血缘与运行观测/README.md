# F4: 执行批次血缘与运行观测

**优先级**: P0  
**状态**: READY  
**依赖**: F1

## 目标

让每次接入执行形成稳定 execution context，并贯穿 IngestionExecution、Addax、Airflow、ODS 技术字段和运行日志。

## Task 列表

| ID | Task | 优先级 | 状态 |
|---|---|---|---|
| T01 | Execution Context 与 batch_id 生成 | P0 | READY |
| T02 | Addax/Airflow 参数透传 | P0 | READY |
| T03 | ODS 到执行记录的反查 API | P1 | READY |
| T04 | 运行观测字段与日志脱敏 | P0 | READY |

## 完成标准

- [ ] 执行开始前生成稳定 batch_id。
- [ ] batch_id、execution_id、task_id 进入 ODS 技术字段。
- [ ] 日志和审计不泄露数据库口令、文件绝对敏感路径和 token。
