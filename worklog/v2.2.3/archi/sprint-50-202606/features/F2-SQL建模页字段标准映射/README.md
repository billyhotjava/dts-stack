# F2 SQL 建模页字段标准映射

**状态**: DONE  
**目标**: 在 SQL 建模页完成模型字段与数据元标准绑定，不做新页面。

## Tasks

| Task | 内容 | 状态 | 代码/证据 |
|------|------|------|-----------|
| T01 | 后端提供字段标准绑定查询接口 | DONE | `GET /api/modeling/sql-models/{id}/standard-bindings` |
| T02 | 后端提供字段标准绑定保存接口 | DONE | `PUT /api/modeling/sql-models/{id}/standard-bindings` |
| T03 | 绑定信息持久化到模型 `semanticContract.dts.standardBindings` | DONE | `ModelingSqlModelService` |
| T04 | SQL 建模页展示字段标准、码表、绑定状态 | DONE | `SqlModelingPage.tsx` |
| T05 | SQL 建模页支持自动匹配数据元并保存绑定 | DONE | `listMetadataStandards` + `saveSqlModelStandardBindings` |

## 验收

- `ModelingSqlModelServiceTest#saveStandardBindings_shouldPersistBindingsInSemanticContractAndGenerateDbtSchemaYml`
- source-contract 覆盖 `listSqlModelStandardBindings`、`saveSqlModelStandardBindings`、`自动匹配数据元`。
