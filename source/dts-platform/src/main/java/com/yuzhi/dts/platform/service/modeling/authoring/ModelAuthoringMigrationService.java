package com.yuzhi.dts.platform.service.modeling.authoring;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.repository.modeling.DbtImplementationDraftRepository;
import com.yuzhi.dts.platform.repository.modeling.DbtImplementationDraftRepository.AuthoringMetadata;
import com.yuzhi.dts.platform.repository.modeling.DbtImplementationDraftRepository.DraftRow;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelRevisionRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.AuthoringOrigin;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.DraftException;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.DraftState;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.SourceBundleView;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftService;
import com.yuzhi.dts.platform.service.modeling.imports.checksum.ModelPackageChecksum;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.ProjectionCoverage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/** Controlled preview/apply/rollback lane for legacy authoring draft metadata. */
@Service
public class ModelAuthoringMigrationService {

    private static final int MAX_BATCH_SIZE = 100;
    private final DbtImplementationDraftRepository drafts;
    private final DbtImplementationDraftService draftService;
    private final ModelSpecApplicationService modelSpecs;
    private final ModelLifecycleService lifecycle;
    private final ModelAuthoringProjectionService projections;
    private final ModelAuthoringSnapshotFactory snapshots;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ModelAuthoringMigrationService(
        DbtImplementationDraftRepository drafts,
        DbtImplementationDraftService draftService,
        ModelSpecApplicationService modelSpecs,
        ModelLifecycleService lifecycle,
        ModelAuthoringProjectionService projections,
        ModelAuthoringSnapshotFactory snapshots,
        JdbcTemplate jdbc,
        ObjectMapper objectMapper
    ) {
        this(drafts, draftService, modelSpecs, lifecycle, projections, snapshots, jdbc, objectMapper, Clock.systemUTC());
    }

    ModelAuthoringMigrationService(
        DbtImplementationDraftRepository drafts,
        DbtImplementationDraftService draftService,
        ModelSpecApplicationService modelSpecs,
        ModelLifecycleService lifecycle,
        ModelAuthoringProjectionService projections,
        ModelAuthoringSnapshotFactory snapshots,
        JdbcTemplate jdbc,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        this.drafts = Objects.requireNonNull(drafts, "drafts is required");
        this.draftService = Objects.requireNonNull(draftService, "draftService is required");
        this.modelSpecs = Objects.requireNonNull(modelSpecs, "modelSpecs is required");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle is required");
        this.projections = Objects.requireNonNull(projections, "projections is required");
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots is required");
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc is required");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    @Transactional(readOnly = true)
    public Preview preview(int requestedLimit) {
        List<Plan> plans = plans(requestedLimit);
        List<PreviewRow> rows = plans.stream().map(Plan::view).toList();
        int applicable = (int) rows.stream().filter(row -> row.action() == MigrationAction.APPLY).count();
        long totalPending = drafts.countAuthoringMigrationCandidates();
        return new Preview(
            hash(rows),
            rows,
            new Counts(applicable, rows.size() - applicable),
            totalPending,
            rows.size() == requireLimit(requestedLimit) && totalPending > rows.size()
        );
    }

    @Transactional
    public ApplyResult apply(String expectedPreviewHash, int requestedLimit, String correlationId) {
        AppliedBatch replay = appliedBatch(expectedPreviewHash);
        if (replay != null) return replay.view();

        List<Plan> plans = plans(requestedLimit);
        List<PreviewRow> rows = plans.stream().map(Plan::view).toList();
        String actualHash = hash(rows);
        if (!StringUtils.hasText(expectedPreviewHash) || !Objects.equals(expectedPreviewHash, actualHash)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "MODEL_AUTHORING_MIGRATION_PREVIEW_STALE");
        }

        UUID batchId = UUID.randomUUID();
        Instant now = clock.instant();
        String actor = SecurityUtils.getCurrentUserLogin().orElse("system");
        jdbc.update(
            """
            insert into modeling_authoring_migration_batch(
                id, preview_hash, status, previewed_count, applied_count, skipped_count, conflict_count,
                created_by, correlation_id, created_date, applied_date
            ) values (?, ?, 'APPLIED', ?, 0, 0, 0, ?, ?, ?, ?)
            """,
            batchId,
            actualHash,
            plans.size(),
            actor,
            trimToNull(correlationId),
            Timestamp.from(now),
            Timestamp.from(now)
        );

        int applied = 0;
        int skipped = 0;
        int conflicts = 0;
        for (Plan plan : plans) {
            if (plan.view().action() != MigrationAction.APPLY || plan.next() == null) {
                skipped++;
                continue;
            }
            DraftRow row = plan.row();
            if (
                !drafts.backfillAuthoringMetadata(
                    row,
                    plan.next().modelSpecSnapshot(),
                    plan.next().projectionSummary(),
                    plan.next().authoringOrigin()
                )
            ) {
                conflicts++;
                continue;
            }
            insertItem(batchId, plan, now);
            applied++;
        }
        jdbc.update(
            """
            update modeling_authoring_migration_batch
               set applied_count = ?, skipped_count = ?, conflict_count = ?
             where id = ? and status = 'APPLIED'
            """,
            applied,
            skipped,
            conflicts,
            batchId
        );
        return new ApplyResult(batchId, actualHash, plans.size(), applied, skipped, conflicts, now);
    }

    @Transactional
    public RollbackResult rollback(UUID batchId) {
        Batch batch = batch(batchId);
        if (!"APPLIED".equals(batch.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "MODEL_AUTHORING_MIGRATION_BATCH_NOT_APPLIED");
        }
        List<AppliedItem> items = items(batchId);
        for (AppliedItem item : items) {
            if (!drafts.authoringMetadataMatches(item.draftId(), item.expectedEtag(), item.expectedUpdatedAt(), item.applied())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "MODEL_AUTHORING_MIGRATION_ROLLBACK_DRIFT");
            }
        }
        int restored = 0;
        for (AppliedItem item : items) {
            if (
                !drafts.rollbackAuthoringMetadata(
                    item.draftId(),
                    item.expectedEtag(),
                    item.expectedUpdatedAt(),
                    item.applied(),
                    item.previous()
                )
            ) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "MODEL_AUTHORING_MIGRATION_ROLLBACK_DRIFT");
            }
            restored++;
        }
        Instant now = clock.instant();
        jdbc.update(
            """
            update modeling_authoring_migration_batch
               set status = 'ROLLED_BACK', rolled_back_date = ?
             where id = ? and status = 'APPLIED'
            """,
            Timestamp.from(now),
            batchId
        );
        return new RollbackResult(batchId, restored, now);
    }

    private List<Plan> plans(int requestedLimit) {
        int limit = requireLimit(requestedLimit);
        Instant now = clock.instant();
        List<Plan> result = new ArrayList<>();
        for (DraftRow row : drafts.listAuthoringMigrationCandidates(limit)) {
            result.add(classify(row, now));
        }
        return List.copyOf(result);
    }

    private Plan classify(DraftRow row, Instant now) {
        String before = beforeChecksum(row, null);
        if (row.state() == DraftState.COMMITTED || row.state() == DraftState.COMMITTING) {
            return skipped(row, before, "MODEL_AUTHORING_MIGRATION_STATE_IMMUTABLE");
        }
        if (row.expiresAt() == null || !row.expiresAt().isAfter(now)) {
            return skipped(row, before, "MODEL_AUTHORING_MIGRATION_DRAFT_EXPIRED");
        }
        if (!StringUtils.hasText(row.sourceBundleSnapshot())) {
            return skipped(row, before, "MODEL_AUTHORING_MIGRATION_SOURCE_BUNDLE_MISSING");
        }
        try {
            SourceBundleView source = draftService.restoreSourceBundleSnapshot(row.sourceBundleSnapshot());
            ModelSpecView model = model(row);
            ImplementationView implementation = implementation(row);
            JsonNode snapshot = snapshots.create(model, implementation, "migration-authoring-" + row.id());
            var projection = projections.project(source);
            String modelSnapshot = row.modelSpecSnapshot() == null ? json(snapshot) : row.modelSpecSnapshot();
            String projectionSummary = row.projectionSummary() == null
                ? json(objectMapper.valueToTree(projection))
                : row.projectionSummary();
            String origin = row.authoringOrigin() == null
                ? (snapshot.hasNonNull("visualImplementation") ? AuthoringOrigin.SYSTEM_GENERATED.name() : AuthoringOrigin.UNKNOWN.name())
                : row.authoringOrigin();
            AuthoringMetadata next = new AuthoringMetadata(modelSnapshot, projectionSummary, origin);
            String stableBefore = beforeChecksum(row, source.bundleChecksum());
            PreviewRow view = new PreviewRow(
                row.id(),
                row.state().name(),
                stableBefore,
                AuthoringOrigin.fromStorage(origin),
                projection.coverage(),
                MigrationAction.APPLY,
                null
            );
            return new Plan(row, next, view, afterChecksum(row, next, source.bundleChecksum()));
        } catch (RuntimeException failure) {
            return skipped(row, before, failureReason(failure));
        }
    }

    private ModelSpecView model(DraftRow row) {
        ModelSpecView current = modelSpecs.get(row.tenantId(), row.modelSpecId());
        ModelSpecView model = current.revision() == row.baseModelRevision()
            ? current
            : modelSpecs.revision(row.tenantId(), new ModelRevisionRef(row.modelSpecId(), row.baseModelRevision()));
        if (!Objects.equals(model.checksum(), row.baseModelChecksum())) {
            throw new IllegalStateException("MODEL_AUTHORING_MIGRATION_MODEL_PIN_STALE");
        }
        return model;
    }

    private ImplementationView implementation(DraftRow row) {
        if (row.baseImplementationRevision() == null) return null;
        ImplementationView implementation = lifecycle.timeline(row.tenantId(), row.modelSpecId()).implementation();
        if (
            implementation == null ||
            implementation.implementationRevision() != row.baseImplementationRevision() ||
            !Objects.equals(implementation.implementationChecksum(), row.baseImplementationChecksum())
        ) {
            return null;
        }
        return implementation;
    }

    private Plan skipped(DraftRow row, String beforeChecksum, String reason) {
        return new Plan(
            row,
            null,
            new PreviewRow(
                row.id(),
                row.state().name(),
                beforeChecksum,
                AuthoringOrigin.fromStorage(row.authoringOrigin()),
                coverage(row.projectionSummary()),
                MigrationAction.SKIP,
                reason
            ),
            beforeChecksum
        );
    }

    private ProjectionCoverage coverage(String projectionSummary) {
        if (!StringUtils.hasText(projectionSummary)) return ProjectionCoverage.UNKNOWN;
        try {
            return ProjectionCoverage.valueOf(objectMapper.readTree(projectionSummary).path("coverage").asText("UNKNOWN"));
        } catch (RuntimeException | JsonProcessingException ignored) {
            return ProjectionCoverage.UNKNOWN;
        }
    }

    private void insertItem(UUID batchId, Plan plan, Instant now) {
        DraftRow row = plan.row();
        AuthoringMetadata previous = metadata(row);
        jdbc.update(
            """
            insert into modeling_authoring_migration_item(
                batch_id, draft_id, expected_etag, expected_last_modified_date, before_checksum, after_checksum,
                previous_model_spec_snapshot, previous_projection_summary, previous_authoring_origin,
                applied_model_spec_snapshot, applied_projection_summary, applied_authoring_origin, created_date
            ) values (?, ?, ?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb), ?, cast(? as jsonb), cast(? as jsonb), ?, ?)
            """,
            batchId,
            row.id(),
            row.etag(),
            Timestamp.from(row.updatedAt()),
            plan.view().beforeChecksum(),
            plan.afterChecksum(),
            previous.modelSpecSnapshot(),
            previous.projectionSummary(),
            previous.authoringOrigin(),
            plan.next().modelSpecSnapshot(),
            plan.next().projectionSummary(),
            plan.next().authoringOrigin(),
            Timestamp.from(now)
        );
    }

    private AppliedBatch appliedBatch(String previewHash) {
        if (!StringUtils.hasText(previewHash)) return null;
        return jdbc.query(
            """
            select id, preview_hash, previewed_count, applied_count, skipped_count, conflict_count, applied_date
              from modeling_authoring_migration_batch
             where preview_hash = ? and status = 'APPLIED'
             order by created_date, id limit 1
            """,
            (rs, rowNum) ->
                new AppliedBatch(
                    rs.getObject("id", UUID.class),
                    rs.getString("preview_hash"),
                    rs.getInt("previewed_count"),
                    rs.getInt("applied_count"),
                    rs.getInt("skipped_count"),
                    rs.getInt("conflict_count"),
                    rs.getTimestamp("applied_date").toInstant()
                ),
            previewHash
        ).stream().findFirst().orElse(null);
    }

    private Batch batch(UUID batchId) {
        return jdbc.query(
            "select id, status from modeling_authoring_migration_batch where id = ?",
            (rs, rowNum) -> new Batch(rs.getObject("id", UUID.class), rs.getString("status")),
            batchId
        ).stream().findFirst().orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "MODEL_AUTHORING_MIGRATION_BATCH_NOT_FOUND")
        );
    }

    private List<AppliedItem> items(UUID batchId) {
        return jdbc.query(
            """
            select draft_id, expected_etag, expected_last_modified_date,
                   previous_model_spec_snapshot::text as previous_model_spec_snapshot,
                   previous_projection_summary::text as previous_projection_summary,
                   previous_authoring_origin,
                   applied_model_spec_snapshot::text as applied_model_spec_snapshot,
                   applied_projection_summary::text as applied_projection_summary,
                   applied_authoring_origin
              from modeling_authoring_migration_item
             where batch_id = ? order by draft_id
            """,
            (rs, rowNum) -> item(rs),
            batchId
        );
    }

    private static AppliedItem item(ResultSet rs) throws SQLException {
        return new AppliedItem(
            rs.getObject("draft_id", UUID.class),
            rs.getString("expected_etag"),
            rs.getTimestamp("expected_last_modified_date").toInstant(),
            new AuthoringMetadata(
                rs.getString("previous_model_spec_snapshot"),
                rs.getString("previous_projection_summary"),
                rs.getString("previous_authoring_origin")
            ),
            new AuthoringMetadata(
                rs.getString("applied_model_spec_snapshot"),
                rs.getString("applied_projection_summary"),
                rs.getString("applied_authoring_origin")
            )
        );
    }

    private String hash(List<PreviewRow> rows) {
        try {
            return ModelPackageChecksum.sha256(objectMapper.writeValueAsBytes(rows));
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Model authoring migration preview could not be hashed", failure);
        }
    }

    private String beforeChecksum(DraftRow row, String sourceBundleChecksum) {
        return metadataChecksum(row, metadata(row), sourceBundleChecksum);
    }

    private String afterChecksum(DraftRow row, AuthoringMetadata metadata, String sourceBundleChecksum) {
        return metadataChecksum(row, metadata, sourceBundleChecksum);
    }

    private String metadataChecksum(DraftRow row, AuthoringMetadata metadata, String sourceBundleChecksum) {
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("draftId", row.id());
        value.put("status", row.state());
        value.put("etag", row.etag());
        value.put("updatedAt", row.updatedAt());
        value.put("baseModelRevision", row.baseModelRevision());
        value.put("baseModelChecksum", row.baseModelChecksum());
        value.put("baseImplementationRevision", row.baseImplementationRevision());
        value.put("baseImplementationChecksum", row.baseImplementationChecksum());
        value.put("sourceBundleChecksum", sourceBundleChecksum);
        value.put("modelSpecSnapshot", metadata.modelSpecSnapshot());
        value.put("projectionSummary", metadata.projectionSummary());
        value.put("authoringOrigin", metadata.authoringOrigin());
        try {
            return ModelPackageChecksum.sha256(objectMapper.writeValueAsBytes(value));
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Model authoring migration row could not be hashed", failure);
        }
    }

    private String json(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Model authoring migration metadata could not be serialized", failure);
        }
    }

    private static AuthoringMetadata metadata(DraftRow row) {
        return new AuthoringMetadata(row.modelSpecSnapshot(), row.projectionSummary(), row.authoringOrigin());
    }

    private static int requireLimit(int requestedLimit) {
        return Math.max(1, Math.min(requestedLimit, MAX_BATCH_SIZE));
    }

    private static String failureReason(RuntimeException failure) {
        if (failure instanceof DraftException draftFailure) return draftFailure.code();
        String message = failure.getMessage();
        if (message != null && message.startsWith("MODEL_AUTHORING_MIGRATION_")) return message;
        return "MODEL_AUTHORING_MIGRATION_INPUT_UNRESOLVED";
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    public enum MigrationAction {
        APPLY,
        SKIP,
    }

    public record Counts(int applicable, int skipped) {}

    public record PreviewRow(
        UUID draftId,
        String status,
        String beforeChecksum,
        AuthoringOrigin provenance,
        ProjectionCoverage coverage,
        MigrationAction action,
        String reason
    ) {}

    public record Preview(String previewHash, List<PreviewRow> rows, Counts counts, long totalPending, boolean truncated) {
        public Preview {
            rows = List.copyOf(rows == null ? List.of() : rows);
        }
    }

    public record ApplyResult(
        UUID batchId,
        String previewHash,
        int previewed,
        int applied,
        int skipped,
        int conflicts,
        Instant appliedAt
    ) {}

    public record RollbackResult(UUID batchId, int restored, Instant rolledBackAt) {}

    private record Plan(DraftRow row, AuthoringMetadata next, PreviewRow view, String afterChecksum) {}

    private record Batch(UUID id, String status) {}

    private record AppliedItem(
        UUID draftId,
        String expectedEtag,
        Instant expectedUpdatedAt,
        AuthoringMetadata previous,
        AuthoringMetadata applied
    ) {}

    private record AppliedBatch(
        UUID batchId,
        String previewHash,
        int previewed,
        int applied,
        int skipped,
        int conflicts,
        Instant appliedAt
    ) {
        private ApplyResult view() {
            return new ApplyResult(batchId, previewHash, previewed, applied, skipped, conflicts, appliedAt);
        }
    }
}
