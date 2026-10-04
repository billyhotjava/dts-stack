package com.yuzhi.dts.platform.service.catalog.dto;

public record CatalogTagMigrationRollback(
    String batchId,
    String checksum,
    String status,
    boolean replayed,
    long matched,
    long unmatched,
    long ambiguous,
    long protectedEvidence,
    long created,
    long skipped,
    long deleted
) {}
