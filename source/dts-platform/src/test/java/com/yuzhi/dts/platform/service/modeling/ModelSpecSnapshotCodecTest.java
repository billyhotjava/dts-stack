package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModelSpecSnapshotCodecTest {

    private final ModelSpecSnapshotCodec codec = new ModelSpecSnapshotCodec(new ObjectMapper().findAndRegisterModules());

    @Test
    void hashesNormalizedContentWithoutIdempotencyOrServerManagedFields() {
        CreateModelSpecCommand first = command("idem-1");
        CreateModelSpecCommand replay = command("idem-2");

        assertThat(codec.requestHash(first)).isEqualTo(codec.requestHash(replay));
        assertThat(codec.contentChecksum(first)).hasSize(64);
        assertThat(codec.contentChecksum(codec.toCreatedView(UUID.randomUUID(), first, Instant.EPOCH)))
            .isEqualTo(codec.contentChecksum(first));
    }

    @Test
    void contentHashesDoNotDependOnEnvironmentJsonPrettyPrinting() {
        ObjectMapper prettyMapper = new ObjectMapper().findAndRegisterModules().enable(SerializationFeature.INDENT_OUTPUT);
        ModelSpecSnapshotCodec prettyCodec = new ModelSpecSnapshotCodec(prettyMapper);
        CreateModelSpecCommand command = command("idem-profile-independent");

        assertThat(prettyCodec.contentChecksum(command)).isEqualTo(codec.contentChecksum(command));
        assertThat(prettyCodec.requestHash(command)).isEqualTo(codec.requestHash(command));
    }

    @Test
    void roundTripsTheExactResponseSnapshot() {
        CreateModelSpecCommand command = command("idem-1");
        Instant now = Instant.parse("2026-07-19T00:00:00Z");
        ModelSpecView view = codec.toCreatedView(UUID.fromString("30000000-0000-0000-0000-000000000001"), command, now);

        assertThat(codec.readView(codec.write(view))).isEqualTo(view);
    }

    @Test
    void omitsAbsentDimensionProfileFromLegacyCompatibleSnapshotsAndHashes() {
        CreateModelSpecCommand command = command("legacy-compatible");
        ModelSpecView view = codec.toCreatedView(
            UUID.fromString("30000000-0000-0000-0000-000000000001"),
            command,
            Instant.EPOCH
        );

        assertThat(codec.write(view)).doesNotContain("\"dimensionProfile\"");
        assertThat(codec.contentChecksum(view)).isEqualTo(codec.contentChecksum(command));
    }

    @Test
    void readsHistoricalSnapshotsWithoutWarehouseLayerCodeAsCanonicalLayer() throws Exception {
        CreateModelSpecCommand command = command("historical-layer");
        ModelSpecView view = codec.toCreatedView(UUID.randomUUID(), command, Instant.EPOCH);
        String snapshot = codec.write(view);
        com.fasterxml.jackson.databind.JsonNode tree = new ObjectMapper().readTree(snapshot);

        assertThat(tree.has("warehouseLayerCode")).isTrue();
        ModelSpecView withCustom = codec.readView(
            ((com.fasterxml.jackson.databind.node.ObjectNode) tree).put("warehouseLayerCode", "FIN_DETAIL").toString()
        );
        assertThat(withCustom.warehouseLayerCode()).isEqualTo("FIN_DETAIL");

        com.fasterxml.jackson.databind.node.ObjectNode legacy = (com.fasterxml.jackson.databind.node.ObjectNode) new ObjectMapper().readTree(snapshot);
        legacy.remove("warehouseLayerCode");
        ModelSpecView historical = codec.readView(legacy.toString());
        assertThat(historical.warehouseLayerCode()).isEqualTo("DWD");

        ModelSpecView current = codec.readView(snapshot);
        assertThat(codec.matchesStoredContentChecksum(snapshot, current, codec.contentChecksum(current)))
            .isTrue();
    }

    @Test
    void readsPinnedDimensionDefinitionSnapshotsWhileKeepingHistoricalNullReferencesOutOfJson() throws Exception {
        ModelSpecView historical = codec.toCreatedView(
            UUID.fromString("30000000-0000-0000-0000-000000000001"),
            command("legacy-snapshot"),
            Instant.EPOCH
        );
        String historicalSnapshot = codec.write(historical);
        var pinnedSnapshot = new ObjectMapper().readTree(historicalSnapshot);
        ((com.fasterxml.jackson.databind.node.ObjectNode) pinnedSnapshot)
            .putObject("dimensionDefinitionRef")
            .put("dimensionDefinitionId", "60000000-0000-0000-0000-000000000001")
            .put("revision", 3);

        assertThat(codec.readView(pinnedSnapshot.toString())).isNotNull();
        assertThat(codec.write(codec.readView(historicalSnapshot))).doesNotContain("\"dimensionDefinitionRef\"");
        assertThat(codec.contentChecksum(codec.readView(historicalSnapshot))).isEqualTo(codec.contentChecksum(historical));
    }

    @Test
    void keepsThePinnedDimensionDefinitionInTheUpdatedContentChecksum() {
        CreateModelSpecCommand create = pinnedDimensionCommand();
        ModelSpecView current = codec.toCreatedView(UUID.randomUUID(), create, Instant.EPOCH);
        UpdateModelSpecCommand update = new UpdateModelSpecCommand(
            create.planId(), create.domainId(), create.modelType(), create.layer(), "customer_dimension_v2", create.description(),
            create.implementationMode(), create.materialization(), create.businessActivityRef(), create.consumptionScenario(),
            create.grain(), create.factShape(), create.timeSemantics(), create.fields(), create.sourceRefs(), create.dependsOn(),
            create.dimensionRefs(), create.metricRefs(), create.standardBindings(), create.generationStrategy(), create.dimensionProfile()
        );

        ModelSpecView replacement = codec.toUpdatedView(current, update, 2, Instant.EPOCH.plusSeconds(60));

        assertThat(replacement.dimensionDefinitionRef()).isEqualTo(create.dimensionDefinitionRef());
        assertThat(replacement.checksum()).isEqualTo(codec.contentChecksum(replacement));
        assertThat(ModelSpecContract.validateView(replacement)).isEmpty();
    }

    @Test
    void preservesLegacyDimensionIdentityFieldsWhenCanonicalUpdatesOmitThem() {
        DimensionProfile legacyProfile = new DimensionProfile(
            "DIM_CUSTOMER",
            List.of(),
            new ScdPolicy(ScdType.TYPE1, null, null, null),
            ReuseScope.DOMAIN
        );
        CreateModelSpecCommand create = withDimensionProfile(pinnedDimensionCommand(), legacyProfile);
        ModelSpecView current = codec.toCreatedView(UUID.randomUUID(), create, Instant.EPOCH);
        DimensionProfile canonicalProfile = new DimensionProfile(
            null,
            legacyProfile.hierarchies(),
            legacyProfile.scdPolicy(),
            null
        );
        UpdateModelSpecCommand update = new UpdateModelSpecCommand(
            create.planId(), create.domainId(), create.modelType(), create.layer(), create.name(), create.description(),
            create.implementationMode(), create.materialization(), create.businessActivityRef(), create.consumptionScenario(),
            create.grain(), create.factShape(), create.timeSemantics(), create.fields(), create.sourceRefs(), create.dependsOn(),
            create.dimensionRefs(), create.metricRefs(), create.standardBindings(), create.generationStrategy(), canonicalProfile
        );

        ModelSpecView replacement = codec.toUpdatedView(current, update, 2, Instant.EPOCH.plusSeconds(60));

        assertThat(replacement.dimensionProfile().dimensionCode()).isEqualTo("DIM_CUSTOMER");
        assertThat(replacement.dimensionProfile().reuseScope()).isEqualTo(ReuseScope.DOMAIN);
        assertThat(replacement.dimensionDefinitionRef()).isEqualTo(current.dimensionDefinitionRef());
        assertThat(replacement.checksum()).isEqualTo(current.checksum());
        assertThat(ModelSpecContract.validateView(replacement)).isEmpty();
    }

    private static CreateModelSpecCommand command(String idempotencyKey) {
        return new CreateModelSpecCommand(
            UUID.fromString("10000000-0000-0000-0000-000000000001"),
            UUID.fromString("20000000-0000-0000-0000-000000000001"),
            ModelType.FACT,
            Layer.DWD,
            " customer_detail ",
            null,
            ImplementationMode.DESIGNER_GENERATED,
            "table",
            null,
            null,
            new Grain("one row per customer event", List.of("customer_id")),
            null,
            null,
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
                    UUID.fromString("50000000-0000-0000-0000-000000000001"),
                    "v1"
                )
            ),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            idempotencyKey
        );
    }

    private static CreateModelSpecCommand pinnedDimensionCommand() {
        CreateModelSpecCommand base = command("pinned-dimension");
        return new CreateModelSpecCommand(
            base.planId(),
            base.domainId(),
            ModelType.DIMENSION,
            Layer.DWD,
            "customer_dimension",
            base.description(),
            base.implementationMode(),
            base.materialization(),
            null,
            null,
            base.grain(),
            null,
            null,
            base.fields(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            null,
            new DimensionDefinitionRef(UUID.fromString("60000000-0000-0000-0000-000000000001"), 3),
            base.idempotencyKey()
        );
    }

    private static CreateModelSpecCommand withDimensionProfile(
        CreateModelSpecCommand base,
        DimensionProfile dimensionProfile
    ) {
        return new CreateModelSpecCommand(
            base.planId(),
            base.domainId(),
            base.modelType(),
            base.layer(),
            base.name(),
            base.description(),
            base.implementationMode(),
            base.materialization(),
            base.businessActivityRef(),
            base.consumptionScenario(),
            base.grain(),
            base.factShape(),
            base.timeSemantics(),
            base.fields(),
            base.sourceRefs(),
            base.dependsOn(),
            base.dimensionRefs(),
            base.metricRefs(),
            base.standardBindings(),
            base.generationStrategy(),
            dimensionProfile,
            base.dimensionDefinitionRef(),
            base.idempotencyKey()
        );
    }
}
