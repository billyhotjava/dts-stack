package com.yuzhi.dts.platform.service.iam.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserClassificationDto(
    UUID id,
    String username,
    String displayName,
    List<String> orgPath,
    List<String> roles,
    List<String> projects,
    String securityLevel,
    Instant updatedAt
) {}
