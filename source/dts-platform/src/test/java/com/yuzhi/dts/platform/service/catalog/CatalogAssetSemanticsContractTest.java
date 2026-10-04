package com.yuzhi.dts.platform.service.catalog;

import static com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogAssetSemanticsContractTest {

    private static final Instant NOW = Instant.parse("2026-08-10T08:00:00Z");

    @Test
    void excludesEphemeralRelationsWithoutCreatingPlaceholderAssets() {
        AdmissionDecision decision = admit(command(RelationType.EPHEMERAL, "DWD", AssetRole.RELATION, ProducerKind.DBT_MODEL), NOW);

        assertThat(decision.admitted()).isFalse();
        assertThat(decision.excluded()).isTrue();
        assertThat(decision.reasonCode()).isEqualTo("NON_STABLE_RELATION_EXCLUDED");
    }

    @Test
    void normalizesSourceToProducerSemanticsWithoutInventingWarehouseLayer() {
        AdmissionDecision decision = admit(command(RelationType.TABLE, "SOURCE", AssetRole.RELATION, ProducerKind.SOURCE_SYSTEM), NOW);

        assertThat(decision.admitted()).isTrue();
        assertThat(decision.plan().canonicalLayer()).isNull();
        assertThat(decision.plan().legacyLayerCode()).isEqualTo("SOURCE");
        assertThat(decision.plan().producer().producerKind()).isEqualTo(ProducerKind.SOURCE_SYSTEM);
    }

    @Test
    void rejectsSourceWhenItsProducerCannotBeConfirmed() {
        AdmissionDecision decision = admit(command(RelationType.TABLE, "SOURCE", AssetRole.RELATION, ProducerKind.MANUAL_BUILD), NOW);

        assertThat(decision.admitted()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo("SOURCE_PRODUCER_UNRESOLVED");
    }

    @Test
    void normalizesConfirmedDimensionRoleToDwd() {
        AdmissionDecision decision = admit(command(RelationType.TABLE, "DIM", AssetRole.DIMENSION_TABLE, ProducerKind.DBT_MODEL), NOW);

        assertThat(decision.admitted()).isTrue();
        assertThat(decision.plan().canonicalLayer()).isEqualTo("DWD");
        assertThat(decision.plan().assetRole()).isEqualTo(AssetRole.DIMENSION_TABLE);
    }

    @Test
    void rejectsUnconfirmedLegacyDimRatherThanGuessingFromItsName() {
        AdmissionDecision decision = admit(command(RelationType.TABLE, "DIM", AssetRole.RELATION, ProducerKind.DBT_MODEL), NOW);

        assertThat(decision.admitted()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo("LAYER_NORMALIZATION_REQUIRED");
    }

    @Test
    void keepsFiveAxesIndependentAndExplainsConditionalEligibility() {
        StatusAxes axes = new StatusAxes(
            DiscoveryState.DISCOVERED,
            GovernanceReadiness.UNASSIGNED,
            PublicationState.UNPUBLISHED,
            ServingHealth.UNKNOWN,
            LifecycleState.ACTIVE
        );

        ConsumptionEligibility result = evaluateEligibility(axes, null, null, NOW);

        assertThat(result.decision()).isEqualTo(EligibilityDecision.CONDITIONAL);
        assertThat(result.reasonCodes())
            .containsExactly("DOMAIN_UNASSIGNED", "NOT_PUBLISHED", "SERVING_HEALTH_UNKNOWN", "QUALITY_EVIDENCE_MISSING", "ACCESS_EVIDENCE_MISSING");
    }

    @Test
    void failsClosedForAnyHardBlocker() {
        StatusAxes axes = new StatusAxes(
            DiscoveryState.VERIFIED,
            GovernanceReadiness.GOVERNED,
            PublicationState.PUBLISHED,
            ServingHealth.FAILED,
            LifecycleState.ACTIVE
        );

        ConsumptionEligibility result = evaluateEligibility(axes, true, true, NOW);

        assertThat(result.decision()).isEqualTo(EligibilityDecision.BLOCKED);
        assertThat(result.reasonCodes()).containsExactly("RELATION_FAILED");
    }

    @Test
    void marksOnlyTheCompletePolicyCombinationEligible() {
        StatusAxes axes = new StatusAxes(
            DiscoveryState.VERIFIED,
            GovernanceReadiness.GOVERNED,
            PublicationState.PUBLISHED,
            ServingHealth.HEALTHY,
            LifecycleState.ACTIVE
        );

        ConsumptionEligibility result = evaluateEligibility(axes, true, true, NOW);

        assertThat(result.decision()).isEqualTo(EligibilityDecision.ELIGIBLE);
        assertThat(result.reasonCodes()).isEmpty();
    }

    private ObservationCommand command(RelationType relationType, String layer, AssetRole role, ProducerKind producerKind) {
        return new ObservationCommand(
            CatalogAssetType.DATASET,
            "source:11111111-1111-1111-1111-111111111111/schema:public/table:orders",
            UUID.fromString("22222222-2222-2222-2222-222222222222"),
            relationType,
            false,
            false,
            null,
            layer,
            role,
            producerKind,
            "producer-1",
            "v1",
            EvidenceChannel.SCANNER,
            "scan-1",
            NOW,
            EvidenceStatus.ACTIVE,
            null,
            null,
            null
        );
    }
}
