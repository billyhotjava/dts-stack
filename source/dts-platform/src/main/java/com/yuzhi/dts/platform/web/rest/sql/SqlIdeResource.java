package com.yuzhi.dts.platform.web.rest.sql;

import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for the SQL IDE v2 feature (Sprint-11 F1/T02).
 *
 * <p>Endpoints are always exposed — no backend flag-gating is applied here.
 * Frontend flag {@code WEBAPP_ENABLE_SQL_IDE_V2} (surfaced as
 * {@code window.__RUNTIME_CONFIG__.enableSqlIdeV2}) controls whether users
 * can navigate to SqlIdePage. This asymmetry is intentional and documented in
 * {@link com.yuzhi.dts.platform.config.SqlIdeFeatureProperties}.
 */
@RestController
@RequestMapping("/api/sql/v2")
public class SqlIdeResource {

    @GetMapping("/ping")
    public ApiResponse<Map<String, String>> ping() {
        return ApiResponses.ok(Map.of("status", "ok", "version", "v2-skeleton"));
    }
}
