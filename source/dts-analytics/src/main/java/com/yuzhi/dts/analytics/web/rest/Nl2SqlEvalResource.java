package com.yuzhi.dts.analytics.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.yuzhi.dts.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.analytics.service.Nl2SqlEvalService;
import com.yuzhi.dts.analytics.web.support.MetabaseAuth;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/nl2sql-eval")
public class Nl2SqlEvalResource {

    private final AnalyticsSessionService sessionService;
    private final Nl2SqlEvalService nl2SqlEvalService;

    public Nl2SqlEvalResource(
            AnalyticsSessionService sessionService,
            Nl2SqlEvalService nl2SqlEvalService) {
        this.sessionService = sessionService;
        this.nl2SqlEvalService = nl2SqlEvalService;
    }

    @GetMapping(path = "/cases", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> listCases(
            @RequestParam(name = "enabledOnly", required = false, defaultValue = "false") boolean enabledOnly,
            @RequestParam(name = "limit", required = false, defaultValue = "200") int limit,
            HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.ok(nl2SqlEvalService.listCases(enabledOnly, limit));
    }

    @PostMapping(path = "/cases", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createCase(@RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.ok(nl2SqlEvalService.saveCase(null, body));
    }

    @PutMapping(path = "/cases/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateCase(
            @PathVariable("id") long caseId,
            @RequestBody(required = false) JsonNode body,
            HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.ok(nl2SqlEvalService.saveCase(caseId, body));
    }

    @DeleteMapping(path = "/cases/{id}")
    public ResponseEntity<?> deleteCase(@PathVariable("id") long caseId, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        nl2SqlEvalService.deleteCase(caseId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/run", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> runEvaluation(@RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireSuperuser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }

        boolean enabledOnly = body == null || body.path("enabledOnly").asBoolean(true);
        int limit = body == null ? 200 : body.path("limit").asInt(200);
        List<Long> caseIds = extractCaseIds(body == null ? null : body.path("caseIds"));
        return ResponseEntity.ok(nl2SqlEvalService.runEvaluation(caseIds, enabledOnly, limit));
    }

    private List<Long> extractCaseIds(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode() || !node.isArray()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (JsonNode item : node) {
            long id = item == null ? 0 : item.asLong(0);
            if (id > 0) {
                ids.add(id);
            }
        }
        return ids;
    }
}
