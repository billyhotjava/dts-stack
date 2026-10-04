package com.yuzhi.dts.platform.service.modeling.serving;

import com.yuzhi.dts.platform.service.catalog.CatalogAssetType;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.ExpectedRelationType;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.PhysicalColumn;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Request, policy and result types for controlled physical sample preview. */
public final class PhysicalPreviewContract {

    private PhysicalPreviewContract() {}

    public enum PreviewScope {
        SERVING,
        CANDIDATE,
    }

    public enum PreviewMode {
        STRUCTURE,
        SAMPLE,
    }

    public enum ColumnPolicy {
        ALLOW,
        MASK,
        DENY,
        UNKNOWN,
    }

    public record PhysicalPreviewRequest(
        UUID modelSpecId,
        int modelRevision,
        String modelChecksum,
        int implementationRevision,
        String implementationChecksum,
        PreviewScope scope,
        UUID candidateId,
        int candidateVersion,
        int attempt,
        UUID pipelineRunId,
        int observationAttempt,
        UUID relationEvidenceId,
        String evidenceChecksum,
        PreviewMode mode,
        int limit
    ) {}

    public record RelationEvidence(
        String tenantId,
        UUID relationEvidenceId,
        UUID modelSpecId,
        int modelRevision,
        String modelChecksum,
        int implementationRevision,
        String implementationChecksum,
        UUID candidateId,
        int candidateVersion,
        String candidateStatus,
        int attempt,
        String dispatchStatus,
        UUID pipelineRunId,
        String pipelineStatus,
        int observationAttempt,
        String adapter,
        String credentialVersionRef,
        String databaseName,
        String schemaName,
        String identifier,
        ExpectedRelationType actualType,
        boolean relationExists,
        boolean verified,
        List<PhysicalColumn> columns,
        String evidenceChecksum,
        Instant observedAt
    ) {
        public RelationEvidence {
            columns = columns == null ? List.of() : List.copyOf(columns);
        }
    }

    public record AccessContext(UUID physicalAssetId) {}

    public record ColumnDecision(ColumnPolicy policy, String maskingStrategy) {}

    public record PolicyDecision(String classification, Map<String, ColumnDecision> columns) {
        public PolicyDecision {
            columns = columns == null ? Map.of() : Map.copyOf(columns);
        }
    }

    public record QueryResult(List<Map<String, Object>> rows, Instant queriedAt, boolean truncated) {
        public QueryResult {
            rows = rows == null ? List.of() : List.copyOf(rows);
        }
    }

    public record PreviewColumn(
        int ordinalPosition,
        String name,
        String dataType,
        boolean nullable,
        ColumnPolicy policy
    ) {}

    public record MaskingSummary(int allowedColumnCount, int maskedColumnCount, int deniedColumnCount) {}

    public record PhysicalPreviewView(
        UUID modelSpecId,
        int modelRevision,
        int implementationRevision,
        PreviewMode previewMode,
        PreviewScope previewScope,
        UUID candidateId,
        int candidateVersion,
        int attempt,
        UUID pipelineRunId,
        int observationAttempt,
        UUID relationEvidenceId,
        String evidenceChecksum,
        CatalogAssetType catalogAssetType,
        String catalogAssetKey,
        String relationRef,
        Instant observedAt,
        Instant queriedAt,
        List<PreviewColumn> columns,
        List<Map<String, Object>> maskedRows,
        MaskingSummary maskingSummary,
        int returnedRows,
        boolean truncated,
        String driftStatus,
        String correlationId,
        String candidateLabel
    ) {}
}
