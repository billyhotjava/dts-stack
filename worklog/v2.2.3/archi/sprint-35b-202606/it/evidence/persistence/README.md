# 证据：持久化（重启不丢 + 并发版本锁）

**对应缺陷**: #1 零持久化（事实源曾全在 ConcurrentHashMap）
**测试**: `com.yuzhi.dts.metrics.it.MetricLifecyclePersistenceIT`（Testcontainers `postgres:16-alpine`，真实 DB）
**结果**: `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`（见同目录 `*.txt` 原始报告）
**采集时间**: 2026-06-14
**运行命令**: `cd source && ./mvnw -pl dts-metrics test -Dtest=MetricLifecyclePersistenceIT`（需 Docker）

## 阻断条件 → 证明映射

| it/README 阻断条件 | 证明测试方法 | 如何证明 |
|---|---|---|
| 服务重启后版本史/回滚链丢失 | `graphDraftSurvivesContextRestart` | 清空持久化上下文（模拟重启）后，graph draft 行仍物理存在于 Postgres 并可重载 |
| 服务重启后版本史丢失 | `versionHistorySurvivesContextRestart` | v1+v2 发布后模拟重启、纯从 DB 重载，`versionHistory` 仍返回 2 条有序版本 |
| 并发 publish 同一 model 产生两个 active 版本 | `concurrentPublishOnOneModelLetsExactlyOneWinAndConflictsTheOther` | 两并发 publish → 恰 1 胜；败者命中 `uk_metric_model_version` 唯一约束 + `@Version` 乐观锁，得 409 `metric_version_conflict`；DB 中该 model 仅 1 行 v1 |
| （回滚链持久化） | `rollbackEventPersists` | rollback 事件行落库，`versionHistory` 重放出 `rollback-1` |

## 关键运行时佐证

并发测试运行日志含 Postgres 服务端约束触发（败者）：

```
ERROR: duplicate key value violates unique constraint "uk_metric_model_version"
  Detail: Key (model_id, version)=(im-concurrent-publish, v1) already exists.
```

这是乐观锁/唯一约束生效的真实数据库级证据，而非内存断言。
