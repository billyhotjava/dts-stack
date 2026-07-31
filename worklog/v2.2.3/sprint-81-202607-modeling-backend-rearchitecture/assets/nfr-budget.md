# 非功能预算（Gate G1）

**依据**：`assets/domain-profile.md` + DTS 领域不变量 + Sprint-76 执行链约束
**适用范围**：模块化控制面、唯一建模链、quality evidence、两类 outbox、DbtExecutionGateway、迁移与物理退役
**状态说明**：预算已定义；状态为 `PLANNED/GAP` 表示适应度函数尚未执行，不代表实现通过。

| 维度 | 预算 | 适应度函数（可执行） | 归属 Task | 状态 |
|---|---|---|---|---|
| 模块边界 | 模块依赖无环；跨模块只经公开 port；0 个跨域 repository/entity import | ArchUnit 输出依赖图并断言 forbidden imports=0 | F1/T01 | PLANNED |
| 单一 owner | 新代码对 semantic/old plan/vNext/sql model/business object 运行面引用=0 | GitNexus change impact + source contract `rg` allowlist | F1/T01、F5 | PLANNED |
| 查询效率 | 单次 StageGate 评估 SQL ≤8，禁止按 rule/binding/run N+1 | PostgreSQL IT 统计 statement count ≤8 | F2/T02、F3/T02 | PLANNED |
| StageGate 延迟 | 50 个 evidence refs 时 P95 ≤500 ms；不含真实 dbt 构建 | 固定数据集 JMH/IT 200 次，断言 P95 | F3/T02、F6/T01 | PLANNED |
| API 延迟 | canonical plan/spec/lifecycle 单对象读写 P95 ≤400 ms（本地基准，50 并发） | k6/JMeter 50 VU，失败率 <1% | F2/T01～T03 | PLANNED |
| 并发/CAS | 同一 aggregate 20 并发写仅 1 次成功，其余 409/412；无 lost update | PostgreSQL 并发 IT | F2/T01～T03 | PLANNED |
| 幂等 | 同一 `Idempotency-Key` 重放 100 次只产生 1 revision/candidate/execution/outbox logical event | 重放 IT + unique constraint 断言 | F2、F3/T03、F4/T01 | PLANNED |
| Outbox 原子性 | 业务提交与对应 event/audit outbox 同事务；任一写失败全部回滚 | 故障注入事务 IT | F3/T03 | PLANNED |
| Outbox 分离 | event/audit 物理分表、独立 dispatcher、独立 backlog/DLQ 指标 | schema + Spring context + metric contract test | F3/T03～T04 | PLANNED |
| 审计耐久性 | dts-admin 30 s 不可用期间 0 丢失；恢复后 60 s 内投递完成；重复中央记录=0 | kill/restart 故障注入 + eventId 幂等断言 | F3/T04 | PLANNED |
| Outbox 批量 | 每批 ≤100，锁等待 ≤1 s，单 worker 不持锁做 HTTP；积压 10k 可在 10 min 内清空 | Testcontainers 10k backlog 基准 + SQL lock 观测 | F3/T03～T04 | PLANNED |
| 出站超时 | dts-admin connect ≤2 s/read ≤5 s；Airflow connect ≤3 s/read ≤10 s；总调用 deadline ≤15 s | client 配置静态断言 + WireMock 延迟测试 | F3/T04、F4/T03 | PLANNED |
| 重试 | 仅网络/5xx/429 重试，指数退避 1/2/4/8/16 s + jitter，最多 5 次；4xx 不重试 | WireMock 契约测试 | F3/T04、F4/T03 | PLANNED |
| 执行提交 | duplicate candidate/version/attempt 返回同一 handle；submit P95 ≤2 s（不含 DagRun 执行） | Airflow adapter IT + 幂等断言 | F4/T01～T03 | PLANNED |
| 执行对账 | RUNNING 超过 deadline 进入 UNKNOWN；错误 attempt 回执绝不推进新 candidate | clock/fault injection IT | F4/T02～T03 | PLANNED |
| 批量迁移 | 默认 500 行/批，可配置 100～2000；单事务 ≤30 s；dry-run 零写入 | Testcontainers 10k 行 migration IT | F0/T03、F5/T02～T04 | PLANNED |
| 迁移一致性 | 每 tenant source=mapped+ignored；conflict/orphan=0；source/target checksum 一致 | manifest verifier 退出码红/绿 | F5/T02～T04 | PLANNED |
| 备份恢复 | RPO=0（停机窗口最终快照）；当前本地量级 RTO ≤30 min，客户 RTO 在画像后确认 | 隔离库 restore rehearsal + checksum | F0/T03、F6/T02 | GAP |
| 删除安全 | pre-drop manifest 漂移=0；旧 route/resource/service/entity/repository/table=0；中央审计/changelog 不变 | preflight command + context/schema/route/audit assertions | F5、F6/T02 | PLANNED |
| 权限/租户 | tenant 仅服务端解析；跨租户 403；现有 read/write/export 粒度不扩张 | multi-tenant auth IT | F2、F3、F4 | PLANNED |
| 凭据 | profile/secret/token 不进入 DB、outbox、URL、日志或证据；请求仅含 leaseId | secret scanner + payload/schema test | F3/T03、F4 | PLANNED |
| 审计分类 | 所有 P0 状态变化 action code 在 dts-admin 字典登记，未分类=0 | integration IT 查询中央审计分类 | F3/T04、F6/T03 | PLANNED |
| 客户容量 | 客户真实行数/增长率未知，不承诺生产 P95 | F0/T02 画像后更新本表并重新跑容量检查 | F0/T02 | GAP |

## 未达标项处置

| 缺口 | 影响 | 处置 | 关联 Task |
|---|---|---|---|
| 客户数据量级、增长率和调用量未知 | 迁移批次、索引和生产 P95 不能最终批准 | 逐环境只读画像，按最大租户复跑适应度函数 | F0/T02、F6/T01 |
| 客户 RTO 未签字 | 物理 DROP 的维护窗口不可批准 | 以 restore rehearsal 实测值与客户协商窗口 | F0/T03、F6/T02 |
| 当前 `gov_quality_run=0` | 无真实质量 evidence 延迟和正确性基线 | 最终 E2E 前建立隔离规则/binding/run | F3/T02、F6/T03 |
| durable audit outbox 尚未实现 | 高风险动作存在进程崩溃丢审计可能 | F3/T03/T04 完成前发布/删除类动作不得进入 DONE | F3/T03～T04 |

## DoD 回跑规则

- F1～F5 可运行定向单测/IT，但不得声称 Sprint E2E 完成。
- F6/T01 汇总回跑全部架构与 NFR 适应度函数；任何红项阻断 F5 后续 DROP 或 release。
- F6/T03 在全部编码结束后一次执行最终旅程；结果、命令、commit、环境和残余风险进入 `it/`。
