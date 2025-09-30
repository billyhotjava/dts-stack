package com.yuzhi.dts.platform.service.query;

import java.util.Map;

public interface QueryGateway {

    /**
     * Execute a read-only query and return a simple tabular payload.
     * Implementations should enforce read-only semantics.
     */
    Map<String, Object> execute(String effectiveSql);
}

