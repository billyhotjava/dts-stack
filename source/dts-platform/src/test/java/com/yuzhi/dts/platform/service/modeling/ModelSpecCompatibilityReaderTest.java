package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.repository.modeling.DimensionDefinitionRepository;
import com.yuzhi.dts.platform.repository.modeling.DimensionDefinitionRepository.LegacyDefinitionMapping;
import com.yuzhi.dts.platform.repository.modeling.DimensionDefinitionRepository.LegacyDefinitionRef;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository.StoredModelSpec;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.CompatibilityMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ModelSpecCompatibilityReaderTest {

    @Mock
    private ModelSpecRepository repository;

    @Mock
    private DimensionDefinitionRepository dimensionDefinitions;

    @Test
    void reportsMissingModelSpecWithoutFailingWhenTheIdentifierIsNull() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        ModelSpecCompatibilityReader reader = new ModelSpecCompatibilityReader(
            repository,
            new ModelSpecSnapshotCodec(mapper),
            mapper
        );
        when(repository.findCurrent("server-tenant", null)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reader.get("server-tenant", null))
            .isInstanceOf(ModelSpecException.class)
            .satisfies(error -> {
                ModelSpecException notFound = (ModelSpecException) error;
                assertThat(notFound.code()).isEqualTo("MODEL_SPEC_NOT_FOUND");
                assertThat(notFound.details()).isEqualTo(java.util.Map.of());
            });
    }

    @Test
    void prefersV2SnapshotAndMapsV1SpecAsLegacyReadonly() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        ModelSpecSnapshotCodec codec = new ModelSpecSnapshotCodec(mapper);
        ModelSpecCompatibilityReader reader = new ModelSpecCompatibilityReader(repository, codec, mapper);
        UUID id = UUID.fromString("30000000-0000-0000-0000-000000000001");
        ModelingVNextContract.ModelSpec legacy = new ModelingVNextContract.ModelSpec(
            id.toString(),
            UUID.randomUUID().toString(),
            "customer_activity",
            ModelingVNextContract.Layer.DWD,
            ModelingVNextContract.ModelType.FACT,
            ModelingVNextContract.ImplementationMode.DESIGNER_GENERATED,
            "legacy_customer_detail",
            new ModelingVNextContract.Grain("one row per customer", List.of("customer_id")),
            List.of(new ModelingVNextContract.StandardBinding("customer_id", "std.customer.id", null, null)),
            List.of(
                new ModelingVNextContract.SourceRef("TABLE", "ods.customer", ModelingVNextContract.Layer.ODS),
                new ModelingVNextContract.SourceRef("LEGACY_FILE", "legacy/customer.csv", ModelingVNextContract.Layer.ODS)
            ),
            List.of("customer_id"),
            List.of("amount"),
            "table",
            3,
            List.of("legacy-upstream-id"),
            "legacy-model-ref"
        );
        StoredModelSpec row = new StoredModelSpec(
            1,
            "server-tenant",
            id,
            null,
            null,
            ModelStatus.DRAFT,
            3,
            "a".repeat(64),
            null,
            mapper.writeValueAsString(legacy),
            null,
            null,
            null,
            Instant.EPOCH,
            Instant.EPOCH
        );
        when(repository.findCurrent("server-tenant", id)).thenReturn(Optional.of(row));

        ModelSpecContract.ModelSpecView view = reader.get("server-tenant", id);

        assertThat(view.compatibilityMode()).isEqualTo(CompatibilityMode.LEGACY_READONLY);
        assertThat(view.id()).isEqualTo(id);
        assertThat(view.fields()).extracting(ModelSpecContract.ModelField::name).containsExactly("customer_id", "amount");
        assertThat(view.businessActivityRef()).isEqualTo("customer_activity");
        assertThat(view.legacyRefs().legacyModelRef()).isEqualTo("legacy-model-ref");
        assertThat(view.legacyRefs().unresolvedDependencyRefs()).containsExactly("legacy-upstream-id");
        assertThat(view.legacyRefs().unresolvedSourceRefs()).extracting(ModelSpecContract.LegacySourceRef::kind).containsExactly("LEGACY_FILE");
        assertThat(view.legacyRefs().unresolvedStandardRefs())
            .extracting(ModelSpecContract.LegacyStandardRef::standardElementId)
            .containsExactly("std.customer.id");
    }

    @Test
    void resolvesLegacyDimensionDefinitionsInOneBoundedBatchForRelationshipGraph() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        ModelSpecCompatibilityReader reader = new ModelSpecCompatibilityReader(
            repository,
            new ModelSpecSnapshotCodec(mapper),
            mapper,
            dimensionDefinitions
        );
        UUID planId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID domainId = UUID.fromString("20000000-0000-0000-0000-000000000001");
        UUID firstId = UUID.fromString("30000000-0000-0000-0000-000000000001");
        UUID secondId = UUID.fromString("30000000-0000-0000-0000-000000000002");
        UUID firstDefinitionId = UUID.fromString("40000000-0000-0000-0000-000000000001");
        UUID secondDefinitionId = UUID.fromString("40000000-0000-0000-0000-000000000002");
        StoredModelSpec first = legacyDimensionRow(mapper, firstId, planId, domainId, "first_dimension");
        StoredModelSpec second = legacyDimensionRow(mapper, secondId, planId, domainId, "second_dimension");
        when(
            dimensionDefinitions.findLegacyDefinitionRefsForRelationshipGraph(
                "server-tenant",
                List.of(firstId, secondId),
                501
            )
        )
            .thenReturn(
                List.of(
                    new LegacyDefinitionMapping(firstId, firstDefinitionId, 2),
                    new LegacyDefinitionMapping(secondId, secondDefinitionId, 3)
                )
            );

        List<ModelSpecContract.ModelSpecView> projected = reader.readForRelationshipGraph(List.of(first, second));

        assertThat(projected)
            .extracting(ModelSpecContract.ModelSpecView::dimensionDefinitionRef)
            .containsExactly(
                new ModelSpecContract.DimensionDefinitionRef(firstDefinitionId, 2),
                new ModelSpecContract.DimensionDefinitionRef(secondDefinitionId, 3)
            );
        verify(dimensionDefinitions)
            .findLegacyDefinitionRefsForRelationshipGraph("server-tenant", List.of(firstId, secondId), 501);
        verifyNoMoreInteractions(dimensionDefinitions);
    }

    @Test
    void preservesLegacyObjectIdWhenNoExplicitLegacyReferenceExists() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        ModelSpecCompatibilityReader reader = new ModelSpecCompatibilityReader(
            repository,
            new ModelSpecSnapshotCodec(mapper),
            mapper
        );
        UUID id = UUID.fromString("30000000-0000-0000-0000-000000000002");
        String legacyObjectId = UUID.fromString("40000000-0000-0000-0000-000000000002").toString();
        ModelingVNextContract.ModelSpec legacy = new ModelingVNextContract.ModelSpec(
            id.toString(),
            legacyObjectId,
            "customer_activity",
            ModelingVNextContract.Layer.DWD,
            ModelingVNextContract.ModelType.FACT,
            ModelingVNextContract.ImplementationMode.DESIGNER_GENERATED,
            "legacy_customer_detail",
            new ModelingVNextContract.Grain("one row per customer", List.of("customer_id")),
            List.of(),
            List.of(new ModelingVNextContract.SourceRef("TABLE", "ods.customer", ModelingVNextContract.Layer.ODS)),
            List.of("customer_id"),
            List.of(),
            "table",
            1,
            List.of(),
            null
        );
        StoredModelSpec row = new StoredModelSpec(
            1,
            "server-tenant",
            id,
            null,
            null,
            ModelStatus.DRAFT,
            1,
            "a".repeat(64),
            null,
            mapper.writeValueAsString(legacy),
            null,
            null,
            null,
            Instant.EPOCH,
            Instant.EPOCH
        );

        ModelSpecContract.ModelSpecView view = reader.read(row);

        assertThat(view.legacyRefs().legacyModelRef()).isEqualTo(legacyObjectId);
    }

    @Test
    void projectsTheLegacyMapAsAnEffectivePinnedDimensionReference() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        ModelSpecCompatibilityReader reader = new ModelSpecCompatibilityReader(
            repository,
            new ModelSpecSnapshotCodec(mapper),
            mapper,
            dimensionDefinitions
        );
        UUID id = UUID.fromString("30000000-0000-0000-0000-000000000077");
        UUID domainId = UUID.fromString("20000000-0000-0000-0000-000000000077");
        UUID definitionId = UUID.fromString("60000000-0000-0000-0000-000000000077");
        ModelingVNextContract.ModelSpec legacy = new ModelingVNextContract.ModelSpec(
            id.toString(),
            UUID.randomUUID().toString(),
            null,
            ModelingVNextContract.Layer.DWD,
            ModelingVNextContract.ModelType.DIMENSION,
            ModelingVNextContract.ImplementationMode.DESIGNER_GENERATED,
            "legacy_customer_dimension",
            new ModelingVNextContract.Grain("one row per customer", List.of("customer_id")),
            List.of(),
            List.of(),
            List.of("customer_id"),
            List.of(),
            "table",
            1,
            List.of(),
            "legacy-customer-dimension"
        );
        StoredModelSpec row = new StoredModelSpec(
            1,
            "server-tenant",
            id,
            UUID.randomUUID(),
            domainId,
            ModelStatus.DRAFT,
            1,
            "a".repeat(64),
            null,
            mapper.writeValueAsString(legacy),
            null,
            null,
            null,
            Instant.EPOCH,
            Instant.EPOCH
        );
        when(repository.findCurrent("server-tenant", id)).thenReturn(Optional.of(row));
        when(dimensionDefinitions.findLegacyDefinitionRef("server-tenant", id, domainId))
            .thenReturn(Optional.of(new LegacyDefinitionRef(definitionId, 3)));

        ModelSpecContract.ModelSpecView view = reader.get("server-tenant", id);

        assertThat(view.dimensionDefinitionRef())
            .isEqualTo(new ModelSpecContract.DimensionDefinitionRef(definitionId, 3));
        assertThat(view.compatibilityMode()).isEqualTo(CompatibilityMode.LEGACY_READONLY);
        verify(dimensionDefinitions).findLegacyDefinitionRef("server-tenant", id, domainId);
    }

    @Test
    void rejectsCanonicalSnapshotsWhoseDomainDoesNotMatchTheLedgerHead() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        ModelSpecSnapshotCodec codec = new ModelSpecSnapshotCodec(mapper);
        ModelSpecCompatibilityReader reader = new ModelSpecCompatibilityReader(repository, codec, mapper);
        UUID id = UUID.fromString("30000000-0000-0000-0000-000000000001");
        UUID planId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID snapshotDomain = UUID.fromString("20000000-0000-0000-0000-000000000001");
        UUID ledgerDomain = UUID.fromString("20000000-0000-0000-0000-000000000002");
        var command = new ModelSpecContract.CreateModelSpecCommand(
            planId, snapshotDomain, ModelSpecContract.ModelType.FACT, ModelSpecContract.Layer.DWD,
            "customer_detail", null, ModelSpecContract.ImplementationMode.DESIGNER_GENERATED, "table",
            null, null, new ModelSpecContract.Grain("one row per customer", List.of("customer_id")),
            null, null,
            List.of(new ModelSpecContract.ModelField("customer_id", "varchar", false, null, ModelSpecContract.FieldRole.KEY, null)),
            List.of(
                new ModelSpecContract.SourceRef(
                    ModelSpecContract.SourceKind.TABLE,
                    "ods.customer",
                    ModelSpecContract.Layer.ODS,
                    ModelSpecContract.SourceRole.PRIMARY,
                    null,
                    null,
                    null,
                    0,
                    UUID.fromString("50000000-0000-0000-0000-000000000001"),
                    "v1"
                )
            ),
            List.of(), List.of(), List.of(), List.of(), null, "create-1"
        );
        ModelSpecContract.ModelSpecView snapshot = codec.toCreatedView(id, command, Instant.EPOCH);
        StoredModelSpec row = new StoredModelSpec(
            2, "server-tenant", id, planId, ledgerDomain, ModelStatus.DRAFT, 1, snapshot.checksum(),
            codec.write(snapshot), null, "create-1", codec.requestHash(command), codec.write(snapshot),
            Instant.EPOCH, Instant.EPOCH
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> reader.read(row))
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_SPEC_SNAPSHOT_INVALID");
    }

    @Test
    void projectsAPinnedDefinitionFromAHistoricalRevisionWithoutRewritingTheSnapshot() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        ModelSpecSnapshotCodec codec = new ModelSpecSnapshotCodec(mapper);
        ModelSpecCompatibilityReader reader = new ModelSpecCompatibilityReader(repository, codec, mapper);
        UUID id = UUID.fromString("30000000-0000-0000-0000-000000000088");
        UUID planId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID domainId = UUID.fromString("20000000-0000-0000-0000-000000000001");
        UUID definitionId = UUID.fromString("60000000-0000-0000-0000-000000000001");
        var command = new ModelSpecContract.CreateModelSpecCommand(
            planId, domainId, ModelSpecContract.ModelType.DIMENSION, ModelSpecContract.Layer.DWD,
            "customer_dimension", "Customer dimension", ModelSpecContract.ImplementationMode.DESIGNER_GENERATED, "table",
            null, null, new ModelSpecContract.Grain("one row per customer", List.of("customer_id")),
            null, null,
            List.of(new ModelSpecContract.ModelField("customer_id", "varchar", false, null, ModelSpecContract.FieldRole.KEY, null)),
            List.of(), List.of(), List.of(), List.of(), List.of(), null, null,
            new ModelSpecContract.DimensionDefinitionRef(definitionId, 1), "dimension-immutable-snapshot"
        );
        ModelSpecContract.ModelSpecView snapshot = codec.toCreatedView(id, command, Instant.EPOCH);
        String snapshotJson = codec.write(snapshot);
        StoredModelSpec row = new StoredModelSpec(
            false, 2, "server-tenant", id, planId, domainId, ModelStatus.DRAFT, 1,
            null, snapshot.checksum(), snapshotJson, null, command.idempotencyKey(),
            codec.requestHash(command), snapshotJson, Instant.EPOCH, Instant.EPOCH
        );
        when(repository.findRevision("server-tenant", id, 1)).thenReturn(Optional.of(row));

        ModelSpecContract.ModelSpecView result = reader.revision(
            "server-tenant",
            new ModelSpecContract.ModelRevisionRef(id, 1)
        );

        assertThat(result.dimensionDefinitionRef()).isEqualTo(new ModelSpecContract.DimensionDefinitionRef(definitionId, 1));
        assertThat(row.currentSnapshot()).isEqualTo(snapshotJson);
        assertThat(row.revisionChecksum()).isEqualTo(snapshot.checksum());
        verify(repository).findRevision("server-tenant", id, 1);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void overlaysALegacyMapOnCanonicalDimensionSnapshotsThatPredateTheReferenceField() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        ModelSpecSnapshotCodec codec = new ModelSpecSnapshotCodec(mapper);
        ModelSpecCompatibilityReader reader = new ModelSpecCompatibilityReader(
            repository,
            codec,
            mapper,
            dimensionDefinitions
        );
        UUID id = UUID.fromString("30000000-0000-0000-0000-000000000099");
        UUID planId = UUID.fromString("10000000-0000-0000-0000-000000000099");
        UUID domainId = UUID.fromString("20000000-0000-0000-0000-000000000099");
        UUID definitionId = UUID.fromString("60000000-0000-0000-0000-000000000099");
        var legacyCanonicalCommand = new ModelSpecContract.CreateModelSpecCommand(
            planId,
            domainId,
            ModelSpecContract.ModelType.DIMENSION,
            ModelSpecContract.Layer.DWD,
            "customer_dimension",
            "Customer dimension",
            ModelSpecContract.ImplementationMode.DESIGNER_GENERATED,
            "table",
            null,
            null,
            new ModelSpecContract.Grain("one row per customer", List.of("customer_id")),
            null,
            null,
            List.of(
                new ModelSpecContract.ModelField(
                    "customer_id",
                    "varchar",
                    false,
                    null,
                    ModelSpecContract.FieldRole.KEY,
                    null
                )
            ),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            null,
            "legacy-canonical-dimension"
        );
        ModelSpecContract.ModelSpecView snapshot = codec.toCreatedView(
            id,
            legacyCanonicalCommand,
            Instant.EPOCH
        );
        String snapshotJson = codec.write(snapshot);
        StoredModelSpec row = new StoredModelSpec(
            2,
            "server-tenant",
            id,
            planId,
            domainId,
            ModelStatus.DRAFT,
            1,
            snapshot.checksum(),
            snapshotJson,
            null,
            "legacy-canonical-dimension",
            codec.requestHash(legacyCanonicalCommand),
            snapshotJson,
            Instant.EPOCH,
            Instant.EPOCH
        );
        when(repository.findCurrent("server-tenant", id)).thenReturn(Optional.of(row));
        when(dimensionDefinitions.findLegacyDefinitionRef("server-tenant", id, domainId))
            .thenReturn(Optional.of(new LegacyDefinitionRef(definitionId, 2)));

        ModelSpecContract.ModelSpecView projected = reader.get("server-tenant", id);

        assertThat(projected.dimensionDefinitionRef())
            .isEqualTo(new ModelSpecContract.DimensionDefinitionRef(definitionId, 2));
        assertThat(projected.checksum()).isEqualTo(snapshot.checksum());
        assertThat(row.currentSnapshot()).isEqualTo(snapshotJson);
    }

    @Test
    void acceptsHistoricalExtendedSnapshotsThatPredateFieldDisplayNames() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        ModelSpecSnapshotCodec codec = new ModelSpecSnapshotCodec(mapper);
        ModelSpecCompatibilityReader reader = new ModelSpecCompatibilityReader(repository, codec, mapper);
        String snapshotJson = historicalExtendedSnapshot();
        ModelSpecContract.ModelSpecView snapshot = codec.readView(snapshotJson);
        StoredModelSpec row = new StoredModelSpec(
            2,
            "server-tenant",
            snapshot.id(),
            snapshot.planId(),
            snapshot.domainId(),
            snapshot.status(),
            snapshot.revision(),
            snapshot.checksum(),
            snapshotJson,
            null,
            null,
            null,
            snapshotJson,
            snapshot.createdAt(),
            snapshot.updatedAt()
        );

        ModelSpecContract.ModelSpecView result = reader.read(row);

        assertThat(result.id()).isEqualTo(snapshot.id());
        assertThat(result.checksum()).isEqualTo(snapshot.checksum());
        assertThat(result.fields()).singleElement().extracting(ModelSpecContract.ModelField::name)
            .isEqualTo("project_code");
    }

    @Test
    void rejectsTamperedHistoricalExtendedSnapshotsEvenWhenTheyPredateFieldDisplayNames() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        ModelSpecSnapshotCodec codec = new ModelSpecSnapshotCodec(mapper);
        ModelSpecCompatibilityReader reader = new ModelSpecCompatibilityReader(repository, codec, mapper);
        String snapshotJson = historicalExtendedSnapshot().replace("\"name\":\"project_code\"", "\"name\":\"tampered\"");
        ModelSpecContract.ModelSpecView snapshot = codec.readView(snapshotJson);
        StoredModelSpec row = new StoredModelSpec(
            2,
            "server-tenant",
            snapshot.id(),
            snapshot.planId(),
            snapshot.domainId(),
            snapshot.status(),
            snapshot.revision(),
            snapshot.checksum(),
            snapshotJson,
            null,
            null,
            null,
            snapshotJson,
            snapshot.createdAt(),
            snapshot.updatedAt()
        );

        assertThatThrownBy(() -> reader.read(row))
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_SPEC_SNAPSHOT_INVALID");
    }

    @Test
    void rejectsCanonicalSnapshotsWithNonCanonicalModeOrDivergentLedgerChecksums() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        ModelSpecSnapshotCodec codec = new ModelSpecSnapshotCodec(mapper);
        ModelSpecCompatibilityReader reader = new ModelSpecCompatibilityReader(repository, codec, mapper);
        UUID id = UUID.fromString("30000000-0000-0000-0000-000000000001");
        UUID planId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID domainId = UUID.fromString("20000000-0000-0000-0000-000000000001");
        var command = new ModelSpecContract.CreateModelSpecCommand(
            planId, domainId, ModelSpecContract.ModelType.DIMENSION, ModelSpecContract.Layer.DWD,
            "customer_dimension", null, ModelSpecContract.ImplementationMode.DESIGNER_GENERATED, "table",
            null, null, new ModelSpecContract.Grain("one row per customer", List.of("customer_id")),
            null, null,
            List.of(new ModelSpecContract.ModelField("customer_id", "varchar", false, null, ModelSpecContract.FieldRole.KEY, null)),
            List.of(), List.of(), List.of(), List.of(), List.of(), null, "dimension-1"
        );
        ModelSpecContract.ModelSpecView snapshot = codec.toCreatedView(id, command, Instant.EPOCH);
        com.fasterxml.jackson.databind.node.ObjectNode wrongMode = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(
            codec.write(snapshot)
        );
        wrongMode.put("compatibilityMode", "LEGACY_READONLY");
        StoredModelSpec incompatibleMode = new StoredModelSpec(
            true, 2, "server-tenant", id, planId, domainId, ModelStatus.DRAFT, 1,
            snapshot.checksum(), snapshot.checksum(), wrongMode.toString(), null, "dimension-1",
            codec.requestHash(command), codec.write(snapshot), Instant.EPOCH, Instant.EPOCH
        );
        StoredModelSpec divergentLedger = new StoredModelSpec(
            true, 2, "server-tenant", id, planId, domainId, ModelStatus.DRAFT, 1,
            "b".repeat(64), snapshot.checksum(), codec.write(snapshot), null, "dimension-1",
            codec.requestHash(command), codec.write(snapshot), Instant.EPOCH, Instant.EPOCH
        );

        assertThatThrownBy(() -> reader.read(incompatibleMode))
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_SPEC_SNAPSHOT_INVALID");
        assertThatThrownBy(() -> reader.read(divergentLedger))
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_SPEC_SNAPSHOT_INVALID");
    }

    private static String historicalExtendedSnapshot() {
        return """
            {
              "id":"e0b7a417-47db-40d5-ad90-9aa02effaef3",
              "name":"财务项目",
              "grain":{"keys":["project_code"],"statement":"项目数据"},
              "layer":"DWD",
              "fields":[{
                "name":"project_code",
                "role":"KEY",
                "dataType":"string",
                "nullable":false,
                "redundant":false,
                "securityLevel":null,
                "sourceFieldRef":null,
                "redundancySourceRef":null,
                "dimensionAttributeCode":null
              }],
              "planId":"fa404d61-d4e6-443b-80f2-0719eb585b29",
              "status":"DRAFT",
              "checksum":"a11fab45b290c571abb1f15e07555313208becd5fda081ab4aa7b9356ba3063a",
              "domainId":"e1f7371f-8c17-44e4-9a81-6a0754dce096",
              "revision":2,
              "createdAt":"2026-07-26T15:06:29.303886617Z",
              "dependsOn":[],
              "factShape":null,
              "modelType":"DIMENSION",
              "updatedAt":"2026-07-26T15:12:31.198181258Z",
              "dataMartId":"490d1935-93ab-4448-a2a3-1e95c6feadec",
              "legacyRefs":null,
              "metricRefs":[],
              "sourceRefs":[],
              "description":null,
              "variantCode":null,
              "dimensionRefs":[],
              "timeSemantics":null,
              "contractVersion":2,
              "materialization":null,
              "standardBindings":[],
              "compatibilityMode":"CANONICAL",
              "generationStrategy":null,
              "implementationMode":"DESIGNER_GENERATED",
              "businessActivityRef":null,
              "consumptionScenario":null,
              "dimensionDefinitionRef":{
                "revision":5,
                "dimensionDefinitionId":"352e8558-9cad-4a7b-8ce7-9b629608cdbb"
              }
            }
            """;
    }

    private static StoredModelSpec legacyDimensionRow(
        ObjectMapper mapper,
        UUID id,
        UUID planId,
        UUID domainId,
        String name
    ) throws Exception {
        ModelingVNextContract.ModelSpec legacy = new ModelingVNextContract.ModelSpec(
            id.toString(),
            null,
            null,
            ModelingVNextContract.Layer.DWD,
            ModelingVNextContract.ModelType.DIMENSION,
            ModelingVNextContract.ImplementationMode.DESIGNER_GENERATED,
            name,
            null,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            "table",
            1,
            List.of(),
            null
        );
        return new StoredModelSpec(
            1,
            "server-tenant",
            id,
            planId,
            domainId,
            ModelStatus.DRAFT,
            1,
            "a".repeat(64),
            null,
            mapper.writeValueAsString(legacy),
            null,
            null,
            null,
            Instant.EPOCH,
            Instant.EPOCH
        );
    }
}
