package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@JdbcTest(
    properties = {
        "spring.liquibase.enabled=true",
        "spring.liquibase.change-log=classpath:config/liquibase/master.xml"
    }
)
@Import(CatalogClassificationMigrationService.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class CatalogClassificationMigrationServiceIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:17.4")
            .withDatabaseName("classification_migration_it")
            .withUsername("classification_migration_test")
            .withPassword("classification_migration_test");

    @DynamicPropertySource
    static void dataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CatalogClassificationMigrationService migrationService;

    @MockBean
    private ClassificationMigrationItemWorker itemWorker;

    @Test
    void unlabeledLegacyColumnsInheritTheHighestTableOrDatasetFloorDuringDryRun() {
        UUID datasetId = UUID.randomUUID();
        UUID tableId = UUID.randomUUID();
        UUID ordinaryColumnId = UUID.randomUUID();
        UUID sensitiveColumnId = UUID.randomUUID();
        jdbcTemplate.update(
            """
            insert into catalog_dataset (
                id, name, type, classification, hive_database, hive_table
            ) values (?, 's72_customer', 'POSTGRESQL', 'SECRET', 'public', 's72_customer')
            """,
            datasetId
        );
        jdbcTemplate.update(
            """
            insert into catalog_table_schema (
                id, dataset_id, name, classification
            ) values (?, ?, 's72_customer', 'INTERNAL')
            """,
            tableId,
            datasetId
        );
        jdbcTemplate.update(
            """
            insert into catalog_column_schema (
                id, table_id, name, data_type, nullable, status
            ) values
              (?, ?, 'display_name', 'varchar', true, 'ACTIVE'),
              (?, ?, 'identity_no', 'varchar', false, 'ACTIVE')
            """,
            ordinaryColumnId,
            tableId,
            sensitiveColumnId,
            tableId
        );
        jdbcTemplate.update(
            "update catalog_column_schema set sensitive_tags='PII:identity' where id=?",
            sensitiveColumnId
        );

        var run = migrationService.dryRun(
            new CatalogClassificationMigrationService.DryRunCommand(
                "s72-column-inheritance-" + UUID.randomUUID(),
                100
            ),
            "sprint72-test"
        );
        List<CatalogClassificationMigrationService.MigrationItemView> items =
            migrationService.items(run.id(), null, 1000);

        assertThat(items)
            .filteredOn(item ->
                "catalog_column_schema".equals(item.sourceTable()) &&
                (ordinaryColumnId.toString().equals(item.sourceId()) ||
                    sensitiveColumnId.toString().equals(item.sourceId()))
            )
            .hasSize(2)
            .allSatisfy(item -> {
                assertThat(item.legacyLevel()).isEqualTo("SECRET");
                assertThat(item.computedEffectiveLevel()).isEqualTo("SECRET");
                assertThat(item.decision()).isEqualTo("CREATE");
            });
    }
}
