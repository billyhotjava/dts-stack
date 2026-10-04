package com.yuzhi.dts.platform.service.sql.dto;

import java.time.Instant;
import java.util.UUID;

public record QueryHistoryItemDto(
    UUID id,
    String sqlText,
    String engine,
    String connection,
    String status,
    Instant startedAt,
    Instant finishedAt,
    Long rowCount,
    Long elapsedMs,
    String createdBy
) {}
