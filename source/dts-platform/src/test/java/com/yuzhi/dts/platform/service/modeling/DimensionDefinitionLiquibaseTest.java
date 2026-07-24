package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DimensionDefinitionLiquibaseTest {

    private static final String CHANGELOG = "/config/liquibase/changelog/20260724_01_dimension_definition.xml";

    @Test
    void createsTenantScopedImmutableDimensionDefinitionLedger() throws Exception {
        String xml = read(CHANGELOG);

        assertThat(xml)
            .contains("tableName=\"modeling_dimension_definition\"")
            .contains("tableName=\"modeling_dimension_definition_revision\"")
            .contains("tableName=\"modeling_dimension_definition_legacy_map\"")
            .contains("name=\"system_code\"")
            .contains("name=\"domain_id\"")
            .contains("name=\"reuse_scope\"")
            .contains("name=\"hierarchies_json\"")
            .contains("name=\"current_checksum\"")
            .contains("name=\"idempotency_key\"")
            .contains("name=\"idempotency_request_hash\"")
            .contains("name=\"idempotency_response_snapshot\"")
            .contains("columnNames=\"tenant_id, id\"")
            .contains("constraintName=\"uk_dimension_definition_tenant_id\"")
            .contains("columnNames=\"tenant_id, dimension_definition_id, revision\"")
            .contains("constraintName=\"uk_dimension_definition_revision\"")
            .contains("CREATE UNIQUE INDEX uk_dimension_definition_tenant_system_code")
            .contains("CREATE UNIQUE INDEX uk_dimension_definition_idempotency")
            .contains("CREATE INDEX idx_dimension_definition_legacy_revision")
            .contains("ck_dimension_definition_status")
            .contains("ck_dimension_definition_reuse_scope")
            .contains("ck_dimension_definition_revision_snapshot")
            .contains("ck_dimension_definition_legacy_classification")
            .contains("CHECK (system_code ~ '^dim_[0-9a-f]{32}$')")
            .doesNotContain("type=\"${datetimeType}\"");
        assertThat(xml.split("type=\"timestamptz\"", -1)).hasSize(5);
    }

    @Test
    void bindsReferencesByTenantAndRevisionWithoutDeleteCascade() throws Exception {
        String xml = read(CHANGELOG);

        assertThat(xml)
            .contains("baseTableName=\"modeling_dimension_definition_revision\"")
            .contains("baseColumnNames=\"tenant_id, dimension_definition_id\"")
            .contains("referencedColumnNames=\"tenant_id, id\"")
            .contains("baseTableName=\"modeling_dimension_definition_legacy_map\"")
            .contains("baseColumnNames=\"tenant_id, legacy_model_spec_id\"")
            .contains("baseColumnNames=\"tenant_id, dimension_definition_id, dimension_definition_revision\"")
            .contains("referencedColumnNames=\"tenant_id, dimension_definition_id, revision\"")
            .contains("baseTableName=\"modeling_dimension_definition\"")
            .contains("baseColumnNames=\"tenant_id, id, revision\"")
            .contains("constraintName=\"fk_dimension_definition_current_revision\"")
            .doesNotContain("onDelete=\"CASCADE\"")
            .doesNotContain("cascadeConstraints=\"true\"");
    }

    @Test
    void addsNullableRevisionBindingsToBothModelSpecLedgersWithDimensionOnlyChecks() throws Exception {
        String xml = read(CHANGELOG);

        assertThat(xml)
            .contains("addColumn tableName=\"modeling_model_spec\"")
            .contains("addColumn tableName=\"modeling_model_spec_revision\"")
            .contains("name=\"dimension_definition_id\"")
            .contains("name=\"dimension_definition_revision\"")
            .contains("ck_model_spec_dimension_definition_ref")
            .contains("model_type = 'DIMENSION'")
            .contains("ck_model_spec_revision_dimension_definition_ref")
            .contains("contract_version = 2")
            .contains("coalesce(snapshot_json ->> 'modelType', '') = 'DIMENSION'")
            .contains("baseTableName=\"modeling_model_spec\"")
            .contains("baseTableName=\"modeling_model_spec_revision\"")
            .contains("baseColumnNames=\"tenant_id, dimension_definition_id, dimension_definition_revision\"")
            .contains("referencedColumnNames=\"tenant_id, dimension_definition_id, revision\"");
    }

    @Test
    void includesTheForwardOnlyChangeInMaster() throws Exception {
        String master = read("/config/liquibase/master.xml");

        assertThat(master)
            .contains(
                "<include file=\"config/liquibase/changelog/20260724_01_dimension_definition.xml\" relativeToChangelogFile=\"false\"/>"
            );
    }

    @Test
    void explicitlyRejectsRollbackForBothForwardOnlyChangesets() throws Exception {
        String xml = read(CHANGELOG);

        assertThat(xml)
            .contains("ROLLBACK_BLOCKED_DIMENSION_DEFINITION_LEDGER_FORWARD_ONLY")
            .contains("ROLLBACK_BLOCKED_MODEL_SPEC_DIMENSION_REFERENCE_FORWARD_ONLY")
            .contains("Recovery requires a new forward changeset");
    }

    private String read(String resource) throws Exception {
        try (var input = getClass().getResourceAsStream(resource)) {
            assertThat(input).as(resource).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
