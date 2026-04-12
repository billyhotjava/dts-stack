package com.yuzhi.dts.admin.web.rest.testapi;

import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.jhipster.config.JHipsterConstants;

/**
 * Test helper endpoints exposed under /test/**. Intended to be called by the
 * external API test suites (tests/api-e2e-python, tests/api-e2e-java).
 *
 * <p>Each endpoint requires {@code X-Test-Token}. Gated by
 * {@code app.test-api.enabled} and disabled on prod profile.
 */
@RestController
@RequestMapping("/test")
@ConditionalOnProperty(name = "app.test-api.enabled", havingValue = "true")
@Profile("!" + JHipsterConstants.SPRING_PROFILE_PRODUCTION)
public class TestApiResource {

    private static final Logger LOG = LoggerFactory.getLogger(TestApiResource.class);
    private static final String MODULE = "admin";

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of("status", "ok", "module", MODULE, "ts", Instant.now().toString());
    }

    @PostMapping("/reset-data")
    public ResponseEntity<Map<String, Object>> resetData() {
        LOG.warn("[test-api] reset-data called (stub)");
        return ResponseEntity
            .status(HttpStatus.NOT_IMPLEMENTED)
            .body(Map.of("status", "stub", "module", MODULE, "hint", "implement per admin schema"));
    }

    @PostMapping("/seed/{scenario}")
    public ResponseEntity<Map<String, Object>> seed(@PathVariable("scenario") String scenario) {
        LOG.warn("[test-api] seed scenario={} (stub)", scenario);
        return ResponseEntity
            .status(HttpStatus.NOT_IMPLEMENTED)
            .body(Map.of("status", "stub", "module", MODULE, "scenario", scenario));
    }

    @PostMapping("/clock/advance")
    public ResponseEntity<Map<String, Object>> advanceClock(@RequestBody(required = false) Map<String, Object> body) {
        LOG.warn("[test-api] clock/advance body={} (stub)", body);
        return ResponseEntity
            .status(HttpStatus.NOT_IMPLEMENTED)
            .body(Map.of("status", "stub", "module", MODULE));
    }
}
