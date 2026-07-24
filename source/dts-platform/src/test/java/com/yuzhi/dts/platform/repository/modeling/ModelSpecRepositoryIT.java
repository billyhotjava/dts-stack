package com.yuzhi.dts.platform.repository.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.IntegrationTest;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.*;
import com.yuzhi.dts.platform.service.modeling.ModelSpecSnapshotCodec;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class ModelSpecRepositoryIT {

    @Autowired
    private ModelSpecRepository repository;

    @Autowired
    private DimensionDefinitionRepository dimensionDefinitionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void convergesIdempotentInsertAndUsesRevisionChecksumCas() throws Exception {
        String tenant = "model-spec-it-" + UUID.randomUUID();
        String actor = "owner-1";
        UUID planId = UUID.randomUUID();
        UUID domainId = UUID.randomUUID();
        UUID sourceBindingId = UUID.randomUUID();
        seedContext(tenant, actor, planId, domainId, sourceBindingId);
        ModelSpecSnapshotCodec codec = new ModelSpecSnapshotCodec(objectMapper);
        CreateModelSpecCommand create = command(planId, domainId, sourceBindingId);
        Instant now = Instant.parse("2026-07-19T00:00:00Z");
        ModelSpecView first = codec.toCreatedView(UUID.randomUUID(), create, now);
        String firstSnapshot = codec.write(first);

        assertThat(repository.insertV2(tenant, actor, create, first, codec.requestHash(create), firstSnapshot)).isEqualTo(1);
        repository.insertV2Revision(tenant, actor, first, firstSnapshot);
        assertThat(repository.insertV2(tenant, actor, create, first, codec.requestHash(create), firstSnapshot)).isZero();
        assertThat(repository.findCurrent(tenant, first.id())).get().extracting(ModelSpecRepository.StoredModelSpec::revision).isEqualTo(1);

        UpdateModelSpecCommand update = update(create, "customer_detail_v2");
        ModelSpecView second = codec.toUpdatedView(first, update, 2, now.plusSeconds(60));
        String secondSnapshot = codec.write(second);
        assertThat(repository.compareAndSetV2(tenant, actor, 1, first.checksum(), second, secondSnapshot)).isEqualTo(1);
        repository.insertV2Revision(tenant, actor, second, secondSnapshot);
        assertThat(repository.compareAndSetV2(tenant, actor, 1, first.checksum(), second, secondSnapshot)).isZero();
        assertThat(repository.findCurrent(tenant, first.id())).get().extracting(ModelSpecRepository.StoredModelSpec::revision).isEqualTo(2);

        ModelSpecView published = codec.toLifecycleView(second, ModelStatus.PUBLISHED, second.revision(), now.plusSeconds(120));
        String publishedSnapshot = codec.write(published);
        assertThat(repository.compareAndSetLifecycle(tenant, actor, 2, second.checksum(), ModelStatus.DRAFT, published)).isEqualTo(1);
        assertThat(repository.updateV2RevisionLifecycle(tenant, actor, ModelStatus.DRAFT, published, publishedSnapshot)).isEqualTo(1);
        assertThat(repository.compareAndSetPublishedMetricRefs(tenant, actor, 2, published.checksum(), published, publishedSnapshot))
            .isEqualTo(1);
        assertThat(repository.findCurrent(tenant, first.id())).get().extracting(ModelSpecRepository.StoredModelSpec::status)
            .isEqualTo(ModelStatus.PUBLISHED);

        String persistedFirstSnapshot = repository.findRevision(tenant, first.id(), 1).orElseThrow().currentSnapshot();
        assertThat(objectMapper.readTree(persistedFirstSnapshot)).isEqualTo(objectMapper.readTree(firstSnapshot));
        assertThat(repository.findCurrent("another-tenant", first.id())).isEmpty();
        assertThat(repository.findSourceBinding(tenant, planId, sourceBindingId))
            .get()
            .extracting(ModelSpecRepository.SourceBindingState::sourceVersion)
            .isEqualTo("v1");
        assertThat(repository.findSourceBinding("another-tenant", planId, sourceBindingId)).isEmpty();
    }

    @Test
    void persistsPinnedDimensionDefinitionAcrossHeadAndRevisionsWhileHistoricalNullRowsRemainReadable() {
        String tenant = "model-spec-pin-it-" + UUID.randomUUID();
        String actor = "owner-1";
        UUID planId = UUID.randomUUID();
        UUID domainId = UUID.randomUUID();
        UUID sourceBindingId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();
        seedContext(tenant, actor, planId, domainId, sourceBindingId);
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        DimensionDefinitionContract.CreateCommand definitionCommand = new DimensionDefinitionContract.CreateCommand(
            domainId,
            "Customer",
            "Reusable customer dimension",
            actor,
            DimensionDefinitionContract.ReuseScope.DOMAIN,
            List.of(),
            "model-spec-pin-definition"
        );
        DimensionDefinitionContract.View definition = new DimensionDefinitionContract.View(
            definitionId,
            "dim_" + definitionId.toString().replace("-", ""),
            domainId,
            definitionCommand.name(),
            definitionCommand.definition(),
            definitionCommand.ownerId(),
            definitionCommand.reuseScope(),
            definitionCommand.hierarchies(),
            DimensionDefinitionContract.Status.DRAFT,
            1,
            "a".repeat(64),
            0,
            now,
            now
        );
        assertThat(dimensionDefinitionRepository.insert(tenant, actor, definitionCommand, definition, "b".repeat(64)))
            .isEqualTo(1);

        ModelSpecSnapshotCodec codec = new ModelSpecSnapshotCodec(objectMapper);
        CreateModelSpecCommand create = dimensionCommand(
            planId,
            domainId,
            new DimensionDefinitionRef(definitionId, 1),
            "repository-it-pinned",
            "customer_dimension"
        );
        ModelSpecView first = codec.toCreatedView(UUID.randomUUID(), create, now);
        String firstSnapshot = codec.write(first);
        assertThat(repository.insertV2(tenant, actor, create, first, codec.requestHash(create), firstSnapshot)).isEqualTo(1);
        repository.insertV2Revision(tenant, actor, first, firstSnapshot);

        UpdateModelSpecCommand update = update(create, "customer_dimension_v2");
        ModelSpecView second = codec.toUpdatedView(first, update, 2, now.plusSeconds(60));
        String secondSnapshot = codec.write(second);
        assertThat(repository.compareAndSetV2(tenant, actor, 1, first.checksum(), second, secondSnapshot)).isEqualTo(1);
        repository.insertV2Revision(tenant, actor, second, secondSnapshot);

        assertThat(
            jdbcTemplate.queryForObject(
                "select dimension_definition_id from modeling_model_spec where tenant_id = ? and id = ?",
                UUID.class,
                tenant,
                first.id()
            )
        ).isEqualTo(definitionId);
        assertThat(
            jdbcTemplate.queryForObject(
                "select dimension_definition_revision from modeling_model_spec where tenant_id = ? and id = ?",
                Integer.class,
                tenant,
                first.id()
            )
        ).isEqualTo(1);
        for (int revision : List.of(1, 2)) {
            assertThat(
                jdbcTemplate.queryForObject(
                    """
                    select dimension_definition_id
                      from modeling_model_spec_revision
                     where tenant_id = ? and model_spec_id = ? and revision = ?
                    """,
                    UUID.class,
                    tenant,
                    first.id(),
                    revision
                )
            ).isEqualTo(definitionId);
            assertThat(
                jdbcTemplate.queryForObject(
                    """
                    select dimension_definition_revision
                      from modeling_model_spec_revision
                     where tenant_id = ? and model_spec_id = ? and revision = ?
                    """,
                    Integer.class,
                    tenant,
                    first.id(),
                    revision
                )
            ).isEqualTo(1);
        }

        CreateModelSpecCommand historicalCreate = dimensionCommand(
            planId,
            domainId,
            null,
            "repository-it-historical-null",
            "historical_customer_dimension"
        );
        ModelSpecView historical = codec.toCreatedView(UUID.randomUUID(), historicalCreate, now);
        String historicalSnapshot = codec.write(historical);
        assertThat(
            repository.insertV2(
                tenant,
                actor,
                historicalCreate,
                historical,
                codec.requestHash(historicalCreate),
                historicalSnapshot
            )
        ).isEqualTo(1);
        repository.insertV2Revision(tenant, actor, historical, historicalSnapshot);
        assertThat(
            jdbcTemplate.queryForObject(
                """
                select dimension_definition_id is null and dimension_definition_revision is null
                  from modeling_model_spec
                 where tenant_id = ? and id = ?
                """,
                Boolean.class,
                tenant,
                historical.id()
            )
        ).isTrue();
        assertThat(
            jdbcTemplate.queryForObject(
                """
                select dimension_definition_id is null and dimension_definition_revision is null
                  from modeling_model_spec_revision
                 where tenant_id = ? and model_spec_id = ? and revision = 1
                """,
                Boolean.class,
                tenant,
                historical.id()
            )
        ).isTrue();
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
            planId,
            tenant,
            actor,
            "wp_" + planId.toString().replace("-", ""),
            "Repository IT plan",
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
            ) values (?, ?, ?, 'CATALOG_TABLE', 'ods.customer', 'v1', 'CONFIRMED', current_timestamp, current_timestamp)
            """,
            sourceBindingId,
            tenant,
            planId
        );
    }

    private static CreateModelSpecCommand command(UUID planId, UUID domainId, UUID sourceBindingId) {
        return new CreateModelSpecCommand(
            planId, domainId, ModelType.FACT, Layer.DWD, "customer_detail", null,
            ImplementationMode.DESIGNER_GENERATED, "table", null, null,
            new Grain("one row per customer event", List.of("customer_id")), null, null,
            List.of(new ModelField("customer_id", "varchar", false, null, FieldRole.KEY, null)),
            List.of(
                new SourceRef(
                    SourceKind.TABLE,
                    "ods.customer",
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
            List.of(), List.of(), List.of(), List.of(), null, "repository-it"
        );
    }

    private static UpdateModelSpecCommand update(CreateModelSpecCommand command, String name) {
        return new UpdateModelSpecCommand(
            command.planId(), command.domainId(), command.modelType(), command.layer(), name, command.description(),
            command.implementationMode(), command.materialization(), command.businessActivityRef(), command.consumptionScenario(),
            command.grain(), command.factShape(), command.timeSemantics(), command.fields(), command.sourceRefs(),
            command.dependsOn(), command.dimensionRefs(), command.metricRefs(), command.standardBindings(), command.generationStrategy()
        );
    }

    private static CreateModelSpecCommand dimensionCommand(
        UUID planId,
        UUID domainId,
        DimensionDefinitionRef definitionRef,
        String idempotencyKey,
        String name
    ) {
        return new CreateModelSpecCommand(
            planId,
            domainId,
            ModelType.DIMENSION,
            Layer.DWD,
            name,
            null,
            ImplementationMode.DESIGNER_GENERATED,
            "table",
            null,
            null,
            new Grain("one row per customer", List.of("customer_id")),
            null,
            null,
            List.of(new ModelField("customer_id", "varchar", false, null, FieldRole.KEY, null)),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            null,
            definitionRef,
            idempotencyKey
        );
    }
}
