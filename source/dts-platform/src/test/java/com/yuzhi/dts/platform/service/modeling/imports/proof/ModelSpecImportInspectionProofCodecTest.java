package com.yuzhi.dts.platform.service.modeling.imports.proof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.platform.config.ModelMaterializationProperties;
import com.yuzhi.dts.platform.service.modeling.imports.ModelPackageFixtures;
import com.yuzhi.dts.platform.service.modeling.imports.proof.ModelSpecImportInspectionProofCodec.InspectionProofException;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider.WarehousePlanActor;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ModelSpecImportInspectionProofCodecTest {

    private static final Instant NOW = Instant.parse("2026-08-02T01:00:00Z");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void issuesThirtyMinuteProofBoundToTenantActorAndNormalizedPackage() {
        var modelPackage = ModelPackageFixtures.validPackage();
        var codec = codec("tenant-a", "actor-a", NOW);

        var issued = codec.issue(modelPackage);

        assertThat(issued.proofExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
        assertThat(issued.inspectionProof()).doesNotContain("tenant-a", "actor-a", modelPackage.packageChecksum());
        assertThatCode(() -> codec.verify(issued.inspectionProof(), OBJECT_MAPPER.valueToTree(modelPackage)))
            .doesNotThrowAnyException();
    }

    @Test
    void rejectsTechnicalPackageMutationEvenWhenPublicChecksumIsRewritten() {
        var modelPackage = ModelPackageFixtures.validPackage();
        var codec = codec("tenant-a", "actor-a", NOW);
        var issued = codec.issue(modelPackage);
        ObjectNode changed = OBJECT_MAPPER.valueToTree(modelPackage);
        changed.withArray("models").get(0).withObject("/sql").put("effectiveSql", "select secret from tampered");
        changed.put("packageChecksum", "a".repeat(64));

        assertInvalid(() -> codec.verify(issued.inspectionProof(), changed));
    }

    @Test
    void rejectsCrossTenantAndCrossActorReplay() {
        var modelPackage = ModelPackageFixtures.validPackage();
        var issued = codec("tenant-a", "actor-a", NOW).issue(modelPackage);

        assertInvalid(() ->
            codec("tenant-b", "actor-a", NOW).verify(issued.inspectionProof(), OBJECT_MAPPER.valueToTree(modelPackage))
        );
        assertInvalid(() ->
            codec("tenant-a", "actor-b", NOW).verify(issued.inspectionProof(), OBJECT_MAPPER.valueToTree(modelPackage))
        );
    }

    @Test
    void reportsExpiredOnlyAfterAuthenticatingTheProof() {
        var modelPackage = ModelPackageFixtures.validPackage();
        var issued = codec("tenant-a", "actor-a", NOW).issue(modelPackage);

        assertThatThrownBy(() ->
            codec("tenant-a", "actor-a", NOW.plus(Duration.ofMinutes(31)))
                .verify(issued.inspectionProof(), OBJECT_MAPPER.valueToTree(modelPackage))
        )
            .isInstanceOfSatisfying(InspectionProofException.class, exception ->
                assertThat(exception.code()).isEqualTo("DBT_IMPORT_INSPECTION_PROOF_EXPIRED")
            );
        assertInvalid(() ->
            codec("tenant-a", "actor-a", NOW.plus(Duration.ofMinutes(31)))
                .verify(issued.inspectionProof() + "x", OBJECT_MAPPER.valueToTree(modelPackage))
        );
    }

    @Test
    void failsClosedWhenSigningKeyIsUnavailable() {
        var actorProvider = actorProvider("actor-a");
        var properties = new ModelMaterializationProperties();
        properties.setRuntimeSpecSigningKey("short");
        var codec = new ModelSpecImportInspectionProofCodec(
            properties,
            actorProvider,
            "tenant-a",
            Clock.fixed(NOW, ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> codec.issue(ModelPackageFixtures.validPackage()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("runtime spec signing key");
    }

    private static ModelSpecImportInspectionProofCodec codec(String tenantId, String actorId, Instant now) {
        var properties = new ModelMaterializationProperties();
        properties.setRuntimeSpecSigningKey("inspection-proof-shared-platform-key-" + "x".repeat(32));
        return new ModelSpecImportInspectionProofCodec(
            properties,
            actorProvider(actorId),
            tenantId,
            Clock.fixed(now, ZoneOffset.UTC)
        );
    }

    private static WarehousePlanActorProvider actorProvider(String actorId) {
        WarehousePlanActorProvider provider = mock(WarehousePlanActorProvider.class);
        when(provider.currentActor()).thenReturn(new WarehousePlanActor(actorId, "dept-a"));
        return provider;
    }

    private static void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
            .isInstanceOfSatisfying(InspectionProofException.class, exception ->
                assertThat(exception.code()).isEqualTo("DBT_IMPORT_INSPECTION_PROOF_INVALID")
            );
    }
}
