# T02: ODS 到 dbt source 契约自动化

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

让 ODS 表进入 dbt source 注册/生成流程，减少手工导入模型和手工补 source 配置。

## 技术设计

- 复查现有 ODS mapping、dbt files、modeling sql model 和 `dts-dbt-import` 约束。
- 为 ODS 表生成或刷新 dbt source/schema.yml 候选产物。
- 产物必须附带源系统、owner、刷新频率、字段快照和变更记录。
- STG 如存在，仅作为 dbt 内部层或诊断层，不暴露为普通业务入口。

## 影响范围

- `source/dts-platform` modeling/dbt/catalog
- `source/dts-ingestion` ODS mapping
- `worklog` IT 脚本

## 验证

- [ ] 新增 ODS 表后能生成 dbt source 候选配置。
- [ ] 缺字段快照或 owner 时不能进入发布。

## 完成标准

- [ ] ODS 到 dbt source 不再依赖人工复制文件作为默认流程。
