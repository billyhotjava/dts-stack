package com.yuzhi.dts.copilot.analytics.web.rest;

import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.copilot.analytics.service.PlatformIndicatorClient;
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
@RequestMapping("/api/platform/indicators")
public class PlatformIndicatorResource {

    private final AnalyticsSessionService sessionService;
    private final PlatformIndicatorClient indicatorClient;

    public PlatformIndicatorResource(
            AnalyticsSessionService sessionService,
            PlatformIndicatorClient indicatorClient) {
        this.sessionService = sessionService;
        this.indicatorClient = indicatorClient;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> indicators(HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.ok(indicatorClient.listIndicators());
    }

    @GetMapping(path = "/dashboard", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> dashboard(
            @RequestParam(name = "days", required = false) Integer days,
            HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.ok(indicatorClient.getDashboard(days));
    }

    @GetMapping(path = "/{indicatorId}/detail", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> detail(
            @PathVariable("indicatorId") String indicatorId,
            @RequestParam(name = "days", required = false) Integer days,
            HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.ok(indicatorClient.getDetail(indicatorId, days));
    }

    @GetMapping(path = "/{indicatorId}/drilldown", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> drilldown(
            @PathVariable("indicatorId") String indicatorId,
            @RequestParam("dimension") String dimension,
            @RequestParam(name = "period", required = false) String period,
            HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.ok(indicatorClient.drilldown(indicatorId, dimension, period));
    }
}
