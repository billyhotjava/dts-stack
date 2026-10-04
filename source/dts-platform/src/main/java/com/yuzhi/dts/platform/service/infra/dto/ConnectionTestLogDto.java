package com.yuzhi.dts.platform.service.infra.dto;

import java.time.Instant;
import java.util.UUID;

public record ConnectionTestLogDto(
    UUID id,
    UUID dataSourceId,
    String result,
    String message,
    Integer elapsedMs,
    Instant createdAt
) {}
