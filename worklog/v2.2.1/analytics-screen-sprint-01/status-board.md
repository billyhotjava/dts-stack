# Status Board

| ID | Type | Module | Summary | Status | Notes |
|---|---|---|---|---|---|
| AN-001 | Design | analytics-webapp/modern | finalize builtin template contract and project-management interaction contract v1 | done | landed `actions` contract type, spec validation, and template category extension without breaking legacy `interaction` |
| AN-002 | UI-Infra | analytics-webapp/modern | enhance template gallery metadata, categories, and search labels | done | template gallery now understands `qms/plm/hr/project-management`, uses ordered category tabs, and shows richer metadata |
| AN-003 | Template | analytics-webapp/modern | add builtin `QMS / PLM / HR / Finance` templates | done | added `qms-cockpit`, `plm-cockpit`, `hr-cockpit`, and `finance-execution-cockpit` as light-first builtin templates |
| AN-004 | Template | analytics-webapp/modern | add project-management cockpit template | done | added `project-management-cockpit` with KPI, milestone, risk/change, workload, todo/deliverable zones and baseline drill config |
| AN-005 | Interaction | analytics-webapp/modern | implement filter enhancement v1 | pending | scope includes shared filter bar and richer variable bindings |
| AN-006 | Interaction | analytics-webapp/modern | implement drill-down and roll-up enhancement v1 | pending | scope includes breadcrumb/up-level state and detail-panel drill entry |
| AN-007 | Interaction | analytics-webapp/modern | implement action entry model v1 | pending | `set-variable`, `drill-down`, `drill-up`, `jump-url`, `open-panel`, `emit-intent` |
| AN-008 | Verification | analytics-webapp/modern | build verification and manual audit closeout | in-progress | `pnpm -C source/dts-analytics-webapp/modern build` and `pnpm -C source/dts-analytics-webapp/modern typecheck` both passed on 2026-03-09; manual browser audit and interaction completion still pending |
