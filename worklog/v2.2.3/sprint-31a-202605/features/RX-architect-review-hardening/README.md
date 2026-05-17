# RX: 架构评审追补项

**优先级**: P0
**状态**: DONE

## 目标

吸收外部架构评审中关于资产、语义、指标三层整合裂缝的有效意见，补齐当前版本可安全承接的契约和导入 guardrail。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 整合裂缝评审与低风险契约追补 | P0 | DONE | F1-F6, Sprint-32 |

## 完成标准

- [x] 已区分当前版本承接项和 v2.3 运行时增强项。
- [x] 资产类型覆盖已存在的 Modeling / Governance / Standard / Service / Policy 实体。
- [x] asset key 契约声明 tenant/env/dialect 维度。
- [x] metric-pack 校验强制 glossary term、tenant/owner namespace、RLS 和 pack dependency。
- [x] 有针对性单测和静态检查。
