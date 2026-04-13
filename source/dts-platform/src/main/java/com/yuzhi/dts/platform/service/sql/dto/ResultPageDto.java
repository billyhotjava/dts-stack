package com.yuzhi.dts.platform.service.sql.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ResultPageDto(
    UUID executionId,
    long from,
    long to,
    long totalRows,
    boolean hasMore,
    List<String> headers,
    List<Map<String, Object>> rows
) {}
