package com.yuzhi.dts.analytics.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.analytics.service.SemanticAuditService;
import com.yuzhi.dts.analytics.service.semantic.SemanticQueryService;
import com.yuzhi.dts.analytics.web.support.MetabaseAuth;
import com.yuzhi.dts.analytics.web.support.PlatformContext;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import java.util.LinkedHashMap;
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
    private final SemanticAuditService semanticAuditService;

    public SemanticResource(
        AnalyticsSessionService sessionService,
        SemanticQueryService semanticQueryService,
        SemanticAuditService semanticAuditService
    ) {
        this.sessionService = sessionService;
        this.semanticQueryService = semanticQueryService;
        this.semanticAuditService = semanticAuditService;
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
        AnalyticsUser actor = MetabaseAuth.currentUser(sessionService, request).orElse(null);
        PlatformContext ctx = PlatformContext.from(request);
        String maxLevel = includeClassificationAbove != null ? includeClassificationAbove : ctx.classification();
        Object result = semanticQueryService.getMeta(subjectArea, exposedToModeler, maxLevel);
        Map<String, Object> attrs = new LinkedHashMap<>();
        if (subjectArea != null) attrs.put("subjectArea", subjectArea);
        if (exposedToModeler != null) attrs.put("exposedToModeler", exposedToModeler);
        if (maxLevel != null) attrs.put("classificationCeiling", maxLevel);
        semanticAuditService.logSuccess("SEMANTIC_META_VIEW", "查看语义元信息", actor, request, null, attrs);
        return ResponseEntity.ok(result);
    }

    @GetMapping(path = "/graph", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> graph(HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        AnalyticsUser actor = MetabaseAuth.currentUser(sessionService, request).orElse(null);
        Object result = semanticQueryService.getGraph(PlatformContext.from(request).classification());
        semanticAuditService.logSuccess("SEMANTIC_GRAPH_VIEW", "查看语义 Join 图", actor, request, null, null);
        return ResponseEntity.ok(result);
    }

    @PostMapping(path = "/query/preview-sql", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> previewSql(@RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        AnalyticsUser actor = MetabaseAuth.currentUser(sessionService, request).orElse(null);
        Map<String, Object> attrs = queryAttributes(body);
        try {
            Object result = semanticQueryService.previewSql(body, PlatformContext.from(request));
            semanticAuditService.logSuccess("SEMANTIC_QUERY_PREVIEW", "预览语义查询 SQL", actor, request, null, attrs);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException ex) {
            semanticAuditService.logFailure("SEMANTIC_QUERY_PREVIEW", "预览语义查询 SQL", actor, request, null, attrs, ex.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage(), "status", "error"));
        } catch (SemanticQueryService.SemanticAccessDeniedException ex) {
            semanticAuditService.logFailure("SEMANTIC_QUERY_PREVIEW", "预览语义查询 SQL", actor, request, null, attrs, "DENY:" + ex.getMessage());
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", ex.getMessage(), "status", "error"));
        }
    }

    @PostMapping(path = "/query", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> query(@RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        AnalyticsUser actor = MetabaseAuth.currentUser(sessionService, request).orElse(null);
        Map<String, Object> attrs = queryAttributes(body);
        try {
            Long userId = actor != null ? actor.getId() : null;
            Object result = semanticQueryService.runQuery(body, PlatformContext.from(request), userId);
            semanticAuditService.logSuccess("SEMANTIC_QUERY_EXECUTE", "执行语义查询", actor, request, null, attrs);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException ex) {
            semanticAuditService.logFailure("SEMANTIC_QUERY_EXECUTE", "执行语义查询", actor, request, null, attrs, ex.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage(), "status", "error"));
        } catch (SemanticQueryService.SemanticAccessDeniedException ex) {
            semanticAuditService.logFailure("SEMANTIC_QUERY_EXECUTE", "执行语义查询", actor, request, null, attrs, "DENY:" + ex.getMessage());
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", ex.getMessage(), "status", "error"));
        } catch (SQLException ex) {
            semanticAuditService.logFailure("SEMANTIC_QUERY_EXECUTE", "执行语义查询", actor, request, null, attrs, "SQL:" + ex.getMessage());
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
        AnalyticsUser actor = user.orElseThrow();
        Object result = semanticQueryService.listVirtualDatasets(actor, owner, workspaceId);
        Map<String, Object> attrs = new LinkedHashMap<>();
        if (owner != null) attrs.put("owner", owner);
        if (workspaceId != null) attrs.put("workspaceId", workspaceId);
        semanticAuditService.logSuccess("SEMANTIC_VDS_LIST", "查看虚拟数据集列表", actor, request, null, attrs);
        return ResponseEntity.ok(result);
    }

    @GetMapping(path = "/virtual-datasets/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getVirtualDataset(@PathVariable("id") long id, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        AnalyticsUser actor = user.orElseThrow();
        try {
            Object result = semanticQueryService.getVirtualDataset(id, actor);
            semanticAuditService.logSuccess("SEMANTIC_VDS_VIEW", "查看虚拟数据集", actor, request, id, null);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException ex) {
            semanticAuditService.logFailure("SEMANTIC_VDS_VIEW", "查看虚拟数据集", actor, request, id, null, ex.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping(path = "/virtual-datasets", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createVirtualDataset(@RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        AnalyticsUser actor = user.orElseThrow();
        try {
            Object result = semanticQueryService.createVirtualDataset(body, actor);
            Object newId = extractId(result);
            semanticAuditService.logSuccess("SEMANTIC_VDS_CREATE", "创建虚拟数据集", actor, request, newId, vdsAttributes(body));
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException ex) {
            semanticAuditService.logFailure("SEMANTIC_VDS_CREATE", "创建虚拟数据集", actor, request, null, vdsAttributes(body), ex.getMessage());
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
        AnalyticsUser actor = user.orElseThrow();
        try {
            Object result = semanticQueryService.updateVirtualDataset(id, body, actor);
            semanticAuditService.logSuccess("SEMANTIC_VDS_UPDATE", "更新虚拟数据集", actor, request, id, vdsAttributes(body));
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException ex) {
            semanticAuditService.logFailure("SEMANTIC_VDS_UPDATE", "更新虚拟数据集", actor, request, id, vdsAttributes(body), ex.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @DeleteMapping(path = "/virtual-datasets/{id}")
    public ResponseEntity<?> deleteVirtualDataset(@PathVariable("id") long id, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        AnalyticsUser actor = user.orElseThrow();
        try {
            semanticQueryService.deleteVirtualDataset(id, actor);
            semanticAuditService.logSuccess("SEMANTIC_VDS_DELETE", "删除虚拟数据集", actor, request, id, null);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException ex) {
            semanticAuditService.logFailure("SEMANTIC_VDS_DELETE", "删除虚拟数据集", actor, request, id, null, ex.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping(path = "/virtual-datasets/{id}/promote", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> promoteVirtualDataset(@PathVariable("id") long id, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        AnalyticsUser actor = user.orElseThrow();
        try {
            Object result = semanticQueryService.promoteVirtualDataset(id, actor);
            semanticAuditService.logSuccess("SEMANTIC_VDS_PROMOTE", "提升虚拟数据集到 dbt", actor, request, id, null);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException ex) {
            semanticAuditService.logFailure("SEMANTIC_VDS_PROMOTE", "提升虚拟数据集到 dbt", actor, request, id, null, ex.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
        }
    }

    private static Map<String, Object> queryAttributes(JsonNode body) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        if (body == null || body.isMissingNode() || body.isNull()) {
            return attrs;
        }
        if (body.hasNonNull("model")) {
            attrs.put("model", body.path("model").asText());
        }
        if (body.path("metrics").isArray()) {
            attrs.put("metricsCount", body.path("metrics").size());
        }
        if (body.path("dimensions").isArray()) {
            attrs.put("dimensionsCount", body.path("dimensions").size());
        }
        if (body.path("filters").isArray()) {
            attrs.put("filtersCount", body.path("filters").size());
        }
        if (body.hasNonNull("limit")) {
            attrs.put("limit", body.path("limit").asInt());
        }
        return attrs;
    }

    private static Map<String, Object> vdsAttributes(JsonNode body) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        if (body == null || body.isMissingNode() || body.isNull()) {
            return attrs;
        }
        if (body.hasNonNull("name")) {
            attrs.put("name", body.path("name").asText());
        }
        if (body.hasNonNull("baseModel")) {
            attrs.put("baseModel", body.path("baseModel").asText());
        }
        if (body.hasNonNull("workspaceId")) {
            attrs.put("workspaceId", body.path("workspaceId").asLong());
        }
        return attrs;
    }

    private static Object extractId(Object result) {
        if (result instanceof Map<?, ?> map) {
            Object id = map.get("id");
            if (id != null) {
                return id;
            }
        }
        return null;
    }
}
