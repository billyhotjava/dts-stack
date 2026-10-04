package com.yuzhi.dts.platform.service.sql.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SqlResultPageResponse(
    UUID executionId,
    UUID resultSetId,
    Integer page,
    Integer pageSize,
    Long totalRows,
    Integer totalPages,
    boolean hasNext,
    List<String> headers,
    List<Map<String, Object>> rows
) {}
