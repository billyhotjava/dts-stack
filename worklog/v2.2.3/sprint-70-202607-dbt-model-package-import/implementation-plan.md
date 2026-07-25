# Sprint-70 implementation plan

## Global constraints

- Execute in order `F1 -> F2 -> F3 -> F4 -> F5`.
- Reuse the canonical `ModelSpec -> ModelImplementation -> artifact` services; do not promote the legacy SQL model tables into the main path.
- Preview must not write canonical models, implementations, artifacts, source inventory, or dbt workspace files.
- STG, ephemeral, macro, and test nodes stay in the technical dependency graph and never become one of the four canonical ModelSpec types.
- Never infer business grain, model type, domain, fact shape, dimension policy, consumption scenario, or security class from SQL or naming alone.
- Complex SQL remains `DBT_BACKED`; it must not be represented as a fabricated visual mapping.
- Apply must revalidate the preview hash and target versions, be atomic per candidate, fail closed on conflicts, and be idempotent.
- Existing dirty-worktree changes are preserved. No task may revert or overwrite unrelated edits.
- Implement F1-F4 before the single final backend test batch, frontend production build, and Chrome 95 E2E run.

## Task 1: F1 model-package contract and converter

Requirements are the files under `features/F1-模型包契约与转换器/` plus
`assets/model-package-architecture.md`.

Deliver the versioned internal JSON Schema, deterministic checksum/validation,
safe dbt ZIP inspection, manifest/catalog/schema and legacy TSV conversion,
conversion classifier, technical-node preservation, repo-native generator,
focused tests, and PJM golden fixture. ZIP is the UI input; JSON remains an
internal/automation contract.

## Task 2: F2 preview and diff control plane

Requirements are the files under `features/F2-导入预检与差异分析/` plus
`assets/import-api-contract.md`.

Deliver persisted/recoverable preview runs, target-context validation,
dependency topology and actions, stable issue codes, preview hash, preview
API, and run query API. Preview is strictly zero-write to modeling truth.

## Task 3: F3 canonical apply engine

Requirements are the files under `features/F3-canonical模型应用引擎/`.

Deliver revision-pinned ModelSpec application, normal/DBT-managed
implementation ownership, dbt and technical artifacts, per-candidate
transactions, idempotency, concurrency guards, partial failure, and retry.

## Task 4: F4 shared import journey

Requirements are the files under `features/F4-建模工作台导入体验/` plus
`assets/import-ui-flow.md`.

Deliver one shared four-step wizard used by Modeling Workbench and Model
Center, ZIP upload and server inspection, locked plan context, preview matrix,
apply confirmation, persistent result view, model navigation, and failed-only
retry. Do not expose JSON as the user upload contract, add a top-level menu,
or add a second state implementation.

## Task 5: F5 integrated verification and delivery

Requirements are the files under `features/F5-集成验收与交付/` and
`it/README.md`.

After Tasks 1-4 are complete, run one relevant backend test batch, one
frontend production build, then a real PostgreSQL/authenticated Chrome 95
PJM journey. Save evidence and finish the operator guide and Go/No-Go.
