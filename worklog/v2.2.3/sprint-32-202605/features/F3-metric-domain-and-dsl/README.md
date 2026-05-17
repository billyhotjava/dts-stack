# F3: 指标领域模型、DSL 与安全生成

**优先级**: P0
**状态**: DONE
**目标**: 在 `dts-metrics` 内建立指标领域模型和受控公式 DSL，支撑 DWS/ADS/dbt artifact 安全生成。

**Sprint-31A 依赖**: 指标模型只能引用 platform asset contract 中的资产和字段，不能直接保存 platform 内部 Catalog 表 ID 作为唯一事实。

## 任务

| Task | 内容 | 验收 |
|---|---|---|
| T01 | 领域实体和 Liquibase | 支持主题域、业务对象、维度、指标、公式、模型、过滤条件、生成物、发布记录 |
| T02 | 指标公式 DSL v1 | 支持 sum、count、count_distinct、avg、count_if、sum_if、ratio、case_when、date_trunc |
| T03 | SQL 生成器 | 根据目标数据库生成只读 SQL，强制处理空值、除零、limit、timeout 和 group by |
| T04 | DWS/ADS 候选 artifact 生成 | 输出 dbt model SQL、schema.yml、字段说明、指标口径和 lineage hint，提交发布前仍需 platform/dbt gate |
| T05 | 预览安全控制 | 禁止 DDL/DML、禁止跨未授权资产、限制扫描规模，错误友好返回 |
| T06 | 版本和状态机 | 草稿、待审核、已发布、已撤销状态清晰，不允许跳过审核发布 |

## 完成标准

- [x] 不接受合作方任意 SQL 作为第一版指标公式。
- [x] ratio 指标必须处理分母为 0。
- [x] 所有生成 artifact 都能追溯到指标包、来源模型和候选发布边界。
- [x] Sprint-32 不承诺复杂跨事实表 join 优化，只支持显式声明来源模型的最小聚合链路。

## 证据

- `source/dts-metrics/src/main/java/com/yuzhi/dts/metrics/service/MetricFormulaSqlGenerator.java`
- `source/dts-metrics/src/main/java/com/yuzhi/dts/metrics/service/MetricArtifactGenerationService.java`
- `source/dts-metrics/src/test/java/com/yuzhi/dts/metrics/service/MetricFormulaSqlGeneratorTest.java`
- `source/dts-metrics/src/test/java/com/yuzhi/dts/metrics/service/MetricArtifactGenerationServiceTest.java`
- `worklog/v2.2.3/sprint-32-202605/it/evidence/dsl-preview/README.md`
