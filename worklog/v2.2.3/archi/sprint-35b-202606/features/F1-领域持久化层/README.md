# F1: 领域持久化层

**优先级**: P0
**状态**: DONE（84 单测 + 4 Testcontainers IT 全绿，2026-06-14 验证）
**对应缺陷**: #1 零持久化

## 目标

把 dts-metrics 从"内存态原型"变成真正的指标语义事实源：graph draft、model state、model version、rollback event 全部落库，写入带乐观锁，支持重启不丢与多实例部署。datasource / Liquibase / 连接池约定 **mirror 同仓 `dts-platform` 与 `dts-admin`**，不自创基础设施。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 持久化 schema 与 JPA 实体设计 | P0 | DONE | - |
| T02 | datasource/Liquibase/pom 基础设施（mirror 同仓服务） | P0 | DONE | T01 |
| T03 | Repository 层 + 替换 MetricGraphDraftService 内存存储 | P0 | DONE | T02 |
| T04 | 替换 MetricModelLifecycleService 三个 ConcurrentHashMap + 乐观锁 | P0 | DONE | T02,T03 |
| T05 | 持久化集成测试（Testcontainers）：重启不丢 + 并发版本锁 | P0 | DONE | T03,T04 |

## 完成标准

- [ ] 新增 graph_draft / metric_model_state / metric_model_version / metric_rollback_event 四张表，由 Liquibase 管理。
- [ ] `MetricGraphDraftService` 与 `MetricModelLifecycleService` 不再持有任何 `ConcurrentHashMap` 业务状态。
- [ ] model_version 写入带 `@Version` 乐观锁，并发发布冲突返回 409 `metric_version_conflict`。
- [ ] Testcontainers 集成测试证明：服务重启后版本史/回滚链仍可读；并发 publish 不产生脏版本。
- [ ] `./mvnw -pl dts-metrics test` 全绿。
