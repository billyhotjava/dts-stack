package com.yuzhi.dts.platform.service.services.dto;

import java.time.Instant;
import java.util.UUID;

public record TokenInfoDto(
    UUID id,
    String tokenHint,
    Instant expiresAt,
    boolean revoked,
    Instant createdAt
) {}
