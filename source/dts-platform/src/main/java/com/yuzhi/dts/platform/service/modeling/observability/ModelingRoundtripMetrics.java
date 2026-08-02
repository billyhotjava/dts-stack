package com.yuzhi.dts.platform.service.modeling.observability;

import static com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ApplyResponse;
import static com.yuzhi.dts.platform.service.modeling.imports.converter.DbtArchiveInspectionContract.DbtCompatibilityView;
import static com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.PreviewSummary;
import static com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.ModelRepresentationView;
import static com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.PhysicalPreviewView;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Bounded-label operational metrics for the canonical dbt round-trip modeling control plane. */
@Component
public class ModelingRoundtripMetrics {

    public enum Operation {
        INSPECT,
        IMPORT_PREVIEW,
        IMPORT_APPLY,
        IMPORT_RETRY,
        DRAFT_PURGE,
        WORKSPACE_PURGE,
        REPRESENTATION,
        PHYSICAL_PREVIEW,
        CATALOG_PROJECTION,
        SERVING_PROMOTION,
        MATERIALIZATION_START,
        MATERIALIZATION_RETRY,
    }

    public enum Outcome {
        SUCCESS,
        PARTIAL,
        BLOCKED,
        FAILED,
        CONFLICT,
        REPLAY,
        RUNNING,
    }

    private static final String PREFIX = "dts.modeling.dbt.roundtrip";

    private final MeterRegistry registry;

    public ModelingRoundtripMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordOperation(Operation operation, Outcome outcome, long elapsedNanos) {
        String operationTag = tag(operation);
        String outcomeTag = tag(outcome);
        registry.counter(PREFIX + ".operations", "operation", operationTag, "result", outcomeTag).increment();
        registry
            .timer(PREFIX + ".duration", "operation", operationTag, "result", outcomeTag)
            .record(Duration.ofNanos(Math.max(0, elapsedNanos)));
    }

    public void recordInspect(long archiveBytes, DbtCompatibilityView compatibility) {
        DistributionSummary
            .builder(PREFIX + ".inspect.bytes")
            .baseUnit("bytes")
            .register(registry)
            .record(Math.max(0, archiveBytes));
        if (compatibility == null) return;
        recordCompatibility("inspect", compatibility.inspection() == null ? "unknown" : compatibility.inspection().name());
        recordCompatibility(
            "import_projection",
            compatibility.importProjection() == null ? "unknown" : compatibility.importProjection().name()
        );
        recordCompatibility(
            "materialization",
            compatibility.materialization() == null ? "unknown" : compatibility.materialization().name()
        );
    }

    public void recordPreview(PreviewSummary summary) {
        if (summary == null) return;
        recordItemCount("preview", "total", summary.total());
        recordItemCount("preview", "ready", summary.ready());
        recordItemCount("preview", "blocked", summary.blocked());
        recordItemCount("preview", "conflict", summary.conflict());
    }

    public void recordApply(ApplyResponse response) {
        if (response == null || response.summary() == null) return;
        var summary = response.summary();
        recordItemCount("apply", "selected", summary.selected());
        recordItemCount("apply", "created", summary.created());
        recordItemCount("apply", "updated", summary.updated());
        recordItemCount("apply", "skipped", summary.skipped());
        recordItemCount("apply", "failed", summary.failed());
        recordItemCount("apply", "blocked", summary.blocked());
    }

    public void recordCapability(ModelRepresentationView representation) {
        if (representation == null || representation.visualizationCapability() == null) return;
        registry
            .counter(PREFIX + ".representation.capability", "capability", tag(representation.visualizationCapability()))
            .increment();
    }

    public void recordPhysicalPreview(PhysicalPreviewView preview) {
        if (preview == null) return;
        String scope = preview.previewScope() == null ? "unknown" : tag(preview.previewScope());
        String mode = preview.previewMode() == null ? "unknown" : tag(preview.previewMode());
        DistributionSummary
            .builder(PREFIX + ".physical_preview.rows")
            .tags("scope", scope, "mode", mode)
            .register(registry)
            .record(Math.max(0, preview.returnedRows()));
        if (preview.maskingSummary() != null) {
            recordPreviewColumnCount(scope, "allow", preview.maskingSummary().allowedColumnCount());
            recordPreviewColumnCount(scope, "mask", preview.maskingSummary().maskedColumnCount());
            recordPreviewColumnCount(scope, "deny", preview.maskingSummary().deniedColumnCount());
        }
    }

    public void recordCleanup(String kind, int count) {
        String boundedKind = switch (kind) {
            case "draft_expired", "workspace_expired", "workspace_orphan" -> kind;
            default -> "unknown";
        };
        registry.counter(PREFIX + ".workspace.cleanup", "kind", boundedKind).increment(Math.max(0, count));
    }

    private void recordCompatibility(String axis, String state) {
        registry
            .counter(PREFIX + ".compatibility", "axis", axis, "state", state.toLowerCase(Locale.ROOT))
            .increment();
    }

    private void recordItemCount(String phase, String state, int count) {
        DistributionSummary
            .builder(PREFIX + ".items")
            .tags("phase", phase, "state", state)
            .register(registry)
            .record(Math.max(0, count));
    }

    private void recordPreviewColumnCount(String scope, String decision, int count) {
        DistributionSummary
            .builder(PREFIX + ".physical_preview.columns")
            .tags("scope", scope, "decision", decision)
            .register(registry)
            .record(Math.max(0, count));
    }

    private static String tag(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
