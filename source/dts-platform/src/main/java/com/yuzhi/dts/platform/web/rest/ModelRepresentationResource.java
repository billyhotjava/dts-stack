package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.modeling.ModelSpecException;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.ModelRepresentationView;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.RepresentationScope;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationException;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationService;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only boundary for exact, scope-specific model representations. */
@RestController
@RequestMapping("/api/modeling/model-specs")
public class ModelRepresentationResource {

    private final ModelRepresentationService service;
    private final WarehousePlanActorProvider actorProvider;
    private final String serverTenantId;

    public ModelRepresentationResource(
        ModelRepresentationService service,
        WarehousePlanActorProvider actorProvider,
        @Value("${dts.platform.modeling.default-tenant-id:default}") String serverTenantId
    ) {
        this.service = service;
        this.actorProvider = actorProvider;
        this.serverTenantId = serverTenantId;
    }

    @GetMapping("/{id}/representations")
    public ResponseEntity<ApiResponse<ModelRepresentationView>> get(
        @PathVariable UUID id,
        @RequestParam int modelRevision,
        @RequestParam(required = false) Integer implementationRevision,
        @RequestParam RepresentationScope representationScope
    ) {
        boolean technicalAuthorized =
            representationScope == RepresentationScope.TECHNICAL &&
            SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.CATALOG_MAINTAINERS);
        ModelRepresentationView view = service.get(
            serverTenantId,
            actorId(),
            id,
            modelRevision,
            implementationRevision,
            representationScope,
            technicalAuthorized
        );
        return ResponseEntity.ok().eTag(view.etag()).body(ApiResponses.ok(view));
    }

    @ExceptionHandler(ModelRepresentationException.class)
    public ResponseEntity<ApiResponse<Object>> handleRepresentationError(ModelRepresentationException exception) {
        HttpStatus status = switch (exception.getKind()) {
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
        };
        return ResponseEntity
            .status(status)
            .body(
                new ApiResponse<>(
                    ResultStatus.ERROR.getCode(),
                    exception.getMessage(),
                    exception.getCode(),
                    exception.getDetails()
                )
            );
    }

    @ExceptionHandler(ModelSpecException.class)
    public ResponseEntity<ApiResponse<Object>> handleModelSpecError(ModelSpecException exception) {
        HttpStatus status = switch (exception.kind()) {
            case BAD_REQUEST, UNPROCESSABLE, PRECONDITION_REQUIRED -> HttpStatus.BAD_REQUEST;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
        };
        return ResponseEntity
            .status(status)
            .body(
                new ApiResponse<>(
                    ResultStatus.ERROR.getCode(),
                    exception.getMessage(),
                    exception.code(),
                    exception.details()
                )
            );
    }

    private String actorId() {
        WarehousePlanActorProvider.WarehousePlanActor actor = actorProvider.currentActor();
        return actor == null ? null : actor.ownerId();
    }
}
