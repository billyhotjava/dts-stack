# Clojure → Java rewrite TODO (priority-ordered)

Priorities are ordered for BI business impact: keep query paths, security, and data freshness first, then user-facing analytics APIs, followed by integrations and enterprise add-ons. Use this as the execution checklist; mark items as DONE/IN PROGRESS/NOT STARTED as migration proceeds.

## P0 — Core runtime & request pipeline
- [ ] Server & request middleware (`metabase/server/**`, `metabase/request/**`): MVC wiring, filters, compression, metrics, locale/timezone, session handling replacements.
- [ ] Security/auth foundations (`metabase/auth_identity/**`, `auth_provider/**`, `sso/**`, `session/**`): Spring Security equivalents for authn/z, SSO/OIDC/SAML, session/token lifecycles, login history, audit hooks.
- [ ] Error model & logging (`metabase/server/middleware/**`, `logger`, `events`): structured errors, request/trace IDs, audit/event emission, rate/size limits.

## P1 — Query engine & data freshness
- [ ] Query processor (`metabase/query_processor/**`, `queries/**`, `parameters/**`, `native_query_snippets/**`, `legacy_mbql/**`): planning/execution, MBQL/native support, parameter binding, caching, timeseries helpers.
- [ ] Data sync & tasks (`metabase/sync/**`, `task/**`, `task_history/**`, `startup/**`): schedulers, sync orchestration, job registry, task history persistence.
- [ ] App DB/model persistence (`metabase/app_db/**`, `model_persistence/**`, `database_routing/**`): JPA entities replacing Toucan, migrations, multi-db routing, transactional boundaries.
- [ ] Caching & secrets (`cache`, `secrets`): cache strategy, secret storage abstractions.

## P2 — Core BI APIs (user-facing)
- [ ] Metadata & permissions APIs (`metabase/api/**`, `api_routes/**`, `permissions/**`, `permissions_rest/**`, `users/**`, `users_rest/**`, `user_key_value/**`): controllers + DTOs + mappers, role/graph checks.
- [ ] Collections/dashboards/content (`collections/**`, `dashboards/**`, `dashboards_rest/**`, `revisions/**`, `tiles/**`, `public_sharing/**`, `public_sharing_rest/**`): CRUD, sharing, revisioning.
- [ ] Search & glossary (`search/**`, `indexed_entities/**`, `glossary/**`): search endpoints, relevance logic, indexing hooks.
- [ ] Notifications & pulses (`notification/**`, `pulse/**`): scheduling, channels, templates.
- [ ] Analytics & xrays (`analytics/**`, `xrays/**`): insight generation, x-ray flows, MBQL helpers.
- [ ] Uploads & assets (`upload/**`, `appearance/**`, `view_log/**`, `content_translation/**`, `content_verification/**`).

## P3 — Integrations & drivers
- [ ] Driver SPI + shared driver code (`metabase/driver/**`, `driver_api/**`, `plugins/**`): Java SPI, capability declarations, query translation interfaces.
- [ ] High-priority drivers (volume/enterprise critical): BigQuery, ClickHouse, Snowflake, Redshift, Starburst/Presto/Trino, Databricks, Mongo.
- [ ] Remaining drivers: Athena, Druid, Druid-JDBC, Hive-like, Oracle, SQL Server, SQLite, SparkSQL, Vertica.
- [ ] External services: `remote_sync/**`, `database_replication/**`, `database_routing/**` as part of sync/integration surfaces.

## P4 — Enterprise features
- [ ] Permissions/sandbox/impersonation (`metabase_enterprise/advanced_permissions/**`, `sandbox/**`, `impersonation/**`, `support_access_grants/**`, `permission_debug/**`).
- [ ] Semantic search & metabot (`semantic_search/**`, `metabot_v3/**`, `ai_*` modules, `llm/**`).
- [ ] Transforms & pipelines (`transforms/**`, `transforms_python/**`, `documents/**`, `upload_management/**`, `data_studio/**`).
- [ ] SSO/SCIM enterprise extensions (`auth_identity`, `auth_provider`, `sso`, `scim`).
- [ ] Billing/cloud add-ons, remote sync enterprise pieces, serialization/stale cleanup.

## P5 — Tooling & tests
- [ ] OSS tests (`test/metabase/**`) migrated to JUnit/MockMvc/integration suites.
- [ ] Enterprise tests (`enterprise/backend/test/metabase_enterprise/**`).
- [ ] Dev/tooling (`dev/**`, `mage/**`, `.clj-kondo/**`, `test_resources/**`): replace with Maven/Gradle tasks or shell helpers.

## Tracking/exit criteria
- For each namespace family above, link the replacing Java package/class and mark status (DONE/IN PROGRESS/NOT STARTED).
- Exit when all Clojure namespaces in `docs/clojure-inventory.md` are covered or explicitly retired, CI runs Java tests only, and Clojure deps are removed from the build.
