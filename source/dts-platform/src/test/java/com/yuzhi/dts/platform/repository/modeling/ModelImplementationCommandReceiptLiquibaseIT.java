package com.yuzhi.dts.platform.repository.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.IntegrationTest;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.GeneratedInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.InputMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class ModelImplementationCommandReceiptLiquibaseIT {

    private static final String CHANGELOG =
        "config/liquibase/changelog/20260801_06_model_implementation_command_receipt.xml";
    private static final Pattern OWNED_SCHEMA = Pattern.compile("^s81_impl_receipt_[0-9a-f]{32}$");
    private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Environment environment;

    @Test
    void persistsStableActionScopedResultsAndBlocksMutationAndDataLossRollback() throws Exception {
        String schema = "s81_impl_receipt_" + UUID.randomUUID().toString().replace("-", "");
        createSchema(schema);
        try {
            try (ClassLoaderResourceAccessor resources = new ClassLoaderResourceAccessor()) {
                Connection connection = dataSource.getConnection();
                Database database = null;
                try {
                    connection.setAutoCommit(true);
                    setSchema(connection, schema);
                    createPrerequisites(connection);
                    database = DatabaseFactory.getInstance()
                        .findCorrectDatabaseImplementation(new JdbcConnection(connection));
                    database.setDefaultSchemaName(schema);
                    database.setLiquibaseSchemaName(schema);
                    try (Liquibase liquibase = new Liquibase(CHANGELOG, resources, database)) {
                        liquibase.setChangeLogParameter("uuidType", "uuid");
                        liquibase.update(new Contexts(), new LabelExpression());
                        assertThat(tableExists(connection, schema, "modeling_model_implementation_command_receipt"))
                            .isTrue();

                        liquibase.rollback(1, new Contexts(), new LabelExpression());
                        assertThat(tableExists(connection, schema, "modeling_model_implementation_command_receipt"))
                            .isFalse();
                        liquibase.update(new Contexts(), new LabelExpression());
                        connection.setAutoCommit(true);

                        String tenant = "tenant-receipt-it";
                        UUID modelSpecId = UUID.randomUUID();
                        UUID planId = UUID.randomUUID();
                        seedModel(connection, tenant, modelSpecId);
                        ModelLifecycleCommandReceiptRepository repository = new ModelLifecycleCommandReceiptRepository(
                            new JdbcTemplate(new SingleConnectionDataSource(connection, true)),
                            new ObjectMapper()
                        );
                        ImplementationView result = implementation(modelSpecId, planId);
                        repository.append(
                            tenant,
                            modelSpecId,
                            "SAVE",
                            "save-once",
                            "a".repeat(64),
                            result,
                            "alice",
                            NOW
                        );

                        assertThat(repository.find(tenant, modelSpecId, "SAVE", "save-once"))
                            .contains(new ModelLifecycleCommandReceiptRepository.Receipt("a".repeat(64), result));
                        repository.append(
                            tenant,
                            modelSpecId,
                            "CLAIM",
                            "save-once",
                            "b".repeat(64),
                            result,
                            "alice",
                            NOW
                        );
                        assertThatThrownBy(() -> repository.append(
                            tenant,
                            modelSpecId,
                            "SAVE",
                            "save-once",
                            "c".repeat(64),
                            result,
                            "alice",
                            NOW
                        )).hasStackTraceContaining("uk_model_implementation_command_receipt_key");

                        assertThatThrownBy(() -> execute(
                            connection,
                            "update modeling_model_implementation_command_receipt set created_by='mallory'"
                        )).hasStackTraceContaining("MODEL_IMPLEMENTATION_COMMAND_RECEIPT_APPEND_ONLY");
                        assertThatThrownBy(() -> execute(
                            connection,
                            "delete from modeling_model_implementation_command_receipt"
                        )).hasStackTraceContaining("MODEL_IMPLEMENTATION_COMMAND_RECEIPT_APPEND_ONLY");
                        assertThatThrownBy(() -> liquibase.rollback(1, new Contexts(), new LabelExpression()))
                            .hasStackTraceContaining("ROLLBACK_BLOCKED_MODEL_IMPLEMENTATION_COMMAND_RECEIPT_DATA_EXISTS");
                        assertThat(tableExists(connection, schema, "modeling_model_implementation_command_receipt"))
                            .isTrue();
                    }
                } finally {
                    try {
                        if (database != null && !database.getConnection().isClosed()) database.close();
                    } finally {
                        if (!connection.isClosed()) connection.close();
                    }
                }
            }
        } finally {
            dropOwnedSchema(schema);
        }
    }

    @Test
    void advisoryLockSerializesTwoRealTransactionsAndDetectsDifferentPayloads() throws Exception {
        DriverManagerDataSource isolated = isolatedDataSource();
        String schema = "s81_impl_receipt_" + UUID.randomUUID().toString().replace("-", "");
        createSchema(isolated, schema);
        try {
            prepareConcurrentSchema(isolated, schema);
            JdbcTemplate jdbc = new JdbcTemplate(isolated);
            TransactionTemplate transactions = new TransactionTemplate(new DataSourceTransactionManager(isolated));
            ModelLifecycleCommandReceiptRepository repository = new ModelLifecycleCommandReceiptRepository(
                jdbc,
                new ObjectMapper()
            );
            UUID modelSpecId = UUID.randomUUID();
            UUID planId = UUID.randomUUID();
            try (Connection connection = isolated.getConnection()) {
                connection.setAutoCommit(true);
                setSchema(connection, schema);
                seedModel(connection, "tenant-concurrency", modelSpecId);
            }
            ImplementationView result = implementation(modelSpecId, planId);

            ConcurrentResult samePayload = runConcurrentCommands(
                transactions,
                jdbc,
                repository,
                schema,
                modelSpecId,
                result,
                "same-payload",
                "a".repeat(64),
                "a".repeat(64)
            );
            assertThat(samePayload.first()).isEqualTo(result);
            assertThat(samePayload.second()).isEqualTo(result);
            assertThat(businessWriteCount(jdbc, schema, "same-payload")).isEqualTo(1);
            assertThat(receiptCount(jdbc, schema, modelSpecId, "same-payload")).isEqualTo(1);

            assertThatThrownBy(() -> runConcurrentCommands(
                transactions,
                jdbc,
                repository,
                schema,
                modelSpecId,
                result,
                "different-payload",
                "b".repeat(64),
                "c".repeat(64)
            ))
                .isInstanceOf(ExecutionException.class)
                .hasRootCauseInstanceOf(ModelSpecException.class)
                .hasRootCauseMessage("The implementation idempotency key belongs to a different payload");
            assertThat(businessWriteCount(jdbc, schema, "different-payload")).isEqualTo(1);
            assertThat(receiptCount(jdbc, schema, modelSpecId, "different-payload")).isEqualTo(1);
        } finally {
            dropOwnedSchema(isolated, schema);
        }
    }

    @Test
    void springTransactionRollsBackBusinessWriteWhenReceiptInsertFails() throws Exception {
        DriverManagerDataSource isolated = isolatedDataSource();
        String schema = "s81_impl_receipt_" + UUID.randomUUID().toString().replace("-", "");
        createSchema(isolated, schema);
        try {
            prepareConcurrentSchema(isolated, schema);
            JdbcTemplate jdbc = new JdbcTemplate(isolated);
            TransactionTemplate transactions = new TransactionTemplate(new DataSourceTransactionManager(isolated));
            ModelLifecycleCommandReceiptRepository repository = new ModelLifecycleCommandReceiptRepository(
                jdbc,
                new ObjectMapper()
            );
            UUID modelSpecId = UUID.randomUUID();
            ImplementationView result = implementation(modelSpecId, UUID.randomUUID());
            try (Connection connection = isolated.getConnection()) {
                connection.setAutoCommit(true);
                setSchema(connection, schema);
                seedModel(connection, "tenant-rollback", modelSpecId);
            }
            transactions.executeWithoutResult(status -> {
                setLocalSchema(jdbc, schema);
                repository.append(
                    "tenant-rollback",
                    modelSpecId,
                    "SAVE",
                    "receipt-failure",
                    "d".repeat(64),
                    result,
                    "alice",
                    NOW
                );
            });

            assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
                setLocalSchema(jdbc, schema);
                jdbc.update("insert into command_business_effect(command_key) values (?)", "rolled-back-business");
                repository.append(
                    "tenant-rollback",
                    modelSpecId,
                    "SAVE",
                    "receipt-failure",
                    "d".repeat(64),
                    result,
                    "alice",
                    NOW
                );
            })).isInstanceOf(DataIntegrityViolationException.class);

            assertThat(businessWriteCount(jdbc, schema, "rolled-back-business")).isZero();
            assertThat(receiptCount(jdbc, schema, modelSpecId, "receipt-failure")).isEqualTo(1);
        } finally {
            dropOwnedSchema(isolated, schema);
        }
    }

    private ConcurrentResult runConcurrentCommands(
        TransactionTemplate transactions,
        JdbcTemplate jdbc,
        ModelLifecycleCommandReceiptRepository repository,
        String schema,
        UUID modelSpecId,
        ImplementationView result,
        String idempotencyKey,
        String firstHash,
        String secondHash
    ) throws Exception {
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondAttempting = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<ImplementationView> first = executor.submit(() -> executeConcurrentCommand(
                transactions,
                jdbc,
                repository,
                schema,
                modelSpecId,
                result,
                idempotencyKey,
                firstHash,
                true,
                firstLocked,
                releaseFirst,
                secondAttempting
            ));
            assertThat(firstLocked.await(5, TimeUnit.SECONDS)).isTrue();
            Future<ImplementationView> second = executor.submit(() -> executeConcurrentCommand(
                transactions,
                jdbc,
                repository,
                schema,
                modelSpecId,
                result,
                idempotencyKey,
                secondHash,
                false,
                firstLocked,
                releaseFirst,
                secondAttempting
            ));
            assertThat(secondAttempting.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(second.isDone()).isFalse();
            releaseFirst.countDown();
            ImplementationView firstResult = first.get(5, TimeUnit.SECONDS);
            ImplementationView secondResult = second.get(5, TimeUnit.SECONDS);
            return new ConcurrentResult(firstResult, secondResult);
        } finally {
            releaseFirst.countDown();
        }
    }

    private ImplementationView executeConcurrentCommand(
        TransactionTemplate transactions,
        JdbcTemplate jdbc,
        ModelLifecycleCommandReceiptRepository repository,
        String schema,
        UUID modelSpecId,
        ImplementationView result,
        String idempotencyKey,
        String payloadHash,
        boolean first,
        CountDownLatch firstLocked,
        CountDownLatch releaseFirst,
        CountDownLatch secondAttempting
    ) {
        return transactions.execute(status -> {
            setLocalSchema(jdbc, schema);
            if (!first) secondAttempting.countDown();
            repository.lockCommandKey("tenant-concurrency", modelSpecId, "SAVE", idempotencyKey);
            if (first) {
                firstLocked.countDown();
                await(releaseFirst);
            }
            ModelLifecycleCommandReceiptRepository.Receipt existing = repository
                .find("tenant-concurrency", modelSpecId, "SAVE", idempotencyKey)
                .orElse(null);
            if (existing != null) {
                if (!Objects.equals(existing.payloadHash(), payloadHash)) {
                    throw new ModelSpecException(
                        "MODEL_IMPLEMENTATION_IDEMPOTENCY_CONFLICT",
                        "The implementation idempotency key belongs to a different payload",
                        ModelSpecException.Kind.CONFLICT
                    );
                }
                return existing.result();
            }
            jdbc.update("insert into command_business_effect(command_key) values (?)", idempotencyKey);
            repository.append(
                "tenant-concurrency",
                modelSpecId,
                "SAVE",
                idempotencyKey,
                payloadHash,
                result,
                "alice",
                NOW
            );
            return result;
        });
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("concurrency coordination timed out");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrency coordination interrupted", exception);
        }
    }

    private static int businessWriteCount(JdbcTemplate jdbc, String schema, String commandKey) {
        requireOwnedSchema(schema);
        return jdbc.queryForObject(
            "select count(*) from " + schema + ".command_business_effect where command_key = ?",
            Integer.class,
            commandKey
        );
    }

    private static int receiptCount(JdbcTemplate jdbc, String schema, UUID modelSpecId, String idempotencyKey) {
        requireOwnedSchema(schema);
        return jdbc.queryForObject(
            "select count(*) from " + schema +
            ".modeling_model_implementation_command_receipt where model_spec_id = ? and action = 'SAVE' and idempotency_key = ?",
            Integer.class,
            modelSpecId,
            idempotencyKey
        );
    }

    private void prepareConcurrentSchema(DataSource isolated, String schema) throws Exception {
        try (Connection connection = isolated.getConnection()) {
            connection.setAutoCommit(true);
            setSchema(connection, schema);
            createPrerequisites(connection);
            execute(connection, "create table command_business_effect (command_key varchar(128) primary key)");
            applyReceiptMigration(connection, schema);
        }
    }

    private static void applyReceiptMigration(Connection connection, String schema) throws Exception {
        try (ClassLoaderResourceAccessor resources = new ClassLoaderResourceAccessor()) {
            Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try {
                database.setDefaultSchemaName(schema);
                database.setLiquibaseSchemaName(schema);
                try (Liquibase liquibase = new Liquibase(CHANGELOG, resources, database)) {
                    liquibase.setChangeLogParameter("uuidType", "uuid");
                    liquibase.update(new Contexts(), new LabelExpression());
                }
            } finally {
                if (!database.getConnection().isClosed()) database.close();
            }
        }
    }

    private DriverManagerDataSource isolatedDataSource() {
        return new DriverManagerDataSource(
            environment.getRequiredProperty("spring.datasource.url"),
            environment.getRequiredProperty("spring.datasource.username"),
            environment.getRequiredProperty("spring.datasource.password")
        );
    }

    private static void setLocalSchema(JdbcTemplate jdbc, String schema) {
        requireOwnedSchema(schema);
        jdbc.execute("set local search_path to " + schema);
    }

    private record ConcurrentResult(ImplementationView first, ImplementationView second) {}

    private static ImplementationView implementation(UUID modelSpecId, UUID planId) {
        return new ImplementationView(
            UUID.randomUUID(),
            modelSpecId,
            planId,
            7,
            "d".repeat(64),
            ImplementationMode.DESIGNER_GENERATED,
            "dts",
            "model.dts.calendar",
            "ACTIVE",
            3,
            "e".repeat(64),
            InputMode.GENERATED,
            List.of(new GeneratedInput("DATE_DIMENSION", Map.of("startYear", 2020))),
            List.of(),
            Map.of("loadStrategy", "FULL"),
            "table"
        );
    }

    private static void createPrerequisites(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                "create table modeling_model_spec (tenant_id varchar(128) not null, id uuid not null, primary key (tenant_id, id))"
            );
            statement.execute("create table modeling_model_implementation (id uuid primary key)");
        }
    }

    private static void seedModel(Connection connection, String tenant, UUID modelSpecId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
            "insert into modeling_model_spec (tenant_id, id) values (?, ?)"
        )) {
            statement.setString(1, tenant);
            statement.setObject(2, modelSpecId);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private static void execute(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private void createSchema(String schema) throws Exception {
        requireOwnedSchema(schema);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            connection.setAutoCommit(true);
            statement.execute("create schema " + schema);
        }
    }

    private static void createSchema(DataSource source, String schema) throws Exception {
        requireOwnedSchema(schema);
        try (Connection connection = source.getConnection(); Statement statement = connection.createStatement()) {
            connection.setAutoCommit(true);
            statement.execute("create schema " + schema);
        }
    }

    private static boolean tableExists(Connection connection, String schema, String table) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
            "select exists(select 1 from information_schema.tables where table_schema = ? and table_name = ?)"
        )) {
            statement.setString(1, schema);
            statement.setString(2, table);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getBoolean(1);
            }
        }
    }

    private static void setSchema(Connection connection, String schema) throws Exception {
        requireOwnedSchema(schema);
        try (Statement statement = connection.createStatement()) {
            statement.execute("set search_path to " + schema);
        }
    }

    private void dropOwnedSchema(String schema) throws Exception {
        requireOwnedSchema(schema);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            connection.setAutoCommit(true);
            statement.execute("drop schema " + schema + " cascade");
        }
    }

    private static void dropOwnedSchema(DataSource source, String schema) throws Exception {
        requireOwnedSchema(schema);
        try (Connection connection = source.getConnection(); Statement statement = connection.createStatement()) {
            connection.setAutoCommit(true);
            statement.execute("drop schema " + schema + " cascade");
        }
    }

    private static void requireOwnedSchema(String schema) {
        if (schema == null || !OWNED_SCHEMA.matcher(schema).matches()) {
            throw new IllegalArgumentException("refusing non-owned schema: " + schema);
        }
    }
}
