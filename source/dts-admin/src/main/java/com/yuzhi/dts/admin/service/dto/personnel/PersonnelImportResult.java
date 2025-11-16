package com.yuzhi.dts.admin.service.dto.personnel;

public record PersonnelImportResult(
    Long batchId,
    String status,
    int totalRecords,
    int successRecords,
    int failureRecords,
    int skippedRecords,
    boolean dryRun
) {}
