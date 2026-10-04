package com.yuzhi.dts.opmanager.job;

public record UpgradeJob(
    String id,
    String packageRegistrationId,
    String packageId,
    String version,
    UpgradeJobState state,
    String createdAt,
    String updatedAt
) {}
