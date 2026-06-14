# Sprint-35b: dts-metrics 架构收口与持久化硬化（202606）

**时间**: 2026-06
**状态**: IN_PROGRESS
**类型**: Architecture Hardening / Implementation（dts-metrics + dts-metrics-webapp，跨服务依赖 dts-platform）
**目标**: 把 Sprint-35 的 dts-metrics 从"原型方向对、产品未成"推到"可水平部署、可验收"，落实架构评审发现的 6 个缺陷修复（metrics 侧实现，platform 侧依赖明确标注）。

## 背景

Sprint-35 把 React Flow 指标工作台重构为清晰的 ELT 分层契约（DWS/ADS 默认入口、候选 artifact、platform 控制面边界），方向正确。但 2026-06-13 架构评审发现落地已背离自己写下的不变量，详见 `assets/architecture-review.md`。核心问题：

1. 🔴 **被指定为"事实源"的服务零持久化** —— model state / version / rollback / graph draft 全在 `ConcurrentHashMap`，重启即丢、只能单实例，与"事实源"承诺矛盾。
2. 🟠 **新旧两条 artifact 链路安全不对等** —— 老 metric-pack 链路有 permission+RLS+audit 纵深防御，新 graph lifecycle 链路（Sprint-35 主路径）全无，policySource/predicateHash 是占位符。
3. 🟠 **发布无一致性/saga** —— BI/lineage/audit register 三个 internal 端点契约里有、主代码零调用点，发布闭环断裂、跨服务状态可分叉。
4. 🟡 **领域模型全是 `Map<String,Object>`** —— 契约纸上精确、代码零编译期保障。
5. 🟡 **platform 同步依赖零韧性** —— RestClient 无超时/重试/熔断。
6. 🟡 **API 契约漂移** —— 文档说走 `/internal/metrics/visual-assets`，实际调通用 `/catalog/assets-v2` 且无逐资产权限富化。

本 sprint 是 Sprint-35 的硬化续期（参照 Sprint-31a/31b 对 Sprint-31 的关系），不新增业务目标，只闭合 Sprint-35 的完成标准与 IT 准入。

## Feature 列表

| ID | Feature | 优先级 | Task 数 | 状态 | 缺陷 | 依赖 |
|----|---------|--------|---------|------|------|------|
| F1 | 领域持久化层 | P0 | 5 | DONE | #1 | Sprint-35 F4 |
| F2 | 安全链路统一与审计收口 | P0 | 4 | DONE | #2,#3(审计) | F1 |
| F3 | 发布一致性与跨服务收口 | P0 | 3 | READY | #3 | F1,F2 |
| F4 | 韧性与契约对齐 | P1 | 3 | IN_PROGRESS（T01 DONE） | #5,#6 | - |
| F5 | 领域类型化 | P1 | 2 | READY | #4 | F1 |
| F6 | IT 准入与验收证据 | P0 | 3 | READY | 全部 | F1-F4 |

**统计**: READY=3, IN_PROGRESS=1, DONE=2, BLOCKED=0
**进度**: F1（持久化 🔴#1）、F2（安全对等+审计 #2/#3审计）、F4-T01（RestClient 超时 #5）已实现并验证绿（90 单测含 6 安全对等 + 4 Testcontainers IT，2026-06-14）。下一步 F3 发布一致性（saga + BI/lineage/audit 注册，跨 platform 端点）。

## 完成标准

- [ ] dts-metrics 的 graph draft / model state / version / rollback 落库（mirror 同仓 dts-platform/dts-admin 的 datasource+Liquibase 约定），重启不丢、支持多实例。
- [ ] 模型版本写入带乐观锁，并发发布不产生脏版本。
- [ ] graph lifecycle 链路与 metric-pack 链路的 permission / RLS / masking / audit 行为对等，policySource/predicateHash 来自真实 platform 解析而非占位符。
- [ ] generate / validate / publish / rollback 每个阶段都向 platform `/internal/audit-events` 落审计。
- [ ] publish 编排具备幂等与失败补偿，BI Dataset register + lineage register 已接入（platform 端点就绪时联调）。
- [ ] RestClient 配置连接/读超时，platform 抖动不再无界挂起。
- [ ] visual-assets 端点契约与实现一致（消除 `/catalog/assets-v2` 漂移）。
- [ ] 核心契约 DTO（GraphNode/VisualAssetSummary/ValidationDiagnostic/ModelState）record 化。
- [ ] IT 证据覆盖：重启持久性、安全对等、发布闭环/回滚，落在 `it/evidence/`。

## 非目标

- 不在本 sprint 实现 platform 侧的 BI Dataset / lineage / audit-event 端点本体（属 platform 职责），只在 metrics 侧接入调用并标注联调依赖。
- 不引入 cube cache / cost-based optimizer / GraphQL（见 v2.3 Backlog）。
- 不执行历史 `semantic_*` 生产迁移（沿用 Sprint-35 dry-run 策略）。
- 不改 dts-metrics 与 platform 的职责边界，只补 metrics 侧的持久化、安全对等与韧性。

## 执行方式

F1（持久化）+ F2（安全对等）+ F4-T01（RestClient 超时）通过 Workflow 多代理编排实现（design-first，mirror 同仓服务约定，末段并行 build/test/review/gitnexus 影响分析）；在专用分支 `feat/sprint-35b-dts-metrics-hardening` 上落地，评审通过后再提交。F3 跨服务部分与 F5/F6 视 F1/F2 落地结果排期。

## 相关材料

- 架构评审底稿: `assets/architecture-review.md`
- Sprint-35 原始计划: `worklog/v2.2.3/sprint-35-202605/README.md`
- IT 计划: `it/README.md`
