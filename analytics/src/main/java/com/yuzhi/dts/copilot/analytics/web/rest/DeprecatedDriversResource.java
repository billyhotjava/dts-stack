package com.yuzhi.dts.copilot.analytics.web.rest;

import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.copilot.analytics.web.support.MetabaseAuth;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DeprecatedDriversResource {

    private final AnalyticsSessionService sessionService;

    public DeprecatedDriversResource(AnalyticsSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping(path = "/db-ids-with-deprecated-drivers", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> get(HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.ok(List.of());
    }
}

