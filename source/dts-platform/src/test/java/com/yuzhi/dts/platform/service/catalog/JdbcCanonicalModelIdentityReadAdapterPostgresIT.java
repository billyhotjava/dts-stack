package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.service.catalog.CanonicalModelIdentityReadPort.ModelIdentity;
import com.yuzhi.dts.platform.service.catalog.CanonicalModelIdentityReadPort.ModelIdentityType;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class JdbcCanonicalModelIdentityReadAdapterPostgresIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.4")
        .withDatabaseName("canonicalModelIdentityReadAdapterIT")
        .withUsername("canonical_model_identity_test")
        .withPassword("canonical_model_identity_test");

    private Connection connection;
    private String schema;
    private JdbcCanonicalModelIdentityReadAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        schema = "canonical_model_identity_" + UUID.randomUUID().toString().replace("-", "");
        connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        try (Statement statement = connection.createStatement()) {
            statement.execute("create schema " + schema);
            statement.execute("set search_path to " + schema);
            statement.execute(
                """
                create table modeling_model_spec (
                    id uuid primary key,
                    tenant_id varchar(128) not null,
                    name varchar(255) not null,
                    revision int not null,
                    dbt_unique_id varchar(512)
                )
                """
            );
            statement.execute(
                """
                create table modeling_model_implementation (
                    id uuid primary key,
                    tenant_id varchar(128) not null,
                    model_spec_id uuid not null,
                    model_revision int not null,
                    dbt_unique_id varchar(512) not null
                )
                """
            );
        }
        adapter = new JdbcCanonicalModelIdentityReadAdapter(
            new JdbcTemplate(new SingleConnectionDataSource(connection, true)),
            "tenant-a"
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        if (connection == null) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("set search_path to public");
            statement.execute("drop schema " + schema + " cascade");
        } finally {
            connection.close();
        }
    }

    @Test
    void resolvesOnlyCanonicalModelsInsideServerTenant() throws Exception {
        UUID specId = insertModel("tenant-a", "预算执行事实", 3, "model.finance.fct_budget_execution");
        UUID implementationId = insertImplementation("tenant-a", specId, 3, "model.finance.fct_budget_execution");
        UUID foreignSpecId = insertModel("tenant-b", "预算执行事实", 9, "model.foreign.fct_budget_execution");
        insertImplementation("tenant-b", foreignSpecId, 9, "model.foreign.fct_budget_execution");

        ModelIdentity dbtIdentity = adapter.findDbtModel("fct_budget_execution").orElseThrow();
        ModelIdentity semanticIdentity = adapter.findById(specId).orElseThrow();
        ModelIdentity implementationIdentity = adapter.findById(implementationId).orElseThrow();

        assertThat(dbtIdentity.type()).isEqualTo(ModelIdentityType.DBT_MODEL);
        assertThat(dbtIdentity.assetId()).isEqualTo(implementationId);
        assertThat(dbtIdentity.assetKey()).isEqualTo("dbt:model.finance.fct_budget_execution");
        assertThat(dbtIdentity.contractVersion()).isEqualTo("r3");
        assertThat(semanticIdentity.type()).isEqualTo(ModelIdentityType.SEMANTIC_MODEL);
        assertThat(semanticIdentity.assetKey()).isEqualTo("semantic-model:" + specId);
        assertThat(implementationIdentity).isEqualTo(dbtIdentity);
    }

    @Test
    void failsClosedForAmbiguousDbtResourceNameButAllowsExactUniqueId() throws Exception {
        UUID firstSpec = insertModel("tenant-a", "订单事实 A", 1, "model.sales.orders");
        UUID secondSpec = insertModel("tenant-a", "订单事实 B", 2, "model.finance.orders");
        insertImplementation("tenant-a", firstSpec, 1, "model.sales.orders");
        insertImplementation("tenant-a", secondSpec, 2, "model.finance.orders");

        assertThat(adapter.findDbtModel("orders")).isEmpty();
        assertThat(adapter.findDbtModel("model.finance.orders")).isPresent();
        assertThat(adapter.findDbtModelsByResourceNames(Set.of("orders"))).isEmpty();
    }

    @Test
    void batchLookupReturnsOneCanonicalIdentityPerUnambiguousRef() throws Exception {
        UUID orderSpec = insertModel("tenant-a", "订单事实", 4, "model.finance.fct_order");
        UUID budgetSpec = insertModel("tenant-a", "预算事实", 5, "model.finance.fct_budget");
        insertImplementation("tenant-a", orderSpec, 4, "model.finance.fct_order");
        insertImplementation("tenant-a", budgetSpec, 5, "model.finance.fct_budget");

        Map<String, ModelIdentity> result = adapter.findDbtModelsByResourceNames(
            Set.of("fct_order", "fct_budget", "missing")
        );

        assertThat(result).containsOnlyKeys("fct_order", "fct_budget");
        assertThat(result.get("fct_order").modelSpecId()).isEqualTo(orderSpec);
        assertThat(result.get("fct_budget").modelSpecId()).isEqualTo(budgetSpec);
    }

    private UUID insertModel(String tenantId, String name, int revision, String dbtUniqueId) throws Exception {
        UUID id = UUID.randomUUID();
        try (
            PreparedStatement statement = connection.prepareStatement(
                "insert into modeling_model_spec (id, tenant_id, name, revision, dbt_unique_id) values (?, ?, ?, ?, ?)"
            )
        ) {
            statement.setObject(1, id);
            statement.setString(2, tenantId);
            statement.setString(3, name);
            statement.setInt(4, revision);
            statement.setString(5, dbtUniqueId);
            statement.executeUpdate();
        }
        return id;
    }

    private UUID insertImplementation(String tenantId, UUID modelSpecId, int revision, String dbtUniqueId) throws Exception {
        UUID id = UUID.randomUUID();
        try (
            PreparedStatement statement = connection.prepareStatement(
                "insert into modeling_model_implementation (id, tenant_id, model_spec_id, model_revision, dbt_unique_id) values (?, ?, ?, ?, ?)"
            )
        ) {
            statement.setObject(1, id);
            statement.setString(2, tenantId);
            statement.setObject(3, modelSpecId);
            statement.setInt(4, revision);
            statement.setString(5, dbtUniqueId);
            statement.executeUpdate();
        }
        return id;
    }
}
