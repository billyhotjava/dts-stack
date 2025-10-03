package com.yuzhi.dts.platform.service.services.dto;

import java.time.Instant;
import java.util.List;

public record DataProductVersionDto(
    String version,
    String status,
    Instant releasedAt,
    String diffSummary,
    List<DataProductFieldDto> fields,
    DataProductConsumptionDto consumption,
    DataProductMetadataDto metadata
) {}
