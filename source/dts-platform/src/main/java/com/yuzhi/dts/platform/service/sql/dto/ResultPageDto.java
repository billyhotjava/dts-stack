package com.yuzhi.dts.platform.service.sql.dto;

import java.util.List;
import java.util.Map;

public record ResultPageDto(
    List<Map<String, Object>> rows,
    List<ColumnMetaDto> columns,
    int page,
    int pageSize,
    long total,
    boolean truncated
) {}
