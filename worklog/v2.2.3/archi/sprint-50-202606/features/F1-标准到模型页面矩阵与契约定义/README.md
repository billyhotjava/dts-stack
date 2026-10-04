# F1 标准到模型页面矩阵与契约定义

**状态**: DONE  
**目标**: 明确标准管理、SQL 建模、dbt 文件证据面的职责和接口契约。

## Tasks

| Task | 内容 | 状态 | 代码/证据 |
|------|------|------|-----------|
| T01 | 固化现有页面矩阵，不新增菜单 | DONE | `README.md`、`assets/data-standards-dbt-modeling-architecture.md` |
| T02 | 固化字段标准绑定后端 DTO | DONE | `SqlModelStandardBinding*` records |
| T03 | 固化前端 API 契约 | DONE | `platformApi.ts` |
| T04 | source-contract 覆盖路由、按钮、API | DONE | `dataDevelopmentWorkbench.source-contract.test.ts` |

## 验收

- source-contract 覆盖 `/studio/sql-modeling`、`/modeling/dbt-files`、`/governance/standards/elements`、`/governance/standards/reference`。
