package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.modeling.PlanExecutionException;
import com.yuzhi.dts.platform.service.modeling.PlanExecutionBindingCommandService;
import com.yuzhi.dts.platform.service.modeling.PlanExecutionBindingCommandService.RepairView;
import com.yuzhi.dts.platform.service.modeling.PlanExecutionHealthService;
import com.yuzhi.dts.platform.service.modeling.PlanExecutionHealthService.WorkspaceView;
import com.yuzhi.dts.platform.service.modeling.PlanOperationalRunService;
import com.yuzhi.dts.platform.service.modeling.PlanOperationalRunService.OperationalRunView;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Operator-only plan execution commands; users never supply DAG/runtime internals. */
@RestController
@RequestMapping(
    "/api/modeling/plans/{planId}/execution-bindings"
)
public class PlanExecutionBindingResource {

    private static final String RELEASE_DUTY_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).MODEL_RELEASE_DUTIES)";
    private static final String RELEASE_OPERATOR_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).MODEL_RELEASE_OPERATORS)";
    private static final Pattern BINDING_ETAG = Pattern.compile(
        "^\\\"plan-execution-binding:([0-9a-fA-F-]{36}):([1-9][0-9]*)\\\"$"
    );

    private final PlanOperationalRunService runs;
    private final PlanExecutionHealthService health;
    private final PlanExecutionBindingCommandService commands;
    private final WarehousePlanActorProvider actorProvider;
    private final String serverTenantId;

    public PlanExecutionBindingResource(
        PlanOperationalRunService runs,
        PlanExecutionHealthService health,
        PlanExecutionBindingCommandService commands,
        WarehousePlanActorProvider actorProvider,
        @Value("${dts.platform.modeling.default-tenant-id:default}") String serverTenantId
    ) {
        this.runs = runs;
        this.health = health;
        this.commands = commands;
        this.actorProvider = actorProvider;
        this.serverTenantId = serverTenantId;
    }

    @PostMapping("/{bindingId}/repair")
    @PreAuthorize(RELEASE_OPERATOR_EXPRESSION)
    public ResponseEntity<ApiResponse<RepairView>> repair(
        @PathVariable UUID planId,
        @PathVariable UUID bindingId,
        @RequestHeader(value = "If-Match", required = false) String ifMatch
    ) {
        RepairView result = commands.repair(
            serverTenantId,
            actorProvider.currentActor().ownerId(),
            planId,
            bindingId,
            expectedVersion(ifMatch, bindingId)
        );
        return ResponseEntity.accepted().body(ApiResponses.ok(result));
    }

    @GetMapping("/workspace")
    @PreAuthorize(RELEASE_DUTY_EXPRESSION)
    public ResponseEntity<ApiResponse<WorkspaceView>> workspace(
        @PathVariable UUID planId
    ) {
        WorkspaceView result = health.workspace(
            serverTenantId,
            actorProvider.currentActor().ownerId(),
            planId
        );
        return ResponseEntity.ok(ApiResponses.ok(result));
    }

    @PostMapping("/{bindingId}/runs")
    @PreAuthorize(RELEASE_OPERATOR_EXPRESSION)
    public ResponseEntity<ApiResponse<OperationalRunView>> runNow(
        @PathVariable UUID planId,
        @PathVariable UUID bindingId,
        @RequestHeader(
            value = "Idempotency-Key",
            required = false
        ) String idempotencyKey
    ) {
        OperationalRunView result = runs.openManual(
            serverTenantId,
            actorProvider.currentActor().ownerId(),
            planId,
            bindingId,
            idempotencyKey
        );
        return ResponseEntity
            .status(
                result.replayed()
                    ? HttpStatus.OK
                    : HttpStatus.ACCEPTED
            )
            .body(ApiResponses.ok(result));
    }

    @ExceptionHandler(PlanExecutionException.class)
    public ResponseEntity<Map<String, String>> handle(
        PlanExecutionException failure
    ) {
        HttpStatus status = switch (failure.kind()) {
            case INVALID -> HttpStatus.BAD_REQUEST;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        return ResponseEntity
            .status(status)
            .body(
                Map.of(
                    "code",
                    failure.code(),
                    "message",
                    failure.getMessage()
                )
            );
    }

    private static int expectedVersion(
        String value,
        UUID expectedBindingId
    ) {
        Matcher match = BINDING_ETAG.matcher(
            value == null ? "" : value.trim()
        );
        if (!match.matches()) {
            throw new PlanExecutionException(
                "MODEL_PLAN_EXECUTION_ETAG_REQUIRED",
                "A current plan execution binding ETag is required",
                PlanExecutionException.Kind.INVALID
            );
        }
        try {
            UUID bindingId = UUID.fromString(match.group(1));
            int version = Integer.parseInt(match.group(2));
            if (!expectedBindingId.equals(bindingId)) {
                throw new IllegalArgumentException();
            }
            return version;
        } catch (IllegalArgumentException invalid) {
            throw new PlanExecutionException(
                "MODEL_PLAN_EXECUTION_ETAG_REQUIRED",
                "A current plan execution binding ETag is required",
                PlanExecutionException.Kind.INVALID
            );
        }
    }
}
