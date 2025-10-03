package com.yuzhi.dts.platform.service.iam.dto;

import java.util.UUID;

public record DatasetClassificationDto(
    UUID id,
    String name,
    String domain,
    String owner,
    String classification
) {}
