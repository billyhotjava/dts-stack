package com.yuzhi.dts.platform.service.sql;

import com.yuzhi.dts.platform.service.sql.dto.TempViewDto;
import java.util.Map;
import java.util.UUID;

public interface SqlSubqueryService {
    /** Creates an UNLOGGED table from execution result; returns the table metadata. */
    TempViewDto createTempView(UUID executionId);
    /** Executes a SELECT against a previously created temp view. */
    Map<String, Object> executeOnView(String viewName, String sql);
    void dropView(String viewName);
    int cleanupExpired();
}
