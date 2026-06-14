# Sprint-35b 集成测试计划

## 目标

证明 dts-metrics 架构收口达成：状态持久可多实例、两条 artifact 链路安全对等、发布闭环幂等可补偿可回滚、platform 依赖有超时韧性。

## 证据目录

| 证据 | 路径 | 状态 |
|------|------|------|
| 持久化（重启/并发锁） | `it/evidence/persistence/` | DONE（IT 4 例，真实 Postgres） |
| 安全对等（permission/RLS/audit） | `it/evidence/security-parity/` | DONE（6 + 3 例） |
| 发布闭环/回滚 | `it/evidence/publish-closure/` | DONE（4 + 10 例） |
| 韧性（超时/重试） | `it/evidence/resilience/` | DONE（配置级证据；慢服务端计时测试列 followup） |

> 证据采集基线 2026-06-14：单元 96 + 持久化/artifact IT 7 = **103 例全绿**。各目录 README 含阻断条件→测试逐条映射 + 原始 surefire 报告。

## 验收命令

```bash
cd source
./mvnw -q -pl dts-metrics test
./mvnw -q -pl dts-metrics -Dtest='*PersistenceIT,*SecurityParity*,*LifecycleResourceTest' test
```

```bash
# 重启持久性手测
curl -sS -X POST http://127.0.0.1:18084/api/metrics/models/{modelId}/artifacts -d @fixtures...
curl -sS -X POST http://127.0.0.1:18084/api/metrics/models/{modelId}/validate -d @fixtures...
curl -sS -X POST http://127.0.0.1:18084/api/metrics/models/{modelId}/publish
# 重启 dts-metrics 容器后：
curl -sS http://127.0.0.1:18084/api/metrics/models/{modelId}/versions   # 应仍返回完整版本史
```

## 阻断条件

- 服务重启后版本史/回滚链丢失（持久化未生效）。
- 并发 publish 同一 model 产生两个 active 版本（乐观锁未生效）。
- graph lifecycle 链路在无权限/无 RLS 解析下仍生成 artifact（安全未对等）。
- lifecycle 生成 SQL 缺少 pack 链路应有的 RLS WHERE / masking。
- publish 标 PUBLISHED 但 BI/lineage/audit 未注册（闭环断裂）。
- platform 慢响应导致 metrics 请求无界挂起（无超时）。
- 高风险动作（publish/rollback）审计缺失。
