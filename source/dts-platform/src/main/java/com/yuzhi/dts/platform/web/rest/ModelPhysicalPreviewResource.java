package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.service.modeling.serving.ModelPhysicalPreviewService;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.PhysicalPreviewRequest;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.PhysicalPreviewView;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.PreviewMode;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.PreviewScope;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewException;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Explicit-load physical sample endpoint. No export, paging or raw SQL surface exists. */
@RestController
@RequestMapping("/api/modeling/model-specs")
public class ModelPhysicalPreviewResource {

    private final ModelPhysicalPreviewService service;
    private final WarehousePlanActorProvider actorProvider;
    private final String tenantId;

    public ModelPhysicalPreviewResource(
        ModelPhysicalPreviewService service,
        WarehousePlanActorProvider actorProvider,
        @Value("${dts.platform.modeling.default-tenant-id:default}") String tenantId
    ) {
        this.service = service;
        this.actorProvider = actorProvider;
        this.tenantId = tenantId;
    }

    @GetMapping("/{modelSpecId}/implementations/{implementationRevision}/physical-preview")
    public ResponseEntity<ApiResponse<PhysicalPreviewView>> preview(
        @PathVariable String modelSpecId,
        @PathVariable String implementationRevision,
        @RequestParam(required = false) String modelRevision,
        @RequestParam(required = false) String modelChecksum,
        @RequestParam(required = false) String implementationChecksum,
        @RequestParam(required = false) String scope,
        @RequestParam(required = false) String candidateId,
        @RequestParam(required = false) String candidateVersion,
        @RequestParam(required = false) String attempt,
        @RequestParam(required = false) String pipelineRunId,
        @RequestParam(required = false) String observationAttempt,
        @RequestParam(required = false) String relationEvidenceId,
        @RequestParam(required = false) String evidenceChecksum,
        @RequestParam(defaultValue = "STRUCTURE") String mode,
        @RequestParam(defaultValue = "100") String limit
    ) {
        PreviewScope previewScope = parseScope(scope);
        PreviewMode previewMode = parseMode(mode);
        PhysicalPreviewView view = service.preview(
            tenantId,
            actorProvider.currentActor().ownerId(),
            new PhysicalPreviewRequest(
                parseUuid(modelSpecId),
                parsePositiveInt(modelRevision),
                modelChecksum,
                parsePositiveInt(implementationRevision),
                implementationChecksum,
                previewScope,
                parseUuid(candidateId),
                parsePositiveInt(candidateVersion),
                parsePositiveInt(attempt),
                parseUuid(pipelineRunId),
                parsePositiveInt(observationAttempt),
                parseUuid(relationEvidenceId),
                evidenceChecksum,
                previewMode,
                parsePositiveInt(limit)
            )
        );
        return noStore(ApiResponses.ok(view));
    }

    @ExceptionHandler(PhysicalPreviewException.class)
    public ResponseEntity<ApiResponse<Void>> physicalPreviewFailure(PhysicalPreviewException failure) {
        return ResponseEntity
            .status(failure.status())
            .cacheControl(CacheControl.noStore().cachePrivate())
            .varyBy(HttpHeaders.AUTHORIZATION)
            .body(
                ApiResponses.error(
                    failure.errorCode(),
                    "Physical preview failed; correlationId=" + failure.correlationId()
                )
            );
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(ApiResponse<T> response) {
        return ResponseEntity
            .ok()
            .cacheControl(CacheControl.noStore().cachePrivate())
            .varyBy(HttpHeaders.AUTHORIZATION)
            .body(response);
    }

    private static PreviewScope parseScope(String value) {
        try {
            return PreviewScope.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private static PreviewMode parseMode(String value) {
        try {
            return PreviewMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private static int parsePositiveInt(String value) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : 0;
        } catch (RuntimeException invalid) {
            return 0;
        }
    }
}
