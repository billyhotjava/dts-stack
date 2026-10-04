package com.yuzhi.dts.platform.service.modeling.observability;

import static com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.DraftException;
import static com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ApplyResponse;
import static com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ModelSpecImportApplyException;
import static com.yuzhi.dts.platform.service.modeling.imports.converter.DbtArchiveInspectionContract.InspectArchiveResponse;
import static com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.ModelSpecImportPreviewException;
import static com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.PreviewResponse;
import static com.yuzhi.dts.platform.service.modeling.observability.ModelingRoundtripMetrics.Operation;
import static com.yuzhi.dts.platform.service.modeling.observability.ModelingRoundtripMetrics.Outcome;
import static com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.ModelRepresentationView;
import static com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.PhysicalPreviewView;

import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftRetentionService.PurgeResult;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewException;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import java.util.Locale;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** Applies observability without coupling domain services to Micrometer or duplicating audit payloads. */
@Aspect
@Component
public class ModelingRoundtripMetricsAspect {

    private final ModelingRoundtripMetrics metrics;

    public ModelingRoundtripMetricsAspect(ModelingRoundtripMetrics metrics) {
        this.metrics = metrics;
    }

    @Around("execution(* com.yuzhi.dts.platform.web.rest.ModelSpecImportResource.inspectArchive(..))")
    public Object inspectArchive(ProceedingJoinPoint joinPoint) throws Throwable {
        return observe(joinPoint, Operation.INSPECT);
    }

    @Around("execution(* com.yuzhi.dts.platform.web.rest.ModelSpecImportResource.preview(..))")
    public Object importPreview(ProceedingJoinPoint joinPoint) throws Throwable {
        return observe(joinPoint, Operation.IMPORT_PREVIEW);
    }

    @Around(
        "execution(* com.yuzhi.dts.platform.web.rest.ModelSpecImportResource.apply(..)) || " +
        "execution(* com.yuzhi.dts.platform.web.rest.ModelSpecImportResource.retry(..))"
    )
    public Object importApply(ProceedingJoinPoint joinPoint) throws Throwable {
        return observe(joinPoint, methodName(joinPoint).equals("retry") ? Operation.IMPORT_RETRY : Operation.IMPORT_APPLY);
    }

    @Around("execution(* com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationService.get(..))")
    public Object representation(ProceedingJoinPoint joinPoint) throws Throwable {
        return observe(joinPoint, Operation.REPRESENTATION);
    }

    @Around("execution(* com.yuzhi.dts.platform.service.modeling.serving.ModelPhysicalPreviewService.preview(..))")
    public Object physicalPreview(ProceedingJoinPoint joinPoint) throws Throwable {
        return observe(joinPoint, Operation.PHYSICAL_PREVIEW);
    }

    @Around(
        "execution(* com.yuzhi.dts.platform.service.modeling.serving.CatalogModelServingService.projectSuccessfulPublication(..)) || " +
        "execution(* com.yuzhi.dts.platform.service.modeling.serving.CatalogModelServingService.projectLatestPublication(..))"
    )
    public Object catalogProjection(ProceedingJoinPoint joinPoint) throws Throwable {
        return observe(joinPoint, Operation.CATALOG_PROJECTION);
    }

    @Around("execution(* com.yuzhi.dts.platform.service.modeling.serving.CatalogModelServingService.promoteSuccessfulServing(..))")
    public Object servingPromotion(ProceedingJoinPoint joinPoint) throws Throwable {
        return observe(joinPoint, Operation.SERVING_PROMOTION);
    }

    @Around(
        "execution(* com.yuzhi.dts.platform.service.modeling.ModelMaterializationStartService.start(..)) || " +
        "execution(* com.yuzhi.dts.platform.service.modeling.ModelMaterializationStartService.startWithBuild(..)) || " +
        "execution(* com.yuzhi.dts.platform.service.modeling.ModelMaterializationStartService.retry(..))"
    )
    public Object materialization(ProceedingJoinPoint joinPoint) throws Throwable {
        return observe(
            joinPoint,
            methodName(joinPoint).equals("retry") ? Operation.MATERIALIZATION_RETRY : Operation.MATERIALIZATION_START
        );
    }

    @Around("execution(* com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftRetentionService.purgeExpired(..))")
    public Object draftPurge(ProceedingJoinPoint joinPoint) throws Throwable {
        return observe(joinPoint, Operation.DRAFT_PURGE);
    }

    @Around("execution(* com.yuzhi.dts.platform.service.modeling.imports.converter.AdvancedDbtDraftStaticValidator.purgeOrphanedWorkspaces(..))")
    public Object workspacePurge(ProceedingJoinPoint joinPoint) throws Throwable {
        return observe(joinPoint, Operation.WORKSPACE_PURGE);
    }

    private Object observe(ProceedingJoinPoint joinPoint, Operation operation) throws Throwable {
        long started = System.nanoTime();
        try {
            Object result = joinPoint.proceed();
            Object payload = unwrap(result);
            metrics.recordOperation(operation, outcome(payload), System.nanoTime() - started);
            recordPayload(operation, payload, joinPoint.getArgs());
            return result;
        } catch (Throwable failure) {
            metrics.recordOperation(operation, outcome(failure), System.nanoTime() - started);
            throw failure;
        }
    }

    private void recordPayload(Operation operation, Object payload, Object[] arguments) {
        if (payload instanceof InspectArchiveResponse response) {
            metrics.recordInspect(archiveBytes(arguments), response.compatibility());
        } else if (payload instanceof PreviewResponse response) {
            metrics.recordPreview(response.summary());
        } else if (payload instanceof ApplyResponse response) {
            metrics.recordApply(response);
        } else if (payload instanceof ModelRepresentationView representation) {
            metrics.recordCapability(representation);
        } else if (payload instanceof PhysicalPreviewView preview) {
            metrics.recordPhysicalPreview(preview);
        } else if (payload instanceof PurgeResult purge) {
            metrics.recordCleanup("draft_expired", purge.draftCount());
            metrics.recordCleanup("workspace_expired", purge.workspaceCount());
        } else if (operation == Operation.WORKSPACE_PURGE && payload instanceof Integer count) {
            metrics.recordCleanup("workspace_orphan", count);
        }
    }

    private static Object unwrap(Object value) {
        return value instanceof ApiResponse<?> response ? response.getData() : value;
    }

    private static Outcome outcome(Object value) {
        if (value instanceof ApplyResponse response && response.status() != null) {
            return switch (response.status()) {
                case SUCCESS -> Outcome.SUCCESS;
                case PARTIAL -> Outcome.PARTIAL;
                case BLOCKED -> Outcome.BLOCKED;
                case FAILED -> Outcome.FAILED;
                case RUNNING -> Outcome.RUNNING;
            };
        }
        return Outcome.SUCCESS;
    }

    private static Outcome outcome(Throwable failure) {
        String code = null;
        if (failure instanceof ModelSpecImportApplyException exception) code = exception.code();
        else if (failure instanceof ModelSpecImportPreviewException exception) code = exception.code();
        else if (failure instanceof PhysicalPreviewException exception) code = exception.errorCode();
        else if (failure instanceof DraftException exception) code = exception.code();
        String normalized = code == null ? "" : code.toUpperCase(Locale.ROOT);
        if (normalized.contains("CONFLICT") || normalized.contains("STALE") || normalized.contains("CAS")) {
            return Outcome.CONFLICT;
        }
        if (
            normalized.contains("BLOCK") ||
            normalized.contains("DENY") ||
            normalized.contains("NOT_READY") ||
            normalized.contains("NOT_CERTIFIED") ||
            normalized.contains("UNAVAILABLE")
        ) {
            return Outcome.BLOCKED;
        }
        return Outcome.FAILED;
    }

    private static long archiveBytes(Object[] arguments) {
        if (arguments == null) return 0;
        for (Object argument : arguments) {
            if (argument instanceof MultipartFile archive) return Math.max(0, archive.getSize());
        }
        return 0;
    }

    private static String methodName(ProceedingJoinPoint joinPoint) {
        return joinPoint.getSignature() instanceof MethodSignature signature
            ? signature.getMethod().getName()
            : joinPoint.getSignature().getName();
    }
}
