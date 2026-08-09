package com.yuzhi.dts.platform.service.catalog;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.util.StringUtils;

/**
 * Orthogonal asset semantics approved by ADR-86-04/05/18/19.
 *
 * <p>The existing {@link CatalogAssetType} + {@link CatalogAssetKey} pair remains the only asset identity.
 * Producer, discovery evidence and operational states are projections of that identity and must never mint a
 * second catalog asset.
 */
public final class CatalogAssetSemanticsContract {

    private static final List<String> CANONICAL_LAYERS = List.of("ODS", "STG", "DWD", "DWS", "ADS");

    private CatalogAssetSemanticsContract() {}

    public enum RelationType {
        TABLE,
        VIEW,
        MATERIALIZED_VIEW,
        EPHEMERAL,
        TEMPORARY,
    }

    public enum AssetRole {
        RELATION,
        DIMENSION_TABLE,
    }

    public enum ProducerKind {
        SOURCE_SYSTEM,
        INGESTION_JOB,
        DBT_MODEL,
        MANUAL_BUILD,
        MODELING,
    }

    public enum EvidenceChannel {
        SCANNER,
        INGESTION_EVENT,
        DBT_SYNC,
        MATERIALIZATION_OBSERVATION,
        MANUAL,
    }

    public enum EvidenceStatus {
        ACTIVE,
        STALE,
        FAILED,
    }

    public enum DiscoveryState {
        DISCOVERED,
        VERIFIED,
        MISSING,
    }

    public enum GovernanceReadiness {
        UNASSIGNED,
        INCOMPLETE,
        GOVERNED,
    }

    public enum PublicationState {
        UNPUBLISHED,
        PUBLISHED,
        WITHDRAWN,
    }

    public enum ServingHealth {
        UNKNOWN,
        HEALTHY,
        STALE,
        FAILED,
    }

    public enum LifecycleState {
        ACTIVE,
        DEPRECATED,
        RETIRED,
    }

    public enum EligibilityDecision {
        ELIGIBLE,
        CONDITIONAL,
        BLOCKED,
    }

    public enum Freshness {
        FRESH,
        STALE,
        REBUILDING,
    }

    public record ProducerRef(
        ProducerKind producerKind,
        String producerId,
        String producerVersion,
        Instant validFrom,
        Instant validTo
    ) {}

    public record RegistrationEvidence(
        CatalogAssetType assetType,
        String assetKey,
        EvidenceChannel channel,
        String evidenceRef,
        Instant firstObservedAt,
        Instant lastObservedAt,
        EvidenceStatus status
    ) {}

    public record StatusAxes(
        DiscoveryState discovery,
        GovernanceReadiness governance,
        PublicationState publication,
        ServingHealth serving,
        LifecycleState lifecycle
    ) {
        public StatusAxes {
            discovery = discovery == null ? DiscoveryState.DISCOVERED : discovery;
            governance = governance == null ? GovernanceReadiness.UNASSIGNED : governance;
            publication = publication == null ? PublicationState.UNPUBLISHED : publication;
            serving = serving == null ? ServingHealth.UNKNOWN : serving;
            lifecycle = lifecycle == null ? LifecycleState.ACTIVE : lifecycle;
        }
    }

    public record ConsumptionEligibility(
        EligibilityDecision decision,
        List<String> reasonCodes,
        Instant evaluatedAt
    ) {
        public ConsumptionEligibility {
            reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
            evaluatedAt = evaluatedAt == null ? Instant.now() : evaluatedAt;
        }
    }

    public record ObservationCommand(
        CatalogAssetType assetType,
        String assetKey,
        UUID resourceId,
        RelationType relationType,
        boolean temporary,
        boolean ephemeral,
        UUID domainId,
        String warehouseLayer,
        AssetRole assetRole,
        ProducerKind producerKind,
        String producerId,
        String producerVersion,
        EvidenceChannel evidenceChannel,
        String evidenceRef,
        Instant observedAt,
        EvidenceStatus evidenceStatus,
        StatusAxes statusAxes,
        Boolean qualityGatePassed,
        Boolean permissionGatePassed
    ) {}

    public record RegistrationPlan(
        CatalogAssetType assetType,
        String assetKey,
        UUID resourceId,
        RelationType relationType,
        UUID domainId,
        String canonicalLayer,
        String legacyLayerCode,
        AssetRole assetRole,
        ProducerRef producer,
        RegistrationEvidence evidence,
        StatusAxes statusAxes,
        Boolean qualityGatePassed,
        Boolean permissionGatePassed,
        ConsumptionEligibility eligibility
    ) {}

    public record AdmissionDecision(boolean admitted, boolean excluded, String reasonCode, RegistrationPlan plan) {

        static AdmissionDecision admitted(RegistrationPlan plan) {
            return new AdmissionDecision(true, false, null, plan);
        }

        static AdmissionDecision excluded(String reasonCode) {
            return new AdmissionDecision(false, true, reasonCode, null);
        }

        static AdmissionDecision rejected(String reasonCode) {
            return new AdmissionDecision(false, false, reasonCode, null);
        }
    }

    public static AdmissionDecision admit(ObservationCommand command, Instant now) {
        if (command == null) {
            return AdmissionDecision.rejected("ASSET_OBSERVATION_REQUIRED");
        }
        if (command.assetType() != CatalogAssetType.DATASET) {
            return AdmissionDecision.rejected("PHYSICAL_ASSET_TYPE_REQUIRED");
        }
        if (!StringUtils.hasText(command.assetKey()) || command.assetKey().trim().length() > 512) {
            return AdmissionDecision.rejected("CATALOG_ASSET_KEY_INVALID");
        }
        if (
            command.temporary() ||
            command.ephemeral() ||
            command.relationType() == RelationType.EPHEMERAL ||
            command.relationType() == RelationType.TEMPORARY
        ) {
            return AdmissionDecision.excluded("NON_STABLE_RELATION_EXCLUDED");
        }
        if (
            command.relationType() != RelationType.TABLE &&
            command.relationType() != RelationType.VIEW &&
            command.relationType() != RelationType.MATERIALIZED_VIEW
        ) {
            return AdmissionDecision.rejected("STABLE_RELATION_TYPE_REQUIRED");
        }
        if (
            command.producerKind() == null ||
            !StringUtils.hasText(command.producerId()) ||
            command.evidenceChannel() == null ||
            !StringUtils.hasText(command.evidenceRef())
        ) {
            return AdmissionDecision.rejected("PRODUCER_AND_EVIDENCE_REQUIRED");
        }

        String legacyLayer = upper(command.warehouseLayer());
        String canonicalLayer = legacyLayer;
        AssetRole assetRole = command.assetRole() == null ? AssetRole.RELATION : command.assetRole();
        if ("SOURCE".equals(legacyLayer)) {
            if (command.producerKind() != ProducerKind.SOURCE_SYSTEM) {
                return AdmissionDecision.rejected("SOURCE_PRODUCER_UNRESOLVED");
            }
            canonicalLayer = null;
        } else if ("DIM".equals(legacyLayer)) {
            if (assetRole != AssetRole.DIMENSION_TABLE) {
                return AdmissionDecision.rejected("LAYER_NORMALIZATION_REQUIRED");
            }
            canonicalLayer = "DWD";
        } else if (StringUtils.hasText(legacyLayer) && !CANONICAL_LAYERS.contains(legacyLayer)) {
            return AdmissionDecision.rejected("WAREHOUSE_LAYER_UNRECOGNIZED");
        }

        Instant observedAt = command.observedAt() == null ? now : command.observedAt();
        StatusAxes axes = command.statusAxes() == null ? defaultAxes(command.evidenceChannel()) : command.statusAxes();
        ConsumptionEligibility eligibility = evaluateEligibility(
            axes,
            command.qualityGatePassed(),
            command.permissionGatePassed(),
            now
        );
        String normalizedKey = command.assetKey().trim();
        return AdmissionDecision.admitted(
            new RegistrationPlan(
                command.assetType(),
                normalizedKey,
                command.resourceId(),
                command.relationType(),
                command.domainId(),
                canonicalLayer,
                legacyLayer,
                assetRole,
                new ProducerRef(
                    command.producerKind(),
                    command.producerId().trim(),
                    trimToNull(command.producerVersion()),
                    observedAt,
                    null
                ),
                new RegistrationEvidence(
                    command.assetType(),
                    normalizedKey,
                    command.evidenceChannel(),
                    command.evidenceRef().trim(),
                    observedAt,
                    observedAt,
                    command.evidenceStatus() == null ? EvidenceStatus.ACTIVE : command.evidenceStatus()
                ),
                axes,
                command.qualityGatePassed(),
                command.permissionGatePassed(),
                eligibility
            )
        );
    }

    public static ConsumptionEligibility evaluateEligibility(
        StatusAxes axes,
        Boolean qualityGatePassed,
        Boolean permissionGatePassed,
        Instant now
    ) {
        StatusAxes effective = axes == null ? new StatusAxes(null, null, null, null, null) : axes;
        List<String> reasons = new ArrayList<>();
        if (effective.discovery() == DiscoveryState.MISSING) reasons.add("RELATION_MISSING");
        if (effective.governance() == GovernanceReadiness.UNASSIGNED) reasons.add("DOMAIN_UNASSIGNED");
        if (effective.governance() == GovernanceReadiness.INCOMPLETE) reasons.add("GOVERNANCE_INCOMPLETE");
        if (effective.publication() == PublicationState.UNPUBLISHED) reasons.add("NOT_PUBLISHED");
        if (effective.publication() == PublicationState.WITHDRAWN) reasons.add("PUBLICATION_WITHDRAWN");
        if (effective.serving() == ServingHealth.UNKNOWN) reasons.add("SERVING_HEALTH_UNKNOWN");
        if (effective.serving() == ServingHealth.STALE) reasons.add("RELATION_STALE");
        if (effective.serving() == ServingHealth.FAILED) reasons.add("RELATION_FAILED");
        if (effective.lifecycle() == LifecycleState.DEPRECATED) reasons.add("ASSET_DEPRECATED");
        if (effective.lifecycle() == LifecycleState.RETIRED) reasons.add("ASSET_RETIRED");
        if (qualityGatePassed == null) reasons.add("QUALITY_EVIDENCE_MISSING");
        if (Boolean.FALSE.equals(qualityGatePassed)) reasons.add("QUALITY_FAILED");
        if (permissionGatePassed == null) reasons.add("ACCESS_EVIDENCE_MISSING");
        if (Boolean.FALSE.equals(permissionGatePassed)) reasons.add("ACCESS_DENIED");

        boolean blocked =
            effective.discovery() == DiscoveryState.MISSING ||
            effective.publication() == PublicationState.WITHDRAWN ||
            effective.serving() == ServingHealth.FAILED ||
            effective.lifecycle() == LifecycleState.RETIRED ||
            Boolean.FALSE.equals(qualityGatePassed) ||
            Boolean.FALSE.equals(permissionGatePassed);
        boolean fullyEligible =
            effective.discovery() == DiscoveryState.VERIFIED &&
            effective.governance() == GovernanceReadiness.GOVERNED &&
            effective.publication() == PublicationState.PUBLISHED &&
            effective.serving() == ServingHealth.HEALTHY &&
            effective.lifecycle() == LifecycleState.ACTIVE &&
            Boolean.TRUE.equals(qualityGatePassed) &&
            Boolean.TRUE.equals(permissionGatePassed);
        EligibilityDecision decision = blocked
            ? EligibilityDecision.BLOCKED
            : fullyEligible ? EligibilityDecision.ELIGIBLE : EligibilityDecision.CONDITIONAL;
        return new ConsumptionEligibility(decision, reasons, now);
    }

    private static StatusAxes defaultAxes(EvidenceChannel channel) {
        DiscoveryState discovery = switch (channel) {
            case INGESTION_EVENT, DBT_SYNC, MATERIALIZATION_OBSERVATION -> DiscoveryState.VERIFIED;
            case SCANNER, MANUAL -> DiscoveryState.DISCOVERED;
        };
        return new StatusAxes(discovery, null, null, null, null);
    }

    private static String upper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
