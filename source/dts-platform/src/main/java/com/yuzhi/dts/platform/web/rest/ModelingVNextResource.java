package com.yuzhi.dts.platform.web.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.DbtModelingContract;
import com.yuzhi.dts.platform.service.modeling.ModelingRunCallbackContract;
import com.yuzhi.dts.platform.service.modeling.ModelingRunRequestContract;
import com.yuzhi.dts.platform.service.modeling.ModelingVNextApplicationService;
import com.yuzhi.dts.platform.service.modeling.ModelingVNextContract;
import com.yuzhi.dts.platform.service.modeling.migration.LegacyObjectMigrationService;
import com.yuzhi.dts.platform.security.SecurityUtils;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST boundary for the vNext modeling ledger and its dbt/runtime links. */
@RestController
@RequestMapping("/api/modeling/vnext")
public class ModelingVNextResource {

    private static final String MODELING_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).CATALOG_MAINTAINERS)";

    private final ModelingVNextApplicationService service;
    private final ObjectMapper objectMapper;
    private final AuditService audit;
    private final LegacyObjectMigrationService legacyRetirement;

    public ModelingVNextResource(ModelingVNextApplicationService service, ObjectMapper objectMapper) {
        this(service, objectMapper, null, null);
    }

    @Autowired
    public ModelingVNextResource(
        ModelingVNextApplicationService service,
        ObjectMapper objectMapper,
        AuditService audit,
        LegacyObjectMigrationService legacyRetirement
    ) {
        this.service = service;
        this.objectMapper = objectMapper;
        this.audit = audit;
        this.legacyRetirement = legacyRetirement;
    }

    @GetMapping("/business-objects")
    public ResponseEntity<ApiResponse<List<JsonNode>>> listBusinessObjects(
        @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
        @RequestParam(required = false) String processId,
        @RequestParam(required = false) String status
    ) {
        List<ModelingVNextContract.BusinessObject> values = service.listBusinessObjects(tenantId, processId, status);
        boolean compatibilityProjectionEnabled = legacyRetirement != null && legacyRetirement.legacyReadEnabled();
        List<JsonNode> projected = compatibilityProjectionEnabled
            ? legacyRetirement.projectLegacyRead(tenantId, LegacyObjectMigrationService.MODELING_VNEXT_SOURCE, values)
            : values.stream().map(value -> (JsonNode) objectMapper.valueToTree(value)).toList();
        if (compatibilityProjectionEnabled) {
            legacyRetirement.recordApiUsage(tenantId, currentActor(), "/api/modeling/vnext/business-objects", "GET", "LEGACY_READONLY");
        }
        return LegacyModelingRetirementHttp.deprecated(ApiResponses.ok(projected));
    }

    @PostMapping("/business-objects")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> createBusinessObject(
        @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
        @RequestBody JsonNode body
    ) {
        if (legacyRetirement != null && legacyRetirement.writeFrozen()) {
            return rejectLegacyWrite(tenantId, "/api/modeling/vnext/business-objects", "POST");
        }
        ModelingVNextContract.BusinessObject result = service.saveBusinessObject(tenantId, read(body, ModelingVNextContract.BusinessObject.class), revision(body), idempotencyKey(body));
        auditSuccess("MODELING_VNEXT_BUSINESS_OBJECT_SAVE", result.id(), Map.of("tenantId", valueOrEmpty(tenantId), "revision", revision(body)));
        return ResponseEntity.ok(ApiResponses.ok(result));
    }

    @PutMapping("/business-objects/{id}")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> updateBusinessObject(
        @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
        @PathVariable String id,
        @RequestBody JsonNode body
    ) {
        if (legacyRetirement != null && legacyRetirement.writeFrozen()) {
            return rejectLegacyWrite(tenantId, "/api/modeling/vnext/business-objects/{id}", "PUT");
        }
        ObjectNode payload = objectNode(body);
        if (!payload.has("id") || payload.get("id").isNull()) payload.put("id", id);
        ModelingVNextContract.BusinessObject result = service.saveBusinessObject(tenantId, read(payload, ModelingVNextContract.BusinessObject.class), revision(payload), idempotencyKey(payload));
        auditSuccess("MODELING_VNEXT_BUSINESS_OBJECT_SAVE", result.id(), Map.of("tenantId", valueOrEmpty(tenantId), "revision", revision(payload)));
        return ResponseEntity.ok(ApiResponses.ok(result));
    }

    @PatchMapping("/business-objects/{id}")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> patchBusinessObject(
        @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
        @PathVariable String id,
        @RequestBody(required = false) JsonNode ignored
    ) {
        return rejectLegacyWrite(tenantId, "/api/modeling/vnext/business-objects/{id}", "PATCH");
    }

    @GetMapping("/plans")
    public ApiResponse<List<ModelingVNextApplicationService.WarehousePlan>> listPlans(
        @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
        @RequestParam(required = false) String processId,
        @RequestParam(required = false) String layer
    ) {
        return ApiResponses.ok(service.listPlans(tenantId, processId, layer));
    }

    @PostMapping("/plans")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<ModelingVNextApplicationService.WarehousePlan> createPlan(
        @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
        @RequestBody JsonNode body
    ) {
        ModelingVNextApplicationService.WarehousePlan result = service.savePlan(tenantId, read(body, ModelingVNextApplicationService.WarehousePlan.class), revision(body), idempotencyKey(body));
        auditSuccess("MODELING_VNEXT_PLAN_SAVE", result.id(), Map.of("tenantId", valueOrEmpty(tenantId), "revision", revision(body)));
        return ApiResponses.ok(result);
    }

    @PutMapping("/plans/{id}")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<ModelingVNextApplicationService.WarehousePlan> updatePlan(
        @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
        @PathVariable String id,
        @RequestBody JsonNode body
    ) {
        ObjectNode payload = objectNode(body);
        if (!payload.has("id") || payload.get("id").isNull()) payload.put("id", id);
        ModelingVNextApplicationService.WarehousePlan result = service.savePlan(tenantId, read(payload, ModelingVNextApplicationService.WarehousePlan.class), revision(payload), idempotencyKey(payload));
        auditSuccess("MODELING_VNEXT_PLAN_SAVE", result.id(), Map.of("tenantId", valueOrEmpty(tenantId), "revision", revision(payload)));
        return ApiResponses.ok(result);
    }

    @GetMapping("/model-specs")
    public ApiResponse<List<ModelingVNextContract.ModelSpec>> listModelSpecs(
        @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
        @RequestParam(required = false) String objectId,
        @RequestParam(required = false) String processId,
        @RequestParam(required = false) String layer
    ) {
        return ApiResponses.ok(service.listModelSpecs(tenantId, objectId, processId, layer));
    }

    @PostMapping("/model-specs")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<ModelingVNextContract.ModelSpec> createModelSpec(
        @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
        @RequestBody JsonNode body
    ) {
        ModelingVNextContract.ModelSpec result = service.saveModelSpec(tenantId, read(body, ModelingVNextContract.ModelSpec.class), revision(body), idempotencyKey(body));
        auditSuccess("MODELING_VNEXT_MODEL_SPEC_SAVE", result.id(), Map.of("tenantId", valueOrEmpty(tenantId), "revision", revision(body)));
        return ApiResponses.ok(result);
    }

    @PutMapping("/model-specs/{id}")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<ModelingVNextContract.ModelSpec> updateModelSpec(
        @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
        @PathVariable String id,
        @RequestBody JsonNode body
    ) {
        ObjectNode payload = objectNode(body);
        if (!payload.has("id") || payload.get("id").isNull()) payload.put("id", id);
        ModelingVNextContract.ModelSpec result = service.saveModelSpec(tenantId, read(payload, ModelingVNextContract.ModelSpec.class), revision(payload), idempotencyKey(payload));
        auditSuccess("MODELING_VNEXT_MODEL_SPEC_SAVE", result.id(), Map.of("tenantId", valueOrEmpty(tenantId), "revision", revision(payload)));
        return ApiResponses.ok(result);
    }

    @GetMapping("/model-specs/{id}/dependencies")
    public ApiResponse<List<ModelingVNextContract.ModelSpec>> dependencies(
        @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
        @PathVariable String id
    ) {
        return ApiResponses.ok(service.dependencies(tenantId, id));
    }

    @PostMapping("/dbt/import")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<DbtModelingContract.ImportResult> importDbt(
        @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
        @RequestBody DbtModelingContract.ManifestImportRequest request
    ) {
        DbtModelingContract.ImportResult result = service.importDbt(tenantId, request);
        auditSuccess("MODELING_VNEXT_DBT_IMPORT", result.modelSpecId(), Map.of("tenantId", valueOrEmpty(tenantId), "status", result.status()));
        return ApiResponses.ok(result);
    }

    @GetMapping("/model-specs/{id}/artifacts")
    public ApiResponse<List<ModelingVNextApplicationService.Artifact>> artifacts(
        @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
        @PathVariable String id
    ) {
        return ApiResponses.ok(service.artifacts(tenantId, id));
    }

    @GetMapping("/model-specs/{id}/drift")
    public ApiResponse<ModelingVNextApplicationService.DriftView> drift(
        @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
        @PathVariable String id
    ) {
        return ApiResponses.ok(service.drift(tenantId, id));
    }

    @GetMapping("/model-specs/{id}/release-gate")
    public ApiResponse<ModelingVNextApplicationService.ReleaseGateView> releaseGate(
        @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
        @PathVariable String id
    ) {
        return ApiResponses.ok(service.releaseGate(tenantId, id));
    }

    @PostMapping("/model-specs/{id}/compile")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<ModelingVNextApplicationService.CompileResult> compile(
        @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
        @PathVariable String id,
        @RequestBody JsonNode body
    ) {
        ModelingVNextApplicationService.CompileResult result = service.compile(tenantId, id, revision(body), idempotencyKey(body));
        auditSuccess("MODELING_VNEXT_MODEL_SPEC_COMPILE", id, Map.of("tenantId", valueOrEmpty(tenantId), "revision", revision(body), "status", result.status()));
        return ApiResponses.ok(result);
    }

    @PostMapping("/runs")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<ModelingVNextApplicationService.RunView> createRun(
        @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
        @RequestBody JsonNode body
    ) {
        ModelingVNextApplicationService.RunView result = service.createRun(tenantId, runRequest(body));
        auditSuccess("MODELING_VNEXT_RUN_CREATE", result.id(), Map.of("tenantId", valueOrEmpty(tenantId), "state", result.state(), "modelSpecId", valueOrEmpty(result.modelSpecId())));
        return ApiResponses.ok(result);
    }

    @GetMapping("/runs/{id}")
    public ApiResponse<ModelingVNextApplicationService.RunView> getRun(
        @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
        @PathVariable String id
    ) {
        return ApiResponses.ok(service.getRun(tenantId, id));
    }

    @PostMapping("/runs/{id}/callback")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<ModelingVNextApplicationService.RunView> callbackRun(
        @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
        @PathVariable String id,
        @RequestBody ModelingRunCallbackContract.Callback callback
    ) {
        ModelingVNextApplicationService.RunView result = service.callbackRun(tenantId, id, callback);
        auditSuccess(
            "MODELING_VNEXT_RUN_CALLBACK",
            id,
            Map.of(
                "tenantId", tenantId,
                "state", callback.state().name(),
                "addaxTaskId", valueOrEmpty(callback.addaxTaskId()),
                "airflowRunId", valueOrEmpty(callback.airflowRunId()),
                "dbtRunId", valueOrEmpty(callback.dbtRunId())
            )
        );
        return ApiResponses.ok(result);
    }

    @GetMapping("/lineage/{id}")
    public ApiResponse<ModelingVNextApplicationService.LineageView> lineage(
        @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
        @PathVariable String id
    ) {
        return ApiResponses.ok(service.lineage(tenantId, id));
    }

    @ExceptionHandler({ ModelingVNextApplicationService.DomainException.class, IllegalArgumentException.class, JsonProcessingException.class })
    public ResponseEntity<ApiResponse<Void>> handleModelingError(Exception exception) {
        String code = exception instanceof ModelingVNextApplicationService.DomainException domain ? domain.code() : "MODEL_REQUEST_INVALID";
        return ResponseEntity.badRequest().body(ApiResponses.error(code, exception.getMessage()));
    }

    private ModelingRunRequestContract.RunRequest runRequest(JsonNode body) {
        return new ModelingRunRequestContract.RunRequest(
            text(body, "modelSpecId"),
            revision(body),
            idempotencyKey(body),
            new ModelingRunRequestContract.ExternalContext(
                text(body, "sourceBatchId"), text(body, "addaxTaskId"), text(body, "airflowDagId"), text(body, "airflowRunId"),
                text(body, "dbtRunId"), text(body, "dbtSelector"), text(body, "targetTable")
            )
        );
    }

    private <T> T read(JsonNode body, Class<T> type) {
        try {
            ObjectNode payload = objectNode(body);
            payload.remove(List.of("revision", "idempotencyKey"));
            return objectMapper.treeToValue(payload, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("请求体格式不正确", exception);
        }
    }

    private ObjectNode objectNode(JsonNode body) {
        if (body == null || !body.isObject()) throw new IllegalArgumentException("请求体必须是 JSON 对象");
        return (ObjectNode) body.deepCopy();
    }

    private static int revision(JsonNode body) {
        return body == null ? 0 : body.path("revision").asInt(0);
    }

    private static String idempotencyKey(JsonNode body) {
        return text(body, "idempotencyKey");
    }

    private static String text(JsonNode body, String name) {
        if (body == null || !body.hasNonNull(name)) return null;
        String value = body.get(name).asText();
        return value.isBlank() ? null : value;
    }

    private ResponseEntity<ApiResponse<Object>> rejectLegacyWrite(String tenantId, String route, String method) {
        if (legacyRetirement != null) {
            legacyRetirement.recordApiUsage(tenantId, currentActor(), route, method, "BUSINESS_OBJECT_RETIRED");
        }
        if (audit != null) {
            audit.auditAction(
                "LEGACY_MODELING_WRITE_REJECTED",
                AuditStage.FAIL,
                route,
                Map.of("tenantId", valueOrEmpty(tenantId), "route", route, "method", method, "result", "BUSINESS_OBJECT_RETIRED")
            );
        }
        return LegacyModelingRetirementHttp.retired("/modeling/workbench");
    }

    private static String currentActor() {
        return SecurityUtils.getCurrentUserLogin().orElse("system");
    }

    private void auditSuccess(String action, String resourceId, Object payload) {
        if (audit != null) audit.auditAction(action, AuditStage.SUCCESS, resourceId, payload);
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
