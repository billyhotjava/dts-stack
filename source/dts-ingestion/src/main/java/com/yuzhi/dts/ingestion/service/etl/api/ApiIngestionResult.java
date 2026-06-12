package com.yuzhi.dts.ingestion.service.etl.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ApiIngestionResult(
    boolean success,
    Long rowsRead,
    Long rowsWritten,
    String errorMessage,
    Map<String, Object> metrics
) {
    public ApiIngestionResult {
        metrics = metrics == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(metrics));
    }

    public static ApiIngestionResult success(Long rowsRead, Long rowsWritten, Map<String, Object> metrics) {
        return new ApiIngestionResult(true, rowsRead, rowsWritten, null, metrics);
    }

    public static ApiIngestionResult failed(String errorMessage, Map<String, Object> metrics) {
        return new ApiIngestionResult(false, 0L, 0L, errorMessage, metrics);
    }
}
