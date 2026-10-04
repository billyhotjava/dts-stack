# Sprint-11 Regression Matrix

Old `SqlWorkbenchExperimental` → new SQL IDE mapping. Every feature must be verified in both legacy (flag off) and new (flag on) paths.

| 老功能 | 新版位置 | 验证方式 | 状态 |
|---|---|---|---|
| 写 SQL | F1 SqlEditor (Monaco) | E2E | 待测 |
| 数据源选择 | F3 Schema Select 下拉 | E2E | 待测 |
| Schema 树展开 | F3 SchemaTree | E2E | 待测 |
| 搜索表 | F3 SchemaTree 搜索框 | E2E | 待测 |
| 保存查询列表 | F3 SavedPanel | E2E | 待测 |
| 运行 SQL | F1 Ctrl+Enter / 工具栏 Run | E2E | 待测 |
| 取消执行 | F4 useSqlExecution.cancel | E2E | 待测 |
| 格式化 | F1 Ctrl+Alt+F | E2E | 待测 |
| 保存为查询 | F3 Ctrl+S SaveQueryDialog | E2E | 待测 |
| 沉淀为数据集 | 复用 QueryDatasetManager | E2E | 待测 |
| 行数限制 | F4 100k 上限硬编码 | — | 实现即满足 |
| 结果表格（基础） | F4 ResultGrid | E2E | 待测 |
| 结果分页 | F4 ResultGrid 底部分页 | E2E | 待测 |
| 复制结果 | F4 "复制全部 (TSV)" 按钮 | E2E | 待测 |
| 耗时/行数统计 | F5 LogPanel + BottomPanel 头 | E2E | 待测 |
| 日志 Tab | F5 LogPanel | E2E | 待测 |
| 历史 Tab | F3 HistoryPanel | E2E | 待测 |
| 导出（CSV/Excel/JSON） | F4 ExportMenu + 流式导出 | E2E | 待测 |
| 多 Tab | F2 TabBar + 持久化 | E2E | 待测 |
| Chart 可视化 | F5 ResultChart | E2E | 待测 |
| Pivot 透视 | F5 ResultPivot | E2E | 待测 |
| EXPLAIN 查询计划 | F5 QueryPlanView | E2E | 待测 |
| 二次查询 | F5 SubQueryButton | E2E | 待测 |

## 未覆盖（接受清单）

- 鼠标拖拽调整列宽 — `columnState` reducer 就绪但 UI handle 待 T13 followup 接入
- 图表保存到看板 — 本 Sprint 占位，未实现
