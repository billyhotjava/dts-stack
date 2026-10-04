package com.yuzhi.dts.platform.service.modeling.serving;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.repository.modeling.CatalogModelServingProjectionRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetType;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.ExpectedRelationType;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.PhysicalColumn;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelServingContract.ModelServingProjection;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelServingContract.ServingRef;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.AccessContext;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.ColumnDecision;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.ColumnPolicy;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.PhysicalPreviewRequest;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.PolicyDecision;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.PreviewMode;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.PreviewScope;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.QueryResult;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.RelationEvidence;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ModelPhysicalPreviewServiceTest {

    private static final String TENANT = "tenant-a";
    private static final UUID MODEL_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID CANDIDATE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID PIPELINE_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID EVIDENCE_ID = UUID.fromString("60000000-0000-0000-0000-000000000001");
    private static final UUID PHYSICAL_ASSET_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-02T10:00:00Z");

    @Mock private ModelSpecApplicationService modelSpecs;
    @Mock private CatalogModelServingProjectionRepository repository;
    @Mock private PhysicalPreviewAccessPort access;
    @Mock private PhysicalPreviewPolicyPort policies;
    @Mock private PhysicalPreviewQueryPort queries;
    @Mock private AuditService audit;
    @Mock private ModelSpecView model;

    private ModelPhysicalPreviewService service;

    @BeforeEach
    void setUp() {
        service = new ModelPhysicalPreviewService(
            modelSpecs,
            repository,
            access,
            policies,
            queries,
            audit,
            Clock.fixed(NOW, ZoneOffset.UTC),
            () -> "corr-1"
        );
        lenient().when(modelSpecs.get(TENANT, MODEL_ID)).thenReturn(model);
        lenient().when(model.id()).thenReturn(MODEL_ID);
        lenient().when(model.revision()).thenReturn(4);
    }

    @Test
    void rejectsAnyEvidencePinMismatchBeforePolicyOrWarehouseAccess() {
        RelationEvidence evidence = evidence("finance", "dwd_budget", "c".repeat(64));
        when(repository.findProjectionForPreview(TENANT, MODEL_ID)).thenReturn(Optional.of(projection(evidence)));
        when(repository.findRelationEvidenceForPreview(TENANT, EVIDENCE_ID)).thenReturn(Optional.of(evidence));

        assertThatThrownBy(() -> service.preview(TENANT, "alice", request("d".repeat(64), 100)))
            .isInstanceOf(PhysicalPreviewException.class)
            .extracting(error -> ((PhysicalPreviewException) error).errorCode())
            .isEqualTo("PHYSICAL_PREVIEW_EVIDENCE_MISMATCH");

        verify(access, never()).authorize(any(), any(), any(), any());
        verify(queries, never()).query(any(), any(), any(Integer.class));
    }

    @Test
    void rejectsMaliciousObservationIdentifierBeforeOpeningWarehouse() {
        RelationEvidence evidence = evidence("finance", "budget;drop_table", "c".repeat(64));
        when(repository.findProjectionForPreview(TENANT, MODEL_ID)).thenReturn(Optional.of(projection(evidence)));
        when(repository.findRelationEvidenceForPreview(TENANT, EVIDENCE_ID)).thenReturn(Optional.of(evidence));

        assertThatThrownBy(() -> service.preview(TENANT, "alice", request("c".repeat(64), 100)))
            .isInstanceOf(PhysicalPreviewException.class)
            .extracting(error -> ((PhysicalPreviewException) error).errorCode())
            .isEqualTo("PHYSICAL_PREVIEW_IDENTIFIER_INVALID");

        verify(access, never()).authorize(any(), any(), any(), any());
        verify(queries, never()).query(any(), any(), any(Integer.class));
    }

    @Test
    void appliesAllowMaskAndDenyAndAuditsOnlyCounts() {
        RelationEvidence evidence = evidence("finance", "dwd_budget", "c".repeat(64));
        when(repository.findProjectionForPreview(TENANT, MODEL_ID)).thenReturn(Optional.of(projection(evidence)));
        when(repository.findRelationEvidenceForPreview(TENANT, EVIDENCE_ID)).thenReturn(Optional.of(evidence));
        when(access.authorize(any(), any(), any(), any())).thenReturn(new AccessContext(PHYSICAL_ASSET_ID));
        when(policies.resolve(any(), any(), any(), any()))
            .thenReturn(
                new PolicyDecision(
                    "DATA_INTERNAL",
                    Map.of(
                        "account_code", new ColumnDecision(ColumnPolicy.ALLOW, null),
                        "amount", new ColumnDecision(ColumnPolicy.MASK, "REDACT"),
                        "secret_note", new ColumnDecision(ColumnPolicy.DENY, null)
                    )
                )
            );
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("account_code", "A100");
        row.put("amount", "123.45");
        row.put("secret_note", "never-return");
        when(queries.query(eq(evidence), eq(List.of("account_code", "amount")), eq(100)))
            .thenReturn(new QueryResult(List.of(row), NOW.minusSeconds(1), false));

        var view = service.preview(TENANT, "alice", request("c".repeat(64), 100));

        assertThat(view.columns()).extracting(PhysicalPreviewContract.PreviewColumn::name)
            .containsExactly("account_code", "amount");
        assertThat(view.maskedRows()).containsExactly(Map.of("account_code", "A100", "amount", "***"));
        assertThat(view.maskingSummary().maskedColumnCount()).isEqualTo(1);
        assertThat(view.maskingSummary().deniedColumnCount()).isEqualTo(1);
        verify(audit).auditActionStrict(eq("MODELING_PHYSICAL_PREVIEW"), any(), eq(MODEL_ID.toString()), any());
        verify(audit, never()).auditAction(eq("MODELING_PHYSICAL_PREVIEW"), any(), eq(MODEL_ID.toString()), any());
    }

    @Test
    void structureRechecksEvidenceWithoutSelectingRowsAndMarksMaskedColumns() {
        RelationEvidence evidence = evidence("finance", "dwd_budget", "c".repeat(64));
        when(repository.findProjectionForPreview(TENANT, MODEL_ID)).thenReturn(Optional.of(projection(evidence)));
        when(repository.findRelationEvidenceForPreview(TENANT, EVIDENCE_ID)).thenReturn(Optional.of(evidence));
        when(access.authorize(any(), any(), any(), any())).thenReturn(new AccessContext(PHYSICAL_ASSET_ID));
        when(policies.resolve(any(), any(), any(), any()))
            .thenReturn(
                new PolicyDecision(
                    "DATA_INTERNAL",
                    Map.of(
                        "account_code", new ColumnDecision(ColumnPolicy.ALLOW, null),
                        "amount", new ColumnDecision(ColumnPolicy.MASK, "REDACT"),
                        "secret_note", new ColumnDecision(ColumnPolicy.DENY, null)
                    )
                )
            );
        when(queries.verifyStructure(evidence)).thenReturn(NOW.minusSeconds(1));
        PhysicalPreviewRequest structure = new PhysicalPreviewRequest(
            MODEL_ID, 4, "a".repeat(64), 7, "b".repeat(64), PreviewScope.SERVING, CANDIDATE_ID, 11, 2,
            PIPELINE_ID, 1, EVIDENCE_ID, "c".repeat(64), PreviewMode.STRUCTURE, 9999
        );

        var view = service.preview(TENANT, "alice", structure);

        assertThat(view.previewMode()).isEqualTo(PreviewMode.STRUCTURE);
        assertThat(view.returnedRows()).isZero();
        assertThat(view.maskedRows()).isEmpty();
        assertThat(view.columns()).extracting(
            PhysicalPreviewContract.PreviewColumn::name,
            PhysicalPreviewContract.PreviewColumn::policy
        ).containsExactly(
            org.assertj.core.groups.Tuple.tuple("account_code", ColumnPolicy.ALLOW),
            org.assertj.core.groups.Tuple.tuple("amount", ColumnPolicy.MASK)
        );
        verify(queries).verifyStructure(evidence);
        verify(queries, never()).query(any(), any(), any(Integer.class));
    }

    @Test
    void blocksUnknownPolicyWithoutWarehouseQuery() {
        RelationEvidence evidence = evidence("finance", "dwd_budget", "c".repeat(64));
        when(repository.findProjectionForPreview(TENANT, MODEL_ID)).thenReturn(Optional.of(projection(evidence)));
        when(repository.findRelationEvidenceForPreview(TENANT, EVIDENCE_ID)).thenReturn(Optional.of(evidence));
        when(access.authorize(any(), any(), any(), any())).thenReturn(new AccessContext(PHYSICAL_ASSET_ID));
        when(policies.resolve(any(), any(), any(), any()))
            .thenReturn(new PolicyDecision("DATA_INTERNAL", Map.of("amount", new ColumnDecision(ColumnPolicy.UNKNOWN, null))));

        assertThatThrownBy(() -> service.preview(TENANT, "alice", request("c".repeat(64), 100)))
            .isInstanceOf(PhysicalPreviewException.class)
            .extracting(error -> ((PhysicalPreviewException) error).errorCode())
            .isEqualTo("PHYSICAL_PREVIEW_MASKING_UNAVAILABLE");
        verify(queries, never()).query(any(), any(), any(Integer.class));
    }

    @Test
    void rejectsHistoricalRowsAndLimitAboveFiveHundred() {
        when(model.revision()).thenReturn(5);
        assertThatThrownBy(() -> service.preview(TENANT, "alice", request("c".repeat(64), 100)))
            .isInstanceOf(PhysicalPreviewException.class)
            .extracting(error -> ((PhysicalPreviewException) error).errorCode())
            .isEqualTo("PHYSICAL_PREVIEW_HISTORICAL_ROWS_NOT_REPRODUCIBLE");
        assertThatThrownBy(() -> service.preview(TENANT, "alice", request("c".repeat(64), 501)))
            .isInstanceOf(PhysicalPreviewException.class)
            .extracting(error -> ((PhysicalPreviewException) error).errorCode())
            .isEqualTo("PHYSICAL_PREVIEW_LIMIT_EXCEEDED");
        verify(queries, never()).query(any(), any(), any(Integer.class));
    }

    @Test
    void invalidTypedRequestStillProducesStrictFailureAudit() {
        PhysicalPreviewRequest invalid = new PhysicalPreviewRequest(
            MODEL_ID, 4, "a".repeat(64), 7, "b".repeat(64), PreviewScope.SERVING, CANDIDATE_ID, 11, 2,
            PIPELINE_ID, 1, EVIDENCE_ID, "c".repeat(64), null, 100
        );

        assertThatThrownBy(() -> service.preview(TENANT, "alice", invalid))
            .isInstanceOf(PhysicalPreviewException.class)
            .extracting(error -> ((PhysicalPreviewException) error).errorCode())
            .isEqualTo("PHYSICAL_PREVIEW_EVIDENCE_MISMATCH");

        verify(audit).auditActionStrict(eq("MODELING_PHYSICAL_PREVIEW"), eq(AuditStage.FAIL), eq(MODEL_ID.toString()), any());
    }

    @Test
    void auditInfrastructureFailureIsNeverSwallowedOnPreviewFailure() {
        IllegalStateException auditFailure = new IllegalStateException("audit-down");
        doThrow(auditFailure)
            .when(audit)
            .auditActionStrict(eq("MODELING_PHYSICAL_PREVIEW"), eq(AuditStage.FAIL), eq(MODEL_ID.toString()), any());

        assertThatThrownBy(() -> service.preview(TENANT, "alice", request("c".repeat(64), 501)))
            .isSameAs(auditFailure);
    }

    @Test
    void candidatePreviewRejectsAReplayedOlderSuccessfulBuildPin() {
        RelationEvidence stale = evidence("finance", "dwd_budget", "c".repeat(64));
        RelationEvidence current = new RelationEvidence(
            TENANT,
            UUID.fromString("60000000-0000-0000-0000-000000000002"),
            MODEL_ID,
            4,
            "a".repeat(64),
            7,
            "b".repeat(64),
            CANDIDATE_ID,
            12,
            "PUBLISHED",
            3,
            "COMPLETED",
            UUID.fromString("50000000-0000-0000-0000-000000000002"),
            "BUILT",
            1,
            "postgres",
            "sha256:" + "9".repeat(64),
            "warehouse",
            "finance",
            "dwd_budget_v2",
            ExpectedRelationType.TABLE,
            true,
            true,
            stale.columns(),
            "f".repeat(64),
            NOW
        );
        when(repository.findProjectionForPreview(TENANT, MODEL_ID)).thenReturn(Optional.empty());
        when(repository.findRelationEvidenceForPreview(TENANT, EVIDENCE_ID)).thenReturn(Optional.of(stale));
        when(repository.findLatestSuccessfulCandidateEvidence(
            TENANT,
            MODEL_ID,
            4,
            "a".repeat(64),
            7,
            "b".repeat(64)
        )).thenReturn(Optional.of(current));
        PhysicalPreviewRequest replayed = new PhysicalPreviewRequest(
            MODEL_ID, 4, "a".repeat(64), 7, "b".repeat(64), PreviewScope.CANDIDATE,
            CANDIDATE_ID, 11, 2, PIPELINE_ID, 1, EVIDENCE_ID, "c".repeat(64),
            PreviewMode.SAMPLE, 100
        );

        assertThatThrownBy(() -> service.preview(TENANT, "alice", replayed))
            .isInstanceOf(PhysicalPreviewException.class)
            .extracting(error -> ((PhysicalPreviewException) error).errorCode())
            .isEqualTo("PHYSICAL_PREVIEW_EVIDENCE_MISMATCH");

        verify(access, never()).authorize(any(), any(), any(), any());
        verify(queries, never()).query(any(), any(), any(Integer.class));
    }

    private static PhysicalPreviewRequest request(String checksum, int limit) {
        return new PhysicalPreviewRequest(
            MODEL_ID, 4, "a".repeat(64), 7, "b".repeat(64), PreviewScope.SERVING, CANDIDATE_ID, 11, 2,
            PIPELINE_ID, 1, EVIDENCE_ID, checksum, PreviewMode.SAMPLE, limit
        );
    }

    private static RelationEvidence evidence(String schema, String identifier, String checksum) {
        return new RelationEvidence(
            TENANT, EVIDENCE_ID, MODEL_ID, 4, "a".repeat(64), 7, "b".repeat(64),
            CANDIDATE_ID, 11, "PUBLISHED", 2, "COMPLETED", PIPELINE_ID, "BUILT", 1,
            "postgres", "sha256:" + "9".repeat(64), "warehouse", schema, identifier,
            ExpectedRelationType.TABLE, true, true,
            List.of(
                new PhysicalColumn(1, "account_code", "character varying", false),
                new PhysicalColumn(2, "amount", "numeric(18,2)", true),
                new PhysicalColumn(3, "secret_note", "text", true)
            ),
            checksum, NOW.minusSeconds(10)
        );
    }

    private static ModelServingProjection projection(RelationEvidence evidence) {
        ServingRef serving = new ServingRef(
            MODEL_ID, 4, "a".repeat(64), 7, "b".repeat(64), CANDIDATE_ID, 11, 2,
            PIPELINE_ID, 1, EVIDENCE_ID, evidence.evidenceChecksum(), PHYSICAL_ASSET_ID,
            UUID.fromString("10000000-0000-0000-0000-000000000001"),
            "postgres", "warehouse", evidence.schemaName(), evidence.identifier(), evidence.observedAt()
        );
        return new ModelServingProjection(
            TENANT, MODEL_ID, CatalogAssetType.SEMANTIC_MODEL,
            "semantic-model:" + MODEL_ID, null, serving, 2, "SYNC_PENDING", NOW
        );
    }
}
