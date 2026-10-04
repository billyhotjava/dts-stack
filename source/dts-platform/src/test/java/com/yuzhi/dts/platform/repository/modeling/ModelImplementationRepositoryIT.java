package com.yuzhi.dts.platform.repository.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.IntegrationTest;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ArtifactWrite;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.FieldMapping;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.GeneratedInput;
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
class ModelImplementationRepositoryIT {

    @Autowired
    private ModelLifecycleRepository repository;

    @Autowired
    private ModelSpecRepository modelSpecs;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void appendsOnlyChangedInputPayloadsAndPinsArtifactImplementationMetadata() {
        String tenant = "implementation-it-" + UUID.randomUUID();
        String actor = "owner-1";
        UUID planId = UUID.randomUUID();
        UUID domainId = UUID.randomUUID();
        UUID sourceBindingId = UUID.randomUUID();
        seedContext(tenant, actor, planId, domainId, sourceBindingId);
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        ModelSpecSnapshotCodec codec = new ModelSpecSnapshotCodec(objectMapper);
        CreateModelSpecCommand create = command(planId, domainId, sourceBindingId);
        ModelSpecView model = codec.toCreatedView(UUID.randomUUID(), create, now);
        String snapshot = codec.write(model);
        assertThat(modelSpecs.insertV2(tenant, actor, create, model, codec.requestHash(create), snapshot)).isEqualTo(1);
        modelSpecs.insertV2Revision(tenant, actor, model, snapshot);

        SaveImplementationCommand physical = new SaveImplementationCommand(
            InputMode.PHYSICAL_ASSET,
            List.of(new PhysicalAssetInput(sourceBindingId, "v1")),
            List.of(new FieldMapping("customer_id", "customer_id")),
            Map.of("casts", Map.of("customer_id", "string")),
            ImplementationMode.DESIGNER_GENERATED,
            "table",
            "implementation-physical"
        );
        assertThat(repository.saveImplementation(tenant, actor, model, "warehouse", "model.warehouse.customer", physical, now)).isEqualTo(1);
        assertThat(repository.saveImplementation(tenant, actor, model, "warehouse", "model.warehouse.customer", physical, now.plusSeconds(1)))
            .isEqualTo(1);
        assertThat(repository.findImplementation(tenant, model.id()).orElseThrow())
            .extracting(item -> item.implementationRevision(), item -> item.inputMode(), item -> item.inputs())
            .containsExactly(1, InputMode.PHYSICAL_ASSET, physical.inputs());
        assertThat(revisionCount(tenant, model.id())).isEqualTo(1);

        var implementation = repository.findImplementation(tenant, model.id()).orElseThrow();
        repository.saveArtifacts(
            tenant,
            model,
            implementation,
            "compile-physical",
            List.of(new ArtifactWrite("SQL", "models/customer.sql", "a".repeat(64), "select 1", "MODEL", "table", sourceBindingId)),
            now.plusSeconds(2)
        );
        assertThat(repository.listArtifacts(tenant, model.id(), model.revision()))
            .singleElement()
            .extracting(item -> item.implementationRevision(), item -> item.nodeKind(), item -> item.materialization(), item -> item.physicalAssetRef())
            .containsExactly(1, "MODEL", "table", sourceBindingId);

        SaveImplementationCommand generated = new SaveImplementationCommand(
            InputMode.GENERATED,
            List.of(new GeneratedInput("MODEL_COMPILER", Map.of("template", "customer"))),
            List.of(),
            Map.of(),
            ImplementationMode.DESIGNER_GENERATED,
            "view",
            "implementation-generated"
        );
        assertThat(repository.saveImplementation(tenant, actor, model, "warehouse", "model.warehouse.customer", generated, now.plusSeconds(3)))
            .isEqualTo(1);
        assertThat(repository.findImplementation(tenant, model.id()).orElseThrow())
            .extracting(item -> item.implementationRevision(), item -> item.inputMode(), item -> item.materialization())
            .containsExactly(2, InputMode.GENERATED, "view");
        assertThat(revisionCount(tenant, model.id())).isEqualTo(2);
    }

    @Test
    void convertsOnlyDbtManagedHeadIntoANewDesignerImplementationRevision() {
        String tenant = "implementation-convert-it-" + UUID.randomUUID();
        String actor = "owner-1";
        UUID planId = UUID.randomUUID();
        UUID domainId = UUID.randomUUID();
        UUID sourceBindingId = UUID.randomUUID();
        seedContext(tenant, actor, planId, domainId, sourceBindingId);
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        ModelSpecSnapshotCodec codec = new ModelSpecSnapshotCodec(objectMapper);
        ModelSpecView model = codec.toCreatedView(UUID.randomUUID(), command(planId, domainId, sourceBindingId), now);
        assertThat(modelSpecs.insertV2(tenant, actor, command(planId, domainId, sourceBindingId), model, "conversion-request", codec.write(model)))
            .isEqualTo(1);
        modelSpecs.insertV2Revision(tenant, actor, model, codec.write(model));

        SaveImplementationCommand dbt = new SaveImplementationCommand(
            InputMode.GENERATED,
            List.of(new GeneratedInput("DBT_MANAGED_CLAIM", Map.of())),
            List.of(),
            Map.of(),
            ImplementationMode.DBT_MANAGED,
            "table",
            "claim-dbt"
        );
        assertThat(repository.saveImplementation(tenant, actor, model, "dbt-project", "model.dbt.customer", dbt, now)).isEqualTo(1);
        SaveImplementationCommand designer = new SaveImplementationCommand(
            InputMode.PHYSICAL_ASSET,
            List.of(new PhysicalAssetInput(sourceBindingId, "v1")),
            List.of(),
            Map.of(),
            ImplementationMode.DESIGNER_GENERATED,
            "table",
            "convert-designer"
        );

        assertThat(repository.convertImplementationOwnership(tenant, actor, model, "warehouse", "model.warehouse.customer", designer, now.plusSeconds(1)))
            .isEqualTo(1);
        assertThat(repository.findImplementation(tenant, model.id()).orElseThrow())
            .extracting(item -> item.ownership(), item -> item.implementationRevision(), item -> item.inputMode())
            .containsExactly(ImplementationMode.DESIGNER_GENERATED, 2, InputMode.PHYSICAL_ASSET);
        assertThat(revisionCount(tenant, model.id())).isEqualTo(2);
    }

    private int revisionCount(String tenant, UUID modelSpecId) {
        Integer count = jdbcTemplate.queryForObject(
            """
            select count(*) from modeling_model_implementation_revision r
              join modeling_model_implementation i on i.id = r.implementation_id and i.tenant_id = r.tenant_id
             where i.tenant_id = ? and i.model_spec_id = ?
            """,
            Integer.class,
            tenant,
            modelSpecId
        );
        return count == null ? 0 : count;
    }

    private void seedContext(String tenant, String actor, UUID planId, UUID domainId, UUID sourceBindingId) {
        jdbcTemplate.update(
            "insert into catalog_domain (id, name, code, lifecycle_status, access_policy) values (?, ?, ?, 'ACTIVE', 'PUBLIC')",
            domainId,
            "Customers",
            "CUSTOMERS_" + domainId.toString().replace("-", "")
        );
        jdbcTemplate.update(
            """
            insert into modeling_warehouse_plan (
                id, tenant_id, owner, code, name, owner_id, onboarding_mode, lifecycle_status,
                status, version, created_date, last_modified_date
            ) values (?, ?, ?, ?, ?, ?, 'BUSINESS_FIRST', 'DRAFT', 'DRAFT', 1, current_timestamp, current_timestamp)
            """,
            planId, tenant, actor, "wp_" + planId.toString().replace("-", ""), "Repository IT plan", actor
        );
        jdbcTemplate.update(
            """
            insert into modeling_warehouse_plan_domain (
                id, tenant_id, plan_id, domain_id, confirmation_status, created_date, last_modified_date
            ) values (?, ?, ?, ?, 'CONFIRMED', current_timestamp, current_timestamp)
            """,
            UUID.randomUUID(), tenant, planId, domainId
        );
        jdbcTemplate.update(
            """
            insert into modeling_warehouse_plan_source (
                id, tenant_id, plan_id, source_type, source_id, source_version, confirmation_status,
                created_date, last_modified_date
            ) values (?, ?, ?, 'CATALOG_TABLE', 'ods.customer', 'v1', 'CONFIRMED', current_timestamp, current_timestamp)
            """,
            sourceBindingId, tenant, planId
        );
    }

    private static CreateModelSpecCommand command(UUID planId, UUID domainId, UUID sourceBindingId) {
        return new CreateModelSpecCommand(
            planId, domainId, ModelType.FACT, Layer.DWD, "customer_detail", null,
            ImplementationMode.DESIGNER_GENERATED, "table", null, null,
            new Grain("one row per customer event", List.of("customer_id")), null, null,
            List.of(new ModelField("customer_id", "varchar", false, null, FieldRole.KEY, null)),
            List.of(new SourceRef(SourceKind.TABLE, "ods.customer", Layer.ODS, SourceRole.PRIMARY, null, null, null, 0, sourceBindingId, "v1")),
            List.of(), List.of(), List.of(), List.of(), null, "implementation-repository-it"
        );
    }
}
