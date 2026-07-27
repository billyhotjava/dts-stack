package com.yuzhi.dts.platform.repository.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.IntegrationTest;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.InputMode;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.PhysicalAssetInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.SaveImplementationCommand;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.*;
import com.yuzhi.dts.platform.service.modeling.ModelSpecSnapshotCodec;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class Sprint74ModelImplementationSettingsRepositoryIT {

    @Autowired
    private ModelLifecycleRepository implementations;

    @Autowired
    private ModelSpecRepository modelSpecs;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void persistsTheCanonicalTargetLoadPartitionAndRetentionSettings() {
        String tenant = "sprint74-settings-it-" + UUID.randomUUID();
        String actor = "owner-1";
        UUID planId = UUID.randomUUID();
        UUID domainId = UUID.randomUUID();
        UUID sourceBindingId = UUID.randomUUID();
        seedContext(tenant, actor, planId, domainId, sourceBindingId);

        Instant now = Instant.parse("2026-07-27T00:00:00Z");
        ModelSpecSnapshotCodec codec = new ModelSpecSnapshotCodec(objectMapper);
        CreateModelSpecCommand create = new CreateModelSpecCommand(
            planId,
            domainId,
            ModelType.FACT,
            Layer.DWD,
            "sprint74_settings_model",
            "Sprint-74 settings database contract",
            ImplementationMode.DESIGNER_GENERATED,
            "table",
            null,
            null,
            new Grain("one row per settings event", List.of("event_id")),
            FactShape.TRANSACTION,
            new TimeSemantics(TimeSemanticsType.EVENT_TIME, List.of("business_date")),
            List.of(
                new ModelField("event_id", "事项编号", "varchar", false, null, FieldRole.KEY, null, null, false, null),
                new ModelField("business_date", "业务日期", "date", false, null, FieldRole.TIME, null, null, false, null)
            ),
            List.of(
                new SourceRef(
                    SourceKind.TABLE,
                    "ods.sprint74_settings",
                    Layer.ODS,
                    SourceRole.PRIMARY,
                    null,
                    null,
                    null,
                    0,
                    sourceBindingId,
                    "v1"
                )
            ),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            null,
            null,
            "sprint74-settings-create"
        );
        ModelSpecView model = codec.toCreatedView(UUID.randomUUID(), create, now);
        String snapshot = codec.write(model);
        assertThat(modelSpecs.insertV2(tenant, actor, create, model, codec.requestHash(create), snapshot)).isEqualTo(1);
        modelSpecs.insertV2Revision(tenant, actor, model, snapshot);

        SaveImplementationCommand command = new SaveImplementationCommand(
            InputMode.PHYSICAL_ASSET,
            List.of(new PhysicalAssetInput(sourceBindingId, "v1")),
            List.of(),
            Map.of(
                "targetPhysicalName", "dwd_sprint74_settings",
                "loadStrategy", "INCREMENTAL",
                "partitionFields", List.of("business_date"),
                "retentionDays", 3650
            ),
            ImplementationMode.DESIGNER_GENERATED,
            "table",
            "sprint74-settings-implementation"
        );

        assertThat(
            implementations.saveImplementation(
                tenant,
                actor,
                model,
                "sprint74",
                "model.sprint74.dwd_sprint74_settings",
                command,
                now
            )
        ).isEqualTo(1);
        assertThat(implementations.findImplementation(tenant, model.id()).orElseThrow().settings())
            .containsEntry("targetPhysicalName", "dwd_sprint74_settings")
            .containsEntry("loadStrategy", "INCREMENTAL")
            .containsEntry("partitionFields", List.of("business_date"))
            .containsEntry("retentionDays", 3650);
    }

    private void seedContext(String tenant, String actor, UUID planId, UUID domainId, UUID sourceBindingId) {
        jdbcTemplate.update(
            "insert into catalog_domain (id, name, code, lifecycle_status, access_policy) values (?, ?, ?, 'ACTIVE', 'PUBLIC')",
            domainId,
            "Sprint 74 settings",
            "S74_" + domainId.toString().replace("-", "")
        );
        jdbcTemplate.update(
            """
            insert into modeling_warehouse_plan (
                id, tenant_id, owner, code, name, owner_id, onboarding_mode, lifecycle_status,
                status, version, created_date, last_modified_date
            ) values (?, ?, ?, ?, ?, ?, 'BUSINESS_FIRST', 'DRAFT', 'DRAFT', 1, current_timestamp, current_timestamp)
            """,
            planId,
            tenant,
            actor,
            "wp_" + planId.toString().replace("-", ""),
            "Sprint 74 settings plan",
            actor
        );
        jdbcTemplate.update(
            """
            insert into modeling_warehouse_plan_domain (
                id, tenant_id, plan_id, domain_id, confirmation_status, created_date, last_modified_date
            ) values (?, ?, ?, ?, 'CONFIRMED', current_timestamp, current_timestamp)
            """,
            UUID.randomUUID(),
            tenant,
            planId,
            domainId
        );
        jdbcTemplate.update(
            """
            insert into modeling_warehouse_plan_source (
                id, tenant_id, plan_id, source_type, source_id, source_version, confirmation_status,
                created_date, last_modified_date
            ) values (?, ?, ?, 'CATALOG_TABLE', 'ods.sprint74_settings', 'v1', 'CONFIRMED', current_timestamp, current_timestamp)
            """,
            sourceBindingId,
            tenant,
            planId
        );
    }
}
