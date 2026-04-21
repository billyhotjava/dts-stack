# F2: 语义层后端核心

**优先级**: P0
**状态**: READY
**依赖**: F1

## 目标

实现语义层最小可用闭环：**dbt manifest → 指标元信息 → 单表 group-by 查询 → 密级注入 → 结果缓存 → 返回**。本 Feature 不处理多表 join（F3）、不处理派生指标（F4），只保证"一张表上的 measure × dimension × filter × time granularity → SQL → 正确结果"跑通端到端。

## 交付模块位置

```
source/dts-platform/src/main/java/com/yuzhi/dts/platform/
  service/semantic/                      ← 新增
    SemanticLayerService.java
    manifest/
      ManifestIngestor.java
      DbtManifestClient.java
      MetricDefinitionMapper.java
    meta/
      SemanticMetaAssembler.java
    compiler/
      QueryRequest.java
      SingleModelCompiler.java
      DialectAdapter.java               ← Postgres / Doris
    security/
      SecurityInjector.java
      ClassificationPolicy.java
    cache/
      ResultCacheKey.java
      ResultCache.java                  ← Redis
  web/rest/semantic/                    ← 新增
    SemanticMetaResource.java
    SemanticQueryResource.java

source/dts-platform/src/main/resources/
  config/liquibase/changelog/
    9001__sprint13_vds.xml             ← 仅 VDS 表（F3-T04 会改）
    9002__sprint13_semantic_snapshot.xml  ← GovIndicatorDefinition 扩字段
```

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | ManifestIngestor + GovIndicatorDefinition 扩字段 | P0 | READY | F1 |
| T02 | `/api/semantic/meta` 元信息端点 | P0 | READY | T01 |
| T03 | SingleModelCompiler — 单表 group-by 编译 | P0 | READY | T01 |
| T04 | SecurityInjector — 密级/行级 WHERE 注入 | P0 | READY | T03 |
| T05 | ResultCache — Redis 缓存层 | P1 | READY | T03 |

## 完成标准

- [ ] dbt manifest.json 上传后自动解析，3 张示例 model 的 metric/dimension 写入 `GovIndicatorDefinition`
- [ ] `/api/semantic/meta` 按 F1-T02 契约返回，前端可消费
- [ ] `/api/semantic/query` 对单表请求能编译 + 执行并返回 JSON
- [ ] 编译生成的 SQL 通过人工 review 正确（存 `it/evidence/f2-sql-review.md`）
- [ ] 密级超限请求返回 422
- [ ] row_security_predicate 正确注入（单用户多部门测试）
- [ ] 同一 query 二次调用命中缓存（`cache_hit: true`）
- [ ] 首批单元测试覆盖率 ≥ 75%
