package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanApplicationService;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanApplicationService.UpdatePlanHeaderCommand;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanApplicationService.WarehousePlanException;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider.WarehousePlanActor;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.BusinessScope;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.CreateWarehousePlanCommand;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.CreateWarehousePlanResult;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.DomainIssue;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.EditUnit;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.InitialSourceRef;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.OnboardingMode;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.PlanningBaseline;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.PlanningPolicy;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceBinding;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceBusinessMapping;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.Versioned;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.WarehousePlanHeader;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanStageProjectionService;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanStageProjectionService.StageProjection;
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
    private final WarehousePlanActorProvider actorProvider;
    private final String serverTenantId;

    public WarehousePlanResource(
        WarehousePlanApplicationService service,
        WarehousePlanStageProjectionService stageProjectionService,
        WarehousePlanActorProvider actorProvider,
        @Value("${dts.platform.modeling.default-tenant-id:default}") String serverTenantId
    ) {
        this.service = service;
        this.stageProjectionService = stageProjectionService;
        this.actorProvider = actorProvider;
        this.serverTenantId = serverTenantId;
    }

    @GetMapping
    public ApiResponse<List<WarehousePlanHeader>> list(@RequestParam(required = false) String lifecycleStatus) {
        return ApiResponses.ok(service.list(serverTenantId, lifecycleStatus));
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
        WarehousePlanHeader result = service.updateHeader(serverTenantId, id, expectedVersion, request.toCommand());
        return ResponseEntity.ok().eTag(etag(EditUnit.PLAN_HEAD, result.version())).body(ApiResponses.ok(result));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<WarehousePlanHeader>> archive(
        @PathVariable UUID id,
        @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch
    ) {
        WarehousePlanHeader result = service.archive(serverTenantId, id, parseIfMatch(ifMatch, EditUnit.PLAN_HEAD));
        return ResponseEntity.ok().eTag(etag(EditUnit.PLAN_HEAD, result.version())).body(ApiResponses.ok(result));
    }

    @GetMapping("/{id}/baseline")
    public ApiResponse<PlanningBaseline> baseline(@PathVariable UUID id) {
        return ApiResponses.ok(service.getBaseline(serverTenantId, id));
    }

    @GetMapping("/{id}/stage-projection")
    public ApiResponse<StageProjection> stageProjection(@PathVariable UUID id) {
        return ApiResponses.ok(stageProjectionService.project(serverTenantId, id));
    }

    @PutMapping("/{id}/baseline/business-scope")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<BusinessScope>> saveBusinessScope(
        @PathVariable UUID id,
        @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
        @RequestBody BusinessScope request
    ) {
        Versioned<BusinessScope> result = service.saveBusinessScope(
            serverTenantId,
            id,
            parseIfMatch(ifMatch, EditUnit.BUSINESS_SCOPE),
            request
        );
        return versioned(result, EditUnit.BUSINESS_SCOPE);
    }

    @PutMapping("/{id}/baseline/sources")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<List<SourceBinding>>> saveSources(
        @PathVariable UUID id,
        @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
        @RequestBody List<SourceBinding> request
    ) {
        Versioned<List<SourceBinding>> result = service.saveSources(
            serverTenantId,
            id,
            parseIfMatch(ifMatch, EditUnit.SOURCES),
            request
        );
        return versioned(result, EditUnit.SOURCES);
    }

    @PutMapping("/{id}/baseline/source-mappings")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<List<SourceBusinessMapping>>> saveSourceMappings(
        @PathVariable UUID id,
        @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
        @RequestBody List<SourceBusinessMapping> request
    ) {
        Versioned<List<SourceBusinessMapping>> result = service.saveSourceMappings(
            serverTenantId,
            id,
            parseIfMatch(ifMatch, EditUnit.SOURCE_MAPPINGS),
            request
        );
        return versioned(result, EditUnit.SOURCE_MAPPINGS);
    }

    @PutMapping("/{id}/baseline/policy")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<PlanningPolicy>> savePolicy(
        @PathVariable UUID id,
        @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
        @RequestBody PlanningPolicy request
    ) {
        Versioned<PlanningPolicy> result = service.savePolicy(
            serverTenantId,
            id,
            parseIfMatch(ifMatch, EditUnit.POLICY),
            request
        );
        return versioned(result, EditUnit.POLICY);
    }

    @PostMapping("/{id}/baseline/confirm")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<PlanningBaseline>> confirmBaseline(
        @PathVariable UUID id,
        @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch
    ) {
        int expectedVersion = parseIfMatch(ifMatch, EditUnit.PLAN_HEAD);
        PlanningBaseline result = service.confirmBaseline(serverTenantId, id, expectedVersion);
        return ResponseEntity.ok().eTag(etag(EditUnit.PLAN_HEAD, expectedVersion + 1)).body(ApiResponses.ok(result));
    }

    @ExceptionHandler(WarehousePlanException.class)
    public ResponseEntity<ApiResponse<Void>> handleWarehousePlanError(WarehousePlanException exception) {
        HttpStatus status = status(exception.code());
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status);
        if (exception.currentVersion() != null) {
            EditUnit editUnit = exception.editUnit() == null ? EditUnit.PLAN_HEAD : exception.editUnit();
            response.header(HttpHeaders.ETAG, etag(editUnit, exception.currentVersion()));
        }
        return response.body(ApiResponses.error(exception.code(), exception.getMessage()));
    }

    private static <T> ResponseEntity<ApiResponse<T>> versioned(Versioned<T> result, EditUnit editUnit) {
        return ResponseEntity.ok().eTag(etag(editUnit, result.version())).body(ApiResponses.ok(result.value()));
    }

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
            "WAREHOUSE_PLAN_OWNER_DEPARTMENT_FORBIDDEN".equals(code)
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
}
