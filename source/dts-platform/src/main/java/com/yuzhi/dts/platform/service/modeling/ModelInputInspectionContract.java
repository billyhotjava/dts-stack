package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.UpstreamModelInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelRevisionRef;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Auxiliary read contracts; persisted lifecycle and implementation contracts are unchanged. */
public final class ModelInputInspectionContract {
    private ModelInputInspectionContract() {}

    public interface Diagnostic { UUID modelSpecId(); String fieldPath(); String reason(); }
    public record Issue(UUID modelSpecId, String reason, UpstreamModelInput expected, UpstreamModelInput actual,
                        String fieldPath) implements Diagnostic {}
    public static Map<String, Object> details(List<? extends Diagnostic> issues) {
        var order = java.util.Comparator.comparing((Diagnostic issue) -> java.util.Objects.toString(issue.modelSpecId(), ""))
            .thenComparing(issue -> java.util.Objects.toString(issue.fieldPath(), "")).thenComparing(Diagnostic::reason);
        return Map.of("issues", issues.stream().sorted(order).limit(50).toList(), "totalIssues", issues.size(), "truncated", issues.size() > 50);
    }
    public record Request(UUID ownerModelSpecId, int ownerRevision, String ownerChecksum,
                          List<UUID> modelSpecIds, List<UpstreamModelInput> selectedInputs) {}
    public record Item(UUID modelSpecId, Integer revision, String checksum, String implementationState,
                       UpstreamModelInput currentPin, boolean selectable, String blockReason, String referenceState,
                       UpstreamModelInput selectedPin, String referenceReason) {}
    public record View(UUID ownerModelSpecId, int ownerRevision, String ownerChecksum, Instant observedAt, List<Item> items) {}
    public record Context(Map<UUID, ModelSpecView> heads, Map<UUID, ImplementationView> implementations,
                          Map<ModelRevisionRef, ModelSpecView> revisions) {}
}
