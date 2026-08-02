package com.yuzhi.dts.platform.repository.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.IntegrationTest;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationAvailabilityPinRepository.AvailabilityPin;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetType;
import com.yuzhi.dts.platform.service.catalog.CatalogMaterializationSourceAvailabilityPort.ExpectedSource;
import com.yuzhi.dts.platform.service.catalog.JdbcCatalogMaterializationSourceAvailabilityAdapter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class ModelMaterializationAvailabilityPinPostgresIT {

    private static final String CHANGELOG =
        "config/liquibase/changelog/20260801_09_model_materialization_availability_pin.xml";
    private static final Pattern OWNED_SCHEMA = Pattern.compile(
        "^s81_materialization_pin_[0-9a-f]{32}$"
    );
    private static final Instant NOW = Instant.parse(
        "2026-08-01T00:00:00Z"
    );
    private static final String STALE_REASON =
        "MODEL_MATERIALIZATION_SOURCE_GENERATION_STALE";

    @Autowired
    private DataSource dataSource;

    @Test
    void immutablePinAndCatalogLockKeepStaleTerminalTruthMonotonic()
        throws Exception {
        String schema =
            "s81_materialization_pin_" +
            UUID.randomUUID().toString().replace("-", "");
        createSchema(schema);
        try (
            ClassLoaderResourceAccessor resources =
                new ClassLoaderResourceAccessor();
            Connection connection = dataSource.getConnection()
        ) {
            connection.setAutoCommit(true);
            setSchema(connection, schema);
            createPrerequisites(connection);
            Database database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(
                    new JdbcConnection(connection)
                );
            database.setDefaultSchemaName(schema);
            database.setLiquibaseSchemaName(schema);
            try (
                Liquibase liquibase = new Liquibase(
                    CHANGELOG,
                    resources,
                    database
                )
            ) {
                liquibase.setChangeLogParameter("uuidType", "uuid");
                liquibase.setChangeLogParameter(
                    "datetimeType",
                    "timestamp"
                );
                liquibase.update(
                    new Contexts(),
                    new LabelExpression()
                );

                SingleConnectionDataSource single =
                    new SingleConnectionDataSource(connection, true);
                JdbcTemplate jdbc = new JdbcTemplate(single);
                TransactionTemplate transactions =
                    new TransactionTemplate(
                        new DataSourceTransactionManager(single)
                    );
                ModelMaterializationAvailabilityPinRepository pins =
                    new ModelMaterializationAvailabilityPinRepository(
                        jdbc
                    );
                ModelMaterializationRunRepository runs =
                    new ModelMaterializationRunRepository(jdbc);
                JdbcCatalogMaterializationSourceAvailabilityAdapter catalog =
                    new JdbcCatalogMaterializationSourceAvailabilityAdapter(
                        jdbc,
                        new ObjectMapper()
                    );
                UUID dispatchId = UUID.randomUUID();
                UUID sourceBindingId = UUID.randomUUID();
                String assetKey =
                    "dataset://availability-it/" + UUID.randomUUID();
                String pinnedEvent = "available-100-" + dispatchId;

                transactions.executeWithoutResult(status -> {
                    jdbc.update(
                        """
                        insert into modeling_materialization_dispatch (
                            id, profile_lease_id, status, claimed_at,
                            last_modified_at
                        ) values (?, ?, 'CLAIMED', ?, ?)
                        """,
                        dispatchId,
                        UUID.randomUUID(),
                        Timestamp.from(NOW),
                        Timestamp.from(NOW)
                    );
                    jdbc.update(
                        "insert into modeling_warehouse_plan_source (id) values (?)",
                        sourceBindingId
                    );
                    jdbc.update(
                        """
                        insert into modeling_pipeline_run (
                            id, pipeline_run_group_id, run_purpose, status,
                            last_modified_date
                        ) values (?, ?, 'RELEASE_BUILD', 'SUBMITTED', ?)
                        """,
                        UUID.randomUUID(),
                        dispatchId,
                        Timestamp.from(NOW)
                    );
                    jdbc.update(
                        """
                        insert into catalog_asset_availability (
                            asset_type, asset_key, status,
                            availability_epoch, source_sequence, event_id
                        ) values ('DATASET', ?, 'AVAILABLE', 7, 100, ?)
                        """,
                        assetKey,
                        pinnedEvent
                    );
                    pins.persistSnapshot(
                        dispatchId,
                        List.of(
                            new AvailabilityPin(
                                sourceBindingId,
                                CatalogAssetType.DATASET,
                                assetKey,
                                "AVAILABLE",
                                7L,
                                100L,
                                pinnedEvent,
                                "source-version-1"
                            )
                        ),
                        NOW
                    );
                });
                if (!connection.getAutoCommit()) connection.commit();
                connection.setAutoCommit(false);

                assertThat(
                    jdbc.queryForObject(
                        """
                        select availability_pin_count
                          from modeling_materialization_dispatch
                         where id = ?
                        """,
                        Integer.class,
                        dispatchId
                    )
                ).isEqualTo(1);
                try {
                    assertThatThrownBy(() ->
                        jdbc.update(
                            """
                            update modeling_materialization_source_pin
                               set source_sequence = 101
                             where dispatch_id = ?
                            """,
                            dispatchId
                        )
                    ).hasStackTraceContaining(
                        "MODEL_MATERIALIZATION_SOURCE_PIN_APPEND_ONLY"
                    );
                } finally {
                    connection.rollback();
                }
                try {
                    assertThatThrownBy(() ->
                        jdbc.update(
                            """
                            update modeling_materialization_dispatch
                               set availability_pin_count = 2
                             where id = ?
                            """,
                            dispatchId
                        )
                    ).hasStackTraceContaining(
                        "MODEL_MATERIALIZATION_AVAILABILITY_MARKER_IMMUTABLE"
                    );
                } finally {
                    connection.rollback();
                }

                jdbc.update(
                    """
                    update catalog_asset_availability
                       set status = 'FENCED', source_sequence = 101,
                           event_id = ?
                     where asset_type = 'DATASET' and asset_key = ?
                    """,
                    "fence-101-" + dispatchId,
                    assetKey
                );
                transactions.executeWithoutResult(status -> {
                    var drift = catalog.lockAndCompare(
                        List.of(
                            new ExpectedSource(
                                sourceBindingId,
                                CatalogAssetType.DATASET,
                                assetKey,
                                "AVAILABLE",
                                7L,
                                100L,
                                pinnedEvent
                            )
                        )
                    );
                    assertThat(drift)
                        .singleElement()
                        .satisfies(current -> {
                            assertThat(current.currentStatus())
                                .isEqualTo("FENCED");
                            assertThat(current.currentEpoch())
                                .isEqualTo(7L);
                            assertThat(current.currentSourceSequence())
                                .isEqualTo(101L);
                        });
                    assertThat(
                        runs.markAvailabilityStale(
                            dispatchId,
                            STALE_REASON,
                            NOW.plusSeconds(1)
                        )
                    ).isTrue();
                    assertThat(
                        runs.markAvailabilityStale(
                            dispatchId,
                            STALE_REASON,
                            NOW.plusSeconds(2)
                        )
                    ).isFalse();
                    assertThatThrownBy(() ->
                        runs.markDbtSucceeded(
                            dispatchId,
                            UUID.randomUUID(),
                            1,
                            NOW.plusSeconds(3)
                        )
                    ).isInstanceOf(IllegalStateException.class);
                });

                assertThat(
                    jdbc.queryForMap(
                        """
                        select d.status as dispatch_status,
                               d.last_error_code,
                               pr.status as pipeline_status
                          from modeling_materialization_dispatch d
                          join modeling_pipeline_run pr
                            on pr.pipeline_run_group_id = d.id
                         where d.id = ?
                        """,
                        dispatchId
                    )
                )
                    .containsEntry("dispatch_status", "FAILED")
                    .containsEntry("last_error_code", STALE_REASON)
                    .containsEntry("pipeline_status", "FAILED_STALE");
                try {
                    assertThatThrownBy(() ->
                        liquibase.rollback(
                            1,
                            new Contexts(),
                            new LabelExpression()
                        )
                    ).hasStackTraceContaining(
                        "ROLLBACK_BLOCKED_MODEL_MATERIALIZATION_AVAILABILITY_PIN_EXISTS"
                    );
                } finally {
                    if (!connection.getAutoCommit()) connection.rollback();
                }
                assertThat(
                    tableExists(
                        connection,
                        schema,
                        "modeling_materialization_source_pin"
                    )
                ).isTrue();
            } finally {
                if (!database.getConnection().isClosed()) database.close();
            }
        } finally {
            dropOwnedSchema(schema);
        }
    }

    private static void createPrerequisites(Connection connection)
        throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                """
                create table modeling_materialization_dispatch (
                    id uuid primary key,
                    profile_lease_id uuid,
                    status varchar(24) not null,
                    claimed_at timestamp,
                    next_attempt_at timestamp,
                    last_error_code varchar(128),
                    last_modified_at timestamp not null
                )
                """
            );
            statement.execute(
                "create table modeling_warehouse_plan_source (id uuid primary key)"
            );
            statement.execute(
                """
                create table catalog_asset_availability (
                    asset_type varchar(64) not null,
                    asset_key varchar(512) not null,
                    status varchar(32) not null,
                    availability_epoch bigint not null,
                    source_sequence bigint not null,
                    event_id varchar(128) not null,
                    primary key (asset_type, asset_key)
                )
                """
            );
            statement.execute(
                """
                create table modeling_pipeline_run (
                    id uuid primary key,
                    pipeline_run_group_id uuid not null,
                    run_purpose varchar(32) not null,
                    status varchar(32) not null,
                    message varchar(512),
                    started_date timestamp,
                    finished_date timestamp,
                    last_modified_date timestamp,
                    dbt_invocation_id uuid,
                    dbt_run_id varchar(128)
                )
                """
            );
        }
    }

    private void createSchema(String schema) throws Exception {
        requireOwnedSchema(schema);
        try (
            Connection connection = dataSource.getConnection();
            Statement statement = connection.createStatement()
        ) {
            connection.setAutoCommit(true);
            statement.execute("create schema " + schema);
        }
    }

    private void dropOwnedSchema(String schema) throws Exception {
        requireOwnedSchema(schema);
        try (
            Connection connection = dataSource.getConnection();
            Statement statement = connection.createStatement()
        ) {
            connection.setAutoCommit(true);
            statement.execute("drop schema " + schema + " cascade");
        }
    }

    private static void setSchema(Connection connection, String schema)
        throws Exception {
        requireOwnedSchema(schema);
        try (Statement statement = connection.createStatement()) {
            statement.execute("set search_path to " + schema);
        }
    }

    private static boolean tableExists(
        Connection connection,
        String schema,
        String table
    ) throws Exception {
        try (
            PreparedStatement statement = connection.prepareStatement(
                """
                select exists(
                    select 1
                      from information_schema.tables
                     where table_schema = ? and table_name = ?
                )
                """
            )
        ) {
            statement.setString(1, schema);
            statement.setString(2, table);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getBoolean(1);
            }
        }
    }

    private static void requireOwnedSchema(String schema) {
        if (schema == null || !OWNED_SCHEMA.matcher(schema).matches()) {
            throw new IllegalArgumentException(
                "refusing non-owned schema: " + schema
            );
        }
    }
}
