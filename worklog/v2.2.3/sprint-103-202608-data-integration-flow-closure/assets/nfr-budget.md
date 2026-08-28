# NFR 预算与可执行适配度检查

G1 状态：`PASS_WITH_GAPS`。预算已冻结并绑定聚焦测试/运行检查；设计、幂等、分页、批量投影、迁移和浏览器只读链路已有证据。并发压测、三角色/跨部门矩阵、Chrome 95、外部故障注入和安全业务金丝雀仍按各行状态保留，不能由功能测试替代。

| 维度 | 预算/约束 | 可执行检查 | 当前状态 | Owner |
|---|---|---|---|---|
| design 载荷 | mapping ≤1000；规范化 JSON ≤1 MiB | 1000 边界通过；1001/超限 422 | GAP | F0/T02、F1/T01 |
| design 保存 | p95 ≤1s；冲突不静默覆盖 | 20 并发编辑；旧 checksum 409 | GAP | F1/T01 |
| plan 确定性 | 同 design+schemaVersion 产生相同 checksum | 重复规范化 100 次摘要一致 | GAP | F2/T01 |
| topology 投影 | 单任务节点 ≤8；p95 ≤500ms；无坐标持久化 | 1000 mapping 任务投影基准 | GAP | F1/T02 |
| 目标资产解析 | design 单次解析 p95 ≤500ms；列表不逐项远程解析 | 100 个任务页查询计数固定；不存在/越权 fail closed | GAP | F1/T01 |
| 服务端校验 | 不含连接探测 p95 ≤1s；含外部探测 ≤10s | 20 并发；成功/超时/拒绝矩阵 | GAP | F1/T02 |
| 发布一致性 | 失败不能留下 ACTIVE revision 指向未发布 DAG | 暂存/发布/激活故障注入 | GAP | F2/T02 |
| 任务并发 | 每任务 `max_active_runs=1`；写命令幂等 | 双击发布/运行/启停/取消 | GAP | F2/T02、F3/T02 |
| API 分页 | 默认 10、最大 100；服务端过滤 | 101 条 execution 夹具 | GAP | F3/T01 |
| 外部调用 | Airflow connect ≤5s、read ≤10s；有限退避 | 不可达/超时/5xx 集成测试 | SOURCE-ONLY | F2/F3 |
| N+1 | 单页查询最多 1 次 Airflow 批量同步；DB 查询固定上界 | 57 DAG 场景记录调用数 | GAP | F3/T01 |
| 状态新鲜度 | RUNNING 每 5s 轮询并退避；终态确认后停止 | 假时钟 + E2E 网络计数 | GAP | F3/T02 |
| 质量触发时效 | execution 提交成功后 2s 内可见 trigger intent；正常外部条件下 p95 10s 内登记 workflow | after-commit、进程中断恢复、外部超时测试 | SOURCE-ONLY | F3/T03 |
| 质量触发幂等 | 每个 `datasetId + ingestionExecutionId` 最多一个 workflow；重复回调返回同一身份 | 并发 20 次触发与调度恢复重放测试 | SOURCE-ONLY | F3/T03 |
| 当前证据新鲜度 | 新 execution 提交后 5s 内旧 PASS 不再支持“可信可用”；终态同步后 10s 内投影 CURRENT | 假时钟 + 两批次先后完成集成测试 | GAP | F3/T03 |
| 资产质量列表 | 资产/execution 单页 DB 查询固定上界，不按行读取 latest run | 100 资产/100 execution 查询计数断言 | GAP | F3/T01、F3/T03 |
| 取消收敛 | 正常 30s 内终态；超时保留可恢复状态 | 成功/超时/回调丢失测试 | GAP | F3/T02 |
| 幂等窗口 | 写命令 24h 内同键同结果 | 唯一约束 + 重放测试 | GAP | F2/T02、F3/T02 |
| 权限 | 只复用 read/write/export；非授权任务 fail closed | 三角色 × 跨部门矩阵 | BLOCKED | F0/T01、F4/T02 |
| 资产/质量权限 | 目标资产可写且可见；质量规则/证据只按既有角色暴露；跨部门 fail closed | 三角色 × 同/跨部门资产 × 规则可见性矩阵 | BLOCKED | F0/T01、F3/T03、F4/T02 |
| 服务间触发安全 | 只接受已认证 dts-ingestion 机器身份；datasetId 来自冻结 execution；用户/Header 不能切入 trusted 模式 | 无认证、普通用户、伪造 actor/header、替换 datasetId、重放矩阵 | GAP | F0/T02、F3/T03 |
| 密级 | 分类不满足时设计读取/校验/准入 fail closed | 跨密级 source/destination 测试 | GAP | F1/T02 |
| 审计 | 100% 写动作记录 actor/task/revision/checksum/result | action catalog + 审计表断言 | GAP | F4/T01 |
| 敏感信息 | 凭据、token、DSL/脚本全文不得进入日志/审计/URL | 日志扫描 + DTO/toString 单测 | GAP | F4/T01 |
| 浏览器 | Chrome 95 完成设计、投影、发布和运行旅程 | 指定 executable 的 Playwright 单旅程 | PASS_WITH_ENV_NOTE（Chrome 150 只读旅程通过；Chrome 95 BLOCKED） | F0/T01、F4/T02 |
| 回滚 | 制品/schema/DAG 可恢复；旧任务与 legacy DSL 可读 | expand/contract + 回滚演练 | GAP | F4 |

## 容量与索引触发器

- 当前任务 13 条，但设计按 10,000 任务、每任务 10,000 execution 的服务端分页验证，禁止全量加载。
- plan checksum、幂等键或 `task_id + revision` 查询需要新索引时，必须用 `EXPLAIN (ANALYZE, BUFFERS)` 证明；不凭猜测增加索引。
- topology 不存运行状态，不把节点状态持续回写进 JSONB；运行事实保持在 execution/日志 owner。
- 质量证据不复制为新的资产总状态；只持久化 workflow/run 关联与新鲜度所需事实，`可信可用`在读取时派生。
- 31 个未匹配 DAG 不是删除候选清单；先产出只读对账报告和调用方证明。
