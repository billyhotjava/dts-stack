package com.yuzhi.dts.platform.service.governance.dto;

import java.util.UUID;

public record QualityDatasetOptionDto(
    UUID id,
    String name,
    String schemaName,
    String tableName,
    String hiveDatabase,
    String hiveTable,
    UUID sourceId,
    UUID domainId,
    String domainName
) {}
