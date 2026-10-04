# F2: 安全链路统一与审计收口

**优先级**: P0
**状态**: DONE（90 单测含 6 安全对等 + 4 持久化 IT 全绿，2026-06-14 验证）
**对应缺陷**: #2 新旧链路安全不对等、#3 审计缺口

## 目标

让 graph lifecycle 链路（Sprint-35 主路径）复用老 metric-pack 链路已验证的 permission / RLS / masking / audit 纵深防御，消除"主路径走更薄安全管子"的风险；并把 generate/validate/publish/rollback 全阶段审计落到 platform。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 抽取共享安全组件 MetricSecurityPolicyService | P0 | DONE | F1 |
| T02 | lifecycle 接入真实 permission + RLS 解析（替换占位符） | P0 | DONE | T01 |
| T03 | lifecycle 生成 SQL 注入 RLS/masking（对齐 pack 链路） | P0 | DONE | T02 |
| T04 | 全阶段审计 event 接入 platform /internal/audit-events | P0 | DONE（开关默认关，platform 端点联调=F3-T03） | T01 |

## 完成标准

- [ ] `MetricArtifactGenerationService` 与 `MetricModelLifecycleService` 共用同一套 permission/RLS/audit 组件，无重复实现。
- [ ] lifecycle 的 policySource/predicateHash 来自 `platformContractClient.resolveRlsPolicy` 真实解析，非硬编码。
- [ ] lifecycle 生成的 dbt SQL 与 pack 链路一样注入 RLS WHERE 与 masking。
- [ ] generate/validate/publish/rollback 四个动作都向 platform 落审计事件。
- [ ] 安全对等测试证明两条链路在相同输入下 permission/RLS/audit 行为一致。
