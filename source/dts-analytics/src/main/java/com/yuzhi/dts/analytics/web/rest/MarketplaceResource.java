package com.yuzhi.dts.analytics.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.analytics.service.MarketplaceService;
import com.yuzhi.dts.analytics.web.support.MetabaseAuth;
import com.yuzhi.dts.analytics.web.support.PlatformContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/marketplace")
@Transactional
public class MarketplaceResource {

    private final AnalyticsSessionService sessionService;
    private final MarketplaceService marketplaceService;

    public MarketplaceResource(AnalyticsSessionService sessionService, MarketplaceService marketplaceService) {
        this.sessionService = sessionService;
        this.marketplaceService = marketplaceService;
    }

    @GetMapping(path = "/components", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> listComponents(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "category", required = false) String category,
            HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return unauthorized();
        }
        return ResponseEntity.ok(marketplaceService.listComponents(search, category));
    }

    @PostMapping(path = "/components/{id}/install", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> installComponent(
            @PathVariable("id") String id,
            @RequestBody(required = false) JsonNode body,
            HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return unauthorized();
        }
        Object result = marketplaceService.installComponent(id, user.get().getId());
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping(path = "/templates", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> listTemplates(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "category", required = false) String category,
            HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return unauthorized();
        }
        return ResponseEntity.ok(marketplaceService.listTemplates(user.get(), PlatformContext.from(request), search, category));
    }

    @PostMapping(path = "/templates/{id}/install", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> installTemplate(
            @PathVariable("id") long id,
            @RequestBody(required = false) JsonNode body,
            HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return unauthorized();
        }
        MarketplaceService.TemplateInstallResult result =
                marketplaceService.installTemplate(id, user.get(), PlatformContext.from(request));
        if (result.forbidden()) {
            return forbidden();
        }
        if (result.payload() == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result.payload());
    }

    private ResponseEntity<String> unauthorized() {
        return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthorized");
    }

    private ResponseEntity<String> forbidden() {
        return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("Forbidden");
    }
}
