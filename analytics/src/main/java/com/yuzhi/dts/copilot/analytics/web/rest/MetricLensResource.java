package com.yuzhi.dts.copilot.analytics.web.rest;

import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.copilot.analytics.service.MetricLensService;
import com.yuzhi.dts.copilot.analytics.web.support.MetabaseAuth;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/metric-lens")
public class MetricLensResource {

    private final AnalyticsSessionService sessionService;
    private final MetricLensService metricLensService;

    public MetricLensResource(
            AnalyticsSessionService sessionService,
            MetricLensService metricLensService) {
        this.sessionService = sessionService;
        this.metricLensService = metricLensService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> list(HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.ok(metricLensService.list());
    }

    @GetMapping(path = "/{metricId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> detail(@PathVariable("metricId") long metricId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.ok(metricLensService.get(metricId));
    }

    @GetMapping(path = "/{metricId}/compare", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> compare(
            @PathVariable("metricId") long metricId,
            @RequestParam(name = "leftVersion") String leftVersion,
            @RequestParam(name = "rightVersion") String rightVersion,
            HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.ok(metricLensService.compareVersions(metricId, leftVersion, rightVersion));
    }

    @GetMapping(path = "/conflicts", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> conflicts(HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.ok(metricLensService.detectConflicts());
    }
}
