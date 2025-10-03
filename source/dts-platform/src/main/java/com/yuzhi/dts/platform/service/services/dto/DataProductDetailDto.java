package com.yuzhi.dts.platform.service.services.dto;

import java.util.List;
import java.util.UUID;

public record DataProductDetailDto(
    UUID id,
    String code,
    String name,
    String productType,
    String classification,
    String status,
    String sla,
    String refreshFrequency,
    String latencyObjective,
    String failurePolicy,
    int subscriptions,
    List<String> datasets,
    List<DataProductVersionDto> versions,
    String description
) {}
