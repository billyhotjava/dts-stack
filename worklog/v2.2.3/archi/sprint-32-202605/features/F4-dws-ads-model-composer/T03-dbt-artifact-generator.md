# T03: dbt artifact generator

**优先级**: P0
**状态**: READY
**依赖**: T02

## 目标

从模型节点生成 dbt SQL、schema.yml、exposure/metric 文档和 lineage hint。

## 技术设计

- SQL 只由受控 DSL 生成，不拼接用户任意 SQL。
- schema.yml 包含列描述、测试建议、metric metadata。
- exposure/lineage hint 记录 source -> model -> BI Dataset。

## 影响范围

- `source/dts-metrics` artifact generation service
- `source/dts-platform` model validation gateway

## 验证

- [ ] 生成 SQL 不含 DDL/DML。
- [ ] schema.yml 可被 dbt compile 解析。

## 完成标准

- [ ] 生成物可提交 platform/dbt validation gateway。
