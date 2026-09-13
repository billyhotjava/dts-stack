# T02: RLS/masking policy 一致性

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

保证预览、候选生成、dbt validation 和发布阶段使用同一套 RLS/masking 策略，避免预览和发布口径不一致。

## 技术设计

- preview 阶段解析 platform policy，记录 `policySource` 和 `predicateHash`。
- artifact 中保存 security policy snapshot。
- validation/publish 阶段由 platform 重新解析 policy，并与 snapshot 比对。
- masked columns 在 DSL 和 SQL 生成中使用安全替代表达式，不按原始敏感值 group by。

## 影响范围

- `source/dts-metrics/src/main/java/**/MetricArtifactGenerationService.java`
- `source/dts-platform` policy resolve / release gate
- `source/dts-metrics/src/test/resources/golden-sql/**`

## 验证

- [ ] policy hash 变化时 publish dry-run 阻断。
- [ ] masked column 被指标公式引用时返回安全错误或使用 surrogate。
- [ ] 空 RLS policy 在 `apply_rls=true` 时阻断。

## 完成标准

- [ ] 安全策略在所有阶段一致且可审计。
