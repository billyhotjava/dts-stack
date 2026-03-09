# Status Board

| ID | Type | Module | Summary | Status | Notes |
|---|---|---|---|---|---|
| FE-001 | UI-REFactor | all-webapps | unify light-first design contract and token semantics | in-progress | token contract landed in 5 style entry files; `platform/admin` build passed, `analytics modern` verification blocked by missing local deps (`vite` absent, `pnpm install` hit `ENOTFOUND registry.npmjs.org`) |
| FE-002 | UI-REFactor | platform-webapp,admin-webapp | rebuild shared console shell | done | header/main/nav shell refreshed in both apps; search trigger and account dropdown aligned to the new console style; `platform/admin` builds both passed |
| FE-003 | Cleanup | platform-webapp,admin-webapp | remove fake notices, demo services, placeholder surfaces | done | deleted low-risk dead code, placeholder surfaces, and `sys/others` demo pages/materials; `platform/admin` builds both passed; retained only generic `blank` and external-link utility pages |
| FE-004 | UI-REFactor | admin-webapp | refactor customer-visible admin pages | done | added shared admin console page primitives, rebuilt infra/workflow pages into console-style layouts, and turned `/admin/system` into a real overview page; `pnpm -C source/dts-admin-webapp build` passed |
| FE-005 | UI-REFactor | platform-webapp | refactor customer-visible platform pages | planned | remove reserved content and align cards/header/status surfaces |
| FE-006 | UI-REFactor | analytics-webapp/modern | rebuild analytics console shell | planned | `AppLayout` must visually join the same product family |
| FE-007 | UI-REFactor | analytics-webapp/modern | refactor analytics workspace shell | planned | designer header, canvas toolbar, property/layer/page panels |
| FE-008 | UI-REFactor | analytics-webapp/modern | refactor analytics runtime shell | planned | public/preview/export control surfaces only, keep screen canvas themeable |
| FE-009 | Cleanup | analytics-webapp/modern | remove demo adapters and placeholder runtime logic | planned | replace with real empty or unavailable states where needed |
| FE-010 | Verification | all-webapps | build verification and visual audit closeout | planned | three builds green, screenshot/manual audit checklist complete |
