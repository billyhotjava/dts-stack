package com.yuzhi.dts.platform.service.sql.dto;

import java.util.List;
import java.util.Map;

public record PlanNodeDto(
    String id,
    String operator,
    String table,
    Double estimatedRows,
    Double estimatedCost,
    Map<String, String> attributes,
    List<PlanNodeDto> children
) {}
