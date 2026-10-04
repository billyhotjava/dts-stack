package com.yuzhi.dts.copilot.analytics.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.copilot.analytics.service.ReportFactoryService;
import com.yuzhi.dts.copilot.analytics.web.support.MetabaseAuth;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
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
@RequestMapping("/api/report-factory")
public class ReportFactoryResource {

    private final AnalyticsSessionService sessionService;
    private final ReportFactoryService reportFactoryService;

    public ReportFactoryResource(
            AnalyticsSessionService sessionService,
            ReportFactoryService reportFactoryService) {
        this.sessionService = sessionService;
        this.reportFactoryService = reportFactoryService;
    }

    @GetMapping(path = "/templates", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> listTemplates(
            @RequestParam(name = "limit", required = false, defaultValue = "100") int limit,
            HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.ok(reportFactoryService.listTemplates(limit));
    }

    @PostMapping(path = "/templates", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createTemplate(@RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        return ResponseEntity.ok(reportFactoryService.saveTemplate(null, body, user.get().getId()));
    }

    @PutMapping(path = "/templates/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateTemplate(
            @PathVariable("id") long id,
            @RequestBody(required = false) JsonNode body,
            HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        return ResponseEntity.ok(reportFactoryService.saveTemplate(id, body, user.get().getId()));
    }

    @DeleteMapping(path = "/templates/{id}")
    public ResponseEntity<?> deleteTemplate(@PathVariable("id") long id, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        reportFactoryService.archiveTemplate(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(path = "/runs", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> listRuns(
            @RequestParam(name = "limit", required = false, defaultValue = "100") int limit,
            HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        return ResponseEntity.ok(reportFactoryService.listRuns(user.get().getId(), user.get().isSuperuser(), limit));
    }

    @GetMapping(path = "/runs/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getRun(@PathVariable("id") long id, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        return ResponseEntity.ok(reportFactoryService.getRun(id, user.get().getId(), user.get().isSuperuser()));
    }

    @PostMapping(path = "/generate", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> generate(@RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        return ResponseEntity.ok(reportFactoryService.generate(body, user.get().getId()));
    }

    @GetMapping(path = "/runs/{id}/export")
    public ResponseEntity<byte[]> exportRun(
            @PathVariable("id") long id,
            @RequestParam(name = "format", required = false, defaultValue = "html") String format,
            HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated".getBytes());
        }
        byte[] bytes = reportFactoryService.exportRun(id, format, user.get().getId(), user.get().isSuperuser());
        String safeFormat = format == null ? "html" : format.trim().toLowerCase();
        MediaType contentType = "markdown".equals(safeFormat) ? MediaType.TEXT_PLAIN : MediaType.TEXT_HTML;
        String extension = "markdown".equals(safeFormat) ? "md" : "html";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(contentType);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("report-run-" + id + "." + extension)
                .build());
        return ResponseEntity.ok().headers(headers).body(bytes);
    }
}
