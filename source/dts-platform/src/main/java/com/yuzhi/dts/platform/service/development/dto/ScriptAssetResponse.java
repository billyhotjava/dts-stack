package com.yuzhi.dts.platform.service.development.dto;

import java.time.Instant;
import java.util.UUID;

public record ScriptAssetResponse(
    UUID id,
    String name,
    String description,
    String scriptType,
    String status,
    Integer latestVersionNo,
    String ownerDept,
    Boolean enabled,
    String latestContent,
    String createdBy,
    Instant createdDate,
    Instant lastModifiedDate
) {}
