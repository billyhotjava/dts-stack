package com.yuzhi.dts.analytics.web.rest;

import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthResource {

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> payload = Map.of(
                "status", "UP",
                "timestamp", OffsetDateTime.now().toString());
        return ResponseEntity.ok(payload);
    }
}
