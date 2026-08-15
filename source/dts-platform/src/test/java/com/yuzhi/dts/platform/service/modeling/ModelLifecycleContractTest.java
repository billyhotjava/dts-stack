package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryAction;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryActorRole;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryAuditView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryEvidenceType;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.FieldMapping;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.GeneratedInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ClaimImplementationCommand;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.InputMode;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.PhysicalAssetInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.RevisionBoundDeliveryKey;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.SaveImplementationCommand;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.UpstreamModelInput;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModelLifecycleContractTest {

    @Test
    void deliveryStatusAllowsDriftBeforeTerminalStatesAndRequiresReplacementCandidatesAfterward() {
        assertThat(DeliveryStatus.canTransition(DeliveryStatus.DRAFT, DeliveryStatus.BUILDING)).isTrue();
        assertThat(
            Arrays.stream(DeliveryStatus.values())
                .filter(status ->
                    status != DeliveryStatus.REJECTED &&
                    status != DeliveryStatus.ROLLED_BACK &&
                    status != DeliveryStatus.STALE &&
                    status != DeliveryStatus.CANCELLED
                )
                .allMatch(status -> DeliveryStatus.canTransition(status, DeliveryStatus.STALE))
        ).isTrue();
        assertThat(DeliveryStatus.canTransition(DeliveryStatus.REJECTED, DeliveryStatus.DRAFT)).isFalse();
        assertThat(DeliveryStatus.canTransition(DeliveryStatus.ROLLED_BACK, DeliveryStatus.DRAFT)).isFalse();
        assertThat(DeliveryStatus.canTransition(DeliveryStatus.STALE, DeliveryStatus.DRAFT)).isFalse();
        assertThat(DeliveryStatus.REJECTED.allowedActions())
            .containsExactly(DeliveryAction.CREATE_REPLACEMENT_CANDIDATE);
        assertThat(DeliveryStatus.ROLLED_BACK.allowedActions())
            .containsExactly(DeliveryAction.CREATE_REPLACEMENT_CANDIDATE);
        assertThat(DeliveryStatus.STALE.allowedActions())
            .containsExactly(DeliveryAction.CREATE_REPLACEMENT_CANDIDATE);
        assertThat(DeliveryStatus.CANCELLED.allowedActions())
            .containsExactly(DeliveryAction.CREATE_REPLACEMENT_CANDIDATE);
        // F1-T02/T03 creates the replacement with a new candidate ID; this candidate remains terminal.
        assertThat(DeliveryStatus.canTransition(DeliveryStatus.QUALITY_PASSED, DeliveryStatus.PUBLISHED)).isFalse();
        assertThat(DeliveryStatus.canTransition(DeliveryStatus.QUALITY_PASSED, DeliveryStatus.PUBLISHING)).isTrue();
        assertThat(DeliveryStatus.canTransition(DeliveryStatus.REVIEW_PENDING, DeliveryStatus.PUBLISHING)).isTrue();
        assertThat(DeliveryStatus.canTransition(DeliveryStatus.PUBLISHED, DeliveryStatus.DRAFT)).isFalse();
    }

    @Test
    void cancellationIsMaintainerOwnedTerminalAndAvailableUntilPublicationStarts() {
        assertThat(DeliveryStatus.canTransition(DeliveryStatus.DRAFT, DeliveryStatus.CANCELLED)).isTrue();
        assertThat(DeliveryStatus.canTransition(DeliveryStatus.BUILD_FAILED, DeliveryStatus.CANCELLED)).isTrue();
        assertThat(DeliveryStatus.canTransition(DeliveryStatus.QUALITY_FAILED, DeliveryStatus.CANCELLED)).isTrue();
        assertThat(DeliveryStatus.canTransition(DeliveryStatus.BUILDING, DeliveryStatus.CANCELLED)).isFalse();
        assertThat(DeliveryStatus.canTransition(DeliveryStatus.BUILT, DeliveryStatus.CANCELLED)).isTrue();
        assertThat(DeliveryStatus.canTransition(DeliveryStatus.REVIEW_PENDING, DeliveryStatus.CANCELLED)).isTrue();
        assertThat(DeliveryStatus.canTransition(DeliveryStatus.CANCELLED, DeliveryStatus.DRAFT)).isFalse();
        assertThat(DeliveryStatus.canTransition(DeliveryStatus.CANCELLED, DeliveryStatus.STALE)).isFalse();
        assertThat(DeliveryStatus.DRAFT.allowedActions()).contains(DeliveryAction.CANCEL_CANDIDATE);
        assertThat(DeliveryStatus.BUILD_FAILED.allowedActions()).contains(DeliveryAction.CANCEL_CANDIDATE);
        assertThat(DeliveryStatus.QUALITY_FAILED.allowedActions()).contains(DeliveryAction.CANCEL_CANDIDATE);
        assertThat(DeliveryStatus.BUILT.allowedActions()).contains(DeliveryAction.CANCEL_CANDIDATE);
        assertThat(DeliveryStatus.REVIEW_PENDING.allowedActions()).contains(DeliveryAction.CANCEL_CANDIDATE);
        assertThat(DeliveryAction.CANCEL_CANDIDATE.requiredRole()).isEqualTo(DeliveryActorRole.MODEL_MAINTAINER);
    }

    @Test
    void onlyPublishedCompletesStageSixAndReleaseReadyIsNotADeliveryStatus() {
        assertThat(DeliveryStatus.PUBLISHED.satisfiesStageSixCompletion()).isTrue();
        assertThat(
            Arrays.stream(DeliveryStatus.values())
                .filter(status -> status != DeliveryStatus.PUBLISHED)
                .noneMatch(DeliveryStatus::satisfiesStageSixCompletion)
        ).isTrue();
        assertThatThrownBy(() -> DeliveryStatus.valueOf("RELEASE_READY")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void revisionBoundDeliveryKeyRequiresEveryStableStageSixInput() {
        UUID planId = UUID.fromString("10000000-0000-0000-0000-000000000069");
        UUID candidateId = UUID.fromString("20000000-0000-0000-0000-000000000069");
        UUID modelSpecId = UUID.fromString("40000000-0000-0000-0000-000000000069");

        RevisionBoundDeliveryKey key = new RevisionBoundDeliveryKey(
            planId,
            candidateId,
            modelSpecId,
            7,
            "b".repeat(64),
            ImplementationMode.DBT_MANAGED,
            "production"
        );

        assertThat(key.planId()).isEqualTo(planId);
        assertThat(key.candidateId()).isEqualTo(candidateId);
        assertThat(key.modelSpecId()).isEqualTo(modelSpecId);
        assertThat(key.revision()).isEqualTo(7);
        assertThat(key.checksum()).isEqualTo("b".repeat(64));
        assertThat(key.implementationMode()).isEqualTo(ImplementationMode.DBT_MANAGED);
        assertThat(key.environment()).isEqualTo("production");
        assertThatThrownBy(() -> new RevisionBoundDeliveryKey(planId, candidateId, modelSpecId, 0, "checksum", ImplementationMode.DBT_MANAGED, "production"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RevisionBoundDeliveryKey(null, candidateId, modelSpecId, 1, "checksum", ImplementationMode.DBT_MANAGED, "production"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RevisionBoundDeliveryKey(planId, null, modelSpecId, 1, "checksum", ImplementationMode.DBT_MANAGED, "production"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RevisionBoundDeliveryKey(planId, candidateId, null, 1, "checksum", ImplementationMode.DBT_MANAGED, "production"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RevisionBoundDeliveryKey(planId, candidateId, modelSpecId, 1, null, ImplementationMode.DBT_MANAGED, "production"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RevisionBoundDeliveryKey(planId, candidateId, modelSpecId, 1, "checksum", null, "production"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RevisionBoundDeliveryKey(planId, candidateId, modelSpecId, 1, "checksum", ImplementationMode.DBT_MANAGED, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deliveryEvidenceTypesFreezeTheSevenStageSixEvidenceCategories() {
        assertThat(DeliveryEvidenceType.values()).containsExactly(
            DeliveryEvidenceType.ARTIFACT,
            DeliveryEvidenceType.BUILD_RUN,
            DeliveryEvidenceType.QUALITY_RUN,
            DeliveryEvidenceType.REVIEW,
            DeliveryEvidenceType.PUBLICATION,
            DeliveryEvidenceType.REGISTRATION,
            DeliveryEvidenceType.ROLLBACK
        );
    }

    @Test
    void deliveryViewExposesStatusActionsBlockerAndAuditableSeparationOfDuties() {
        DeliveryAuditView audit = new DeliveryAuditView(
            "creator",
            Instant.parse("2026-07-24T00:00:00Z"),
            "submitter",
            Instant.parse("2026-07-24T00:01:00Z"),
            null,
            null,
            null,
            null
        );

        DeliveryView view = DeliveryView.forStatus(DeliveryStatus.REVIEW_PENDING, "REVIEW_PENDING", audit);

        assertThat(view.status()).isEqualTo(DeliveryStatus.REVIEW_PENDING);
        assertThat(view.allowedActions()).containsExactly(
            DeliveryAction.APPROVE,
            DeliveryAction.REJECT,
            DeliveryAction.PUBLISH,
            DeliveryAction.CANCEL_CANDIDATE
        );
        assertThat(view.primaryBlockerCode()).isEqualTo("REVIEW_PENDING");
        assertThat(view.audit()).isEqualTo(audit);
        assertThat(DeliveryAction.APPROVE.requiredRole()).isEqualTo(DeliveryActorRole.RELEASE_REVIEWER);
        assertThat(DeliveryAction.APPROVE.isAllowedFor(DeliveryStatus.REVIEW_PENDING, DeliveryActorRole.RELEASE_REVIEWER, "submitter", audit)).isFalse();
        assertThat(DeliveryAction.APPROVE.isAllowedFor(DeliveryStatus.REVIEW_PENDING, DeliveryActorRole.RELEASE_REVIEWER, "approver", audit)).isTrue();
        assertThat(DeliveryAction.REJECT.isAllowedFor(DeliveryStatus.REVIEW_PENDING, DeliveryActorRole.RELEASE_REVIEWER, "submitter", audit)).isFalse();
        assertThat(DeliveryAction.REJECT.isAllowedFor(DeliveryStatus.REVIEW_PENDING, DeliveryActorRole.RELEASE_REVIEWER, "reviewer", audit)).isTrue();
        assertThat(DeliveryAction.APPROVE.isAllowedFor(DeliveryStatus.REVIEW_PENDING, DeliveryActorRole.MODEL_MAINTAINER, "approver", audit)).isFalse();
        assertThat(DeliveryAction.PUBLISH.requiredRole()).isEqualTo(DeliveryActorRole.RELEASE_OPERATOR);
        assertThat(DeliveryAction.PUBLISH.isAllowedFor(DeliveryStatus.DRAFT, DeliveryActorRole.RELEASE_OPERATOR, "publisher", audit)).isFalse();
        assertThat(DeliveryAction.PUBLISH.isAllowedFor(DeliveryStatus.APPROVED, DeliveryActorRole.RELEASE_OPERATOR, "submitter", audit)).isFalse();
        assertThat(DeliveryAction.PUBLISH.isAllowedFor(DeliveryStatus.APPROVED, DeliveryActorRole.RELEASE_OPERATOR, "publisher", audit)).isFalse();

        DeliveryAuditView approved = new DeliveryAuditView(
            "creator",
            Instant.parse("2026-07-24T00:00:00Z"),
            "submitter",
            Instant.parse("2026-07-24T00:01:00Z"),
            "reviewer",
            Instant.parse("2026-07-24T00:02:00Z"),
            null,
            null
        );
        assertThat(DeliveryAction.PUBLISH.isAllowedFor(DeliveryStatus.APPROVED, DeliveryActorRole.RELEASE_OPERATOR, "publisher", approved)).isTrue();
        assertThat(DeliveryAction.PUBLISH.isAllowedFor(DeliveryStatus.APPROVED, DeliveryActorRole.RELEASE_OPERATOR, "reviewer", approved)).isFalse();

        DeliveryAuditView unsubmitted = new DeliveryAuditView(
            "creator",
            Instant.parse("2026-07-24T00:00:00Z"),
            null,
            null,
            null,
            null,
            null,
            null
        );
        assertThat(DeliveryAction.APPROVE.isAllowedFor(DeliveryStatus.REVIEW_PENDING, DeliveryActorRole.RELEASE_REVIEWER, "approver", unsubmitted)).isFalse();
        assertThat(DeliveryAction.PUBLISH.isAllowedFor(DeliveryStatus.APPROVED, DeliveryActorRole.RELEASE_OPERATOR, "publisher", unsubmitted)).isFalse();
        assertThat(DeliveryAction.PUBLISH.isAllowedFor(DeliveryStatus.QUALITY_PASSED, DeliveryActorRole.RELEASE_OPERATOR, "owner", unsubmitted)).isTrue();
        assertThat(DeliveryAction.PUBLISH.isAllowedFor(DeliveryStatus.REVIEW_PENDING, DeliveryActorRole.RELEASE_OPERATOR, "submitter", audit)).isTrue();

        assertThatThrownBy(() -> new DeliveryView(DeliveryStatus.DRAFT, List.of(DeliveryAction.PUBLISH), "BLOCKED", audit))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("allowedActions");
    }

    @Test
    void everyDeliveryStatusHasAConsistentUiFixture() {
        DeliveryAuditView audit = new DeliveryAuditView(
            "creator",
            Instant.parse("2026-07-24T00:00:00Z"),
            null,
            null,
            null,
            null,
            null,
            null
        );

        for (DeliveryStatus status : DeliveryStatus.values()) {
            DeliveryView view = DeliveryView.forStatus(status, "BLOCKER_" + status.name(), audit);
            assertThat(view.status()).isEqualTo(status);
            assertThat(view.allowedActions()).containsExactlyElementsOf(status.allowedActions());
            assertThat(view.primaryBlockerCode()).isEqualTo("BLOCKER_" + status.name());
            assertThat(view.audit()).isEqualTo(audit);
        }
    }

    @Test
    void implementationInputModesAreTypedAndMutuallyExclusive() {
        UUID sourceBindingId = UUID.fromString("50000000-0000-0000-0000-000000000069");
        UUID upstreamModelId = UUID.fromString("60000000-0000-0000-0000-000000000069");

        SaveImplementationCommand physical = new SaveImplementationCommand(
            InputMode.PHYSICAL_ASSET,
            List.of(new PhysicalAssetInput(sourceBindingId, "source-v7")),
            List.of(new FieldMapping("source_id", "customer_id")),
            Map.of("casts", Map.of("customer_id", "string")),
            ImplementationMode.DBT_MANAGED,
            "table",
            "physical-input"
        );
        SaveImplementationCommand upstream = new SaveImplementationCommand(
            InputMode.UPSTREAM_MODEL,
            List.of(new UpstreamModelInput(upstreamModelId, 3, "a".repeat(64), 0, null, null)),
            List.of(),
            Map.of(),
            ImplementationMode.DBT_MANAGED,
            "view",
            "upstream-input"
        );
        SaveImplementationCommand generated = new SaveImplementationCommand(
            InputMode.GENERATED,
            List.of(new GeneratedInput("DIMENSION_COMPILER", Map.of("scd", "TYPE_2"))),
            List.of(),
            Map.of(),
            ImplementationMode.DESIGNER_GENERATED,
            "table",
            "generated-input"
        );

        assertThat(physical.inputs()).allMatch(input -> input.mode() == InputMode.PHYSICAL_ASSET);
        assertThat(upstream.inputs()).allMatch(input -> input.mode() == InputMode.UPSTREAM_MODEL);
        assertThat(generated.inputs()).allMatch(input -> input.mode() == InputMode.GENERATED);
        assertThatThrownBy(() -> new SaveImplementationCommand(
            InputMode.PHYSICAL_ASSET,
            List.of(new PhysicalAssetInput(sourceBindingId, "source-v7")),
            List.of(new FieldMapping("source_id", "customer_id")),
            Map.of("filter", "deleted = false"),
            ImplementationMode.DBT_MANAGED,
            "table",
            "free-sql-rejected"
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unsupported key");
        assertThatThrownBy(() -> new SaveImplementationCommand(
            InputMode.PHYSICAL_ASSET,
            List.of(new UpstreamModelInput(upstreamModelId, 3, "a".repeat(64), 0, null, null)),
            List.of(),
            Map.of(),
            ImplementationMode.DBT_MANAGED,
            "table",
            "mixed-input"
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("inputMode");
    }

    @Test
    void implementationWriteCommandsRequireCallerProvidedIdempotencyKeys() {
        assertThatThrownBy(() -> new ClaimImplementationCommand(
            ImplementationMode.DBT_MANAGED,
            "project",
            "model.project.customer",
            " "
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("idempotencyKey");
        assertThatThrownBy(() -> new SaveImplementationCommand(
            InputMode.GENERATED,
            List.of(new GeneratedInput("DATE_DIMENSION", Map.of())),
            List.of(),
            Map.of(),
            ImplementationMode.DESIGNER_GENERATED,
            "table",
            null
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("idempotencyKey");
    }

    @Test
    void saveImportConvertAndClaimNormalizeAndBoundCallerIdempotencyKeys() {
        assertThat(generatedCommandWithKey("  save-key  ").idempotencyKey()).isEqualTo("save-key");
        assertThat(new ClaimImplementationCommand(
            ImplementationMode.DBT_MANAGED,
            "project",
            "model.project.customer",
            "  claim-key  "
        ).idempotencyKey()).isEqualTo("claim-key");

        String tooLong = "k".repeat(129);
        for (String action : List.of("SAVE", "IMPORT", "OWNERSHIP_CONVERT")) {
            assertThatThrownBy(() -> generatedCommandWithKey(tooLong))
                .as(action)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("idempotencyKey")
                .hasMessageContaining("128");
        }
        assertThatThrownBy(() -> new ClaimImplementationCommand(
            ImplementationMode.DBT_MANAGED,
            "project",
            "model.project.customer",
            tooLong
        )).as("CLAIM")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("idempotencyKey")
            .hasMessageContaining("128");
    }

    private static SaveImplementationCommand generatedCommandWithKey(String idempotencyKey) {
        return new SaveImplementationCommand(
            InputMode.GENERATED,
            List.of(new GeneratedInput("DATE_DIMENSION", Map.of())),
            List.of(),
            Map.of(),
            ImplementationMode.DESIGNER_GENERATED,
            "table",
            idempotencyKey
        );
    }

    @Test
    void multiInputImplementationsRequireZeroBasedSystemAliasesAndCompleteJoinCoverage() {
        UUID first = UUID.fromString("50000000-0000-0000-0000-000000000069");
        UUID second = UUID.fromString("50000000-0000-0000-0000-000000000070");
        Map<String, Object> join = Map.of(
            "inputIndex", 1,
            "type", "LEFT",
            "leftField", "src_0.customer_id",
            "rightField", "src_1.customer_id"
        );

        SaveImplementationCommand command = new SaveImplementationCommand(
            InputMode.PHYSICAL_ASSET,
            List.of(new PhysicalAssetInput(first, "source-v7"), new PhysicalAssetInput(second, "source-v8")),
            List.of(new FieldMapping("src_0.customer_id", "customer_id")),
            Map.of("joins", List.of(join)),
            ImplementationMode.DESIGNER_GENERATED,
            "table",
            "multi-source"
        );

        assertThat(command.inputs()).hasSize(2);
        assertThat(command.settings()).containsEntry("joins", List.of(join));
        assertThatThrownBy(() -> new SaveImplementationCommand(
            InputMode.PHYSICAL_ASSET,
            command.inputs(),
            command.fieldMappings(),
            Map.of("joins", List.of(Map.of(
                "inputIndex", 2,
                "type", "LEFT",
                "leftField", "src_0.customer_id",
                "rightField", "src_2.customer_id"
            ))),
            command.ownership(),
            command.materialization(),
            "bad-multi-source"
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("inputIndex");
    }

    @Test
    void implementationOwnsPhysicalTargetLoadPartitionAndRetentionSettings() {
        UUID sourceBindingId = UUID.fromString("50000000-0000-0000-0000-000000000069");
        Map<String, Object> settings = Map.of(
            "targetPhysicalName", "dwd_finance_project",
            "loadStrategy", "INCREMENTAL",
            "partitionFields", List.of("business_date"),
            "retentionDays", 365
        );

        SaveImplementationCommand command = new SaveImplementationCommand(
            InputMode.PHYSICAL_ASSET,
            List.of(new PhysicalAssetInput(sourceBindingId, "source-v7")),
            List.of(new FieldMapping("project_id", "project_id")),
            settings,
            ImplementationMode.DESIGNER_GENERATED,
            "incremental",
            "implementation-settings"
        );

        assertThat(command.settings()).containsAllEntriesOf(settings);
        SaveImplementationCommand unpartitioned = new SaveImplementationCommand(
            command.inputMode(),
            command.inputs(),
            command.fieldMappings(),
            Map.of("targetPhysicalName", "dwd_finance_project", "loadStrategy", "FULL", "partitionFields", List.of()),
            command.ownership(),
            "table",
            "unpartitioned-target"
        );
        assertThat(unpartitioned.settings()).containsEntry("partitionFields", List.of());
        assertThatThrownBy(() -> new SaveImplementationCommand(
            command.inputMode(),
            command.inputs(),
            command.fieldMappings(),
            Map.of("targetPhysicalName", "DWD Finance Project", "loadStrategy", "FULL"),
            command.ownership(),
            "table",
            "invalid-target"
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("targetPhysicalName");
        assertThatThrownBy(() -> new SaveImplementationCommand(
            command.inputMode(),
            command.inputs(),
            command.fieldMappings(),
            Map.of("targetPhysicalName", "dwd_finance_project", "loadStrategy", "FULL", "retentionDays", 36_001),
            command.ownership(),
            "table",
            "invalid-retention"
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("retentionDays");
    }
}
