# Clojure modules remaining to rewrite (source/dts-bi-analytics)

This inventory captures the current Clojure namespaces that still need Java rewrites. Counts were generated from `source/dts-bi-analytics` using `rg --files -g"*.clj"` and grouped by top-level namespace.

## Summary totals
- **Runtime (oss)**: 818 files under `src/metabase/**`.
- **Runtime (enterprise)**: 295 files under `enterprise/backend/src/metabase_enterprise/**`.
- **Drivers**: 96 files under `modules/drivers/**` (16 drivers).
- **Tests**: 648 oss tests under `test/metabase/**`; 247 enterprise tests under `enterprise/backend/test/metabase_enterprise/**`.
- **Dev/tooling**: 56 files under `dev/**` and `mage/**`.
- **Total Clojure files** tracked: 2,213.

## OSS runtime namespaces (src/metabase)
Key namespace families and file counts:

- Request/Server pipeline: `server` (27), `request` (8)
- Core data & persistence: `app_db` (22), `model_persistence` (8), `task` (4), `task_history` (5), `sync` (30), `database_routing` (1)
- APIs & routes: `api` (12), `api_routes` (2), REST families (`actions_rest` 1, `dashboards_rest` 1, `embedding_rest` 4, `permissions_rest` 4, `public_sharing_rest` 1, `settings_rest` 1, `users_rest` 1, `warehouse_schema_rest` 3, `warehouses_rest` 1)
- Domain features: `analytics` (10), `dashboard`/`dashboards` (8), `collections` (3), `revisions` (11), `tiles` (3), `xrays` (23), `notifications` (20), `pulse` (16), `search` (27), `segments` (3), `warehouse_schema` (9), `warehouses` (5)
- Query path: `query_processor` (87) plus `query_processor.clj` (1), `queries` (17), `parameters` (10), `native_query_snippets` (4), `legacy_mbql` (2), `tiles` (3)
- AuthN/Z & session: `auth_identity` (7), `auth_provider` (2), `sso` (12), `session` (7), `permissions` (17), `user_key_value` (4), `users` (6)
- Integrations/platform: `driver` (53) plus `driver.clj` (1), `driver_api` (2), `plugins` (7), `cache` (6), `events` (4), `logging` via `logger` (3), `public_sharing` (4)
- Content & assets: `appearance` (3), `content_translation` (1), `content_verification` (4), `glossary` (2), `view_log` (5)
- Misc/system: `core` (5), `config` (1), `startup` (1), `settings` (6), `system` (3), `version` (4), `util` (45)

## Enterprise runtime namespaces (enterprise/backend/src/metabase_enterprise)

- Analytics & AI: `analytics` (1), `ai_entity_analysis` (1), `ai_sql_fixer` (1), `ai_sql_generation` (1)
- Admin/permissions: `advanced_config` (10), `advanced_permissions` (7), `impersonation` (6), `permission_debug` (2), `support_access_grants` (8), `sandbox` (10)
- Auth & SSO: `auth_identity` (1), `auth_provider` (1), `sso` (15), `scim` (7)
- Content & documents: `documents` (9), `content_translation` (2), `content_verification` (2), `snippet_collections` (2)
- Data orchestration: `database_replication` (3), `database_routing` (4), `remote_sync` (15), `transforms` (25), `transforms_python` (8)
- Integrations: `gsheets` (4), `upload_management` (1), `llm` (6), `embedding_hub` (1), `harbormaster` (1)
- Operations: `audit_app` (12), `cache` (4), `dependencies` (11), `internal_stats` (1), `stale` (4)
- Product & UX: `metabot_v3` (38), `semantic_search` (27), `documents` (9), `data_studio` (2), `cloud_add_ons` (1)
- Security & sharing: `public_sharing` (1), `serialization` (11), `upload_management` (1)
- Support modules: `action_v2` (9), `core` (2), `library` (3), `billing` (2), `analytics` (1)

## Drivers (modules/drivers/*/src/metabase/driver)
Driver-specific Clojure code still to port (file counts per driver):

- `athena` (6), `bigquery-cloud-sdk` (9), `clickhouse` (12), `databricks` (3), `druid` (11), `druid-jdbc` (5), `hive-like` (3), `mongo` (20), `oracle` (3), `presto-jdbc` (3), `redshift` (3), `snowflake` (3), `sparksql` (3), `sqlite` (3), `sqlserver` (3), `starburst` (3), `vertica` (3)

## Tests and tooling
- OSS tests: `test/metabase/**` — 648 files spanning server pipeline, query processor, permissions, sync, search, pulse, drivers, etc.
- Enterprise tests: `enterprise/backend/test/metabase_enterprise/**` — 247 files covering metabot, semantic search, sandbox, transforms, SCIM/SSO, remote sync, serialization, etc.
- Dev/tooling: `dev/src` (29), `dev/test` (6), `mage/cmd` (1), `mage/src` (21), `mage/test` (6).

## How to use this inventory
- Use alongside `docs/migration-batches.md` to pick the next batch and tick off namespaces as Java replacements land.
- Prioritize runtime namespaces before tests/tooling; handle enterprise modules and drivers per batch once core server paths are stable.
