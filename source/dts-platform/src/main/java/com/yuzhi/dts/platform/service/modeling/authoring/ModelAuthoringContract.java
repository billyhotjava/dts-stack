package com.yuzhi.dts.platform.service.modeling.authoring;

import com.fasterxml.jackson.databind.JsonNode;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.FieldIssue;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.AuthoringOrigin;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.CommitDraftRequest;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.CommitView;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.Diagnostic;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.DraftView;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.FileInput;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.SourceBundleKind;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.ValidationView;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.AuthoringAction;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.ProjectionCoverage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Public contract for one source-neutral model authoring draft and its two views. */
public final class ModelAuthoringContract {

    private ModelAuthoringContract() {}

    public enum DraftIntent {
        EDIT_DRAFT,
        FORK_PUBLISHED,
    }

    public enum ActiveView {
        VISUAL,
        CODE,
    }

    public record CreateAuthoringDraftRequest(
        @NotNull DraftIntent intent,
        @Positive int baseModelRevision,
        @NotBlank @Size(min = 64, max = 64) String baseModelChecksum,
        @Positive Integer baseImplementationRevision,
        @Size(min = 64, max = 64) String baseImplementationChecksum,
        @Size(max = 63) String targetPhysicalName,
        @NotBlank @Size(max = 128) String idempotencyKey
    ) {}

    public record SaveAuthoringDraftRequest(
        @NotBlank @Size(max = 64) String expectedEtag,
        @NotNull JsonNode modelSpecSnapshot,
        @NotNull @Size(max = 128) List<@NotNull @Valid FileInput> files,
        @NotNull ActiveView activeView
    ) {
        public SaveAuthoringDraftRequest {
            files = List.copyOf(files == null ? List.of() : files);
        }
    }

    public record AuthoringProvenance(
        AuthoringOrigin origin,
        SourceBundleKind sourceKind,
        boolean lossless,
        String bundleChecksum
    ) {}

    public record ProjectionNode(
        String nodeId,
        String kind,
        boolean editable,
        String sourcePath,
        Integer line,
        Integer column,
        String checksum
    ) {}

    public record AuthoringProjection(
        ProjectionCoverage coverage,
        boolean lossless,
        List<String> managedPaths,
        List<ProjectionNode> rawNodes,
        List<String> reasons
    ) {
        public AuthoringProjection {
            coverage = coverage == null ? ProjectionCoverage.UNKNOWN : coverage;
            managedPaths = List.copyOf(managedPaths == null ? List.of() : managedPaths);
            rawNodes = List.copyOf(rawNodes == null ? List.of() : rawNodes);
            reasons = List.copyOf(reasons == null ? List.of() : reasons);
        }

        public static AuthoringProjection unknown(String reason) {
            return new AuthoringProjection(
                ProjectionCoverage.UNKNOWN,
                false,
                List.of(),
                List.of(),
                reason == null ? List.of() : List.of(reason)
            );
        }
    }

    public record AuthoringContextView(
        ModelSpecView model,
        ImplementationView implementation,
        AuthoringProvenance provenance,
        AuthoringProjection projection,
        DraftView openDraft,
        List<AuthoringAction> allowedActions,
        boolean publishedForkRequired
    ) {
        public AuthoringContextView {
            allowedActions = List.copyOf(allowedActions == null ? List.of() : allowedActions);
        }
    }

    public record AuthoringDraftView(
        ModelSpecView model,
        DraftView draft,
        AuthoringProvenance provenance,
        AuthoringProjection projection,
        List<AuthoringAction> allowedActions
    ) {
        public AuthoringDraftView {
            allowedActions = List.copyOf(allowedActions == null ? List.of() : allowedActions);
        }
    }

    public record SaveAuthoringDraftView(
        UUID draftId,
        String etag,
        JsonNode modelSpecSnapshot,
        AuthoringProjection projection,
        int fileCount,
        long totalBytes,
        List<FileInput> files
    ) {
        public SaveAuthoringDraftView {
            files = List.copyOf(files == null ? List.of() : files);
        }
    }

    public record ValidateAuthoringDraftView(
        ValidationView implementationValidation,
        List<FieldIssue> modelIssues,
        List<Diagnostic> projectionIssues
    ) {
        public ValidateAuthoringDraftView {
            modelIssues = List.copyOf(modelIssues == null ? List.of() : modelIssues);
            projectionIssues = List.copyOf(projectionIssues == null ? List.of() : projectionIssues);
        }
    }

    public record CommitAuthoringDraftRequest(@NotNull @Valid CommitDraftRequest implementation) {}

    public record CommitAuthoringDraftView(CommitView receipt, AuthoringOrigin origin) {}
}
