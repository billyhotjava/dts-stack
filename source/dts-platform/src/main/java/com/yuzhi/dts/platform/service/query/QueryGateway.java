package com.yuzhi.dts.platform.service.query;

import java.util.Map;
import java.util.UUID;

public interface QueryGateway {

    /** Strict datasource and bound parameters; unsupported adapters must fail closed. */
    default Map<String, Object> executeBound(String sql, UUID datasourceId, java.util.List<Object> parameters, int limit) {
        throw new UnsupportedOperationException("当前查询适配器不支持指标参数化查询");
    }


    /**
     * Execute a read-only query against a specific datasource, associating the execution with
     * the given {@code executionId} for chunk persistence and cancellation support.
     * This is the primary method; all overloads delegate here.
     * Implementations should enforce read-only semantics.
     */
    Map<String, Object> execute(String effectiveSql, UUID datasourceId, UUID executionId);

    /**
     * Execute a read-only query against a specific datasource.
     * If datasourceId is null, falls back to default execution.
     *
     * @deprecated Prefer the 3-arg form. This shim synthesises a random executionId so chunk
     *     persistence is not wired to any tracked execution — use only for legacy callers that
     *     do not have an executionId in scope.
     *     TODO: migrate remaining call sites to pass a real executionId.
     */
    @Deprecated
    default Map<String, Object> execute(String effectiveSql, UUID datasourceId) {
        return execute(effectiveSql, datasourceId, UUID.randomUUID());
    }

    /**
     * Execute a read-only query using the default datasource.
     *
     * @deprecated Prefer the 3-arg form. This shim synthesises a random executionId.
     *     TODO: migrate remaining call sites to pass a real executionId.
     */
    @Deprecated
    default Map<String, Object> execute(String effectiveSql) {
        return execute(effectiveSql, null, UUID.randomUUID());
    }
}

