package com.yuzhi.dts.platform.service.modeling.representation;

import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.CapabilityReason;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.RepresentationScope;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.VisualizationCapability;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Keeps implementation ownership and visualization capability as independent decisions. */
@Component
public class ModelVisualizationCapabilityEvaluator {

    public record ProjectionTrust(
        boolean implementationPresent,
        boolean fieldsTrusted,
        boolean dependenciesTrusted,
        boolean artifactPinsExact,
        boolean dynamicDependencies,
        boolean runtimeEvidenceStale
    ) {}

    public record CapabilityDecision(VisualizationCapability capability, List<CapabilityReason> reasons) {
        public CapabilityDecision {
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
        }
    }

    public CapabilityDecision evaluate(RepresentationScope scope, ImplementationMode ownership, ProjectionTrust trust) {
        if (trust == null || !trust.implementationPresent()) {
            if (scope == RepresentationScope.TECHNICAL && ownership == ImplementationMode.DBT_MANAGED) {
                return new CapabilityDecision(VisualizationCapability.ADVANCED_DBT_IMPLEMENTATION, List.of());
            }
            return blocked(CapabilityReason.MODEL_REPRESENTATION_NO_IMPLEMENTATION);
        }

        List<CapabilityReason> reasons = new ArrayList<>();
        if (!trust.artifactPinsExact()) reasons.add(CapabilityReason.MODEL_REPRESENTATION_ARTIFACT_PIN_MISMATCH);
        if (!trust.fieldsTrusted()) reasons.add(CapabilityReason.MODEL_REPRESENTATION_FIELDS_UNTRUSTED);
        if (trust.dynamicDependencies()) reasons.add(CapabilityReason.MODEL_REPRESENTATION_DYNAMIC_DEPENDENCY);
        if (!trust.dependenciesTrusted()) reasons.add(CapabilityReason.MODEL_REPRESENTATION_DEPENDENCIES_UNTRUSTED);
        if (trust.runtimeEvidenceStale()) reasons.add(CapabilityReason.MODEL_REPRESENTATION_RUNTIME_EVIDENCE_STALE);

        boolean projectionBlocked = reasons
            .stream()
            .anyMatch(reason -> reason != CapabilityReason.MODEL_REPRESENTATION_RUNTIME_EVIDENCE_STALE);
        if (projectionBlocked) return new CapabilityDecision(VisualizationCapability.BLOCKED, reasons);

        if (scope == RepresentationScope.TECHNICAL) {
            return new CapabilityDecision(
                ownership == ImplementationMode.DBT_MANAGED
                    ? VisualizationCapability.ADVANCED_DBT_IMPLEMENTATION
                    : VisualizationCapability.DESIGNER_DBT_PREVIEW,
                reasons
            );
        }
        VisualizationCapability capability = ownership == ImplementationMode.DESIGNER_GENERATED
            ? VisualizationCapability.BUSINESS_VISUAL_EDIT
            : VisualizationCapability.BUSINESS_VISUAL_READ;
        return new CapabilityDecision(capability, reasons);
    }

    private static CapabilityDecision blocked(CapabilityReason reason) {
        return new CapabilityDecision(VisualizationCapability.BLOCKED, List.of(reason));
    }
}
