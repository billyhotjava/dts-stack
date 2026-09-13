# F3 公共码表到 dbt Seeds 联动增强

**状态**: DONE  
**目标**: 公共码表仍在标准管理页维护，SQL 建模页通过字段绑定引用码表编码，dbt schema.yml 生成 relationship test。

## Tasks

| Task | 内容 | 状态 | 代码/证据 |
|------|------|------|-----------|
| T01 | 公共码表页保留并显式展示“更新 dbt Seeds”同步状态 | DONE | `ReferenceCodesPage.tsx` |
| T02 | 字段标准绑定契约包含 `codeSet` | DONE | `SqlModelStandardBinding` |
| T03 | SQL 建模字段表展示字段绑定的码表编码 | DONE | `SqlModelingPage.tsx` |
| T04 | schema.yml 生成 `relationships` test 引用 seed | DONE | `buildSchemaYmlTests` |

## 验收

- source-contract 覆盖 `syncReferenceCodeSeeds`、`dbt Seeds 同步`。
- 后端目标单测断言 `codeSet: ORDER_STATUS`。
