package com.yuzhi.dts.ingestion.service.dto;

import java.time.Instant;

public record IngestionTaskRevisionDTO(
    Integer revisionNumber,
    String revisionState,
    String sourceKind,
    String effectiveConfigChecksum,
    Integer defaultPolicyVersion,
    String defaultPolicyChecksum,
    String qualityPolicyRef,
    String createdBy,
    Instant createdAt,
    String activatedBy,
    Instant activatedAt,
    String dagDeploymentStatus,
    String dagDeploymentError,
    Instant dagDeploymentUpdatedAt
) {}
