# Migration batches for dts-analytics (Clojure → Java)

This checklist maps the Clojure namespaces under `source/dts-bi-analytics` to Java package targets under `com.yuzhi.dts.analytics`. It will be updated as modules are migrated.

## Batch 1 — Bootstrap (DONE)
- Spring Boot app entrypoint, configuration properties, health/info endpoints.
- Logging filter and base security chain (stateless, basic auth placeholder).

## Batch 2 — Server & request pipeline (IN PROGRESS)
- **Clojure namespaces**: `metabase/server/*`, `metabase/request/*`, `metabase/server/middleware/*`.
- **Java targets**:
  - `config` — HTTP/Security/CORS, metrics, Jackson, locale/timezone.
  - `web.filter` — request ID, logging, metrics, compression.
  - `web.rest.errors` — exception translator, problem details payloads.
  - `security` — Spring Security adapter for session/cookie auth and token flows.
  - `web.support` — request context, forwarded headers, client info, user/session resolution.
- **Exit criteria**:
  - Spring MVC/Security pipeline replaces Clojure middleware stack.
  - Request context + error model are wired and covered by MockMvc tests.
  - Legacy server/request namespaces deprecated in the inventory.

## Batch 3 — Core services & persistence
- **Clojure namespaces**: `metabase/app_db/**`, `metabase/model_persistence/**`, `metabase/task/**`, `metabase/sync/**`.
- **Java targets**:
  - `domain` + `repository` — JPA entities replacing Toucan models.
  - `service` — schedulers, sync orchestration, persistence layer operations.
  - `scheduler` — Quartz/Spring scheduling analogs for Metabase task runners.

## Batch 4 — API surfaces
- **Clojure namespaces**: `metabase/api/**`, `metabase/*_rest/**`, `metabase/api_routes/**`.
- **Java targets**:
  - `web.rest` — REST controllers per route family.
  - `service.dto` + `service.mapper` — DTOs/MapStruct mappers for payloads.

## Batch 5 — Drivers & integrations
- **Clojure namespaces**: `modules/drivers/**/src/metabase/driver/**`.
- **Java targets**:
  - `integration` — SPI interfaces and implementations for database/warehouse connectors.
  - `integration.config` — per-driver configuration and capability declarations.

## Batch 6 — Tooling & tests
- **Clojure namespaces**: `dev/**`, `mage/**`, `test/**`, `enterprise/backend/test/**`, `.clj-kondo/**`, `test_resources/**`.
- **Java targets**:
  - `src/test/java` — JUnit + MockMvc replacements for Clojure tests.
  - Build/IDE helpers and local dev scripts translated or superseded.

## Tracking
- For each migrated namespace, link the replacement Java package/class and mark the Clojure source as deprecated/removed.
- Keep parity checkpoints after each batch (build, smoke tests, key API flows).
