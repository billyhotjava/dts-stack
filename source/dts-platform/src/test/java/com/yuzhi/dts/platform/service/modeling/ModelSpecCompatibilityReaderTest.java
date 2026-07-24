package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.repository.modeling.DimensionDefinitionRepository;
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
}
