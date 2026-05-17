# RX: 架构评审追补项

**优先级**: P0
**状态**: CONTRACT_DONE / ENFORCEMENT_IN_PROGRESS

## 目标

吸收外部架构评审中关于资产、语义、指标三层整合裂缝的有效意见，补齐当前版本可安全承接的契约和导入 guardrail。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 整合裂缝评审与低风险契约追补 | P0 | CONTRACT_DONE | F1-F6, Sprint-32 |
| T02 | 测试边界与 metric-pack 外部引用收窄 | P0 | DONE | T01 |
| T03 | CodeAssetGrantWriter 接入 | P0 | DONE | T01, T02 |
| T04 | CatalogAssetIdentityResolver 扩展 | P0 | DONE | T03 |
| T05 | Metric artifact preview / RLS enforcement | P0 | IN_PROGRESS | T01, T02 |

## 完成标准

- [x] 已区分当前版本承接项和 v2.3 运行时增强项。
- [x] 资产类型覆盖已存在的 Modeling / Governance / Standard / Service / Policy 实体。
- [x] asset key 契约声明 tenant/env/dialect 维度。
- [x] metric-pack 校验强制 glossary term、tenant/owner namespace、RLS 和 pack dependency。
- [x] 有针对性单测、negative IT fixture 和静态检查。
- [x] Glossary existence / ACTIVE 状态检查已进入 artifact preview/import 运行时。
- [x] Artifact preview 已执行 source asset platform permission check，未授权时不暴露资产细节。
- [x] CodeAssetGrantWriter 已接入 `GovIndicatorDefinition`、`ModelingSqlModel`、`DataStandard`、`ModelingGlossaryTerm` 与 `SvcApi` 高频保存/发布链路。
- [x] Resolver 已覆盖 `GLOSSARY_TERM` / `DATA_STANDARD` / `GOV_INDICATOR` / `MODELING_SQL_MODEL` / `METRIC_PACK` 当前版本身份解析。
- [x] Resolver 已覆盖 `API_SERVICE` 和 `scopedDataset(...)` key 的当前版本反向解析。
- [x] Metric artifact preview 已从 platform `/api/internal/v1/asset-permission/policy` 获取 RLS predicate，并注入候选 dbt SQL；legacy path 保留兼容。
- [x] Metric artifact preview 已输出 `securityPolicyJson`，携带 RLS predicate、masking columns 和 release gate 复核提示。
- [ ] platform/dbt gate 运行时二次比对同一 RLS/masking 策略和 live IT 继续独立闭环。
