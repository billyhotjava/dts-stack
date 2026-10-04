package com.yuzhi.dts.platform.service.services.dto;

import java.util.List;
import java.util.UUID;

public record DataProductUpsertRequest(
    String code,
    String name,
    String productType,
    String classification,
    String status,
    String sla,
    String refreshFrequency,
    String latencyObjective,
    String failurePolicy,
    String description,
    List<DataProductDatasetRequest> datasets
) {
    public record DataProductDatasetRequest(UUID datasetId, String datasetName) {}
}
