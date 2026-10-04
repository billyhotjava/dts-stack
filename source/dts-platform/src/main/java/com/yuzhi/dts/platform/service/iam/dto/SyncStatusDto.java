package com.yuzhi.dts.platform.service.iam.dto;

import java.time.Instant;
import java.util.List;

public record SyncStatusDto(
    Instant lastSyncAt,
    int deltaCount,
    List<SyncFailureDto> failures
) {}
