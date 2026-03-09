# Status Board

| ID | Type | Module | Summary | Status | Notes |
|---|---|---|---|---|---|
| BUG-001 | BUG | platform | baseline compile shows dependency convergence warnings | done | fixed by dependencyManagement version alignment |
| BUG-002 | BUG | platform | stabilize backend test baseline after downgrade to `2.2.1` | done | `./mvnw -ntp test` is green; `npm run backend:unit:test` now fails only at integration-test Docker access and `~/.m2` checkstyle permissions |
| UI-001 | UI | platform-webapp | development-center route chunking and first-load polish | done | route overlap warnings removed; remaining large chunks split into future optimization |
| UI-002 | UI | platform-webapp | hide reserved realtime-status card for non-`cdc` ETL tasks | done | `TransformDetailPage` only shows realtime card for `cdc`; batch tasks keep execution info and incremental checkpoints only |
