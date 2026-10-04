package com.yuzhi.dts.platform.web.rest.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogClassificationEvent;
import com.yuzhi.dts.platform.domain.catalog.CatalogClassificationPropagationJob;
import com.yuzhi.dts.platform.domain.catalog.CatalogClassificationSnapshot;
import com.yuzhi.dts.platform.service.catalog.CatalogClassificationException;
import com.yuzhi.dts.platform.service.catalog.CatalogClassificationPropagationJobService;
import com.yuzhi.dts.platform.service.catalog.CatalogClassificationPropagationService;
import com.yuzhi.dts.platform.service.catalog.CatalogClassificationService;
import com.yuzhi.dts.platform.service.catalog.dto.CatalogClassificationDtos.EventEvidence;
import com.yuzhi.dts.platform.service.catalog.dto.CatalogClassificationDtos.Explanation;
import com.yuzhi.dts.platform.service.catalog.dto.CatalogClassificationDtos.InheritRequest;
import com.yuzhi.dts.platform.service.catalog.dto.CatalogClassificationDtos.PropagationJobView;
import com.yuzhi.dts.platform.service.catalog.dto.CatalogClassificationDtos.RaiseRequest;
import com.yuzhi.dts.platform.service.catalog.dto.CatalogClassificationDtos.SealReference;
import com.yuzhi.dts.platform.service.catalog.dto.CatalogClassificationDtos.SealRequest;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import com.yuzhi.dts.platform.web.rest.ResultStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog/classifications")
public class CatalogClassificationResource {

    private static final String GOVERNANCE_WRITE =
        "hasAnyAuthority('ROLE_ADMIN','ROLE_OP_ADMIN','ROLE_GOV_ADMIN','ROLE_DATA_STEWARD','ROLE_INFRA_ADMIN')";

    private final CatalogClassificationService classificationService;
    private final CatalogClassificationPropagationService propagationService;
    private final CatalogClassificationPropagationJobService propagationJobService;

    public CatalogClassificationResource(CatalogClassificationService classificationService) {
        this(classificationService, null, null);
    }

    @Autowired
    public CatalogClassificationResource(
        CatalogClassificationService classificationService,
        CatalogClassificationPropagationService propagationService,
        CatalogClassificationPropagationJobService propagationJobService
    ) {
        this.classificationService = classificationService;
        this.propagationService = propagationService;
        this.propagationJobService = propagationJobService;
    }

    @PostMapping("/seal")
    @PreAuthorize(GOVERNANCE_WRITE)
    public ApiResponse<SealReference> seal(@RequestBody SealRequest request) {
        CatalogClassificationSnapshot snapshot = classificationService.seal(
            new CatalogClassificationService.SealCommand(
                request.subjectType(),
                request.subjectKey(),
                request.assetType(),
                request.declaredLevel(),
                request.detectedLevel(),
                request.manualFloor(),
                request.upstreamLevels(),
                request.originType(),
                request.originRef(),
                request.evidenceChecksum(),
                request.evidenceJson()
            )
        );
        return ApiResponses.ok(toReference(snapshot));
    }

    @PostMapping("/detected-level")
    @PreAuthorize(GOVERNANCE_WRITE)
    public ApiResponse<SealReference> addDetectedLevel(@RequestBody RaiseRequest request) {
        return ApiResponses.ok(
            toReference(
                classificationService.addDetectedLevel(
                    new CatalogClassificationService.LevelCommand(
                        request.subjectType(),
                        request.subjectKey(),
                        request.candidateLevel(),
                        request.triggerRef(),
                        request.evidenceJson()
                    )
                )
            )
        );
    }

    @PostMapping("/manual-floor")
    @PreAuthorize(GOVERNANCE_WRITE)
    public ApiResponse<SealReference> raiseManualFloor(@RequestBody RaiseRequest request) {
        return ApiResponses.ok(
            toReference(
                classificationService.raiseManualFloor(
                    new CatalogClassificationService.LevelCommand(
                        request.subjectType(),
                        request.subjectKey(),
                        request.candidateLevel(),
                        request.triggerRef(),
                        request.evidenceJson()
                    )
                )
            )
        );
    }

    @PostMapping("/inherit")
    @PreAuthorize(GOVERNANCE_WRITE)
    public ApiResponse<SealReference> inherit(@RequestBody InheritRequest request) {
        return ApiResponses.ok(
            toReference(
                classificationService.inherit(
                    new CatalogClassificationService.InheritCommand(
                        request.subjectType(),
                        request.subjectKey(),
                        request.upstreamLevels(),
                        request.triggerRef(),
                        request.evidenceJson()
                    )
                )
            )
        );
    }

    @GetMapping("/explain")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Explanation> explain(
        @RequestParam String subjectType,
        @RequestParam String subjectKey
    ) {
        CatalogClassificationService.Explanation explanation = classificationService.explain(subjectType, subjectKey);
        List<EventEvidence> events = explanation.events().stream().map(CatalogClassificationResource::toEvidence).toList();
        return ApiResponses.ok(new Explanation(toReference(explanation.snapshot()), events));
    }

    @GetMapping("/impact")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<CatalogClassificationPropagationService.ImpactExplanation> impact(
        @RequestParam UUID datasetId
    ) {
        requirePropagationServices();
        return ApiResponses.ok(propagationService.explainImpact(datasetId));
    }

    @PostMapping("/propagation/recompute")
    @PreAuthorize(GOVERNANCE_WRITE)
    public ApiResponse<CatalogClassificationPropagationService.PropagationResult> recompute(
        @RequestParam UUID datasetId,
        @RequestParam String triggerRef
    ) {
        requirePropagationServices();
        return ApiResponses.ok(propagationService.recompute(datasetId, triggerRef));
    }

    @PostMapping("/propagation/replay")
    @PreAuthorize(GOVERNANCE_WRITE)
    public ApiResponse<PropagationJobView> replay(@RequestParam UUID jobId) {
        requirePropagationServices();
        return ApiResponses.ok(toJobView(propagationJobService.replay(jobId)));
    }

    public static SealReference toReference(CatalogClassificationSnapshot snapshot) {
        return new SealReference(
            snapshot.getId(),
            snapshot.getSubjectType(),
            snapshot.getSubjectKey(),
            snapshot.getAssetType(),
            snapshot.getEffectiveLevel(),
            snapshot.getRecordVersion() == null ? 0L : snapshot.getRecordVersion(),
            snapshot.getEvidenceChecksum(),
            snapshot.getSealedAt(),
            snapshot.getPropagationStatus()
        );
    }

    private static EventEvidence toEvidence(CatalogClassificationEvent event) {
        return new EventEvidence(
            event.getId(),
            event.getEventType(),
            event.getPreviousLevel(),
            event.getCandidateLevel(),
            event.getResultingLevel(),
            event.getTriggerType(),
            event.getTriggerRef(),
            event.getEvidenceJson(),
            event.getActor(),
            event.getOccurredAt(),
            event.getSnapshotVersion()
        );
    }

    private static PropagationJobView toJobView(CatalogClassificationPropagationJob job) {
        return new PropagationJobView(
            job.getId(),
            job.getTargetDatasetId(),
            job.getTargetAssetKey(),
            job.getTriggerType(),
            job.getTriggerRef(),
            job.getStatus(),
            job.getAttempts(),
            job.getAffectedSubjects(),
            job.getNextAttemptAt(),
            job.getLastError()
        );
    }

    private void requirePropagationServices() {
        if (propagationService == null || propagationJobService == null) {
            throw new IllegalStateException("Classification propagation services are unavailable");
        }
    }

    @ExceptionHandler(CatalogClassificationException.class)
    public ResponseEntity<ApiResponse<Object>> handleClassificationError(
        CatalogClassificationException exception
    ) {
        HttpStatus status = switch (exception.getCode()) {
            case "CLASSIFICATION_NOT_FOUND", "CLASSIFICATION_TARGET_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "CLASSIFICATION_DOWNGRADE_FORBIDDEN", "CLASSIFICATION_LINEAGE_CYCLE" -> HttpStatus.CONFLICT;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        return ResponseEntity
            .status(status)
            .body(
                new ApiResponse<>(
                    ResultStatus.ERROR.getCode(),
                    exception.getMessage(),
                    exception.getCode(),
                    null
                )
            );
    }
}
