# DTS Stack

Stack owns the lakehouse/data platform, governed query execution, canonical
metrics and BI. The `analytics/` directory is the imported Java 21 / Spring Boot
3.4.5 analytics baseline, moved out of Studio. It is an independent Maven project
with the existing Java packages, API paths and `dts-copilot-analytics` artifact ID.
This baseline does not yet implement the complete Iceberg/lakehouse platform.

## Build and test

Requires Java 21, Maven 3.9+, Python 3, Docker and a preloaded PostgreSQL test image:

```bash
./build.sh verify
./build.sh package
# Alternative explicitly selected preloaded test image:
STACK_TEST_POSTGRES_IMAGE=postgres:17.6 ./build.sh verify
```

The default image is `postgres:18.4`. Tests create an isolated `stack_test` database
on a random loopback port, override all inherited database credentials and remove
only their container. They neither load `.env` nor touch running business databases.
`MVN` can select a Maven executable. Building does not require Studio or Common.

## Runtime boundary

The default database is `dts_stack`, separate from Studio's `dts_studio`. The legacy
internal schema name `copilot_analytics` is preserved; provision it and supply
`PG_HOST`, `PG_PORT`, `PG_DB`, `PG_USER` and `PG_PASSWORD` externally. Changing these
source defaults does not move an existing database or deploy a new service. Existing
installations must keep explicit database settings until a data migration is reviewed.
The packaged JAR listens on the existing analytics port, 8092.

Local user/session handling and analytics persistence remain Stack-owned. Optional
AI/data-source operations use `CopilotAiClient` as an HTTP adapter, with
`COPILOT_AI_BASE_URL` supplied by deployment configuration. Empty/unavailable API
results now fail at that boundary; no direct Studio-schema lookup is performed.
Disabling Studio does not replace optional AI results with cross-database access.
Further data-source ownership/QueryGateway changes remain planned work.

The active Liquibase master no longer includes historical changesets 0060 and 0067
that wrote `copilot_ai.data_source`. Their original files remain byte-identical for
applied-checksum evidence. Stack initialization and repeat migration are tested
against a database containing only the Stack schema. Historical PRS demo seeds and
remaining data-source metadata are transitional, not production configuration.

External JDBC drivers come from Stack's own `drivers/`, `/opt/dts/jdbc` or the explicit
`dts.analytics.jdbc.drivers-dir` setting (or Spring environment variable
`DTS_ANALYTICS_JDBC_DRIVERS_DIR`). There is no hardcoded sibling service source directory.
A Docker image can be built from `analytics/` after packaging its JAR; container and
Kubernetes deployment remain Infra concerns.
