package com.yuzhi.dts.platform.service.catalog.dto;

public record CatalogTagMigrationExecution(
    String batchId,
    String checksum,
    String status,
    boolean replayed,
    long matched,
    long unmatched,
    long ambiguous,
    long protectedEvidence,
    long created,
    long skipped
) {}
