# Sprint-11 E2E Test Report

**Status**: Planned (implementation pending)
**Sprint**: v2.2.3 Sprint-11 — SQL IDE

## Scope

17+ user-journey tests covering:

- [ ] Open SqlIdePage (flag enabled), verify blank state renders
- [ ] Monaco loads, syntax highlighting on `SELECT * FROM foo`
- [ ] Ctrl+Enter executes; result appears in Results tab
- [ ] Multi-tab: open 3 tabs, switch between them, state persists
- [ ] Cross-device recovery: open Tab A on device 1, sign in on device 2, Tab A appears
- [ ] Schema tree: expand datasource → schema → tables; double-click inserts `schema.table` at cursor
- [ ] Right-click table → Generate SELECT → SQL inserted
- [ ] Run query → switch to Chart tab → auto-recommend shows relevant chart
- [ ] Run query → Pivot tab → drag fields → aggregated result
- [ ] Run query → Plan tab → click EXPLAIN → tree renders
- [ ] Run query → Log tab → SQL + metadata visible
- [ ] Export CSV → browser downloads file
- [ ] Export Excel → .xlsx opens in Excel
- [ ] Simple/Advanced toggle → ActivityBar icon count changes
- [ ] Rate limit: export 6 times → 6th rejected (429)
- [ ] Long SQL text (1MB) in tab persists + recovers
- [ ] Feature flag OFF → `/explore/workbench` routes to old QueryWorkbenchPage

## Execution

Not yet run. Playwright setup deferred to dedicated test-infra task.
