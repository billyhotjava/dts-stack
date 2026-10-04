package com.yuzhi.dts.copilot.analytics.web.rest;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.copilot.analytics.service.ReportTemplateCatalogService;
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
@RequestMapping("/api/report-catalog")
public class ReportTemplateCatalogResource {

    private final AnalyticsSessionService sessionService;
    private final ReportTemplateCatalogService catalogService;

    public ReportTemplateCatalogResource(
            AnalyticsSessionService sessionService,
            ReportTemplateCatalogService catalogService) {
        this.sessionService = sessionService;
        this.catalogService = catalogService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> listTemplates(
            @RequestParam(name = "domain", required = false) String domain,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "limit", required = false, defaultValue = "100") int limit,
            HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        return ResponseEntity.ok(catalogService.listTemplates(domain, category, limit));
    }

    @GetMapping(path = "/{templateCode}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getTemplate(
            @PathVariable("templateCode") String templateCode,
            HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        return catalogService.getTemplate(templateCode)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
