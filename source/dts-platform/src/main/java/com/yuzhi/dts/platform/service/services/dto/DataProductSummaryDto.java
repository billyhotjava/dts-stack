package com.yuzhi.dts.platform.service.services.dto;

import java.util.List;
import java.util.UUID;

public record DataProductSummaryDto(
    UUID id,
    String code,
    String name,
    String productType,
    String classification,
    String status,
    String sla,
    String refreshFrequency,
    String currentVersion,
    int subscriptions,
    List<String> datasets
) {}
