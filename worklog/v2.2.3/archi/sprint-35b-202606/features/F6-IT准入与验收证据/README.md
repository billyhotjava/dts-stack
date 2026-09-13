# F6: IT 准入与验收证据

**优先级**: P0
**状态**: DONE（证据已落 `it/evidence/`，2026-06-14 真实运行采集）
**对应缺陷**: 全部（验收闭合）

## 目标

为 Sprint-35b 提供真实集成测试证据，闭合 Sprint-35 因"内存态不持久"而无法成立的验收，证据落 `it/evidence/`。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 重启持久性 IT 证据 | P0 | DONE | F1 |
| T02 | 安全对等 IT 证据（两链路 permission/RLS/audit 一致） | P0 | DONE | F2 |
| T03 | 发布闭环/回滚 IT 证据 | P0 | DONE | F3 |

## 完成标准
- [x] `it/evidence/persistence/`：重启不丢、并发版本锁。（`MetricLifecyclePersistenceIT` 4 例，真实 Postgres）
- [x] `it/evidence/security-parity/`：pack 与 lifecycle 链路安全行为对等。（`MetricLifecycleSecurityParityTest` 6 + `MetricArtifactGenerationIT` 3）
- [x] `it/evidence/publish-closure/`：发布幂等、补偿、回滚链。（`MetricPublishDownstreamTest` 4 + `MetricModelLifecycleResourceTest` 10）
- [x] 阻断条件（见 it/README）无一触发。（逐条映射见各证据目录 README；韧性为配置级证据 + followup 说明）

## 采集基线（2026-06-14）

| 套件 | 命令 | 结果 |
|------|------|------|
| 单元 + web | `./mvnw -pl dts-metrics clean test` | 96 例，0 失败 |
| 持久化/artifact IT（需 Docker） | `./mvnw -pl dts-metrics test -Dtest='MetricLifecyclePersistenceIT,MetricArtifactGenerationIT'` | 4 + 3 例，0 失败 |
| **合计** | | **103 例全绿** |

> 铁律提示：`*IT` 由 failsafe 在 `verify` 阶段运行，`mvn test` 默认不含；本 sprint 用显式
> `-Dtest=<IT 类名>` 强制 surefire 运行以采集证据。改完务必 `clean` 再信测试结果（增量编译假绿本 sprint 真实踩过）。
