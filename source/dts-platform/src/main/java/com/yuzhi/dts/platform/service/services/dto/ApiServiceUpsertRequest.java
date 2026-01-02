package com.yuzhi.dts.platform.service.services.dto;

import java.util.UUID;

/**
 * Admin/maintainer request to create/update an API service definition.
 */
public record ApiServiceUpsertRequest(
    String code,
    String name,
    UUID datasetId,
    String method,
    String path,
    String classification,
    Integer qpsLimit,
    Integer dailyLimit,
    Object policy,
    Object requestSchema,
    Object responseSchema,
    String tags,
    String description
) {}

