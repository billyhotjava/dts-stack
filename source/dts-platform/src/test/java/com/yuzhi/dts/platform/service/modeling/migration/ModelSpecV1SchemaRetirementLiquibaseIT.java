package com.yuzhi.dts.platform.service.modeling.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ModelSpecV1SchemaRetirementLiquibaseIT {

    private static final String CHANGELOG =
        "config/liquibase/changelog/20260801_02_retire_model_spec_v1.xml";
    private static final String CHANGESET_ID = "20260801-02-retire-model-spec-v1";
    private static final Pattern OWNED_SCHEMA = Pattern.compile(
        "^s81_model_spec_v1_[0-9a-f]{32}$"
    );
    private static final UUID PLAN_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID DOMAIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final UUID MODEL_ID = UUID.fromString("00000000-0000-0000-0000-000000000103");
    private static final UUID REVISION_ID = UUID.fromString("00000000-0000-0000-0000-000000000104");
    private static final UUID BUSINESS_OBJECT_ID = UUID.fromString(
        "00000000-0000-0000-0000-000000000105"
    );
    private static final List<String> LEGACY_MODEL_COLUMNS = List.of(
        "object_id",
        "process_id",
        "dimensions",
        "metrics",
        "dbt_project_key",
        "dbt_unique_id",
        "legacy_ref",
        "legacy_refs"
    );
    private static final List<String> CANONICAL_CONSTRAINTS = List.of(
        "ck_model_spec_contract_version",
        "ck_model_spec_v2_identity",
        "ck_model_spec_v2_type_context",
        "ck_model_spec_v2_json_shapes",
        "ck_model_spec_revision_contract_version",
        "ck_model_spec_revision_payload"
    );

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:17.6")
            .withDatabaseName("model_spec_v1_retirement_it")
            .withUsername("model_spec_v1_retirement_test")
            .withPassword("model_spec_v1_retirement_test");

    @Test
    void canonicalRowsSurviveWhileV1SchemaIsRetiredInForeignKeySafeOrder() throws Exception {
        String schema = createPreMigrationSchema(null);
        try {
            assertPreMigrationStructure(schema);

            updateChangelog(schema);

            assertThat(tableExists(schema, "modeling_standard_binding")).isFalse();
            assertThat(tableExists(schema, "modeling_business_object")).isFalse();
            for (String column : LEGACY_MODEL_COLUMNS) {
                assertThat(columnExists(schema, "modeling_model_spec", column)).as(column).isFalse();
            }
            assertThat(columnExists(schema, "modeling_model_spec_revision", "spec_json")).isFalse();
            assertThat(columnExists(schema, "modeling_model_spec", "grain_statement")).isTrue();
            assertThat(columnExists(schema, "modeling_model_spec", "materialization")).isTrue();
            assertThat(modelValue(schema, "grain_statement")).isEqualTo("one row per business event");
            assertThat(modelValue(schema, "materialization")).isEqualTo("table");
            assertThat(rowCount(schema, "modeling_model_spec")).isEqualTo(1);
            assertThat(rowCount(schema, "modeling_model_spec_revision")).isEqualTo(1);
            assertThat(indexExists(schema, "idx_modeling_model_spec_object")).isFalse();
            assertThat(indexExists(schema, "idx_modeling_model_spec_process")).isFalse();
            assertThat(indexExists(schema, "idx_model_spec_v2_plan")).isTrue();
            assertThat(constraintExists(schema, "fk_modeling_model_spec_object")).isFalse();
            assertThat(constraintExists(schema, "fk_model_spec_v2_tenant_plan")).isTrue();
            assertThat(constraintExists(schema, "fk_model_spec_v2_domain")).isTrue();
            assertThat(constraintExists(schema, "fk_model_spec_revision_tenant_spec")).isTrue();
            assertThat(columnDefault(schema, "modeling_model_spec", "contract_version")).contains("2");
            assertThat(columnDefault(schema, "modeling_model_spec_revision", "contract_version")).contains("2");
            assertThat(changeSetApplied(schema)).isTrue();

            assertCanonicalConstraints(schema);
            assertCanonicalConstraintsAreEnforced(schema);
        } finally {
            dropOwnedSchema(schema);
        }
    }

    @ParameterizedTest(name = "non-empty compatibility table {0} halts retirement")
    @MethodSource("legacyTableCases")
    void anyNonEmptyCompatibilityTableHaltsAndPreservesEverything(LegacyState legacyState)
        throws Exception {
        String schema = createPreMigrationSchema(legacyState.mutation());
        try {
            assertRetirementBlocked(schema);

            assertPreMigrationStructure(schema);
            assertThat(rowCount(schema, legacyState.table())).isEqualTo(1);
        } finally {
            dropOwnedSchema(schema);
        }
    }

    @ParameterizedTest(name = "non-null legacy column {0}.{1} halts retirement")
    @MethodSource("legacyColumnCases")
    void anyNonNullLegacyColumnHaltsAndPreservesEverything(LegacyColumn legacyColumn)
        throws Exception {
        String schema = createPreMigrationSchema(legacyColumn.mutation());
        try {
            assertRetirementBlocked(schema);

            assertPreMigrationStructure(schema);
            assertThat(nonNullCount(schema, legacyColumn.table(), legacyColumn.column())).isEqualTo(1);
        } finally {
            dropOwnedSchema(schema);
        }
    }

    @ParameterizedTest(name = "non-v2 row in {0} halts retirement")
    @MethodSource("nonV2Cases")
    void anyNonV2RowHaltsAndPreservesEverything(LegacyState legacyState) throws Exception {
        String schema = createPreMigrationSchema(legacyState.mutation());
        try {
            assertRetirementBlocked(schema);

            assertPreMigrationStructure(schema);
            assertThat(nonV2Count(schema, legacyState.table())).isEqualTo(1);
        } finally {
            dropOwnedSchema(schema);
        }
    }

    private static Stream<LegacyState> legacyTableCases() {
        return Stream.of(
            new LegacyState(
                "modeling_standard_binding",
                "insert into modeling_standard_binding (id, model_spec_id) values (gen_random_uuid(), '" +
                MODEL_ID +
                "')"
            ),
            new LegacyState(
                "modeling_business_object",
                "insert into modeling_business_object (id) values ('" + BUSINESS_OBJECT_ID + "')"
            )
        );
    }

    private static Stream<LegacyColumn> legacyColumnCases() {
        return Stream.of(
            modelColumn("object_id", "'" + BUSINESS_OBJECT_ID + "'::uuid"),
            modelColumn("process_id", "'legacy-process'"),
            modelColumn("dimensions", "'[\"legacy-dimension\"]'"),
            modelColumn("metrics", "'[\"legacy-metric\"]'"),
            modelColumn("dbt_project_key", "'legacy-project'"),
            modelColumn("dbt_unique_id", "'model.legacy'"),
            modelColumn("legacy_ref", "'legacy-ref'"),
            modelColumn("legacy_refs", "'{\"source\":\"legacy\"}'::jsonb"),
            new LegacyColumn(
                "modeling_model_spec_revision",
                "spec_json",
                "update modeling_model_spec_revision set spec_json = '{\"legacy\":true}'"
            )
        );
    }

    private static Stream<LegacyState> nonV2Cases() {
        return Stream.of(
            new LegacyState(
                "modeling_model_spec",
                "update modeling_model_spec set contract_version = 1"
            ),
            new LegacyState(
                "modeling_model_spec_revision",
                "update modeling_model_spec_revision set contract_version = 1"
            )
        );
    }

    private static LegacyColumn modelColumn(String column, String value) {
        return new LegacyColumn(
            "modeling_model_spec",
            column,
            "update modeling_model_spec set " + column + " = " + value
        );
    }

    private String createPreMigrationSchema(String mutation) throws Exception {
        String schema = "s81_model_spec_v1_" + UUID.randomUUID().toString().replace("-", "");
        requireOwnedSchema(schema);
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("create schema " + schema);
        }
        try {
            for (String statement : baseSchemaStatements()) {
                executeInSchema(schema, statement);
            }
            if (mutation != null) {
                executeInSchema(schema, mutation);
            }
            for (String statement : legacyConstraintAndIndexStatements()) {
                executeInSchema(schema, statement);
            }
            return schema;
        } catch (Exception exception) {
            dropOwnedSchema(schema);
            throw exception;
        }
    }

    private List<String> baseSchemaStatements() {
        return List.of(
            "create table catalog_domain (id uuid primary key)",
            """
            create table modeling_warehouse_plan (
                id uuid primary key,
                tenant_id varchar(128) not null,
                unique (tenant_id, id)
            )
            """,
            "create table modeling_business_object (id uuid primary key)",
            """
            create table modeling_model_spec (
                id uuid primary key,
                tenant_id varchar(128) not null,
                object_id uuid,
                plan_id uuid,
                process_id varchar(128),
                layer varchar(16) not null,
                model_type varchar(32) not null,
                implementation_mode varchar(32) not null,
                name varchar(256) not null,
                grain_statement text,
                dimensions text,
                metrics text,
                materialization varchar(32),
                status varchar(32) not null,
                revision int not null,
                dbt_project_key varchar(128),
                dbt_unique_id varchar(256),
                legacy_ref varchar(256),
                version int not null default 1,
                created_date timestamp,
                last_modified_date timestamp,
                contract_version int not null default 1,
                domain_id uuid,
                business_activity_ref varchar(256),
                description text,
                consumption_scenario text,
                fact_shape varchar(32),
                grain_json jsonb,
                time_semantics jsonb,
                fields jsonb,
                source_refs jsonb,
                depends_on jsonb,
                dimension_refs jsonb,
                metric_refs jsonb,
                standard_bindings jsonb,
                generation_strategy jsonb,
                legacy_refs jsonb,
                current_checksum varchar(64),
                idempotency_key varchar(128),
                idempotency_request_hash varchar(64),
                idempotency_response_snapshot jsonb,
                unique (tenant_id, id)
            )
            """,
            """
            create table modeling_model_spec_revision (
                id uuid primary key,
                model_spec_id uuid not null,
                revision int not null,
                spec_json text,
                status varchar(32) not null,
                content_checksum varchar(128),
                created_date timestamp,
                last_modified_date timestamp,
                tenant_id varchar(128) not null,
                contract_version int not null default 1,
                snapshot_json jsonb,
                created_by varchar(128),
                unique (model_spec_id, revision)
            )
            """,
            """
            create table modeling_standard_binding (
                id uuid primary key,
                model_spec_id uuid not null
            )
            """,
            "insert into catalog_domain (id) values ('" + DOMAIN_ID + "')",
            "insert into modeling_warehouse_plan (id, tenant_id) values ('" + PLAN_ID + "', 'tenant-a')",
            canonicalModelInsert(),
            canonicalRevisionInsert()
        );
    }

    private String canonicalModelInsert() {
        return """
            insert into modeling_model_spec (
                id, tenant_id, plan_id, layer, model_type, implementation_mode, name,
                grain_statement, materialization, status, revision, contract_version, domain_id,
                fact_shape, grain_json, time_semantics, fields, source_refs, depends_on,
                dimension_refs, metric_refs, standard_bindings, generation_strategy,
                current_checksum, idempotency_key, idempotency_request_hash,
                idempotency_response_snapshot
            ) values (
                '%s', 'tenant-a', '%s', 'DWD', 'FACT', 'SQL', 'Canonical fact',
                'one row per business event', 'table', 'DRAFT', 1, 2, '%s',
                'TRANSACTION', '{}'::jsonb, '{}'::jsonb, '[]'::jsonb, '[]'::jsonb, '[]'::jsonb,
                '[]'::jsonb, '[]'::jsonb, '[]'::jsonb, '{}'::jsonb,
                '%s', 'create-model', '%s', '{}'::jsonb
            )
            """.formatted(MODEL_ID, PLAN_ID, DOMAIN_ID, "a".repeat(64), "b".repeat(64));
    }

    private String canonicalRevisionInsert() {
        return """
            insert into modeling_model_spec_revision (
                id, model_spec_id, revision, spec_json, status, content_checksum,
                tenant_id, contract_version, snapshot_json, created_by
            ) values (
                '%s', '%s', 1, null, 'DRAFT', '%s',
                'tenant-a', 2, '{}'::jsonb, 'tester'
            )
            """.formatted(REVISION_ID, MODEL_ID, "c".repeat(64));
    }

    private List<String> legacyConstraintAndIndexStatements() {
        return List.of(
            """
            alter table modeling_model_spec
                add constraint fk_modeling_model_spec_object
                foreign key (object_id) references modeling_business_object(id) not valid
            """,
            """
            alter table modeling_model_spec
                add constraint fk_modeling_model_spec_plan
                foreign key (plan_id) references modeling_warehouse_plan(id) not valid
            """,
            """
            alter table modeling_model_spec
                add constraint fk_model_spec_v2_tenant_plan
                foreign key (tenant_id, plan_id) references modeling_warehouse_plan(tenant_id, id) not valid
            """,
            """
            alter table modeling_model_spec
                add constraint fk_model_spec_v2_domain
                foreign key (domain_id) references catalog_domain(id) not valid
            """,
            """
            alter table modeling_model_spec_revision
                add constraint fk_modeling_model_spec_revision_spec
                foreign key (model_spec_id) references modeling_model_spec(id) not valid
            """,
            """
            alter table modeling_model_spec_revision
                add constraint fk_model_spec_revision_tenant_spec
                foreign key (tenant_id, model_spec_id) references modeling_model_spec(tenant_id, id) not valid
            """,
            """
            alter table modeling_standard_binding
                add constraint fk_modeling_standard_binding_spec
                foreign key (model_spec_id) references modeling_model_spec(id) not valid
            """,
            "create index idx_modeling_model_spec_object on modeling_model_spec (tenant_id, object_id)",
            "create index idx_modeling_model_spec_process on modeling_model_spec (tenant_id, process_id)",
            "create index idx_model_spec_v2_plan on modeling_model_spec (tenant_id, plan_id, model_type, status)",
            """
            alter table modeling_model_spec
                add constraint ck_model_spec_contract_version
                check (contract_version in (1, 2)) not valid
            """,
            """
            alter table modeling_model_spec
                add constraint ck_model_spec_v2_identity
                check (
                    contract_version <> 2 or (
                        object_id is null and process_id is null
                        and plan_id is not null and domain_id is not null
                        and status in ('DRAFT', 'DESIGNING', 'VALIDATING', 'READY_TO_PUBLISH', 'PUBLISHED', 'ARCHIVED')
                        and current_checksum ~ '^[0-9a-f]{64}$'
                        and idempotency_key is not null and btrim(idempotency_key) <> ''
                        and idempotency_request_hash ~ '^[0-9a-f]{64}$'
                        and idempotency_response_snapshot is not null
                        and jsonb_typeof(idempotency_response_snapshot) = 'object'
                    )
                ) not valid
            """,
            """
            alter table modeling_model_spec
                add constraint ck_model_spec_v2_type_context
                check (
                    contract_version <> 2 or (
                        (model_type = 'FACT' or business_activity_ref is null)
                        and (
                            model_type <> 'APPLICATION'
                            or status = 'DRAFT'
                            or (consumption_scenario is not null and btrim(consumption_scenario) <> '')
                        )
                        and (model_type = 'APPLICATION' or consumption_scenario is null)
                        and (fact_shape is null or fact_shape in ('TRANSACTION', 'PERIODIC_SNAPSHOT', 'ACCUMULATING_SNAPSHOT'))
                    )
                ) not valid
            """,
            """
            alter table modeling_model_spec
                add constraint ck_model_spec_v2_json_shapes
                check (
                    contract_version <> 2 or (
                        (grain_json is null or jsonb_typeof(grain_json) = 'object')
                        and (time_semantics is null or jsonb_typeof(time_semantics) = 'object')
                        and (fields is null or jsonb_typeof(fields) = 'array')
                        and (source_refs is null or jsonb_typeof(source_refs) = 'array')
                        and (depends_on is null or jsonb_typeof(depends_on) = 'array')
                        and (dimension_refs is null or jsonb_typeof(dimension_refs) = 'array')
                        and (metric_refs is null or jsonb_typeof(metric_refs) = 'array')
                        and (standard_bindings is null or jsonb_typeof(standard_bindings) = 'array')
                        and (generation_strategy is null or jsonb_typeof(generation_strategy) = 'object')
                        and (legacy_refs is null or jsonb_typeof(legacy_refs) in ('array', 'object'))
                    )
                ) not valid
            """,
            """
            alter table modeling_model_spec_revision
                add constraint ck_model_spec_revision_contract_version
                check (contract_version in (1, 2)) not valid
            """,
            """
            alter table modeling_model_spec_revision
                add constraint ck_model_spec_revision_payload
                check (
                    (contract_version = 1 and spec_json is not null)
                    or (
                        contract_version = 2
                        and snapshot_json is not null
                        and jsonb_typeof(snapshot_json) = 'object'
                        and content_checksum ~ '^[0-9a-f]{64}$'
                    )
                ) not valid
            """
        );
    }

    private void assertRetirementBlocked(String schema) {
        assertThatThrownBy(() -> updateChangelog(schema))
            .hasStackTraceContaining("MODEL_SPEC_V1_SCHEMA_RETIREMENT_BLOCKED");
        assertThat(changeSetAppliedUnchecked(schema)).isFalse();
    }

    private void assertPreMigrationStructure(String schema) throws SQLException {
        assertThat(tableExists(schema, "modeling_standard_binding")).isTrue();
        assertThat(tableExists(schema, "modeling_business_object")).isTrue();
        for (String column : LEGACY_MODEL_COLUMNS) {
            assertThat(columnExists(schema, "modeling_model_spec", column)).as(column).isTrue();
        }
        assertThat(columnExists(schema, "modeling_model_spec_revision", "spec_json")).isTrue();
        assertThat(columnExists(schema, "modeling_model_spec", "grain_statement")).isTrue();
        assertThat(columnExists(schema, "modeling_model_spec", "materialization")).isTrue();
        assertThat(constraintExists(schema, "fk_modeling_model_spec_object")).isTrue();
        assertThat(indexExists(schema, "idx_modeling_model_spec_object")).isTrue();
        assertThat(indexExists(schema, "idx_modeling_model_spec_process")).isTrue();
        assertThat(canonicalConstraintCount(schema)).isEqualTo(6);
        assertThat(rowCount(schema, "modeling_model_spec")).isEqualTo(1);
        assertThat(rowCount(schema, "modeling_model_spec_revision")).isEqualTo(1);
        assertThat(modelValue(schema, "grain_statement")).isEqualTo("one row per business event");
        assertThat(modelValue(schema, "materialization")).isEqualTo("table");
    }

    private void assertCanonicalConstraints(String schema) throws SQLException {
        assertThat(canonicalConstraintCount(schema)).isEqualTo(6);
        for (String constraint : CANONICAL_CONSTRAINTS) {
            assertThat(constraintValidated(schema, constraint)).as(constraint).isTrue();
        }
        assertThat(constraintDefinition(schema, "ck_model_spec_contract_version"))
            .contains("contract_version = 2")
            .doesNotContain("ANY (ARRAY[1, 2])");
        assertThat(constraintDefinition(schema, "ck_model_spec_v2_identity"))
            .contains("contract_version = 2")
            .doesNotContain("object_id", "process_id");
        assertThat(constraintDefinition(schema, "ck_model_spec_v2_json_shapes"))
            .contains("contract_version = 2")
            .doesNotContain("legacy_refs");
        assertThat(constraintDefinition(schema, "ck_model_spec_revision_payload"))
            .contains("contract_version = 2", "snapshot_json IS NOT NULL")
            .doesNotContain("spec_json");
    }

    private void assertCanonicalConstraintsAreEnforced(String schema) {
        assertThatThrownBy(() -> executeInSchema(schema, "update modeling_model_spec set contract_version = 1"))
            .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> executeInSchema(schema, "update modeling_model_spec set plan_id = null"))
            .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> executeInSchema(schema, "update modeling_model_spec set fields = '{}'::jsonb"))
            .isInstanceOf(SQLException.class);
        assertThatThrownBy(() ->
            executeInSchema(
                schema,
                "update modeling_model_spec set model_type = 'APPLICATION', status = 'PUBLISHED', consumption_scenario = null"
            )
        ).isInstanceOf(SQLException.class);
        assertThatThrownBy(() ->
            executeInSchema(schema, "update modeling_model_spec_revision set contract_version = 1")
        ).isInstanceOf(SQLException.class);
        assertThatThrownBy(() ->
            executeInSchema(schema, "update modeling_model_spec_revision set snapshot_json = null")
        ).isInstanceOf(SQLException.class);
    }

    private void updateChangelog(String schema) throws Exception {
        try (
            Connection connection = connectionInSchema(schema);
            ClassLoaderResourceAccessor resources = new ClassLoaderResourceAccessor()
        ) {
            Database database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try {
                database.setDefaultSchemaName(schema);
                database.setLiquibaseSchemaName(schema);
                try (Liquibase liquibase = new Liquibase(CHANGELOG, resources, database)) {
                    liquibase.update(new Contexts(), new LabelExpression());
                }
            } finally {
                if (!database.getConnection().isClosed()) {
                    database.close();
                }
            }
        }
    }

    private void executeInSchema(String schema, String sql) throws SQLException {
        try (Connection connection = connectionInSchema(schema); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private int rowCount(String schema, String table) throws SQLException {
        requireTable(table);
        return scalarCount(schema, "select count(*) from " + table);
    }

    private int nonNullCount(String schema, String table, String column) throws SQLException {
        requireLegacyColumn(table, column);
        return scalarCount(schema, "select count(*) from " + table + " where " + column + " is not null");
    }

    private int nonV2Count(String schema, String table) throws SQLException {
        requireTable(table);
        return scalarCount(schema, "select count(*) from " + table + " where contract_version is distinct from 2");
    }

    private int scalarCount(String schema, String sql) throws SQLException {
        try (
            Connection connection = connectionInSchema(schema);
            Statement statement = connection.createStatement();
            ResultSet result = statement.executeQuery(sql)
        ) {
            assertThat(result.next()).isTrue();
            return result.getInt(1);
        }
    }

    private int canonicalConstraintCount(String schema) throws SQLException {
        try (
            Connection connection = openConnection();
            PreparedStatement statement = connection.prepareStatement(
                """
                select count(*)
                  from pg_constraint c
                  join pg_namespace n on n.oid = c.connamespace
                 where n.nspname = ?
                   and c.conname = any (?::text[])
                """
            )
        ) {
            statement.setString(1, schema);
            statement.setArray(2, connection.createArrayOf("text", CANONICAL_CONSTRAINTS.toArray()));
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getInt(1);
            }
        }
    }

    private boolean tableExists(String schema, String table) throws SQLException {
        return informationSchemaExists(
            schema,
            "select count(*) from information_schema.tables where table_schema = ? and table_name = ?",
            table
        );
    }

    private boolean columnExists(String schema, String table, String column) throws SQLException {
        try (
            Connection connection = openConnection();
            PreparedStatement statement = connection.prepareStatement(
                """
                select count(*)
                  from information_schema.columns
                 where table_schema = ? and table_name = ? and column_name = ?
                """
            )
        ) {
            statement.setString(1, schema);
            statement.setString(2, table);
            statement.setString(3, column);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getInt(1) == 1;
            }
        }
    }

    private boolean indexExists(String schema, String index) throws SQLException {
        return informationSchemaExists(
            schema,
            "select count(*) from pg_indexes where schemaname = ? and indexname = ?",
            index
        );
    }

    private boolean constraintExists(String schema, String constraint) throws SQLException {
        return informationSchemaExists(
            schema,
            "select count(*) from information_schema.table_constraints where constraint_schema = ? and constraint_name = ?",
            constraint
        );
    }

    private boolean informationSchemaExists(String schema, String sql, String object) throws SQLException {
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, schema);
            statement.setString(2, object);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getInt(1) == 1;
            }
        }
    }

    private String constraintDefinition(String schema, String constraint) throws SQLException {
        try (
            Connection connection = openConnection();
            PreparedStatement statement = connection.prepareStatement(
                """
                select pg_get_constraintdef(c.oid)
                  from pg_constraint c
                  join pg_namespace n on n.oid = c.connamespace
                 where n.nspname = ? and c.conname = ?
                """
            )
        ) {
            statement.setString(1, schema);
            statement.setString(2, constraint);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).as(constraint).isTrue();
                return result.getString(1);
            }
        }
    }

    private boolean constraintValidated(String schema, String constraint) throws SQLException {
        try (
            Connection connection = openConnection();
            PreparedStatement statement = connection.prepareStatement(
                """
                select c.convalidated
                  from pg_constraint c
                  join pg_namespace n on n.oid = c.connamespace
                 where n.nspname = ? and c.conname = ?
                """
            )
        ) {
            statement.setString(1, schema);
            statement.setString(2, constraint);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).as(constraint).isTrue();
                return result.getBoolean(1);
            }
        }
    }

    private String columnDefault(String schema, String table, String column) throws SQLException {
        try (
            Connection connection = openConnection();
            PreparedStatement statement = connection.prepareStatement(
                """
                select column_default
                  from information_schema.columns
                 where table_schema = ? and table_name = ? and column_name = ?
                """
            )
        ) {
            statement.setString(1, schema);
            statement.setString(2, table);
            statement.setString(3, column);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getString(1);
            }
        }
    }

    private String modelValue(String schema, String column) throws SQLException {
        if (!List.of("grain_statement", "materialization").contains(column)) {
            throw new IllegalArgumentException("Unexpected canonical column: " + column);
        }
        try (
            Connection connection = connectionInSchema(schema);
            Statement statement = connection.createStatement();
            ResultSet result = statement.executeQuery("select " + column + " from modeling_model_spec")
        ) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }

    private boolean changeSetApplied(String schema) throws SQLException {
        if (!tableExists(schema, "databasechangelog")) return false;
        try (
            Connection connection = connectionInSchema(schema);
            PreparedStatement statement = connection.prepareStatement(
                "select count(*) from databasechangelog where id = ?"
            )
        ) {
            statement.setString(1, CHANGESET_ID);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getInt(1) == 1;
            }
        }
    }

    private boolean changeSetAppliedUnchecked(String schema) {
        try {
            return changeSetApplied(schema);
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private Connection connectionInSchema(String schema) throws SQLException {
        requireOwnedSchema(schema);
        Connection connection = openConnection();
        try (Statement statement = connection.createStatement()) {
            statement.execute("set search_path to " + schema);
        } catch (SQLException exception) {
            connection.close();
            throw exception;
        }
        return connection;
    }

    private Connection openConnection() throws SQLException {
        Connection connection = POSTGRES.createConnection("");
        connection.setAutoCommit(true);
        return connection;
    }

    private void dropOwnedSchema(String schema) throws SQLException {
        requireOwnedSchema(schema);
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("drop schema " + schema + " cascade");
        }
    }

    private void requireOwnedSchema(String schema) {
        if (schema == null || !OWNED_SCHEMA.matcher(schema).matches()) {
            throw new IllegalArgumentException("Refusing to modify unowned schema: " + schema);
        }
    }

    private void requireTable(String table) {
        if (
            !List.of(
                    "modeling_model_spec",
                    "modeling_model_spec_revision",
                    "modeling_standard_binding",
                    "modeling_business_object"
                )
                .contains(table)
        ) {
            throw new IllegalArgumentException("Unexpected test table: " + table);
        }
    }

    private void requireLegacyColumn(String table, String column) {
        if (
            ("modeling_model_spec".equals(table) && LEGACY_MODEL_COLUMNS.contains(column)) ||
            ("modeling_model_spec_revision".equals(table) && "spec_json".equals(column))
        ) {
            return;
        }
        throw new IllegalArgumentException("Unexpected legacy column: " + table + "." + column);
    }

    private record LegacyState(String table, String mutation) {
        @Override
        public String toString() {
            return table;
        }
    }

    private record LegacyColumn(String table, String column, String mutation) {
        @Override
        public String toString() {
            return table + "." + column;
        }
    }
}
