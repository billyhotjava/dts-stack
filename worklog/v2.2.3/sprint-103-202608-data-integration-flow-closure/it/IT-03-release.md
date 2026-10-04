# IT-03：校验、准入与调度

检查日期：2026-08-28。判定：`PASS_SOURCE_DEPLOYED`。

## 已验证

- 服务端 validation 与 save 使用相同 write、数据源、目标资产和质量规则引用边界；失败保持 fail closed。
- admit 继续复用既有 revision/DAG 发布 owner；新增 schedule enable/pause 只接受 taskId，不接受任意 dagId。
- 运行和重试使用持久化 command idempotency identity；数据库 `uk_ingestion_execution_batch_id` 已在线创建。
- 部署不发布、暂停或删除任何 Airflow DAG；发布前后 import error 均为 0。

## 保留门禁

未对客户 active 任务执行 admit/启停；原子发布故障注入与真实调度状态切换留待可回收金丝雀。
