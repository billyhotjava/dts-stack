package com.yuzhi.dts.platform.service.services.dto;

import java.util.List;
import java.util.Map;

public record ApiTryInvokeResponseDto(
    List<String> columns,
    List<String> maskedColumns,
    List<Map<String, Object>> rows,
    long filteredRowCount,
    List<String> policyHits
) {}
