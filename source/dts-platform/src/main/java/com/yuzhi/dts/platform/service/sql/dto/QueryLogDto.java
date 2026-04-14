package com.yuzhi.dts.platform.service.sql.dto;

import java.time.Instant;
import java.util.UUID;

public record QueryLogDto(
    UUID executionId,
    String originalSql,
    String rewrittenSql,
    String status,
    Instant startedAt,
    Instant finishedAt,
    Long rowCount,
    Long elapsedMs,
    String errorMessage,
    Long bytesProcessed
) {}
