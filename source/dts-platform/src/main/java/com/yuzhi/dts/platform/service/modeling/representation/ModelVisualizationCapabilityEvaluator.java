package com.yuzhi.dts.platform.service.modeling.representation;

import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.AuthoringAction;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.AuthoringDraftState;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.CapabilityReason;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.ProjectionCoverage;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.RepresentationScope;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.VisualizationCapability;
import java.util.ArrayList;
import java.util.EnumSet;
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

    public record AuthoringCapabilityDecision(List<AuthoringAction> allowedActions) {
        public AuthoringCapabilityDecision {
            allowedActions = allowedActions == null ? List.of() : List.copyOf(allowedActions);
        }
    }

    public CapabilityDecision evaluate(RepresentationScope scope, ImplementationMode ownership, ProjectionTrust trust) {
        if (trust == null) return blocked(CapabilityReason.MODEL_REPRESENTATION_SCHEMA_INVALID);

        List<CapabilityReason> reasons = new ArrayList<>();
        if (!trust.implementationPresent()) {
            reasons.add(CapabilityReason.MODEL_REPRESENTATION_NO_IMPLEMENTATION);
        } else {
            if (!trust.artifactPinsExact()) reasons.add(CapabilityReason.MODEL_REPRESENTATION_ARTIFACT_PIN_MISMATCH);
            if (!trust.fieldsTrusted()) reasons.add(CapabilityReason.MODEL_REPRESENTATION_FIELDS_UNTRUSTED);
            if (trust.dynamicDependencies()) reasons.add(CapabilityReason.MODEL_REPRESENTATION_DYNAMIC_DEPENDENCY);
            if (!trust.dependenciesTrusted()) reasons.add(CapabilityReason.MODEL_REPRESENTATION_DEPENDENCIES_UNTRUSTED);
            if (trust.runtimeEvidenceStale()) reasons.add(CapabilityReason.MODEL_REPRESENTATION_RUNTIME_EVIDENCE_STALE);
        }

        if (scope == RepresentationScope.TECHNICAL) {
            if (trust.implementationPresent() && !trust.artifactPinsExact()) {
                return new CapabilityDecision(VisualizationCapability.BLOCKED, reasons);
            }
            return new CapabilityDecision(VisualizationCapability.ADVANCED_DBT_IMPLEMENTATION, reasons);
        }
        return new CapabilityDecision(VisualizationCapability.BUSINESS_VISUAL_EDIT, reasons);
    }

    /**
     * Computes unified authoring actions without accepting implementation ownership/provenance as input.
     * Projection coverage only gates structured implementation editing; business metadata remains editable.
     */
    public AuthoringCapabilityDecision authoringActions(
        ModelStatus status,
        boolean technicalAuthorized,
        boolean maintainAuthorized,
        AuthoringDraftState draftState,
        ProjectionCoverage coverage
    ) {
        EnumSet<AuthoringAction> actions = EnumSet.of(AuthoringAction.OPEN_VISUAL);
        if (technicalAuthorized) actions.add(AuthoringAction.OPEN_CODE);
        if (!maintainAuthorized || status == null || status == ModelStatus.ARCHIVED) {
            return new AuthoringCapabilityDecision(List.copyOf(actions));
        }
        if (status == ModelStatus.PUBLISHED) {
            actions.add(AuthoringAction.FORK_DRAFT);
            return new AuthoringCapabilityDecision(List.copyOf(actions));
        }
        if (status != ModelStatus.DRAFT) return new AuthoringCapabilityDecision(List.copyOf(actions));

        actions.add(AuthoringAction.EDIT_MODEL);
        actions.add(AuthoringAction.SAVE);
        if (technicalAuthorized) {
            actions.add(AuthoringAction.VALIDATE);
            actions.add(AuthoringAction.EDIT_IMPLEMENTATION);
            if (draftState == AuthoringDraftState.VALIDATED) actions.add(AuthoringAction.COMMIT);
        }
        return new AuthoringCapabilityDecision(List.copyOf(actions));
    }

    private static CapabilityDecision blocked(CapabilityReason reason) {
        return new CapabilityDecision(VisualizationCapability.BLOCKED, List.of(reason));
    }
}
