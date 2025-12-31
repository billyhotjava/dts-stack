# Clojure → Java rewrite roadmap

This roadmap turns the full `source/dts-bi-analytics` Clojure inventory into a sequenced Java rewrite plan under `com.yuzhi.dts.analytics`.

## Phase 1 — Server/request pipeline (Batch 2)
- Target namespaces: `metabase/server/*`, `metabase/request/*`, `metabase/server/middleware/*`.
- Java focus: servlet filters (request ID, logging, metrics), CORS/security chain, locale/timezone/Jackson config, global exception mapping, API base error model.
- Deliverables: replace server entry and middleware stack with Spring MVC/Security equivalents; retire Clojure middleware once parity tests pass.

## Phase 2 — Core services & persistence (Batch 3)
- Target namespaces: `metabase/app_db/**`, `metabase/model_persistence/**`, `metabase/task/**`, `metabase/task_history/**`, `metabase/sync/**`, `metabase/startup/**`.
- Java focus: domain entities + repositories (Toucan → JPA), schedulers (Quartz/Spring), sync orchestration, task history persistence, startup lifecycle hooks.
- Deliverables: first runnable data path with DB migrations, task scheduler, sync lifecycle, and smoke tests covering app startup and task execution.

## Phase 3 — API surfaces (Batch 4)
- Target namespaces: `metabase/api/**`, `metabase/*_rest/**`, `metabase/api_routes/**`, `metabase/actions_rest/**`, `metabase/permissions_rest/**`, `metabase/embedding_rest/**`, `metabase/users_rest/**`, `metabase/warehouse_schema_rest/**`, `metabase/warehouses_rest/**`.
- Java focus: REST controllers, DTOs, mappers, validation, pagination/sorting, authentication/authorization guards.
- Deliverables: route parity for critical APIs (auth/session, metadata, queries, dashboards, search) with MockMvc/contract tests; deprecated corresponding Clojure handlers.

## Phase 4 — Query engine and analytics features
- Target namespaces: `metabase/query_processor/**`, `metabase/queries/**`, `metabase/parameters/**`, `metabase/native_query_snippets/**`, `metabase/analytics/**`, `metabase/xrays/**`, `metabase/search/**`, `metabase/pulse/**`, `metabase/notifications/**`.
- Java focus: query planning/execution pipeline, caching, parameter binding, analytics insights, x-rays, pulses/alerts, notification dispatchers.
- Deliverables: end-to-end query execution path with tests covering MBQL/native queries, caching behavior, pulse scheduling, and analytics/x-rays flows.

## Phase 5 — AuthN/Z, sessions, and permissions
- Target namespaces: `metabase/auth_identity/**`, `metabase/auth_provider/**`, `metabase/sso/**`, `metabase/session/**`, `metabase/permissions/**`, `metabase/user_key_value/**`, `metabase/users/**`.
- Java focus: authentication providers, SSO/SAML/OIDC flows, session/token handling, permission graph, impersonation, login history, audit events.
- Deliverables: Spring Security-based auth stack with permission checks integrated across controllers/services; removal of Clojure auth/session namespaces.

## Phase 6 — Drivers & integrations (Batch 5)
- Target namespaces: `modules/drivers/**/src/metabase/driver/**` (16 drivers) plus shared `metabase/driver/**`, `metabase/driver_api/**`, `metabase/plugins/**`.
- Java focus: driver SPI, capability declarations, SQL/non-SQL adapters, schema introspection, query translation, driver-specific configuration.
- Deliverables: Java driver interfaces and per-driver modules; integration tests for major engines (e.g., BigQuery, ClickHouse, Mongo, Snowflake) with stubs/mocks where needed.

## Phase 7 — Enterprise modules
- Target namespaces: `enterprise/backend/src/metabase_enterprise/**` (analytics, permissions, remote sync, metabot, semantic search, transforms, SCIM/SSO, etc.).
- Java focus: enterprise-only features layered on the OSS stack; shared abstractions with OSS services.
- Deliverables: enterprise feature parity with dedicated tests; OSS/enterprise packaging strategy (profiles/modules) finalized.

## Phase 8 — Tooling, dev, and tests (Batch 6)
- Target namespaces: `test/metabase/**`, `enterprise/backend/test/metabase_enterprise/**`, `dev/**`, `mage/**`, `.clj-kondo/**`, `test_resources/**`.
- Java focus: replace Clojure tests with JUnit/MockMvc/integration suites; migrate dev tooling to Gradle/Maven tasks or shell equivalents.
- Deliverables: green Java test suite covering parity cases; retired Clojure tests and dev scripts.

## Tracking and exit criteria
- Maintain an updated checklist tying each Clojure namespace to its Java replacement (see `docs/migration-batches.md` and `docs/clojure-inventory.md`).
- For each phase: implement → test → deprecate/remove Clojure counterpart → update inventory with status.
- Exit when all inventory namespaces are either replaced or explicitly retired, with CI pipelines running the Java test matrix only.
