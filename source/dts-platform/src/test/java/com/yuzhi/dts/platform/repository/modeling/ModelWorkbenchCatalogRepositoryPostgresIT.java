package com.yuzhi.dts.platform.repository.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.service.modeling.ModelWorkbenchCatalogContract.CatalogPage;
import com.yuzhi.dts.platform.service.modeling.ModelWorkbenchCatalogContract.CatalogQuery;
import java.sql.Connection;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ModelWorkbenchCatalogRepositoryPostgresIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.4")
        .withDatabaseName("model_workbench_catalog_it")
        .withUsername("model_workbench_catalog_test")
        .withPassword("model_workbench_catalog_test");

    @Test
    void pagesTheCombinedProjectionAndAppliesLiteralSearchAndDomainVisibility() throws Exception {
        try (Connection connection = POSTGRES.createConnection("")) {
            connection.setAutoCommit(true);
            JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            createTables(jdbc);
            String tenant = "default";
            UUID visibleDomain = UUID.randomUUID();
            UUID hiddenDomain = UUID.randomUUID();
            UUID planId = UUID.randomUUID();
            UUID modelId = insertModel(jdbc, tenant, planId, visibleDomain, "Visible date", "it_%_date");
            insertModel(jdbc, tenant, planId, hiddenDomain, "Hidden date", "hidden_%_date");
            insertDimension(jdbc, tenant, visibleDomain);

            ModelWorkbenchCatalogRepository repository = new ModelWorkbenchCatalogRepository(jdbc);
            CatalogPage searched = repository.page(
                tenant,
                Set.of(visibleDomain),
                new CatalogQuery(0, 10, "%_date", planId, null, "DIMENSION", null, "DRAFT")
            );
            CatalogPage firstPage = repository.page(
                tenant,
                Set.of(visibleDomain),
                new CatalogQuery(0, 1, null, null, null, null, null, null)
            );

            assertThat(searched.totalElements()).isEqualTo(1);
            assertThat(searched.content()).singleElement().satisfies(entry -> {
                assertThat(entry.id()).isEqualTo(modelId);
                assertThat(entry.code()).isEqualTo("it_%_date");
            });
            assertThat(firstPage.totalElements()).isEqualTo(2);
            assertThat(firstPage.totalPages()).isEqualTo(2);
            assertThat(firstPage.content()).hasSize(1);
        }
    }

    private static void createTables(JdbcTemplate jdbc) {
        jdbc.execute(
            """
            create table modeling_dimension_definition (
                tenant_id varchar(128) not null,
                id uuid primary key,
                name varchar(256) not null,
                system_code varchar(64) not null,
                domain_id uuid not null,
                status varchar(32) not null,
                revision int not null
            )
            """
        );
        jdbc.execute(
            """
            create table modeling_model_spec (
                tenant_id varchar(128) not null,
                id uuid primary key,
                name varchar(256) not null,
                plan_id uuid not null,
                domain_id uuid not null,
                model_type varchar(32) not null,
                layer varchar(16) not null,
                status varchar(32) not null,
                revision int not null,
                contract_version int not null,
                legacy_refs jsonb
            )
            """
        );
        jdbc.execute(
            """
            create table modeling_model_spec_revision (
                tenant_id varchar(128) not null,
                model_spec_id uuid not null,
                revision int not null,
                contract_version int not null,
                snapshot_json jsonb not null
            )
            """
        );
    }

    private static UUID insertModel(
        JdbcTemplate jdbc,
        String tenant,
        UUID planId,
        UUID domainId,
        String name,
        String physicalName
    ) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            """
            insert into modeling_model_spec (
                tenant_id, id, name, plan_id, domain_id, model_type, layer,
                status, revision, contract_version, legacy_refs
            ) values (?, ?, ?, ?, ?, 'DIMENSION', 'DWD', 'DRAFT', 1, 2, null)
            """,
            tenant,
            id,
            name,
            planId,
            domainId
        );
        jdbc.update(
            """
            insert into modeling_model_spec_revision (
                tenant_id, model_spec_id, revision, contract_version, snapshot_json
            ) values (?, ?, 1, 2, cast(? as jsonb))
            """,
            tenant,
            id,
            "{\"implementationPolicy\":{\"physicalName\":\"" + physicalName + "\"}}"
        );
        return id;
    }

    private static void insertDimension(JdbcTemplate jdbc, String tenant, UUID domainId) {
        jdbc.update(
            """
            insert into modeling_dimension_definition (
                tenant_id, id, name, system_code, domain_id, status, revision
            ) values (?, ?, 'Date', 'dim_date', ?, 'CURRENT', 1)
            """,
            tenant,
            UUID.randomUUID(),
            domainId
        );
    }
}
