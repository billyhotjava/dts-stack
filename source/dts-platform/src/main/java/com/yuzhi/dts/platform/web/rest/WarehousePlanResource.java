package com.yuzhi.dts.platform.web.rest;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.FieldIssue;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.IssueSeverity;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Layer;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanApplicationService;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanApplicationService.UpdatePlanHeaderCommand;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanApplicationService.WarehousePlanException;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider.WarehousePlanActor;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanAuthorizationGuard;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.BusinessScope;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.CategoryScopeCommand;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.CategoryScopeView;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.CreateWarehousePlanCommand;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.CreateWarehousePlanResult;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.DomainIssue;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.EditUnit;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.InitialSourceRef;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.OnboardingMode;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.PlanningBaseline;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.PlanningPolicyCommand;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.PlanningPolicyView;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceBinding;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceInventoryCommand;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceInventoryView;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceBusinessMapping;
import com.yuzhi.dts.platform.service.modeling.warehouse.SourceReferenceResolver.AccessContext;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.Versioned;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.WarehousePlanHeader;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanStageProjectionService;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanStageProjectionService.StageProjection;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphContract.RelationshipGraph;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphService;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Canonical REST boundary for classic warehouse planning. */
@RestController
@RequestMapping("/api/modeling/warehouse-plans")
public class WarehousePlanResource {

    private static final String MODELING_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).CATALOG_MAINTAINERS)";

    private final WarehousePlanApplicationService service;
    private final WarehousePlanStageProjectionService stageProjectionService;
    private final WarehousePlanRelationshipGraphService relationshipGraphService;
    private final WarehousePlanActorProvider actorProvider;
    private final WarehousePlanAuthorizationGuard authorizationGuard;
    private final ObjectMapper objectMapper;
    private final String serverTenantId;

    public WarehousePlanResource(
        WarehousePlanApplicationService service,
        WarehousePlanStageProjectionService stageProjectionService,
        WarehousePlanRelationshipGraphService relationshipGraphService,
        WarehousePlanActorProvider actorProvider,
        WarehousePlanAuthorizationGuard authorizationGuard,
        ObjectMapper objectMapper,
        @Value("${dts.platform.modeling.default-tenant-id:default}") String serverTenantId
    ) {
        this.service = service;
        this.stageProjectionService = stageProjectionService;
        this.relationshipGraphService = relationshipGraphService;
        this.actorProvider = actorProvider;
        this.authorizationGuard = authorizationGuard;
        this.objectMapper = objectMapper;
        this.serverTenantId = serverTenantId;
    }

    @GetMapping
    public ApiResponse<List<WarehousePlanHeader>> list(@RequestParam(required = false) String lifecycleStatus) {
        WarehousePlanActor actor = actorProvider.currentActor();
        return ApiResponses.ok(
            service.list(serverTenantId, lifecycleStatus).stream()
                .filter(plan -> authorizationGuard.canReadPlan(plan, actor))
                .toList()
        );
    }

    @PostMapping
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<CreateWarehousePlanResult>> create(@RequestBody CreateWarehousePlanRequest request) {
        rejectRequestedTenant(request.tenantId());
        WarehousePlanActor actor = actorProvider.currentActor();
        rejectRequestedActor(request.ownerId(), request.ownerDepartmentId(), actor);
        CreateWarehousePlanResult result = service.create(serverTenantId, request.toCommand(actor));
        return ResponseEntity.created(URI.create("/api/modeling/warehouse-plans/" + result.planId()))
            .header(HttpHeaders.ETAG, result.etag())
            .body(ApiResponses.ok(result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<WarehousePlanHeader>> get(@PathVariable UUID id) {
        WarehousePlanHeader result = service.get(serverTenantId, id);
        authorizationGuard.requirePlanRead(result, actorProvider.currentActor());
        return ResponseEntity.ok().eTag(etag(EditUnit.PLAN_HEAD, result.version())).body(ApiResponses.ok(result));
    }

    @PatchMapping("/{id}")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<WarehousePlanHeader>> update(
        @PathVariable UUID id,
        @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
        @RequestBody UpdateWarehousePlanRequest request
    ) {
        rejectRequestedTenant(request.tenantId());
        int expectedVersion = parseIfMatch(ifMatch, EditUnit.PLAN_HEAD);
        WarehousePlanHeader current = service.get(serverTenantId, id);
        authorizationGuard.validateHeaderUpdate(
            current,
            request.ownerId(),
            request.ownerDepartmentId(),
            actorProvider.currentActor()
        );
        WarehousePlanHeader result = service.updateHeader(serverTenantId, id, expectedVersion, request.toCommand());
        return ResponseEntity.ok().eTag(etag(EditUnit.PLAN_HEAD, result.version())).body(ApiResponses.ok(result));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<WarehousePlanHeader>> archive(
        @PathVariable UUID id,
        @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch
    ) {
        int expectedVersion = parseIfMatch(ifMatch, EditUnit.PLAN_HEAD);
        requirePlanMaintenance(id);
        WarehousePlanHeader result = service.archive(serverTenantId, id, expectedVersion);
        return ResponseEntity.ok().eTag(etag(EditUnit.PLAN_HEAD, result.version())).body(ApiResponses.ok(result));
    }

    @GetMapping("/{id}/baseline")
    public ApiResponse<PlanningBaseline> baseline(@PathVariable UUID id) {
        WarehousePlanActor actor = requirePlanRead(id);
        return ApiResponses.ok(service.getBaseline(serverTenantId, id, sourceAccessContext(actor)));
    }

    @GetMapping("/{id}/stage-projection")
    public ApiResponse<StageProjection> stageProjection(@PathVariable UUID id) {
        WarehousePlanActor actor = requirePlanRead(id);
        return ApiResponses.ok(stageProjectionService.project(serverTenantId, id, sourceAccessContext(actor)));
    }

    @GetMapping("/{id}/relationship-graph")
    public ApiResponse<RelationshipGraph> relationshipGraph(
        @PathVariable UUID id,
        @RequestParam(required = false) String kind,
        @RequestParam(required = false) String query,
        @RequestParam(defaultValue = "500") int limit,
        @RequestParam(required = false) String cursor
    ) {
        WarehousePlanHeader plan = service.get(serverTenantId, id);
        WarehousePlanActor actor = actorProvider.currentActor();
        authorizationGuard.requirePlanRead(plan, actor);
        RelationshipGraph graph = cursor == null
            ? relationshipGraphService.read(
                serverTenantId,
                plan,
                actor == null ? null : actor.ownerDepartmentId(),
                kind,
                query,
                limit
            )
            : relationshipGraphService.read(
                serverTenantId,
                plan,
                actor == null ? null : actor.ownerDepartmentId(),
                kind,
                query,
                limit,
                cursor
            );
        return ApiResponses.ok(graph);
    }

    @PostMapping("/{id}/naming/validate")
    public ApiResponse<NamingValidationView> validatePhysicalName(
        @PathVariable UUID id,
        @RequestBody NamingValidationRequest request
    ) {
        requirePlanRead(id);
        List<FieldIssue> issues = new java.util.ArrayList<>(
            ModelSpecContract.validatePhysicalName(request == null ? null : request.physicalName())
        );
        if (
            request == null ||
            request.modelType() == null ||
            request.layer() == null ||
            ModelSpecContract.targetLayer(request.modelType()) != request.layer()
        ) {
            issues.add(
                new FieldIssue(
                    "MODEL_SPEC_TYPE_LAYER_MISMATCH",
                    "layer",
                    IssueSeverity.ERROR,
                    "Model type and target layer do not match the planning policy"
                )
            );
        }
        return ApiResponses.ok(
            new NamingValidationView(
                issues.isEmpty(),
                request == null || request.physicalName() == null ? null : request.physicalName().trim(),
                List.copyOf(issues)
            )
        );
    }

    @PutMapping("/{id}/baseline/business-scope")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<BusinessScope>> saveBusinessScope(
        @PathVariable UUID id,
        @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
        @RequestBody BusinessScope request
    ) {
        int expectedVersion = parseIfMatch(ifMatch, EditUnit.BUSINESS_SCOPE);
        requirePlanMaintenance(id);
        Versioned<BusinessScope> result = service.saveBusinessScope(
            serverTenantId,
            id,
            expectedVersion,
            request
        );
        return versioned(result, EditUnit.BUSINESS_SCOPE);
    }

    @GetMapping("/{id}/baseline/categories")
    public ResponseEntity<ApiResponse<Versioned<CategoryScopeView>>> categories(@PathVariable UUID id) {
        requirePlanRead(id);
        Versioned<CategoryScopeView> result = service.getCategoryScope(serverTenantId, id);
        return canonicalVersioned(result, EditUnit.CATEGORY_SCOPE);
    }

    @PutMapping("/{id}/baseline/categories")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Versioned<CategoryScopeView>>> saveCategories(
        @PathVariable UUID id,
        @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
        @RequestBody JsonNode request
    ) {
        int expectedVersion = parseIfMatch(ifMatch, EditUnit.CATEGORY_SCOPE);
        requirePlanMaintenance(id);
        Versioned<CategoryScopeView> result = service.saveCategoryScope(
            serverTenantId,
            id,
            expectedVersion,
            decode(request, CategoryScopeCommand.class, EditUnit.CATEGORY_SCOPE)
        );
        return canonicalVersioned(result, EditUnit.CATEGORY_SCOPE);
    }

    @GetMapping("/{id}/baseline/sources")
    public ResponseEntity<ApiResponse<SourceInventoryView>> sources(@PathVariable UUID id) {
        WarehousePlanActor actor = requirePlanRead(id);
        SourceInventoryView result = service.getSources(serverTenantId, id, sourceAccessContext(actor));
        return sourceInventoryResponse(result);
    }

    @PutMapping("/{id}/baseline/sources")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<SourceInventoryView>> saveSources(
        @PathVariable UUID id,
        @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
        @RequestBody JsonNode request
    ) {
        int expectedVersion = parseIfMatch(ifMatch, EditUnit.SOURCES);
        WarehousePlanActor actor = requirePlanMaintenance(id);
        SourceInventoryCommand command = decode(request, SourceInventoryCommand.class, EditUnit.SOURCES);
        SourceInventoryView result = service.saveSources(
            serverTenantId,
            id,
            expectedVersion,
            command,
            sourceAccessContext(actor)
        );
        return sourceInventoryResponse(result);
    }

    @PutMapping("/{id}/baseline/source-mappings")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    @Deprecated(forRemoval = false)
    public ResponseEntity<ApiResponse<List<SourceBusinessMapping>>> saveSourceMappings(
        @PathVariable UUID id,
        @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
        @RequestBody List<SourceBusinessMapping> request
    ) {
        int expectedVersion = parseIfMatch(ifMatch, EditUnit.SOURCE_MAPPINGS);
        requirePlanMaintenance(id);
        Versioned<List<SourceBusinessMapping>> result = service.saveSourceMappings(
            serverTenantId,
            id,
            expectedVersion,
            request
        );
        return versioned(result, EditUnit.SOURCE_MAPPINGS);
    }

    @GetMapping("/{id}/baseline/policy")
    public ResponseEntity<ApiResponse<Versioned<PlanningPolicyView>>> policy(@PathVariable UUID id) {
        requirePlanRead(id);
        Versioned<PlanningPolicyView> result = service.getPlanningPolicy(serverTenantId, id);
        return canonicalVersioned(result, EditUnit.POLICY);
    }

    @PutMapping("/{id}/baseline/policy")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Versioned<PlanningPolicyView>>> savePolicy(
        @PathVariable UUID id,
        @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
        @RequestBody JsonNode request
    ) {
        int expectedVersion = parseIfMatch(ifMatch, EditUnit.POLICY);
        requirePlanMaintenance(id);
        Versioned<PlanningPolicyView> result = service.savePlanningPolicy(
            serverTenantId,
            id,
            expectedVersion,
            decode(request, PlanningPolicyCommand.class, EditUnit.POLICY)
        );
        return canonicalVersioned(result, EditUnit.POLICY);
    }

    @PostMapping("/{id}/baseline/confirm")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<PlanningBaseline>> confirmBaseline(
        @PathVariable UUID id,
        @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch
    ) {
        int expectedVersion = parseIfMatch(ifMatch, EditUnit.PLAN_HEAD);
        WarehousePlanActor actor = requirePlanMaintenance(id);
        PlanningBaseline result = service.confirmBaseline(
            serverTenantId,
            id,
            expectedVersion,
            new AccessContext(
                serverTenantId,
                actor == null ? null : actor.ownerId(),
                actor == null ? null : actor.ownerDepartmentId()
            )
        );
        return ResponseEntity.ok().eTag(etag(EditUnit.PLAN_HEAD, expectedVersion + 1)).body(ApiResponses.ok(result));
    }

    @ExceptionHandler(WarehousePlanException.class)
    public ResponseEntity<ApiResponse<?>> handleWarehousePlanError(WarehousePlanException exception) {
        HttpStatus status = status(exception.code());
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status);
        WriteConflictDetails details = null;
        if (exception.currentVersion() != null) {
            EditUnit editUnit = exception.editUnit() == null ? EditUnit.PLAN_HEAD : exception.editUnit();
            response.header(HttpHeaders.ETAG, etag(editUnit, exception.currentVersion()));
            details = new WriteConflictDetails(editUnit, exception.currentVersion());
        }
        return response.body(
            new ApiResponse<>(ResultStatus.ERROR.getCode(), exception.getMessage(), exception.code(), details)
        );
    }

    private static <T> ResponseEntity<ApiResponse<T>> versioned(Versioned<T> result, EditUnit editUnit) {
        return ResponseEntity.ok().eTag(etag(editUnit, result.version())).body(ApiResponses.ok(result.value()));
    }

    private static <T> ResponseEntity<ApiResponse<Versioned<T>>> canonicalVersioned(
        Versioned<T> result,
        EditUnit editUnit
    ) {
        return ResponseEntity.ok().eTag(etag(editUnit, result.version())).body(ApiResponses.ok(result));
    }

    private static ResponseEntity<ApiResponse<SourceInventoryView>> sourceInventoryResponse(SourceInventoryView result) {
        return ResponseEntity.ok().header(HttpHeaders.ETAG, result.etag()).body(ApiResponses.ok(result));
    }

    private AccessContext sourceAccessContext(WarehousePlanActor actor) {
        return new AccessContext(
            serverTenantId,
            actor == null ? null : actor.ownerId(),
            actor == null ? null : actor.ownerDepartmentId()
        );
    }

    private WarehousePlanActor requirePlanMaintenance(UUID planId) {
        WarehousePlanActor actor = actorProvider.currentActor();
        authorizationGuard.requirePlanMaintenance(service.get(serverTenantId, planId), actor);
        return actor;
    }

    private WarehousePlanActor requirePlanRead(UUID planId) {
        WarehousePlanActor actor = actorProvider.currentActor();
        authorizationGuard.requirePlanRead(service.get(serverTenantId, planId), actor);
        return actor;
    }

    private <T> T decode(JsonNode request, Class<T> type, EditUnit editUnit) {
        try (JsonParser parser = request.traverse(objectMapper)) {
            return objectMapper
                .readerFor(type)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue(parser);
        } catch (IOException | IllegalArgumentException exception) {
            throw new WarehousePlanException(
                "WAREHOUSE_PLAN_REQUEST_INVALID",
                "Warehouse plan request shape is invalid",
                null,
                editUnit
            );
        }
    }

    public record NamingValidationRequest(ModelType modelType, Layer layer, String physicalName) {}

    public record NamingValidationView(boolean valid, String normalizedName, List<FieldIssue> issues) {}

    private static int parseIfMatch(String ifMatch, EditUnit editUnit) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new WarehousePlanException(
                "WAREHOUSE_PLAN_IF_MATCH_REQUIRED",
                "If-Match is required for warehouse plan writes",
                null,
                editUnit
            );
        }
        String normalized = ifMatch.trim();
        if (normalized.startsWith("W/")) {
            normalized = normalized.substring(2).trim();
        }
        if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        String prefix = editUnit.resourceKey() + ":";
        if (!normalized.startsWith(prefix)) {
            throw invalidIfMatch(editUnit);
        }
        try {
            int version = Integer.parseInt(normalized.substring(prefix.length()));
            if (version < 1) {
                throw invalidIfMatch(editUnit);
            }
            return version;
        } catch (NumberFormatException exception) {
            throw invalidIfMatch(editUnit);
        }
    }

    private static WarehousePlanException invalidIfMatch(EditUnit editUnit) {
        return new WarehousePlanException(
            "WAREHOUSE_PLAN_IF_MATCH_INVALID",
            "If-Match does not match the edited warehouse plan resource",
            null,
            editUnit
        );
    }

    private static String etag(EditUnit editUnit, int version) {
        return "\"" + editUnit.resourceKey() + ":" + version + "\"";
    }

    private static void rejectRequestedTenant(String requestedTenantId) {
        List<DomainIssue> issues = WarehousePlanContract.validateRequestedTenant(requestedTenantId);
        if (!issues.isEmpty()) {
            DomainIssue issue = issues.getFirst();
            throw new WarehousePlanException(issue.code(), issue.message(), null);
        }
    }

    private static void rejectRequestedActor(
        String requestedOwnerId,
        String requestedOwnerDepartmentId,
        WarehousePlanActor actor
    ) {
        List<DomainIssue> issues = WarehousePlanContract.validateRequestedActor(
            requestedOwnerId,
            requestedOwnerDepartmentId,
            actor == null ? null : actor.ownerId(),
            actor == null ? null : actor.ownerDepartmentId()
        );
        if (!issues.isEmpty()) {
            DomainIssue issue = issues.getFirst();
            throw new WarehousePlanException(issue.code(), issue.message(), null);
        }
    }

    private static HttpStatus status(String code) {
        if (
            "RELATIONSHIP_GRAPH_TIMEOUT".equals(code) ||
            "RELATIONSHIP_GRAPH_CURSOR_SIGNING_UNAVAILABLE".equals(code)
        ) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        if ("WAREHOUSE_PLAN_NOT_FOUND".equals(code)) {
            return HttpStatus.NOT_FOUND;
        }
        if ("WAREHOUSE_PLAN_IF_MATCH_REQUIRED".equals(code)) {
            return HttpStatus.PRECONDITION_REQUIRED;
        }
        if ("WAREHOUSE_PLAN_AUTHENTICATED_ACTOR_REQUIRED".equals(code)) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (
            "WAREHOUSE_PLAN_OWNER_FORBIDDEN".equals(code) ||
            "WAREHOUSE_PLAN_OWNER_DEPARTMENT_FORBIDDEN".equals(code) ||
            "WAREHOUSE_PLAN_CATEGORY_FORBIDDEN".equals(code)
        ) {
            return HttpStatus.FORBIDDEN;
        }
        if (
            "WAREHOUSE_PLAN_VERSION_CONFLICT".equals(code) ||
            "WAREHOUSE_PLAN_EDIT_UNIT_VERSION_CONFLICT".equals(code) ||
            "WAREHOUSE_PLAN_CODE_CONFLICT".equals(code) ||
            "WAREHOUSE_PLAN_IDEMPOTENCY_CONFLICT".equals(code) ||
            "WAREHOUSE_PLAN_LIFECYCLE_CONFLICT".equals(code) ||
            "WAREHOUSE_PLAN_BASELINE_INCOMPLETE".equals(code) ||
            "WAREHOUSE_PLAN_SOURCE_IN_USE".equals(code)
        ) {
            return HttpStatus.CONFLICT;
        }
        return HttpStatus.BAD_REQUEST;
    }

    public record CreateWarehousePlanRequest(
        String tenantId,
        String name,
        String objective,
        String scope,
        String ownerId,
        String ownerDepartmentId,
        OnboardingMode onboardingMode,
        List<InitialSourceRef> initialSourceRefs,
        String idempotencyKey
    ) {
        private CreateWarehousePlanCommand toCommand(WarehousePlanActor actor) {
            return new CreateWarehousePlanCommand(
                name,
                objective,
                scope,
                actor.ownerId(),
                actor.ownerDepartmentId(),
                onboardingMode,
                initialSourceRefs,
                idempotencyKey
            );
        }
    }

    public record UpdateWarehousePlanRequest(
        String tenantId,
        String name,
        String objective,
        String scope,
        String ownerId,
        String ownerDepartmentId
    ) {
        private UpdatePlanHeaderCommand toCommand() {
            return new UpdatePlanHeaderCommand(name, objective, scope, ownerId, ownerDepartmentId);
        }
    }

    public record WriteConflictDetails(EditUnit editUnit, int currentVersion) {}
}
