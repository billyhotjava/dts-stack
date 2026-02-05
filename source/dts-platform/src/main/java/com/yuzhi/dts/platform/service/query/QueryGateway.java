package com.yuzhi.dts.platform.service.query;

import java.util.Map;
import java.util.UUID;

public interface QueryGateway {

    /**
     * Execute a read-only query and return a simple tabular payload.
     * Implementations should enforce read-only semantics.
     */
    Map<String, Object> execute(String effectiveSql);

    /**
     * Execute a read-only query against a specific datasource.
     * If datasourceId is null, falls back to default execution.
     */
    default Map<String, Object> execute(String effectiveSql, UUID datasourceId) {
        return execute(effectiveSql);
    }
}

