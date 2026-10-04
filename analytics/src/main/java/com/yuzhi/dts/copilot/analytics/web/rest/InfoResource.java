package com.yuzhi.dts.copilot.analytics.web.rest;

import com.yuzhi.dts.copilot.analytics.config.ApplicationProperties;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class InfoResource {

    private final ApplicationProperties properties;

    public InfoResource(ApplicationProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> info() {
        ApplicationProperties.Service service = properties.service();
        Map<String, Object> payload = Map.of(
                "name", service.name(),
                "version", service.version(),
                "environment", service.environment(),
                "timestamp", OffsetDateTime.now().toString());
        return ResponseEntity.ok(payload);
    }
}
