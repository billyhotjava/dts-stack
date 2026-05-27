# T03: DWD/DWS/ADS artifact generator

**优先级**: P0
**状态**: READY
**依赖**: T02

## 目标

生成符合 dbt 规范的候选 DWS/ADS artifact，并按分层规则处理 DWD、DWS 和 ADS。

## 技术设计

- DWS 默认入口：基于已有 DWS 生成指标模型或 BI Dataset 候选。
- DWD 高级入口：基于 DWD 明细、grain、标准维度和指标聚合生成 DWS 候选。
- ADS 入口：导入已有 ADS 做消费复用，不把 ADS 当新口径唯一真源。
- 输出：dbt SQL、schema.yml、metric doc、exposure doc、lineage hint、security policy snapshot。
- 生成 SQL 使用受控 DSL，不接受任意 SQL。

## 影响范围

- `source/dts-metrics/src/main/java/com/yuzhi/dts/metrics/service/MetricArtifactGenerationService.java`
- `source/dts-metrics/src/test/resources/golden-sql/**`
- `services/dts-dbt/models/**` 仅通过候选 artifact 验证，不直接写生产目录

## 验证

- [ ] DWD 缺标准码时不生成候选 DWS。
- [ ] 生成 schema.yml 包含 accepted_values 或 not_null 等基础测试建议。
- [ ] SQL golden 覆盖 Postgres/Doris 方言边界。

## 完成标准

- [ ] artifact 生成结果可被 platform/dbt validation 消费。
