package com.yuzhi.dts.platform.repository.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.IntegrationTest;
import com.yuzhi.dts.platform.service.catalog.SchemaDriftConsumerReferenceReadPort;
import com.yuzhi.dts.platform.service.catalog.SchemaDriftDetector;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.*;
import com.yuzhi.dts.platform.service.modeling.ModelSpecSnapshotCodec;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

    @Autowired
    private SchemaDriftConsumerReferenceReadPort driftConsumerReferences;

    @Autowired
    private SchemaDriftDetector schemaDriftDetector;

    @ParameterizedTest
    @CsvSource({"ODS,ODS", "DIM,DWD", "dim,DWD", "DWD,DWD", "DWS,DWS", "ADS,ADS", "STG,STG", ",ODS", "UNKNOWN,"})
    void resolvesCatalogTableLocatorThroughParentDataset(String catalogLayer, Layer expectedLayer) {
        String tenant = "model-spec-catalog-source-it-" + UUID.randomUUID();
        String actor = "owner-1";
        UUID planId = UUID.randomUUID();
        UUID domainId = UUID.randomUUID();
        UUID sourceBindingId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID datasetId = UUID.randomUUID();
        UUID tableId = UUID.randomUUID();
        assertThat(tableId).isNotEqualTo(datasetId);
        seedContext(tenant, actor, planId, domainId, sourceBindingId);

        jdbcTemplate.update(
            """
            insert into catalog_dataset (
                id, name, type, source_id, hive_database, hive_table, warehouse_layer,
                enabled, lifecycle_status, harvest_status, created_date, last_modified_date
            ) values (?, 'orders', 'POSTGRES', ?, 'public', 'orders', ?,
                      true, 'DISCOVERED', 'SYNCED', current_timestamp, current_timestamp)
            """,
            datasetId,
            sourceId,
            catalogLayer
        );
        jdbcTemplate.update(
            """
            insert into catalog_table_schema (id, dataset_id, name, created_date, last_modified_date)
            values (?, ?, 'orders', current_timestamp, current_timestamp)
            """,
            tableId,
            datasetId
        );
        jdbcTemplate.update(
            """
            update modeling_warehouse_plan_source
               set source_id = ?, locator_json = cast(? as jsonb)
             where tenant_id = ? and plan_id = ? and id = ?
            """,
            tableId.toString(),
            "{\"assetId\":\"" + tableId + "\"}",
            tenant,
            planId,
            sourceBindingId
        );

        if (expectedLayer == null) {
            assertThat(repository.findCurrentPhysicalSource(tenant, planId, sourceBindingId, "v1")).isEmpty();
            return;
        }
        assertThat(repository.findCurrentPhysicalSource(tenant, planId, sourceBindingId, "v1"))
            .hasValueSatisfying(source -> {
                assertThat(source.kind()).isEqualTo(SourceKind.TABLE);
                assertThat(source.ref()).isEqualTo("public.orders");
                assertThat(source.layer()).isEqualTo(expectedLayer);
                assertThat(source.resolvedVersion()).isEqualTo("v1");
            });
        assertThat(repository.findCurrentPhysicalSource(tenant, planId, sourceBindingId, "stale-version")).isEmpty();
        jdbcTemplate.update("update modeling_warehouse_plan_source set confirmation_status = 'PENDING' where id = ?", sourceBindingId);
        assertThat(repository.findCurrentPhysicalSource(tenant, planId, sourceBindingId, "v1")).isEmpty();
    }

    @Test
    void classifiesReferencedAndUnreferencedCatalogColumnsFromCurrentModelSpecs() throws Exception {
        String tenant = "schema-drift-consumer-it-" + UUID.randomUUID();
        String actor = "owner-1";
        UUID planId = UUID.randomUUID();
        UUID domainId = UUID.randomUUID();
        UUID sourceBindingId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID datasetId = UUID.randomUUID();
        UUID tableId = UUID.randomUUID();
        seedContext(tenant, actor, planId, domainId, sourceBindingId);
        jdbcTemplate.update(
            """
            insert into catalog_dataset (
                id, name, type, source_id, hive_database, hive_table, warehouse_layer,
                enabled, lifecycle_status, harvest_status, created_date, last_modified_date
            ) values (?, 'orders', 'POSTGRES', ?, 'public', 'orders', 'ODS',
                      true, 'DISCOVERED', 'SYNCED', current_timestamp, current_timestamp)
            """,
            datasetId,
            sourceId
        );
        jdbcTemplate.update(
            """
            insert into catalog_table_schema (id, dataset_id, name, created_date, last_modified_date)
            values (?, ?, 'orders', current_timestamp, current_timestamp)
            """,
            tableId,
            datasetId
        );
        jdbcTemplate.update(
            """
            update modeling_warehouse_plan_source
               set source_id = ?, locator_json = cast(? as jsonb)
             where tenant_id = ? and plan_id = ? and id = ?
            """,
            tableId.toString(),
            "{\"assetId\":\"" + tableId + "\"}",
            tenant,
            planId,
            sourceBindingId
        );

        ModelSpecSnapshotCodec codec = new ModelSpecSnapshotCodec(objectMapper);
        CreateModelSpecCommand create = command(planId, domainId, sourceBindingId);
        Instant now = Instant.parse("2026-08-10T00:00:00Z");
        ModelSpecView model = codec.toCreatedView(UUID.randomUUID(), create, now);
        assertThat(repository.insertV2(tenant, actor, create, model, codec.requestHash(create), codec.write(model))).isEqualTo(1);
        jdbcTemplate.update(
            """
            update modeling_model_spec
               set fields = cast(? as jsonb), source_refs = cast(? as jsonb)
             where tenant_id = ? and id = ?
            """,
            "[{\"name\":\"customer_id\",\"sourceFieldRef\":\"orders.customer_id\"}]",
            "[{\"sourceBindingId\":\"" + sourceBindingId + "\",\"alias\":\"orders\"}]",
            tenant,
            model.id()
        );

        Set<String> referencedFields = driftConsumerReferences
            .findCurrentReferencedFields(tableId, sourceId, "public", "orders")
            .orElseThrow();
        SchemaDriftDetector.DriftSummary drift = schemaDriftDetector.diff(
            Map.of(
                "customer_id",
                new SchemaDriftDetector.ColumnSnapshot("customer_id", "varchar(32)", false),
                "legacy_code",
                new SchemaDriftDetector.ColumnSnapshot("legacy_code", "varchar(32)", true)
            ),
            List.of(),
            referencedFields
        );
        Map<String, String> impactByField = new LinkedHashMap<>();
        objectMapper
            .readTree(drift.detailsJson())
            .path("changes")
            .forEach(change -> impactByField.put(change.path("field").asText(), change.path("impact").asText()));

        assertThat(referencedFields).containsExactly("customer_id");
        assertThat(impactByField)
            .containsEntry("customer_id", "BREAKING")
            .containsEntry("legacy_code", "COMPATIBLE");
    }

    @Test
    void persistsWarehouseLayerSelectionOnInsertAndCas() throws Exception {
        String tenant = "model-spec-layer-it-" + UUID.randomUUID();
        String actor = "owner-1";
        UUID planId = UUID.randomUUID();
        UUID domainId = UUID.randomUUID();
        UUID sourceBindingId = UUID.randomUUID();
        seedContext(tenant, actor, planId, domainId, sourceBindingId);
        ModelSpecSnapshotCodec codec = new ModelSpecSnapshotCodec(objectMapper);
        CreateModelSpecCommand create = command(planId, domainId, sourceBindingId);
        create = create.withLayerSelection(Layer.DWD, "FIN_DETAIL");
        Instant now = Instant.parse("2026-08-04T00:00:00Z");
        ModelSpecView first = codec.toCreatedView(UUID.randomUUID(), create, now);
        String firstSnapshot = codec.write(first);
        assertThat(repository.insertV2(tenant, actor, create, first, codec.requestHash(create), firstSnapshot)).isEqualTo(1);

        String stored = jdbcTemplate.queryForObject(
            "select warehouse_layer_code from modeling_model_spec where tenant_id = ? and id = ?",
            String.class,
            tenant,
            first.id()
        );
        assertThat(stored).isEqualTo("FIN_DETAIL");

        UpdateModelSpecCommand update = update(create, "customer_detail_v2");
        update = update.withLayerSelection(Layer.DWD, "FIN_DETAIL");
        ModelSpecView second = codec.toUpdatedView(first, update, 2, now.plusSeconds(60));
        String secondSnapshot = codec.write(second);
        assertThat(repository.compareAndSetV2(tenant, actor, 1, first.checksum(), second, secondSnapshot)).isEqualTo(1);

        String storedAfterCas = jdbcTemplate.queryForObject(
            "select warehouse_layer_code from modeling_model_spec where tenant_id = ? and id = ?",
            String.class,
            tenant,
            first.id()
        );
        assertThat(storedAfterCas).isEqualTo("FIN_DETAIL");
        assertThat(second.warehouseLayerCode()).isEqualTo("FIN_DETAIL");
    }
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
    void permanentlyDeletesOnlyTheMatchingDraftAndItsUncommittedAuthoringData() {
        String tenant = "model-spec-delete-it-" + UUID.randomUUID();
        String actor = "owner-1";
        UUID planId = UUID.randomUUID();
        UUID domainId = UUID.randomUUID();
        UUID sourceBindingId = UUID.randomUUID();
        seedContext(tenant, actor, planId, domainId, sourceBindingId);
        ModelSpecSnapshotCodec codec = new ModelSpecSnapshotCodec(objectMapper);
        CreateModelSpecCommand create = command(planId, domainId, sourceBindingId);
        ModelSpecView draft = codec.toCreatedView(UUID.randomUUID(), create, Instant.parse("2026-09-03T00:00:00Z"));
        String snapshot = codec.write(draft);
        assertThat(repository.insertV2(tenant, actor, create, draft, codec.requestHash(create), snapshot)).isEqualTo(1);
        repository.insertV2Revision(tenant, actor, draft, snapshot);

        UUID authoringDraftId = UUID.randomUUID();
        jdbcTemplate.update(
            """
            insert into modeling_dbt_implementation_draft (
                id, tenant_id, plan_id, model_spec_id, actor_id,
                base_model_revision, base_model_checksum, idempotency_key, request_hash,
                source_bundle_snapshot, status, etag, expires_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), 'DRAFT', ?, current_timestamp + interval '1 day')
            """,
            authoringDraftId,
            tenant,
            planId,
            draft.id(),
            actor,
            draft.revision(),
            draft.checksum(),
            "delete-draft",
            "b".repeat(64),
            "{}",
            "c".repeat(64)
        );
        jdbcTemplate.update(
            """
            insert into modeling_dbt_implementation_draft_file (
                draft_id, path, content_checksum, content, byte_size
            ) values (?, 'models/customer_detail.sql', ?, '', 0)
            """,
            authoringDraftId,
            "d".repeat(64)
        );

        assertThat(repository.deleteDraft(tenant, draft.id(), draft.revision(), draft.checksum())).isTrue();
        assertThat(repository.findCurrent(tenant, draft.id())).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from modeling_model_spec_revision where tenant_id = ? and model_spec_id = ?",
                Integer.class,
                tenant,
                draft.id()
            ))
            .isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from modeling_dbt_implementation_draft where tenant_id = ? and model_spec_id = ?",
                Integer.class,
                tenant,
                draft.id()
            ))
            .isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from modeling_dbt_implementation_draft_file where draft_id = ?",
                Integer.class,
                authoringDraftId
            ))
            .isZero();
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
            definitionCommand.abbreviation(),
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

    @Test
    void appendsReclassificationRevisionAndPersistsDeterministicReplayEvidence() throws Exception {
        String tenant = "model-spec-reclassify-it-" + UUID.randomUUID();
        String actor = "owner-1";
        UUID planId = UUID.randomUUID();
        UUID domainId = UUID.randomUUID();
        UUID sourceBindingId = UUID.randomUUID();
        seedContext(tenant, actor, planId, domainId, sourceBindingId);
        ModelSpecSnapshotCodec codec = new ModelSpecSnapshotCodec(objectMapper);
        CreateModelSpecCommand create = command(planId, domainId, sourceBindingId);
        Instant createdAt = Instant.parse("2026-07-27T01:02:03Z");
        ModelSpecView first = codec.toCreatedView(UUID.randomUUID(), create, createdAt);
        String firstSnapshot = codec.write(first);
        assertThat(repository.insertV2(tenant, actor, create, first, codec.requestHash(create), firstSnapshot)).isEqualTo(1);
        repository.insertV2Revision(tenant, actor, first, firstSnapshot);

        CreateModelSpecCommand projected = dimensionCommand(
            planId,
            domainId,
            null,
            create.idempotencyKey(),
            first.name()
        );
        ModelSpecView second = codec.toReclassifiedView(first, projected, 2, createdAt.plusSeconds(60));
        String secondSnapshot = codec.write(second);
        assertThat(repository.compareAndSetV2(tenant, actor, 1, first.checksum(), second, secondSnapshot)).isEqualTo(1);
        repository.insertV2Revision(tenant, actor, second, secondSnapshot);
        repository.insertReclassificationCommand(
            tenant,
            actor,
            first.id(),
            "fact-to-dimension",
            ModelType.FACT,
            ModelType.DIMENSION,
            second.revision(),
            second.checksum(),
            createdAt.plusSeconds(60)
        );

        assertThat(repository.findRevision(tenant, first.id(), 1)).isPresent();
        assertThat(repository.findRevision(tenant, first.id(), 2)).isPresent();
        assertThat(repository.findReclassificationReplay(tenant, first.id(), "fact-to-dimension"))
            .get()
            .isEqualTo(new ModelSpecRepository.ReclassificationReplay(2, second.checksum()));
        assertThat(
            jdbcTemplate.queryForObject(
                """
                select created_date
                  from modeling_model_reclassification_command
                 where tenant_id = ? and model_spec_id = ? and idempotency_key = ?
                """,
                Instant.class,
                tenant,
                first.id(),
                "fact-to-dimension"
            )
        ).isEqualTo(createdAt.plusSeconds(60));
    }

    @Test
    void relationshipGraphReadIsTenantPlanPermissionAndLimitScopedInPostgresql() {
        String tenantA = "graph-it-a-" + UUID.randomUUID();
        String tenantB = "graph-it-b-" + UUID.randomUUID();
        UUID planA = UUID.randomUUID();
        UUID planB = UUID.randomUUID();
        UUID domainA = UUID.randomUUID();
        UUID domainB = UUID.randomUUID();
        UUID sourceA = UUID.randomUUID();
        UUID sourceB = UUID.randomUUID();
        seedContext(tenantA, "owner-a", planA, domainA, sourceA);
        seedContext(tenantB, "owner-b", planB, domainB, sourceB);
        ModelSpecSnapshotCodec codec = new ModelSpecSnapshotCodec(objectMapper);
        Instant now = Instant.parse("2026-07-30T00:00:00Z");
        CreateModelSpecCommand commandA = command(planA, domainA, sourceA);
        CreateModelSpecCommand commandB = command(planB, domainB, sourceB);
        ModelSpecView modelA = codec.toCreatedView(UUID.randomUUID(), commandA, now);
        ModelSpecView modelB = codec.toCreatedView(UUID.randomUUID(), commandB, now);
        assertThat(repository.insertV2(tenantA, "owner-a", commandA, modelA, codec.requestHash(commandA), codec.write(modelA)))
            .isEqualTo(1);
        repository.insertV2Revision(tenantA, "owner-a", modelA, codec.write(modelA));
        assertThat(repository.insertV2(tenantB, "owner-b", commandB, modelB, codec.requestHash(commandB), codec.write(modelB)))
            .isEqualTo(1);
        repository.insertV2Revision(tenantB, "owner-b", modelB, codec.write(modelB));

        assertThat(repository.listCurrentForRelationshipGraph(tenantA, planA, java.util.Set.of(domainA), 1))
            .extracting(ModelSpecRepository.StoredModelSpec::id)
            .containsExactly(modelA.id());
        assertThat(
            repository.listCurrentForRelationshipGraph(
                tenantA,
                planA,
                java.util.Set.of(domainA),
                modelA.id(),
                1
            )
        )
            .isEmpty();
        assertThat(repository.listCurrentForRelationshipGraph(tenantA, planB, java.util.Set.of(domainA), 1))
            .isEmpty();
        assertThat(repository.listCurrentForRelationshipGraph(tenantB, planB, java.util.Set.of(domainA), 1))
            .isEmpty();
        assertThat(
            repository.listRevisionsForRelationshipGraph(
                tenantA,
                List.of(new ModelRevisionRef(modelA.id(), 1)),
                java.util.Set.of(domainA),
                1
            )
        )
            .extracting(
                ModelSpecRepository.StoredModelSpec::id,
                ModelSpecRepository.StoredModelSpec::revision
            )
            .containsExactly(org.assertj.core.groups.Tuple.tuple(modelA.id(), 1));
        assertThat(
            repository.listRevisionsForRelationshipGraph(
                tenantA,
                List.of(new ModelRevisionRef(modelA.id(), 1)),
                java.util.Set.of(domainB),
                1
            )
        )
            .isEmpty();
        assertThat(
            repository.listRevisionsForRelationshipGraph(
                tenantA,
                List.of(new ModelRevisionRef(modelA.id(), 2)),
                java.util.Set.of(domainA),
                1
            )
        )
            .isEmpty();
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
