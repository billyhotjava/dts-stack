package com.yuzhi.dts.platform.service.services.dto;

import java.util.List;

public record DataProductVersionRequest(
    String version,
    String status,
    String diffSummary,
    List<DataProductFieldDto> fields,
    DataProductConsumptionDto consumption,
    DataProductMetadataDto metadata
) {}
