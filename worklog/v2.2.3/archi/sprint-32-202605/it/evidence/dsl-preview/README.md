# Sprint-32 DSL SQL 生成与安全预览证据

## 状态

**READY**：实现和测试用例已补齐，最终执行待 Sprint-31A/31/32 全部完成后统一进行。

## 覆盖范围

- `MetricFormulaSqlGenerator` 支持受控 DSL：
  - `aggregation`
  - `count_if` / `conditional_count`
  - `sum_if` / `conditional_sum`
  - `ratio`
  - `date_trunc`
  - `case_when`
- 标识符必须通过安全白名单，禁止把任意 SQL 拼进字段名。
- `ratio` 自动生成分母为 0 的 `case when ... then null` 保护。
- `MetricArtifactGenerationService` 可从 inline metric DSL 生成候选 dbt SQL、`schema.yml` 和指标说明。

## 待执行命令

```bash
cd source/dts-metrics
mvn -q -Dtest=MetricFormulaSqlGeneratorTest,MetricArtifactGenerationServiceTest test
```

## 阻断条件

- 可通过 DSL 字段名注入 SQL。
- ratio 指标未处理分母为 0。
- 生成 SQL 缺少明确 `group by`。
- 生成物绕过 platform/dbt release gate 直接发布。
