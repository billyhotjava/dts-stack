package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Revision-bound contracts for implementation, verification, release and registration. */
public final class ModelLifecycleContract {

    private ModelLifecycleContract() {}

    public enum EventType {
        COMPILE,
        TEST,
        REVIEW_SUBMITTED,
        REVIEW_APPROVED,
        RELEASE,
        ROLLBACK,
        RUN,
    }

    public enum RegistrationStep {
        CATALOG_ASSET,
        BI_DATASET,
        LINEAGE,
    }

    public record ClaimImplementationCommand(
        ImplementationMode ownership,
        String projectKey,
        String dbtUniqueId,
        String idempotencyKey
    ) {}

    public record TestEvidenceCommand(String status, String externalRunId, String comment, String idempotencyKey) {}

    public record ReviewCommand(String comment, String idempotencyKey) {}

    public record PublishCommand(String comment, String idempotencyKey) {}

    public record RollbackCommand(String comment, String idempotencyKey) {}

    public record RunCommand(
        String idempotencyKey,
        String sourceBatchId,
        String addaxTaskId,
        String airflowDagId,
        String airflowRunId,
        String dbtRunId,
        String dbtSelector,
        String targetTable
    ) {}

    public record ImplementationView(
        UUID id,
        UUID modelSpecId,
        UUID planId,
        int revision,
        String modelChecksum,
        ImplementationMode ownership,
        String projectKey,
        String dbtUniqueId,
        String status
    ) {}

    public record ArtifactView(
        UUID id,
        UUID modelSpecId,
        UUID planId,
        int revision,
        String modelChecksum,
        ImplementationMode ownership,
        String artifactType,
        String path,
        String checksum,
        String status
    ) {}

    public record ArtifactWrite(String artifactType, String path, String checksum, String content) {}

    public record LifecycleEventView(
        UUID id,
        UUID modelSpecId,
        UUID planId,
        int revision,
        String modelChecksum,
        EventType eventType,
        String status,
        String idempotencyKey,
        String actorId,
        String comment,
        String externalRef,
        Map<String, Object> details,
        Instant createdAt
    ) {}

    public record RegistrationStepView(
        UUID id,
        UUID releaseEventId,
        RegistrationStep step,
        String status,
        String externalRef,
        int attemptCount,
        String errorMessage,
        Instant lastAttemptAt
    ) {}

    public record CompileView(
        ImplementationView implementation,
        LifecycleEventView event,
        List<ArtifactView> artifacts
    ) {}

    public record ReleaseView(
        LifecycleEventView release,
        String status,
        int publishedRevision,
        String modelChecksum,
        List<RegistrationStepView> registrations
    ) {}

    public record TimelineView(
        ImplementationView implementation,
        List<ArtifactView> artifacts,
        List<LifecycleEventView> events
    ) {}
}
