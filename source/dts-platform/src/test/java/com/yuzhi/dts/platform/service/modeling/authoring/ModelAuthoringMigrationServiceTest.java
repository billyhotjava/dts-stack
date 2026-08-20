package com.yuzhi.dts.platform.service.modeling.authoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.repository.modeling.DbtImplementationDraftRepository;
import com.yuzhi.dts.platform.repository.modeling.DbtImplementationDraftRepository.DraftRow;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.authoring.ModelAuthoringContract.AuthoringProjection;
import com.yuzhi.dts.platform.service.modeling.authoring.ModelAuthoringMigrationService.MigrationAction;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.DraftState;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.SourceBundleKind;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.SourceBundleView;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftService;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.ProjectionCoverage;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class ModelAuthoringMigrationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-20T00:00:00Z");
    private static final UUID DRAFT_ID = UUID.fromString("30000000-0000-0000-0000-000000000092");
    private static final UUID MODEL_ID = UUID.fromString("20000000-0000-0000-0000-000000000092");
    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000092");
    private static final String MODEL_CHECKSUM = "a".repeat(64);

    private final DbtImplementationDraftRepository drafts = mock(DbtImplementationDraftRepository.class);
    private final DbtImplementationDraftService draftService = mock(DbtImplementationDraftService.class);
    private final ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
    private final ModelLifecycleService lifecycle = mock(ModelLifecycleService.class);
    private final ModelAuthoringProjectionService projections = mock(ModelAuthoringProjectionService.class);
    private final ModelAuthoringSnapshotFactory snapshots = mock(ModelAuthoringSnapshotFactory.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final ModelAuthoringMigrationService service = new ModelAuthoringMigrationService(
        drafts,
        draftService,
        modelSpecs,
        lifecycle,
        projections,
        snapshots,
        jdbc,
        objectMapper,
        Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void springSelectsTheProductionConstructor() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(DbtImplementationDraftRepository.class, () -> drafts);
            context.registerBean(DbtImplementationDraftService.class, () -> draftService);
            context.registerBean(ModelSpecApplicationService.class, () -> modelSpecs);
            context.registerBean(ModelLifecycleService.class, () -> lifecycle);
            context.registerBean(ModelAuthoringProjectionService.class, () -> projections);
            context.registerBean(ModelAuthoringSnapshotFactory.class, () -> snapshots);
            context.registerBean(JdbcTemplate.class, () -> jdbc);
            context.registerBean(ObjectMapper.class, () -> objectMapper);
            context.registerBean(ModelAuthoringMigrationService.class);

            context.refresh();

            assertThat(context.getBean(ModelAuthoringMigrationService.class)).isNotNull();
        }
    }

    @Test
    void previewIsDeterministicAndContainsNoBundleBody() {
        DraftRow row = row(DraftState.DRAFT, NOW.plusSeconds(3600), "{\"projectKey\":\"project\"}");
        SourceBundleView source = new SourceBundleView(
            "project",
            "b".repeat(64),
            "c".repeat(64),
            SourceBundleKind.FROZEN_SOURCE_BUNDLE,
            true,
            List.of(),
            null,
            null,
            Map.of()
        );
        ModelSpecView model = mock(ModelSpecView.class);
        var snapshot = objectMapper.createObjectNode();
        snapshot.put("schemaVersion", 1);
        snapshot.set("modelSpec", objectMapper.createObjectNode().put("name", "orders"));
        AuthoringProjection projection = new AuthoringProjection(
            ProjectionCoverage.FULL,
            true,
            List.of("models/orders.sql"),
            List.of(),
            List.of()
        );
        when(drafts.listAuthoringMigrationCandidates(100)).thenReturn(List.of(row));
        when(drafts.countAuthoringMigrationCandidates()).thenReturn(1L);
        when(draftService.restoreSourceBundleSnapshot(row.sourceBundleSnapshot())).thenReturn(source);
        when(modelSpecs.get("default", MODEL_ID)).thenReturn(model);
        when(model.revision()).thenReturn(3);
        when(model.checksum()).thenReturn(MODEL_CHECKSUM);
        when(snapshots.create(model, null, "migration-authoring-" + DRAFT_ID)).thenReturn(snapshot);
        when(projections.project(source)).thenReturn(projection);

        var first = service.preview(100);
        var second = service.preview(100);

        assertThat(first.previewHash()).isEqualTo(second.previewHash()).matches("^[0-9a-f]{64}$");
        assertThat(first.rows()).singleElement().satisfies(item -> {
            assertThat(item.action()).isEqualTo(MigrationAction.APPLY);
            assertThat(item.coverage()).isEqualTo(ProjectionCoverage.FULL);
            assertThat(item.reason()).isNull();
        });
        assertThat(objectMapper.valueToTree(first).toString()).doesNotContain("projectKey", "models/orders.sql");
    }

    @Test
    void previewSkipsCommittedExpiredAndMissingBundleRowsBeforeReadingContent() {
        when(drafts.listAuthoringMigrationCandidates(100)).thenReturn(
            List.of(
                row(DraftState.COMMITTED, NOW.plusSeconds(3600), "{}"),
                row(DraftState.DRAFT, NOW.minusSeconds(1), "{}"),
                row(DraftState.DRAFT, NOW.plusSeconds(3600), null)
            )
        );
        when(drafts.countAuthoringMigrationCandidates()).thenReturn(3L);

        var preview = service.preview(100);

        assertThat(preview.rows()).extracting("action").containsOnly(MigrationAction.SKIP);
        assertThat(preview.rows()).extracting("reason").containsExactly(
            "MODEL_AUTHORING_MIGRATION_STATE_IMMUTABLE",
            "MODEL_AUTHORING_MIGRATION_DRAFT_EXPIRED",
            "MODEL_AUTHORING_MIGRATION_SOURCE_BUNDLE_MISSING"
        );
        verifyNoInteractions(draftService, modelSpecs, lifecycle, projections, snapshots);
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void applyReplaysThePreviewThroughOneCasAndStoresEvidenceWithoutChangingLifecycleState() {
        DraftRow row = applicableRow();
        AuthoringProjection projection = applicableProjection();
        SourceBundleView source = applicableSource();
        ModelSpecView model = mock(ModelSpecView.class);
        var snapshot = objectMapper.createObjectNode();
        snapshot.put("schemaVersion", 1);
        snapshot.set("modelSpec", objectMapper.createObjectNode().put("name", "orders"));
        when(drafts.listAuthoringMigrationCandidates(100)).thenReturn(List.of(row));
        when(drafts.countAuthoringMigrationCandidates()).thenReturn(1L);
        when(draftService.restoreSourceBundleSnapshot(row.sourceBundleSnapshot())).thenReturn(source);
        when(modelSpecs.get("default", MODEL_ID)).thenReturn(model);
        when(model.revision()).thenReturn(3);
        when(model.checksum()).thenReturn(MODEL_CHECKSUM);
        when(snapshots.create(model, null, "migration-authoring-" + DRAFT_ID)).thenReturn(snapshot);
        when(projections.project(source)).thenReturn(projection);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(drafts.backfillAuthoringMetadata(eq(row), anyString(), anyString(), eq("UNKNOWN"))).thenReturn(true);
        String previewHash = service.preview(100).previewHash();

        var applied = service.apply(previewHash, 100, "corr-92");

        assertThat(applied.previewHash()).isEqualTo(previewHash);
        assertThat(applied.applied()).isEqualTo(1);
        assertThat(applied.conflicts()).isZero();
        verify(drafts).backfillAuthoringMetadata(eq(row), anyString(), anyString(), eq("UNKNOWN"));
        assertThat(row.state()).isEqualTo(DraftState.DRAFT);
        assertThat(row.etag()).isEqualTo("etag-92");
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void rollbackRestoresOnlyTheExactAppliedMetadataWhenTheDraftHasNotDrifted() throws Exception {
        UUID batchId = UUID.fromString("40000000-0000-0000-0000-000000000092");
        String appliedSnapshot = "{\"schemaVersion\":1}";
        String appliedProjection = "{\"coverage\":\"NONE\"}";
        ResultSet batch = mock(ResultSet.class);
        when(batch.getObject("id", UUID.class)).thenReturn(batchId);
        when(batch.getString("status")).thenReturn("APPLIED");
        ResultSet item = mock(ResultSet.class);
        when(item.getObject("draft_id", UUID.class)).thenReturn(DRAFT_ID);
        when(item.getString("expected_etag")).thenReturn("etag-92");
        when(item.getTimestamp("expected_last_modified_date")).thenReturn(Timestamp.from(NOW.minusSeconds(30)));
        when(item.getString("previous_model_spec_snapshot")).thenReturn(null);
        when(item.getString("previous_projection_summary")).thenReturn(null);
        when(item.getString("previous_authoring_origin")).thenReturn(null);
        when(item.getString("applied_model_spec_snapshot")).thenReturn(appliedSnapshot);
        when(item.getString("applied_projection_summary")).thenReturn(appliedProjection);
        when(item.getString("applied_authoring_origin")).thenReturn("UNKNOWN");
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            RowMapper mapper = invocation.getArgument(1);
            if (sql.contains("from modeling_authoring_migration_batch")) return List.of(mapper.mapRow(batch, 0));
            if (sql.contains("from modeling_authoring_migration_item")) return List.of(mapper.mapRow(item, 0));
            return List.of();
        });
        when(drafts.authoringMetadataMatches(eq(DRAFT_ID), eq("etag-92"), eq(NOW.minusSeconds(30)), any())).thenReturn(true);
        when(drafts.rollbackAuthoringMetadata(eq(DRAFT_ID), eq("etag-92"), eq(NOW.minusSeconds(30)), any(), any())).thenReturn(true);

        var rolledBack = service.rollback(batchId);

        assertThat(rolledBack.batchId()).isEqualTo(batchId);
        assertThat(rolledBack.restored()).isEqualTo(1);
        verify(drafts).authoringMetadataMatches(eq(DRAFT_ID), eq("etag-92"), eq(NOW.minusSeconds(30)), any());
        verify(drafts).rollbackAuthoringMetadata(eq(DRAFT_ID), eq("etag-92"), eq(NOW.minusSeconds(30)), any(), any());
    }

    private static DraftRow applicableRow() {
        return row(DraftState.DRAFT, NOW.plusSeconds(3600), "{\"projectKey\":\"project\"}");
    }

    private static SourceBundleView applicableSource() {
        return new SourceBundleView(
            "project",
            "b".repeat(64),
            "c".repeat(64),
            SourceBundleKind.FROZEN_SOURCE_BUNDLE,
            true,
            List.of(),
            null,
            null,
            Map.of()
        );
    }

    private static AuthoringProjection applicableProjection() {
        return new AuthoringProjection(
            ProjectionCoverage.FULL,
            true,
            List.of("models/orders.sql"),
            List.of(),
            List.of()
        );
    }

    private static DraftRow row(DraftState state, Instant expiresAt, String sourceBundle) {
        return new DraftRow(
            DRAFT_ID,
            "default",
            PLAN_ID,
            MODEL_ID,
            "xiezm",
            3,
            MODEL_CHECKSUM,
            null,
            null,
            "legacy-key",
            "d".repeat(64),
            sourceBundle,
            null,
            null,
            null,
            state,
            "etag-92",
            expiresAt,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            0,
            null,
            NOW.minusSeconds(60),
            NOW.minusSeconds(30)
        );
    }
}
