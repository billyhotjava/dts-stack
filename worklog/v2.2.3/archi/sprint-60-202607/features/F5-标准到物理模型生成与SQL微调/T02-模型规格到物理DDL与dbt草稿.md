# T02: 模型规格到物理 DDL 与 dbt 草稿

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

把模型规格转换为可预览、可比较、可发布的物理 DDL、dbt model 和 `schema.yml` 草稿。

## 技术设计

转换策略：

| 输出 | 规则 |
|------|------|
| 物理 DDL | 根据目标引擎生成表、视图或物化视图定义 |
| dbt model | 生成 `select` 骨架、`ref/source` 引用和 materialization 配置 |
| schema.yml | 输出字段描述、数据类型、标准 meta、dbt tests |
| diff | 和已发布物理表比较字段新增、删除、类型变化、分区变化 |

发布模式需要至少支持：

- 首次创建。
- 增量变更。
- 删除重建，必须高风险提示。

## 影响范围

- `source/dts-platform-webapp/src/pages/modeling/SqlModelingPage.tsx`
- `source/dts-platform-webapp/src/pages/modeling/LowCodeDevelopmentPage.tsx`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelingSqlModelService.java`
- `services/dts-dbt/`

## 验证

- [ ] ODS/DWD/DWS/ADS 模型规格都能生成预览。
- [ ] DDL 中字段名、类型、注释、分区与数据元一致。
- [ ] `schema.yml` 中包含 standard code、standard version、code set、安全级别。
- [ ] 已发布模型再次生成时显示结构 diff。

## 完成标准

- [ ] 用户能在发布前看到 DDL/dbt/schema.yml 三类输出。
- [ ] 危险变更有显式风险提示。
- [ ] 生成内容能被保存为发布记录的一部分。
