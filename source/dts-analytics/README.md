# DTS Analytics Java Rewrite (2.1.0)

This module bootstraps a JHipster-style Spring Boot stack (mirroring `source/dts-admin`) to host the Java rewrite of the existing Clojure analytics service (currently under `source/dts-bi-analytics`). The goal is to migrate the runtime code first, then supporting tooling/tests.

## Stack Highlights
- Spring Boot 3.4.x + JHipster framework 8.x
- Java 21, Maven build (`pom.xml`)
- Package root: `com.yuzhi.dts.analytics`
- Starter REST endpoints: `GET /api/health`, `GET /api/info`
- Request ID middleware + structured error responses
- MockMvc integration tests validating request ID propagation, error translation, and service metadata exposure

## Migration Guide
1. **Module grouping (Clojure source tree)**  
   High-level namespaces to migrate from `source/dts-bi-analytics/src` include:
   - Core/runtime: `metabase/server`, `metabase/core`, `metabase/api`, `metabase/task`, `metabase/app_db`, `metabase/sync`, `metabase/startup`, `metabase/settings`, `metabase/session`, `metabase/query_processor`, `metabase/analytics`, `metabase/model_persistence`, etc.  
   - REST surfaces: `metabase/api`, `metabase/actions_rest`, `metabase/dashboards_rest`, `metabase/embedding_rest`, `metabase/permissions_rest`, `metabase/public_sharing_rest`, `metabase/settings_rest`, `metabase/users_rest`, `metabase/warehouse_schema_rest`, `metabase/warehouses_rest`.  
   - Integrations/drivers: `modules/drivers/**/src` (databricks, bigquery, mongo, druid, snowflake, etc.).  
   - Cross-cutting: `metabase/events`, `metabase/logging`, `metabase/cache`, `metabase/security/auth_identity|auth_provider|sso`, `metabase/eid_translation`, `metabase/internal_stats`, `metabase/audit_app`, `metabase/task_history`, `metabase/batch_processing`.

   Dev/test helpers to migrate later: `dev/**`, `mage/**`, `test/**`, `enterprise/backend/test/**`, `.clj-kondo/**`, `test_resources/**`.

   See `docs/clojure-inventory.md` for the full inventory and file counts of the remaining Clojure namespaces (runtime, enterprise, drivers, tests, and tooling) to be rewritten.

2. **Package mapping (Java)**  
   - `com.yuzhi.dts.analytics.config`: configuration beans, profiles, Jackson, i18n, security.  
   - `com.yuzhi.dts.analytics.security`: auth/authorization, session handling.  
   - `com.yuzhi.dts.analytics.web.rest`: REST controllers replacing `metabase/...` HTTP handlers.  
   - `com.yuzhi.dts.analytics.service`: application services and orchestrators.  
   - `com.yuzhi.dts.analytics.service.dto` & `service.mapper`: DTOs and MapStruct mappers.  
   - `com.yuzhi.dts.analytics.domain` & `repository`: JPA entities and repositories (replacing Toucan/DB layers).  
   - `com.yuzhi.dts.analytics.scheduler`: job orchestration replacing Quartz/`metabase/task` workflows.  
   - `com.yuzhi.dts.analytics.integration`: external driver/connector facades (replacing `modules/drivers/**`).

3. **Batching strategy**
   - **Batch 1 (Bootstrap)**: Wire core app (`DtsAnalyticsApp`), base config, health/info endpoints, Maven build, baseline application.yml.  
   - **Batch 2 (Server & request pipeline — in progress)**: Replace `metabase/server` request handling with Spring MVC filters/interceptors; port session/cookie/security middlewares into Spring Security config; add request context utilities.  
   - **Batch 3 (Core services & persistence)**: Map `metabase/app_db`, `model_persistence`, `task` scheduling to Spring Data + Quartz/Scheduler; introduce domain entities.  
   - **Batch 4 (API surfaces)**: Translate key REST endpoints under `metabase/api`, `*_rest`, `actions_rest`, mapping request/response models to DTOs.  
   - **Batch 5 (Drivers/integrations)**: Recreate `modules/drivers/**` using Spring bean modules; expose SPI interfaces for database/warehouse connectors.  
   - **Batch 6 (Tooling & tests)**: Port dev tools, integration/unit tests to JUnit + MockMvc; retire Clojure tests and scripts.

4. **Working guidelines**
   - Keep parity with `source/dts-admin` layout (Spring profiles, configuration properties, `web/rest` layering).  
   - Migrate feature-by-feature: implement Java module, add tests, remove/flag the corresponding Clojure namespace.  
   - Track migrated namespaces in CHANGELOG or checklist per batch.  
   - Avoid broad refactors until feature parity is validated by tests or smoke endpoints.

## Next Steps (for upcoming batches)
- Add Spring Security configuration, global exception handlers, and request/response logging interceptors.  
- Model the first persistence slice (e.g., core metadata entities) and expose equivalent REST endpoints.  
- Establish migration checklist to mark Clojure namespaces as deprecated once their Java replacements are verified.
