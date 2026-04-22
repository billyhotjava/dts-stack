package com.yuzhi.dts.analytics.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.analytics.service.semantic.SemanticQueryService;
import com.yuzhi.dts.analytics.web.support.MetabaseAuth;
import com.yuzhi.dts.analytics.web.support.PlatformContext;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
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
@RequestMapping("/api/semantic")
@Transactional
public class SemanticResource {

    private final AnalyticsSessionService sessionService;
    private final SemanticQueryService semanticQueryService;

    public SemanticResource(AnalyticsSessionService sessionService, SemanticQueryService semanticQueryService) {
        this.sessionService = sessionService;
        this.semanticQueryService = semanticQueryService;
    }

    @GetMapping(path = "/meta", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> meta(
        @RequestParam(name = "subject_area", required = false) String subjectArea,
        @RequestParam(name = "exposed_to_modeler", required = false) Boolean exposedToModeler,
        @RequestParam(name = "include_classification_above", required = false) String includeClassificationAbove,
        HttpServletRequest request
    ) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        PlatformContext ctx = PlatformContext.from(request);
        String maxLevel = includeClassificationAbove != null ? includeClassificationAbove : ctx.classification();
        return ResponseEntity.ok(semanticQueryService.getMeta(subjectArea, exposedToModeler, maxLevel));
    }

    @GetMapping(path = "/graph", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> graph(HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.ok(semanticQueryService.getGraph(PlatformContext.from(request).classification()));
    }

    @PostMapping(path = "/query/preview-sql", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> previewSql(@RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        try {
            return ResponseEntity.ok(semanticQueryService.previewSql(body, PlatformContext.from(request)));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage(), "status", "error"));
        } catch (SemanticQueryService.SemanticAccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", ex.getMessage(), "status", "error"));
        }
    }

    @PostMapping(path = "/query", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> query(@RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        try {
            Long userId = MetabaseAuth.getUserId(sessionService, request).orElse(null);
            return ResponseEntity.ok(semanticQueryService.runQuery(body, PlatformContext.from(request), userId));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage(), "status", "error"));
        } catch (SemanticQueryService.SemanticAccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", ex.getMessage(), "status", "error"));
        } catch (SQLException ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "语义查询执行失败: " + ex.getMessage(), "status", "error"));
        }
    }

    @GetMapping(path = "/virtual-datasets", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> listVirtualDatasets(
        @RequestParam(name = "owner", required = false) String owner,
        @RequestParam(name = "workspace", required = false) Long workspaceId,
        HttpServletRequest request
    ) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        return ResponseEntity.ok(semanticQueryService.listVirtualDatasets(user.get(), owner, workspaceId));
    }

    @GetMapping(path = "/virtual-datasets/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getVirtualDataset(@PathVariable("id") long id, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        try {
            return ResponseEntity.ok(semanticQueryService.getVirtualDataset(id, user.get()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping(path = "/virtual-datasets", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createVirtualDataset(@RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        try {
            return ResponseEntity.ok(semanticQueryService.createVirtualDataset(body, user.get()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PutMapping(path = "/virtual-datasets/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateVirtualDataset(
        @PathVariable("id") long id,
        @RequestBody(required = false) JsonNode body,
        HttpServletRequest request
    ) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        try {
            return ResponseEntity.ok(semanticQueryService.updateVirtualDataset(id, body, user.get()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @DeleteMapping(path = "/virtual-datasets/{id}")
    public ResponseEntity<?> deleteVirtualDataset(@PathVariable("id") long id, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        try {
            semanticQueryService.deleteVirtualDataset(id, user.get());
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping(path = "/virtual-datasets/{id}/promote", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> promoteVirtualDataset(@PathVariable("id") long id, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        try {
            return ResponseEntity.ok(semanticQueryService.promoteVirtualDataset(id, user.get()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
        }
    }
}
