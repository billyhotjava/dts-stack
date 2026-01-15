package com.yuzhi.dts.analytics.web.rest;

import com.yuzhi.dts.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.analytics.web.support.MetabaseAuth;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform")
public class PlatformIntegrationResource {

    private final AnalyticsSessionService sessionService;

    public PlatformIntegrationResource(AnalyticsSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping(path = "/metrics", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> metrics(HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.ok(List.of());
    }

    @GetMapping(path = "/visible-tables", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> visibleTables(HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.ok(List.of());
    }

    @GetMapping(path = "/context", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> context(HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        String dept = request.getHeader("X-DTS-Dept");
        String level = request.getHeader("X-DTS-Classification");
        String roles = request.getHeader("X-DTS-Roles");
        return ResponseEntity.ok(Map.of(
                "dept", dept,
                "classification", level,
                "roles", roles));
    }
}

