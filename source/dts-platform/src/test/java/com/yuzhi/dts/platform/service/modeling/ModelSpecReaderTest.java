package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository.StoredModelSpec;
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
class ModelSpecReaderTest {

    @Mock
    private ModelSpecRepository repository;

    @Test
    void reportsMissingModelSpecWithoutFailingWhenTheIdentifierIsNull() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        ModelSpecReader reader = new ModelSpecReader(
            repository,
            new ModelSpecSnapshotCodec(mapper)
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
    void rejectsNonCanonicalStoredContractsWithoutAttemptingCompatibilityProjection() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        ModelSpecReader reader = new ModelSpecReader(repository, new ModelSpecSnapshotCodec(mapper));
        StoredModelSpec stored = org.mockito.Mockito.mock(StoredModelSpec.class);
        when(stored.contractVersion()).thenReturn(1);

        assertThatThrownBy(() -> reader.read(stored))
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_SPEC_CONTRACT_VERSION_UNSUPPORTED");
    }

    @Test
    void readsPinnedCanonicalDimensionsInABoundedRelationshipGraphWindow() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        ModelSpecReader reader = new ModelSpecReader(repository, new ModelSpecSnapshotCodec(mapper));
        UUID planId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID domainId = UUID.fromString("20000000-0000-0000-0000-000000000001");
        UUID firstId = UUID.fromString("30000000-0000-0000-0000-000000000001");
        UUID secondId = UUID.fromString("30000000-0000-0000-0000-000000000002");
        UUID firstDefinitionId = UUID.fromString("40000000-0000-0000-0000-000000000001");
        UUID secondDefinitionId = UUID.fromString("40000000-0000-0000-0000-000000000002");
        ModelSpecSnapshotCodec codec = new ModelSpecSnapshotCodec(mapper);
        StoredModelSpec first = canonicalDimensionRow(
            codec,
            firstId,
            planId,
            domainId,
            firstDefinitionId,
            2,
            "first_dimension"
        );
        StoredModelSpec second = canonicalDimensionRow(
            codec,
            secondId,
            planId,
            domainId,
            secondDefinitionId,
            3,
            "second_dimension"
        );

        List<ModelSpecContract.ModelSpecView> projected = reader.readForRelationshipGraph(List.of(first, second));

        assertThat(projected)
            .extracting(ModelSpecContract.ModelSpecView::dimensionDefinitionRef)
            .containsExactly(
                new ModelSpecContract.DimensionDefinitionRef(firstDefinitionId, 2),
                new ModelSpecContract.DimensionDefinitionRef(secondDefinitionId, 3)
            );
    }

    @Test
    void rejectsCanonicalSnapshotsWhoseDomainDoesNotMatchTheLedgerHead() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        ModelSpecSnapshotCodec codec = new ModelSpecSnapshotCodec(mapper);
        ModelSpecReader reader = new ModelSpecReader(repository, codec);
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
            codec.write(snapshot), "create-1", codec.requestHash(command), codec.write(snapshot),
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
        ModelSpecReader reader = new ModelSpecReader(repository, codec);
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
            null, snapshot.checksum(), snapshotJson, command.idempotencyKey(),
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
        org.mockito.Mockito.verify(repository).findRevision("server-tenant", id, 1);
        org.mockito.Mockito.verifyNoMoreInteractions(repository);
    }

    @Test
    void acceptsHistoricalExtendedSnapshotsThatPredateFieldDisplayNames() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        ModelSpecSnapshotCodec codec = new ModelSpecSnapshotCodec(mapper);
        ModelSpecReader reader = new ModelSpecReader(repository, codec);
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
        ModelSpecReader reader = new ModelSpecReader(repository, codec);
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
        ModelSpecReader reader = new ModelSpecReader(repository, codec);
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
            snapshot.checksum(), snapshot.checksum(), wrongMode.toString(), "dimension-1",
            codec.requestHash(command), codec.write(snapshot), Instant.EPOCH, Instant.EPOCH
        );
        StoredModelSpec divergentLedger = new StoredModelSpec(
            true, 2, "server-tenant", id, planId, domainId, ModelStatus.DRAFT, 1,
            "b".repeat(64), snapshot.checksum(), codec.write(snapshot), "dimension-1",
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

    private static StoredModelSpec canonicalDimensionRow(
        ModelSpecSnapshotCodec codec,
        UUID id,
        UUID planId,
        UUID domainId,
        UUID definitionId,
        int definitionRevision,
        String name
    ) {
        var command = new ModelSpecContract.CreateModelSpecCommand(
            planId,
            domainId,
            ModelSpecContract.ModelType.DIMENSION,
            ModelSpecContract.Layer.DWD,
            name,
            null,
            ModelSpecContract.ImplementationMode.DESIGNER_GENERATED,
            "table",
            null,
            null,
            new ModelSpecContract.Grain("one row per dimension member", List.of("id")),
            null,
            null,
            List.of(new ModelSpecContract.ModelField("id", "uuid", false, null, ModelSpecContract.FieldRole.KEY, null)),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            null,
            new ModelSpecContract.DimensionDefinitionRef(definitionId, definitionRevision),
            name
        );
        ModelSpecContract.ModelSpecView snapshot = codec.toCreatedView(id, command, Instant.EPOCH);
        String snapshotJson = codec.write(snapshot);
        return new StoredModelSpec(
            2,
            "server-tenant",
            id,
            planId,
            domainId,
            ModelStatus.DRAFT,
            1,
            snapshot.checksum(),
            snapshotJson,
            name,
            codec.requestHash(command),
            snapshotJson,
            Instant.EPOCH,
            Instant.EPOCH
        );
    }
}
