package com.yuzhi.dts.copilot.analytics.web.rest;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.copilot.analytics.service.CopilotAdminClient;
import com.yuzhi.dts.copilot.analytics.service.SetupStateService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/admin/copilot")
public class CopilotAdminResource {

    private final AnalyticsSessionService sessionService;
    private final SetupStateService setupStateService;
    private final CopilotAdminClient copilotAdminClient;

    public CopilotAdminResource(
            AnalyticsSessionService sessionService,
            SetupStateService setupStateService,
            CopilotAdminClient copilotAdminClient) {
        this.sessionService = sessionService;
        this.setupStateService = setupStateService;
        this.copilotAdminClient = copilotAdminClient;
    }

    @GetMapping(path = "/settings/site", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getSiteSettings(HttpServletRequest request) {
        ResponseEntity<?> auth = requireSuperuser(request);
        if (auth != null) {
            return auth;
        }
        return ResponseEntity.ok(Map.of("siteName", setupStateService.getSiteName().orElse("")));
    }

    @PutMapping(path = "/settings/site", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateSiteSettings(@RequestBody SiteSettingsRequest body, HttpServletRequest request) {
        ResponseEntity<?> auth = requireSuperuser(request);
        if (auth != null) {
            return auth;
        }
        String siteName = body == null ? "" : Objects.toString(body.siteName(), "").trim();
        setupStateService.setSiteName(siteName);
        return ResponseEntity.ok(Map.of("siteName", siteName));
    }

    @GetMapping(path = "/providers", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> listProviders(HttpServletRequest request) {
        ResponseEntity<?> auth = requireSuperuser(request);
        if (auth != null) {
            return auth;
        }
        return proxy(() -> copilotAdminClient.listProviders());
    }

    @GetMapping(path = "/providers/templates", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> listProviderTemplates(HttpServletRequest request) {
        ResponseEntity<?> auth = requireSuperuser(request);
        if (auth != null) {
            return auth;
        }
        return proxy(() -> copilotAdminClient.listProviderTemplates());
    }

    @PostMapping(path = "/providers", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createProvider(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        ResponseEntity<?> auth = requireSuperuser(request);
        if (auth != null) {
            return auth;
        }
        return proxy(() -> copilotAdminClient.createProvider(body));
    }

    @PutMapping(path = "/providers/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateProvider(@PathVariable Long id,
                                            @RequestBody Map<String, Object> body,
                                            HttpServletRequest request) {
        ResponseEntity<?> auth = requireSuperuser(request);
        if (auth != null) {
            return auth;
        }
        return proxy(() -> copilotAdminClient.updateProvider(id, body));
    }

    @DeleteMapping(path = "/providers/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> deleteProvider(@PathVariable Long id, HttpServletRequest request) {
        ResponseEntity<?> auth = requireSuperuser(request);
        if (auth != null) {
            return auth;
        }
        try {
            copilotAdminClient.deleteProvider(id);
            return ResponseEntity.ok(Map.of("id", id, "deleted", true));
        } catch (RestClientException ex) {
            return proxyFailure(ex);
        }
    }

    @PostMapping(path = "/providers/{id}/test", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> testProvider(@PathVariable Long id, HttpServletRequest request) {
        ResponseEntity<?> auth = requireSuperuser(request);
        if (auth != null) {
            return auth;
        }
        return proxy(() -> copilotAdminClient.testProvider(id));
    }

    @GetMapping(path = "/api-keys", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> listApiKeys(HttpServletRequest request) {
        ResponseEntity<?> auth = requireSuperuser(request);
        if (auth != null) {
            return auth;
        }
        return proxy(() -> copilotAdminClient.listApiKeys());
    }

    @PostMapping(path = "/api-keys", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createApiKey(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        ResponseEntity<?> auth = requireSuperuser(request);
        if (auth != null) {
            return auth;
        }
        return proxy(() -> copilotAdminClient.createApiKey(body));
    }

    @PutMapping(path = "/api-keys/{id}/rotate", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> rotateApiKey(@PathVariable Long id, HttpServletRequest request) {
        ResponseEntity<?> auth = requireSuperuser(request);
        if (auth != null) {
            return auth;
        }
        return proxy(() -> copilotAdminClient.rotateApiKey(id));
    }

    @DeleteMapping(path = "/api-keys/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> revokeApiKey(@PathVariable Long id, HttpServletRequest request) {
        ResponseEntity<?> auth = requireSuperuser(request);
        if (auth != null) {
            return auth;
        }
        try {
            copilotAdminClient.revokeApiKey(id);
            return ResponseEntity.ok(Map.of("id", id, "revoked", true));
        } catch (RestClientException ex) {
            return proxyFailure(ex);
        }
    }

    private ResponseEntity<?> requireSuperuser(HttpServletRequest request) {
        AnalyticsUser user = sessionService.resolveUser(request).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Unauthenticated");
        }
        if (!user.isSuperuser()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("You don't have permissions to do that.");
        }
        return null;
    }

    private ResponseEntity<?> proxy(ResponseSupplier supplier) {
        try {
            return ResponseEntity.ok(supplier.get());
        } catch (RestClientException ex) {
            return proxyFailure(ex);
        }
    }

    private ResponseEntity<Map<String, Object>> proxyFailure(RestClientException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Copilot configuration service unavailable");
        body.put("detail", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body);
    }

    public record SiteSettingsRequest(String siteName) {}

    @FunctionalInterface
    private interface ResponseSupplier {
        Object get();
    }
}
