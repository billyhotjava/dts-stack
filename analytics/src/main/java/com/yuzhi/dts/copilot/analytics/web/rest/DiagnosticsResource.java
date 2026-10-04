package com.yuzhi.dts.copilot.analytics.web.rest;

import com.yuzhi.dts.copilot.analytics.web.support.CurrentRequestContext;
import com.yuzhi.dts.copilot.analytics.web.support.RequestContext;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DiagnosticsResource {

    @GetMapping("/echo")
    public ResponseEntity<Map<String, Object>> echo(@RequestParam("q") String query) {
        Map<String, Object> payload = Map.of(
                "echo", query,
                "timestamp", OffsetDateTime.now().toString());
        return ResponseEntity.ok(payload);
    }

    @GetMapping("/request-context")
    public ResponseEntity<RequestContext> requestContext(@CurrentRequestContext RequestContext context) {
        return ResponseEntity.ok(context);
    }
}
