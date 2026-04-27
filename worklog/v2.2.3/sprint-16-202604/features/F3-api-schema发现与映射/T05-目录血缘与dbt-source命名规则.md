# T05: 目录、血缘与 dbt source 命名规则

**优先级**: P1
**状态**: DRAFT
**依赖**: T03

## 目标

让 API 原样落地 ODS 后能被现有目录、血缘、dbt source、stg 生成和质量规则正确识别。

## 范围

- 定义 API source service、resource、ODS dataset 的命名和展示字段。
- 定义 lineage edge：`external_api_resource -> ods_table -> stg_model -> dbt_model`。
- 定义字段级敏感标记从 schema snapshot / stg mapping 向目录传递规则，ODS 仅展示 raw record 和技术字段。
- 定义 API 数据源停用后目录资产状态。

## 完成标准

- [ ] dbt source YAML 或平台 source registry 能表达 API ODS 表。
- [ ] 目录中能看出来源是 API resource。
- [ ] 血缘链路不丢失外部 API 上游节点。
- [ ] 质量任务能绑定 API stg 模型；ODS 只做落地完整性和批次血缘校验。
