# Status Board

| ID | Type | Module | Summary | Status | Notes |
|---|---|---|---|---|---|
| AN-001 | Design | analytics-webapp/modern | finalize builtin template contract and project-management interaction contract v1 | done | landed `actions` contract type, spec validation, and template category extension without breaking legacy `interaction` |
| AN-002 | UI-Infra | analytics-webapp/modern | enhance template gallery metadata, categories, and search labels | done | template gallery now understands `qms/plm/hr/project-management`, uses ordered category tabs, and shows richer metadata |
| AN-003 | Template | analytics-webapp/modern | add builtin `QMS / PLM / HR / Finance` templates | done | added `qms-cockpit`, `plm-cockpit`, `hr-cockpit`, and `finance-execution-cockpit` as light-first builtin templates |
| AN-004 | Template | analytics-webapp/modern | add project-management cockpit template | done | added `project-management-cockpit` with KPI, milestone, risk/change, workload, todo/deliverable zones and baseline drill config |
| AN-005 | Interaction | analytics-webapp/modern | implement filter enhancement v1 | done | filter components now support default values and scope hints in both property panel and runtime initialization; dependent cards continue to refresh through existing variable binding chain |
| AN-006 | Interaction | analytics-webapp/modern | implement drill-down and roll-up enhancement v1 | done | drill runtime now works for static template paths as well as card-bound components; `table` and light-theme `scroll-board` rows can trigger drill/action entry, and project-management risk table exposes breadcrumb roll-up in preview |
| AN-007 | Interaction | analytics-webapp/modern | implement action entry model v1 | done | runtime now supports `set-variable`, `drill-down`, `drill-up`, `jump-url`, `open-panel`, and `emit-intent`; property panel can configure actions and runtime pages render a shared detail panel |
| AN-008 | Verification | analytics-webapp/modern | build verification and manual audit closeout | done | module tests, `typecheck`, `build`, `git diff --check`, and Playwright smoke all passed on 2026-03-09; visual acceptance checklist remains available for on-site signoff |
