package com.yuzhi.dts.admin.web.rest.dto.personnel;

import java.time.Instant;

public record PersonnelBatchView(
    Long id,
    String sourceType,
    String status,
    Integer totalRecords,
    Integer successRecords,
    Integer failureRecords,
    Integer skippedRecords,
    boolean dryRun,
    String reference,
    Instant startedAt,
    Instant completedAt,
    String errorMessage
) {}
