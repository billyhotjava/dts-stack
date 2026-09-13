# Sprint-11 审计覆盖清单

13 类审计动作（`SqlIdeAuditActions`）在代码中的落点。

| # | 常量 | 值 | 触发点 | 载荷字段 | 状态 |
|---|---|---|---|---|---|
| 1 | SQL_EXECUTE_SUBMIT | `sql.ide.execute.submit` | `SqlExecutionService.executeQueued`（post-rewrite） | engine, sqlHash, rewrittenHash | ✅ 已落 |
| 2 | SQL_EXECUTE_CANCEL | `sql.ide.execute.cancel` | `SqlExecutionService.cancel` | reason | ✅ |
| 3 | SQL_EXECUTE_COMPLETE | `sql.ide.execute.complete` | `SqlExecutionService` 终态分支 | status, rows, elapsedMs | ✅ |
| 4 | SQL_RESULT_VIEW | `sql.ide.result.view` | `SqlIdeExecutionController.page` | page, size | 待迁移到常量 |
| 5 | SQL_RESULT_EXPORT | `sql.ide.result.export` | `SqlIdeExecutionController.export` | format | 待迁移到常量 |
| 6 | SQL_RESULT_COPY | `sql.ide.result.copy` | `SqlIdeAuditController.copy` | cells | ✅ |
| 7 | SQL_TEMP_VIEW_CREATE | `sql.ide.temp_view.create` | `SqlIdeSubqueryController.create` | viewName, rowCount | ✅ |
| 8 | SQL_TEMP_VIEW_DROP | `sql.ide.temp_view.drop` | `SqlIdeSubqueryController.delete` | viewName | ✅ |
| 9 | SQL_SUBQUERY_EXECUTE | `sql.ide.subquery.execute` | `SqlIdeSubqueryController.query` | viewName, sqlHash, error? | ✅ |
| 10 | SQL_PLAN_VIEW | `sql.ide.plan.view` | `SqlIdePlanController.explain` | engine, sqlHash, errorSnippet? | ✅ |
| 11 | SQL_IDE_TAB_SAVE | `sql.ide.tab.save` | `SqlIdeTabResource.patch/batch` | tabId, sqlTextHash | 待迁移到常量 |
| 12 | SAVED_QUERY_LOAD | `sql.workbench.saved-query.load` | 旧路径沿用 | queryId | ✅ 兼容 |
| 13 | SQL_CATALOG_BROWSE | `sql.ide.catalog.browse` | `SqlIdeResource.catalog*` (F3 多端点) | dsId, schema, table | 待迁移到常量 |

## Followup

4 个动作点当前仍使用直接字符串字面量（#4/#5/#11/#13），应迁移到 `SqlIdeAuditActions` 常量使用保证一致性。
