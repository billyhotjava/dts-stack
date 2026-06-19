# F3: 运维

**优先级**: P1
**状态**: READY

## 目标

收编旁路区「运维 Ops」域全部页面：运维总览、实例、回填、告警日志、日志中心、发布治理、平台事件可观测、审计证据。按「先占位后充实」——每页至少**可点入口 + mock 列表骨架**（CompactTable 默认 10 条/页）。命名对齐现网 `OpsOverviewPage` / `InstancesPage` / `BackfillPage` / `AlertLogPage` / `LogCenterPage` / `ReleaseGovernancePage` / `PlatformEventObservabilityPage` / `AuditEvidencePage`，统一经 `opsService` 取数。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| [T01](./T01-运维总览与实例与回填.md) | 运维总览 / 实例 / 回填 | P1 | READY | S1 |
| [T02](./T02-告警日志发布治理事件可观测审计证据.md) | 告警 / 日志 / 发布治理 / 事件可观测 / 审计证据 | P1 | READY | S1 |

## 完成标准

- [ ] 运维域 8 个页面在左轨「平台·运维」下均有可点入口、路由可达。
- [ ] 每页至少呈现 mock 列表/概览骨架（CompactTable 默认 10 条/页，状态点 token）。
- [ ] 全部经 `opsService` 取数，返回 `Promise<Result<T>>`，`VITE_USE_MOCK` 开关下可注入/重置样例。
- [ ] 占位页不报错、不断路由；纳入全局搜索 ⌘K 索引（F4-T02）。
