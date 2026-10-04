# T05: live IT 验证多 dialect

**优先级**: P1
**状态**: DONE
**依赖**: T01-T04

## 目标

提供 live IT 证据，验证 RLS predicate 与 column masking 注入后的候选 SQL 在 PostgreSQL 与 Doris 两个目标 dialect 下能被执行；同时验证未授权 actor 完全拿不到任何 artifact。

## 背景

T01-T04 完成后，preview 与 publish 都会注入 platform policy。但 dts-metrics 当前的 SQL 生成器对方言差异 (`coalesce` vs `ifnull`、`{{ ref(...) }}` 解析、mask macro 兼容) 没有针对性 IT。Sprint-32 final IT 会跑完整 smoke，但本任务先做最小 IT 把 dialect 风险前置。

## 技术设计

1. 在 `dts-metrics/src/test/resources/golden-sql/` 增加两份 golden file：
   - `flower-rental-postgres.sql`
   - `flower-rental-doris.sql`
2. `MetricArtifactGenerationIT`（@Testcontainers）：
   - 启动 PostgreSQL 容器，注入 row-filter predicate `dept_code = 'D01'`，运行候选 SQL，断言返回行符合 predicate。
   - 启动 Doris（或最小 mock dialect compiler）做语法 parse，断言 SQL 可编译。
3. 增加 unauthorized actor 测试：preview 调用应被 platform 返回 403，并断言无任何 artifact 落地、错误不暴露资产名。
4. IT 失败时记录到 `it/evidence/rls-dialect-it-{date}.md`。

## 影响范围

- 新增 `source/dts-metrics/src/test/resources/golden-sql/`
- 新增 `source/dts-metrics/src/test/java/com/yuzhi/dts/metrics/it/MetricArtifactGenerationIT.java`
- 新增 evidence 目录 `worklog/v2.2.3/sprint-31b-202605/it/evidence/rls-dialect/`

## 验证

- [x] PostgreSQL 容器执行 RLS 注入 SQL，行数符合预期
- [x] Doris 最小方言编译/Golden 检查不报错
- [x] unauthorized actor preview 拒绝，artifact map 为空，错误信息无资产名

## 完成标准

- [x] 两个 dialect 各有 golden file 与 IT。
- [x] 未授权访问拒绝路径被 IT 验证。
- [x] evidence 文件归档。

## 交付证据

- 新增 `MetricArtifactGenerationIT`，使用 Testcontainers PostgreSQL 执行 RLS + masking 候选 SQL。
- 新增 `golden-sql/flower-rental-postgres.sql` 与 `golden-sql/flower-rental-doris.sql`。
- 验证命令：`./mvnw -q -pl dts-metrics -Dtest=MetricArtifactGenerationIT test`。
- 证据文档：`worklog/v2.2.3/sprint-31b-202605/it/evidence/rls-dialect/2026-05-18.md`。
