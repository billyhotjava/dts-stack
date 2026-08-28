# IT-05：执行、重试、取消与日志恢复

检查日期：2026-08-28。判定：`PASS_SOURCE_DEPLOYED`。

## 已验证

- `IngestionExecutionSubmissionServiceTest` 5 项、`IngestionExecutionCommandServiceTest` 3 项通过。
- 同一运行幂等键返回同一持久化 execution；retry parent 由既有唯一索引保护。
- cancel 同时校验 task/execution 归属与可取消状态；Airflow 外部取消失败不会伪造本地 CANCELLED。
- 前端对不确定网络结果保留运行/重试幂等键；日志入口不依赖 dbt selector。

## 保留门禁

未对客户任务制造失败、重试或取消。外部 Airflow 超时后的 30 秒收敛需在金丝雀环境复验。
