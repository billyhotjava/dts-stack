package com.yuzhi.dts.platform.repository.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuzhi.dts.platform.IntegrationTest;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehouseLayerContract.StoredWarehouseLayer;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class WarehouseLayerRepositoryIT {

    @Autowired
    private WarehouseLayerRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void customRowCreatedWithoutTenantDataIsVisibleToIndependentActorCalls() {
        StoredWarehouseLayer row = row("FIN_DETAIL", "DWD", null);
        assertThat(repository.insert(row)).isEqualTo(1);

        StoredWarehouseLayer found = repository.findByCode("FIN_DETAIL").orElseThrow();
        assertThat(found.name()).isEqualTo("财务明细层");
        assertThat(found.status()).isEqualTo("ACTIVE");
        assertThat(repository.findAllActive()).extracting(StoredWarehouseLayer::code).contains("FIN_DETAIL");
    }

    @Test
    void logicalDeletionDoesNotReleaseTheUniqueCode() {
        StoredWarehouseLayer row = row("FIN_DETAIL", "DWD", null);
        repository.insert(row);
        assertThat(repository.softDelete("FIN_DETAIL", 1, "bob", Instant.now())).isEqualTo(1);

        assertThat(repository.codeExists("FIN_DETAIL")).isTrue();
        assertThat(repository.findAllActive()).extracting(StoredWarehouseLayer::code).doesNotContain("FIN_DETAIL");
        assertThat(repository.findByCode("FIN_DETAIL").orElseThrow().status()).isEqualTo("DELETED");

        assertThatThrownBy(() -> repository.insert(row))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void countActiveModelReferencesCountsNonArchivedStatusesOnly() {
        StoredWarehouseLayer row = row("FIN_DETAIL", "DWD", null);
        repository.insert(row);
        String tenant = "warehouse-layer-it-" + UUID.randomUUID();
        UUID domainId = seedDomain();
        UUID planId = seedPlan(tenant, domainId);
        seedModelSpec(UUID.randomUUID(), "FIN_DETAIL", "DRAFT", planId, domainId, tenant);
        seedModelSpec(UUID.randomUUID(), "FIN_DETAIL", "PUBLISHED", planId, domainId, tenant);
        seedModelSpec(UUID.randomUUID(), "FIN_DETAIL", "ARCHIVED", planId, domainId, tenant);

        assertThat(repository.countActiveModelReferences("FIN_DETAIL")).isEqualTo(2);
    }

    @Test
    void optimisticDeleteWithStaleVersionUpdatesZeroRows() {
        StoredWarehouseLayer row = row("FIN_DETAIL", "DWD", null);
        repository.insert(row);

        assertThat(repository.softDelete("FIN_DETAIL", 99, "bob", Instant.now())).isZero();
        assertThat(repository.findByCode("FIN_DETAIL").orElseThrow().status()).isEqualTo("ACTIVE");
    }

    private static StoredWarehouseLayer row(String code, String systemLayerCode, String prefix) {
        Instant now = Instant.now();
        return new StoredWarehouseLayer(
            UUID.randomUUID(), code, "财务明细层", systemLayerCode, "财务域明细", prefix,
            "ACTIVE", 1, "alice", now, "alice", now
        );
    }

    private void seedModelSpec(
        UUID modelId,
        String warehouseLayerCode,
        String status,
        UUID planId,
        UUID domainId,
        String tenant
    ) {
        jdbcTemplate.update(
            """
            insert into modeling_model_spec (
                id, tenant_id, plan_id, domain_id, layer, model_type, implementation_mode, name, status,
                revision, version, contract_version, warehouse_layer_code, current_checksum,
                idempotency_key, idempotency_request_hash, idempotency_response_snapshot,
                created_date, last_modified_date
            ) values (?, ?, ?, ?, 'DWD', 'FACT', 'DESIGNER_GENERATED', ?, ?, 1, 1, 2, ?, ?, ?, ?, '{}', current_timestamp, current_timestamp)
            """,
            modelId,
            tenant,
            planId,
            domainId,
            "model-" + modelId.toString().substring(0, 8),
            status,
            warehouseLayerCode,
            "a".repeat(64),
            "layer-it-key-" + modelId,
            "b".repeat(64)
        );
    }

    private UUID seedDomain() {
        UUID domainId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into catalog_domain (id, name, code, lifecycle_status, access_policy) values (?, ?, ?, 'ACTIVE', 'PUBLIC')",
            domainId,
            "Warehouse layer IT domain",
            "WLI_DOMAIN_" + domainId.toString().replace("-", "")
        );
        return domainId;
    }

    private UUID seedPlan(String tenant, UUID domainId) {
        UUID planId = UUID.randomUUID();
        jdbcTemplate.update(
            """
            insert into modeling_warehouse_plan (
                id, tenant_id, owner, code, name, owner_id, onboarding_mode, lifecycle_status,
                status, version, created_date, last_modified_date
            ) values (?, ?, ?, ?, ?, ?, 'BUSINESS_FIRST', 'DRAFT', 'DRAFT', 1, current_timestamp, current_timestamp)
            """,
            planId,
            tenant,
            "owner",
            "wp_" + planId.toString().replace("-", ""),
            "Warehouse layer IT plan",
            "owner"
        );
        return planId;
    }
}
