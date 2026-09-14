package com.yuzhi.dts.admin.service.personnel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.admin.config.MdmGatewayProperties;
import com.yuzhi.dts.admin.domain.PersonImportBatch;
import com.yuzhi.dts.admin.domain.PersonImportRecord;
import com.yuzhi.dts.admin.repository.AdminKeycloakUserRepository;
import com.yuzhi.dts.admin.repository.PersonImportBatchRepository;
import com.yuzhi.dts.admin.repository.PersonImportRecordRepository;
import com.yuzhi.dts.admin.service.audit.AuditV2Service;
import com.yuzhi.dts.admin.service.dto.personnel.PersonnelPayload;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Real PostgreSQL constraints and transactions; only external services and repository adapters are substituted. */
@Testcontainers
class PersonnelImportHistoryRegressionTest {

    private static final String REPAIR = "config/liquibase/changelog/20260914-01_person_import_history_constraints.xml";
    private static final String LEGACY = "config/liquibase/changelog/20260623-01_person_import_record_keycloak_id_non_unique.xml";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.4");

    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;
    private String schema;

    @BeforeEach
    void prepareDatabase() {
        schema = "person_history_" + UUID.randomUUID().toString().replace("-", "");
        var admin = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        admin.execute("CREATE SCHEMA " + schema);
        dataSource = new DriverManagerDataSource(
            POSTGRES.getJdbcUrl() + "&currentSchema=" + schema, POSTGRES.getUsername(), POSTGRES.getPassword()
        );
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE person_import_batch (id bigserial PRIMARY KEY, status text, success_records int, failure_records int)");
        jdbc.execute("""
            CREATE TABLE person_import_record (
                id bigserial PRIMARY KEY,
                batch_id bigint NOT NULL REFERENCES person_import_batch(id),
                keycloak_user_id varchar(64), account text, status text,
                CONSTRAINT reject_test_account CHECK (account <> 'reject')
            )
            """);
    }

    @ParameterizedTest
    @ValueSource(strings = {"lowercase", "mixedcase", "standalone", "already-fixed"})
    void migrationPreservesEightThousandRowsAndOtherSchema(String variant) throws Exception {
        jdbc.update("INSERT INTO person_import_batch(id, status) VALUES (100, 'SUCCESS'), (101, 'RUNNING')");
        jdbc.update("""
            INSERT INTO person_import_record(batch_id, keycloak_user_id, account, status)
            SELECT 100, 'kc-' || n, 'user-' || n, 'SUCCESS' FROM generate_series(1, 8000) n
            """);
        jdbc.execute("ALTER TABLE person_import_record ADD CONSTRAINT preserve_composite UNIQUE (batch_id, account)");
        // Another schema must neither cause the target constraint to be missed nor be modified.
        jdbc.execute("CREATE SCHEMA " + schema + "_other");
        jdbc.execute("CREATE TABLE " + schema + "_other.person_import_record (id bigint PRIMARY KEY, keycloak_user_id text CONSTRAINT uk_person_record_kc_id UNIQUE)");
        if (variant.equals("lowercase")) {
            jdbc.execute("ALTER TABLE person_import_record ADD CONSTRAINT uk_person_record_kc_id UNIQUE (keycloak_user_id)");
        } else if (variant.equals("mixedcase")) {
            jdbc.execute("ALTER TABLE person_import_record ADD CONSTRAINT \"UK_Person_record_kc_id\" UNIQUE (keycloak_user_id)");
        } else if (variant.equals("standalone")) {
            jdbc.execute("CREATE UNIQUE INDEX \"UK_Person_record_kc_id\" ON person_import_record(keycloak_user_id)");
        }
        if (variant.equals("lowercase")) {
            migrate(LEGACY); // COUNT(*)=2 across schemas causes the historical precondition to MARK_RAN.
            assertThat(jdbc.queryForObject("SELECT exectype FROM databasechangelog WHERE id = '20260623-01-drop-person-import-record-keycloak-id-unique'", String.class))
                .isEqualTo("MARK_RAN");
        }
        String before = historyDigest();
        migrate(REPAIR);
        migrate(REPAIR);
        assertThat(historyDigest()).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM person_import_record", Integer.class)).isEqualTo(8000);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_index WHERE indrelid='person_import_record'::regclass AND NOT indisunique AND indisvalid", Integer.class))
            .isGreaterThanOrEqualTo(1);
        jdbc.update("INSERT INTO person_import_record(batch_id,keycloak_user_id,account,status) VALUES (101,'kc-1','user-1','SUCCESS')");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM person_import_record WHERE keycloak_user_id='kc-1'", Integer.class)).isEqualTo(2);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO person_import_record(id,batch_id) VALUES (1,100)"))
            .hasMessageContaining("person_import_record_pkey");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO person_import_record(batch_id) VALUES (-1)"))
            .hasMessageContaining("foreign key");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO person_import_record(batch_id,account) VALUES (100,'user-1')"))
            .hasMessageContaining("preserve_composite");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_constraint WHERE conrelid='" + schema + "_other.person_import_record'::regclass AND contype='u'", Integer.class))
            .isEqualTo(1);
    }

    @Test
    void importsCommitBatchBeforeRowsEvenInsideCallerTransactionAndAllowRepeatedUser() {
        var tx = new DataSourceTransactionManager(dataSource);
        PersonnelImportService service = service(tx);
        new TransactionTemplate(tx).executeWithoutResult(outer -> {
            var first = service.importFromMdm("first", List.of(payload("alice")), Map.of());
            assertThat(first.successRecords()).isEqualTo(1);
            var second = service.importFromMdm("repeat", List.of(payload("alice")), Map.of());
            assertThat(second.successRecords()).isEqualTo(1);
            outer.setRollbackOnly(); // Already committed import history must remain after the caller rolls back.
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM person_import_batch WHERE status='SUCCESS'", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM person_import_record WHERE keycloak_user_id='kc-alice'", Integer.class)).isEqualTo(2);
    }

    @Test
    void failedRecordDoesNotPreventLaterSuccessOrBatchCompletion() {
        var result = service(new DataSourceTransactionManager(dataSource)).importFromMdm(
            "partial", List.of(payload("alice"), payload("broken"), payload("bob")), Map.of()
        );
        assertThat(result.successRecords()).isEqualTo(2);
        assertThat(result.failureRecords()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM person_import_record WHERE status='FAILED'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT success_records FROM person_import_batch", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT failure_records FROM person_import_batch", Integer.class)).isEqualTo(1);
    }

    private PersonnelImportService service(DataSourceTransactionManager tx) {
        var batches = mock(PersonImportBatchRepository.class);
        var records = mock(PersonImportRecordRepository.class);
        var provisioning = mock(KeycloakUserProvisioningService.class);
        var snapshots = mock(AdminKeycloakUserRepository.class);
        // These JDBC adapters model Spring Data repository save transactions, while constraints are real.
        when(batches.save(any(PersonImportBatch.class))).thenAnswer(call -> new TransactionTemplate(tx).execute(status -> {
            PersonImportBatch b = call.getArgument(0);
            if (b.getId() == null) {
                b.setId(jdbc.queryForObject("INSERT INTO person_import_batch(status) VALUES (?) RETURNING id", Long.class, b.getStatus().name()));
            } else {
                jdbc.update("UPDATE person_import_batch SET status=?,success_records=?,failure_records=? WHERE id=?",
                    b.getStatus().name(), b.getSuccessRecords(), b.getFailureRecords(), b.getId());
            }
            return b;
        }));
        when(batches.getReferenceById(anyLong())).thenAnswer(call -> {
            var b = new PersonImportBatch(); b.setId(call.getArgument(0)); return b;
        });
        when(records.save(any(PersonImportRecord.class))).thenAnswer(call -> {
            PersonImportRecord r = call.getArgument(0);
            String account = r.getAccount();
            if (account.equals("broken") && r.getStatus().name().equals("SUCCESS")) account = "reject";
            r.setId(jdbc.queryForObject(
                "INSERT INTO person_import_record(batch_id,keycloak_user_id,account,status) VALUES (?,?,?,?) RETURNING id",
                Long.class, r.getBatch().getId(), r.getKeycloakUserId(), account, r.getStatus().name()
            ));
            return r;
        });
        when(provisioning.provision(any())).thenAnswer(call -> "kc-" + ((PersonnelPayload) call.getArgument(0)).account());
        when(snapshots.save(any())).thenAnswer(call -> call.getArgument(0));
        var target = new PersonnelImportService(batches, records, mock(PersonnelProfileService.class), mock(PersonnelExcelParser.class),
            mock(PersonnelApiClient.class), mock(AuditV2Service.class), provisioning, snapshots, new ObjectMapper(), new MdmGatewayProperties(), tx);
        var proxy = new ProxyFactory(target);
        proxy.addAdvice(new TransactionInterceptor(tx, new AnnotationTransactionAttributeSource()));
        return (PersonnelImportService) proxy.getProxy();
    }

    private PersonnelPayload payload(String account) {
        return new PersonnelPayload(account, null, account, account, null, null, null, null, null, null, null, null,
            "ACTIVE", null, null, Map.of("securityLevel", "1"));
    }

    private String historyDigest() {
        return jdbc.queryForObject("SELECT md5(string_agg(row_to_json(r)::text, ',' ORDER BY id)) FROM person_import_record r", String.class);
    }

    private void migrate(String changelog) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            var database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            database.setDefaultSchemaName(schema);
            database.setLiquibaseSchemaName(schema);
            try (var liquibase = new Liquibase(changelog, new ClassLoaderResourceAccessor(), database)) {
                liquibase.update(new Contexts(), new LabelExpression());
            }
        }
    }
}
