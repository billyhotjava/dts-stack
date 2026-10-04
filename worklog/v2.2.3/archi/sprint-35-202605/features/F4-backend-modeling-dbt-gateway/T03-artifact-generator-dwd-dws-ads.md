# T03: DWD/DWS/ADS artifact generator

**优先级**: P0
**状态**: DONE
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

- [x] DWD 缺标准码时不生成候选 DWS。
- [x] 生成 schema.yml 包含 not_null 基础测试建议。
- [x] 当前生成 SQL 使用受控 graph/derived metric expression，不接受 `select/insert/update/delete/drop/alter` 等任意 SQL 片段。
- [x] 候选 artifact 包含 `securitySnapshot`，记录 policy source、predicate hash、classification、target layer 和 masking 标记。
- [x] model lifecycle 默认 DWS candidate SQL 已有 golden 对比。
- [x] SQL golden 覆盖 Postgres/Doris 方言边界。

## 实现记录

- `MetricModelLifecycleService.generateArtifacts` 已生成候选 `dbtModelSql`、`schemaYml`、`exposureYml`、`metricDoc`、`lineageHint` 和 `securitySnapshot`。
- graph preflight 失败时返回 `graph_validation_failed`，不会生成候选 artifact。
- `source/dts-metrics/src/test/resources/golden-sql/dws-order-summary-model.sql` 已固定默认 DWS candidate SQL。
- 当前仍是内存 lifecycle state，未落 DB 版本；Postgres 默认 golden 与 Doris identifier quoting focused test 已补齐。

## 完成标准

- [x] artifact 生成结果可被 platform/dbt validation 消费；当前 metrics 侧候选生成、DWD 标准码阻断、security snapshot、Postgres golden 和 Doris quoting 已覆盖。
