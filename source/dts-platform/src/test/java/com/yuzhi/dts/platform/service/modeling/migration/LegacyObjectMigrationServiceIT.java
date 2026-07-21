package com.yuzhi.dts.platform.service.modeling.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.yuzhi.dts.platform.IntegrationTest;
import com.yuzhi.dts.platform.service.modeling.migration.LegacyObjectMigrationPlanner.Classification;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class LegacyObjectMigrationServiceIT {

    @Autowired
    private LegacyObjectMigrationService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void classifiesAndPersistsAnIsolatedAutoDimensionWithoutCrossTenantLeakage() {
        String tenant = service.defaultTenantId();
        UUID domainId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID legacyObjectId = UUID.randomUUID();
        String processId = "sprint67-auto-dimension-" + legacyObjectId;
        seedDimensionCandidate(tenant, domainId, planId, legacyObjectId, processId);

        LegacyObjectMigrationPlanner.Report report = service.dryRun(tenant);
        LegacyObjectMigrationPlanner.LegacyObjectDecision decision = report
            .decisions()
            .stream()
            .filter(item -> legacyObjectId.equals(item.legacyId()))
            .findFirst()
            .orElseThrow();

        assertThat(decision.classification()).isEqualTo(Classification.AUTO_DIMENSION);
        assertThat(decision.targetType()).isEqualTo("DIMENSION");
        assertThat(decision.targetPlanId()).isEqualTo(planId);
        assertThat(decision.ready()).isTrue();
        assertThat(decision.blockers()).isEmpty();
        assertThatThrownBy(() -> service.dryRun("foreign-" + UUID.randomUUID()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("TENANT_SCOPE_FORBIDDEN");

        assertThatThrownBy(() -> service.execute(tenant, report.batchId(), "0".repeat(64), "sprint67-it"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("MIGRATION_CHECKSUM_MISMATCH");

        LegacyObjectMigrationService.MigrationExecution execution = service.execute(
            tenant,
            report.batchId(),
            report.checksum(),
            "sprint67-it"
        );
        assertThat(execution.status()).isEqualTo("READY_FOR_APPROVAL");
        assertThat(execution.reconciliation().get("readyForApproval")).isPositive();
        assertThat(
            jdbc.queryForMap(
                """
                select classification, decision_status, target_plan_id
                  from modeling_legacy_object_migration
                 where batch_id = ? and tenant_id = ? and legacy_source = ? and legacy_id = ?
                """,
                report.batchId(),
                tenant,
                LegacyObjectMigrationService.MODELING_VNEXT_SOURCE,
                legacyObjectId
            )
        )
            .containsEntry("classification", "AUTO_DIMENSION")
            .containsEntry("decision_status", "READY_FOR_APPROVAL")
            .containsEntry("target_plan_id", planId);

        List<JsonNode> sameTenant = service.projectLegacyRead(
            tenant,
            LegacyObjectMigrationService.MODELING_VNEXT_SOURCE,
            List.of(Map.of("id", legacyObjectId.toString(), "code", "sprint67_auto_dimension"))
        );
        List<JsonNode> otherTenant = service.projectLegacyRead(
            "foreign-" + UUID.randomUUID(),
            LegacyObjectMigrationService.MODELING_VNEXT_SOURCE,
            List.of(Map.of("id", legacyObjectId.toString(), "code", "sprint67_auto_dimension"))
        );
        assertThat(sameTenant).singleElement().satisfies(item -> {
            assertThat(item.path("migration").path("classification").asText()).isEqualTo("AUTO_DIMENSION");
            assertThat(item.path("migration").path("decisionStatus").asText()).isEqualTo("READY_FOR_APPROVAL");
        });
        assertThat(otherTenant).singleElement().satisfies(item ->
            assertThat(item.path("migration").path("decisionStatus").asText()).isEqualTo("NOT_EVALUATED")
        );

        assertThat(service.execute(tenant, report.batchId(), report.checksum(), "sprint67-it").replayed()).isTrue();
    }

    private void seedDimensionCandidate(
        String tenant,
        UUID domainId,
        UUID planId,
        UUID legacyObjectId,
        String processId
    ) {
        jdbc.update(
            "insert into catalog_domain (id, name, code, lifecycle_status, access_policy) values (?, ?, ?, 'ACTIVE', 'PUBLIC')",
            domainId,
            "Sprint 67 isolated domain",
            "SPRINT67_" + domainId.toString().replace("-", "")
        );
        jdbc.update(
            """
            insert into modeling_warehouse_plan (
                id, tenant_id, owner, code, name, owner_id, onboarding_mode, domain_id, process_id,
                layer, modeling_mode, target_grain, lifecycle_status, status, version,
                created_date, last_modified_date
            ) values (?, ?, ?, ?, ?, ?, 'BUSINESS_FIRST', ?, ?, 'DIM', 'DESIGNER_GENERATED', ?, 'DRAFT', 'DRAFT', 1,
                      current_timestamp, current_timestamp)
            """,
            planId,
            tenant,
            "sprint67-it",
            "wp_" + planId.toString().replace("-", ""),
            "Sprint 67 auto-map plan",
            "sprint67-it",
            domainId,
            processId,
            "one row per customer"
        );
        jdbc.update(
            """
            insert into modeling_business_object (
                id, tenant_id, owner, code, name, object_kind, process_id, business_key,
                grain_statement, source_refs, implementation_mode, status, version,
                created_date, last_modified_date
            ) values (?, ?, ?, ?, ?, 'DIMENSION', ?, ?::text, ?, '[]', 'DESIGNER_GENERATED', 'DRAFT', 1,
                      current_timestamp, current_timestamp)
            """,
            legacyObjectId,
            tenant,
            "sprint67-it",
            "legacy_dimension_" + legacyObjectId.toString().replace("-", ""),
            "Sprint 67 isolated legacy dimension",
            processId,
            "[\"customer_id\"]",
            "one row per customer"
        );
    }
}
