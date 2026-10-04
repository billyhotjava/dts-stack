package com.yuzhi.dts.platform.service.sql.dto;

import java.util.List;
import java.util.Map;

public record SqlResultPreview(
    List<String> headers,
    List<Map<String, Object>> rows,
    Long rowCount,
    boolean truncated
) {}
