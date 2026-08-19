package com.yuzhi.dts.platform.repository.modeling;

import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.DraftState;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract.FileInput;
import com.yuzhi.dts.platform.service.modeling.imports.checksum.ModelPackageChecksum;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** Tenant/actor-scoped persistence and CAS transitions for advanced dbt drafts. */
@Repository
public class DbtImplementationDraftRepository {

    private static final RowMapper<DraftRow> DRAFT_MAPPER = DbtImplementationDraftRepository::draft;
    private static final RowMapper<FileRow> FILE_MAPPER = (resultSet, rowNum) ->
        new FileRow(
            resultSet.getString("path"),
            resultSet.getString("content"),
            resultSet.getString("content_checksum"),
            resultSet.getLong("byte_size")
        );

    private final JdbcTemplate jdbcTemplate;

    public DbtImplementationDraftRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public DraftRow create(NewDraft draft) {
        jdbcTemplate.update(
            """
            insert into modeling_dbt_implementation_draft (
                id, tenant_id, plan_id, model_spec_id, actor_id,
                base_model_revision, base_model_checksum,
                base_implementation_revision, base_implementation_checksum,
                idempotency_key, request_hash, source_bundle_snapshot,
                model_spec_snapshot, projection_summary, authoring_origin, status, etag,
                expires_at, created_date, last_modified_date
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb),
                      cast(? as jsonb), cast(? as jsonb), ?, 'DRAFT', ?, ?, ?, ?)
            on conflict (tenant_id, plan_id, model_spec_id, actor_id, idempotency_key) do nothing
            """,
            draft.id(),
            draft.tenantId(),
            draft.planId(),
            draft.modelSpecId(),
            draft.actorId(),
            draft.baseModelRevision(),
            draft.baseModelChecksum(),
            draft.baseImplementationRevision(),
            draft.baseImplementationChecksum(),
            draft.idempotencyKey(),
            draft.requestHash(),
            draft.sourceBundleSnapshot(),
            draft.modelSpecSnapshot(),
            draft.projectionSummary(),
            draft.authoringOrigin(),
            draft.etag(),
            Timestamp.from(draft.expiresAt()),
            Timestamp.from(draft.createdAt()),
            Timestamp.from(draft.createdAt())
        );
        return findByIdempotency(
            draft.tenantId(),
            draft.planId(),
            draft.modelSpecId(),
            draft.actorId(),
            draft.idempotencyKey()
        ).orElseThrow(() -> new IllegalStateException("Advanced dbt draft insert did not produce an idempotency row"));
    }

    public Optional<DraftRow> findByIdempotency(
        String tenantId,
        UUID planId,
        UUID modelSpecId,
        String actorId,
        String idempotencyKey
    ) {
        return jdbcTemplate
            .query(
                baseSelect() +
                " where tenant_id = ? and plan_id = ? and model_spec_id = ? and actor_id = ? and idempotency_key = ?",
                DRAFT_MAPPER,
                tenantId,
                planId,
                modelSpecId,
                actorId,
                idempotencyKey
            )
            .stream()
            .findFirst();
    }

    public Optional<DraftRow> findForActor(String tenantId, UUID modelSpecId, UUID draftId, String actorId) {
        return jdbcTemplate
            .query(
                baseSelect() + " where tenant_id = ? and model_spec_id = ? and id = ? and actor_id = ?",
                DRAFT_MAPPER,
                tenantId,
                modelSpecId,
                draftId,
                actorId
            )
            .stream()
            .findFirst();
    }

    public Optional<DraftRow> findOpenForActor(String tenantId, UUID modelSpecId, String actorId, Instant now) {
        return jdbcTemplate
            .query(
                baseSelect() +
                " where tenant_id = ? and model_spec_id = ? and actor_id = ?" +
                " and expires_at > ? and status in ('DRAFT', 'VALIDATED')" +
                " order by last_modified_date desc, id desc limit 1",
                DRAFT_MAPPER,
                tenantId,
                modelSpecId,
                actorId,
                Timestamp.from(now)
            )
            .stream()
            .findFirst();
    }

    public List<DraftRow> listAuthoringMigrationCandidates(int limit) {
        return jdbcTemplate.query(
            baseSelect() +
            " where model_spec_snapshot is null or projection_summary is null or authoring_origin is null" +
            " order by last_modified_date, id limit ?",
            DRAFT_MAPPER,
            limit
        );
    }

    public long countAuthoringMigrationCandidates() {
        Long count = jdbcTemplate.queryForObject(
            """
            select count(*)
              from modeling_dbt_implementation_draft
             where model_spec_snapshot is null or projection_summary is null or authoring_origin is null
            """,
            Long.class
        );
        return count == null ? 0 : count;
    }

    public boolean backfillAuthoringMetadata(
        DraftRow expected,
        String modelSpecSnapshot,
        String projectionSummary,
        String authoringOrigin
    ) {
        return jdbcTemplate.update(
            """
            update modeling_dbt_implementation_draft
               set model_spec_snapshot = coalesce(model_spec_snapshot, cast(? as jsonb)),
                   projection_summary = coalesce(projection_summary, cast(? as jsonb)),
                   authoring_origin = coalesce(authoring_origin, ?)
             where id = ? and etag = ? and last_modified_date = ? and status = ?
               and model_spec_snapshot is not distinct from cast(? as jsonb)
               and projection_summary is not distinct from cast(? as jsonb)
               and authoring_origin is not distinct from ?
            """,
            modelSpecSnapshot,
            projectionSummary,
            authoringOrigin,
            expected.id(),
            expected.etag(),
            Timestamp.from(expected.updatedAt()),
            expected.state().name(),
            expected.modelSpecSnapshot(),
            expected.projectionSummary(),
            expected.authoringOrigin()
        ) == 1;
    }

    public boolean authoringMetadataMatches(
        UUID draftId,
        String expectedEtag,
        Instant expectedUpdatedAt,
        AuthoringMetadata expected
    ) {
        Integer count = jdbcTemplate.queryForObject(
            """
            select count(*)
              from modeling_dbt_implementation_draft
             where id = ? and etag = ? and last_modified_date = ?
               and model_spec_snapshot is not distinct from cast(? as jsonb)
               and projection_summary is not distinct from cast(? as jsonb)
               and authoring_origin is not distinct from ?
            """,
            Integer.class,
            draftId,
            expectedEtag,
            Timestamp.from(expectedUpdatedAt),
            expected.modelSpecSnapshot(),
            expected.projectionSummary(),
            expected.authoringOrigin()
        );
        return count != null && count == 1;
    }

    public boolean rollbackAuthoringMetadata(
        UUID draftId,
        String expectedEtag,
        Instant expectedUpdatedAt,
        AuthoringMetadata applied,
        AuthoringMetadata previous
    ) {
        return jdbcTemplate.update(
            """
            update modeling_dbt_implementation_draft
               set model_spec_snapshot = cast(? as jsonb),
                   projection_summary = cast(? as jsonb),
                   authoring_origin = ?
             where id = ? and etag = ? and last_modified_date = ?
               and model_spec_snapshot is not distinct from cast(? as jsonb)
               and projection_summary is not distinct from cast(? as jsonb)
               and authoring_origin is not distinct from ?
            """,
            previous.modelSpecSnapshot(),
            previous.projectionSummary(),
            previous.authoringOrigin(),
            draftId,
            expectedEtag,
            Timestamp.from(expectedUpdatedAt),
            applied.modelSpecSnapshot(),
            applied.projectionSummary(),
            applied.authoringOrigin()
        ) == 1;
    }

    public boolean existsForTenant(String tenantId, UUID modelSpecId, UUID draftId) {
        Boolean exists = jdbcTemplate.queryForObject(
            """
            select exists (
                select 1 from modeling_dbt_implementation_draft
                 where tenant_id = ? and model_spec_id = ? and id = ?
            )
            """,
            Boolean.class,
            tenantId,
            modelSpecId,
            draftId
        );
        return Boolean.TRUE.equals(exists);
    }

    public Optional<DraftRow> replaceFiles(
        String tenantId,
        UUID modelSpecId,
        UUID draftId,
        String actorId,
        String expectedEtag,
        String nextEtag,
        List<FileInput> files,
        Instant now
    ) {
        int changed = jdbcTemplate.update(
            """
            update modeling_dbt_implementation_draft
               set status = 'DRAFT', etag = ?, validated_checksum = null,
                   validation_summary = null, project_checksum = null,
                   bundle_checksum = null, bundle_manifest = null, last_modified_date = ?
             where tenant_id = ? and model_spec_id = ? and id = ? and actor_id = ?
               and etag = ? and expires_at > ? and status in ('DRAFT', 'VALIDATED')
            """,
            nextEtag,
            Timestamp.from(now),
            tenantId,
            modelSpecId,
            draftId,
            actorId,
            expectedEtag,
            Timestamp.from(now)
        );
        if (changed != 1) return Optional.empty();
        replaceFileRows(draftId, files, now);
        return findForActor(tenantId, modelSpecId, draftId, actorId);
    }

    public Optional<DraftRow> replaceAuthoringContent(
        String tenantId,
        UUID modelSpecId,
        UUID draftId,
        String actorId,
        String expectedEtag,
        String nextEtag,
        String modelSpecSnapshot,
        String projectionSummary,
        List<FileInput> files,
        Instant now
    ) {
        int changed = jdbcTemplate.update(
            """
            update modeling_dbt_implementation_draft
               set status = 'DRAFT', etag = ?, model_spec_snapshot = cast(? as jsonb),
                   projection_summary = cast(? as jsonb), validated_checksum = null,
                   validation_summary = null, project_checksum = null,
                   bundle_checksum = null, bundle_manifest = null, last_modified_date = ?
             where tenant_id = ? and model_spec_id = ? and id = ? and actor_id = ?
               and etag = ? and expires_at > ? and status in ('DRAFT', 'VALIDATED')
            """,
            nextEtag,
            modelSpecSnapshot,
            projectionSummary,
            Timestamp.from(now),
            tenantId,
            modelSpecId,
            draftId,
            actorId,
            expectedEtag,
            Timestamp.from(now)
        );
        if (changed != 1) return Optional.empty();
        replaceFileRows(draftId, files, now);
        return findForActor(tenantId, modelSpecId, draftId, actorId);
    }

    private void replaceFileRows(UUID draftId, List<FileInput> files, Instant now) {
        jdbcTemplate.update("delete from modeling_dbt_implementation_draft_file where draft_id = ?", draftId);
        jdbcTemplate.batchUpdate(
            """
            insert into modeling_dbt_implementation_draft_file (
                draft_id, path, content_checksum, content, byte_size, created_date
            ) values (?, ?, ?, ?, ?, ?)
            """,
            files,
            files.size(),
            (statement, file) -> {
                byte[] bytes = file.content().getBytes(StandardCharsets.UTF_8);
                statement.setObject(1, draftId);
                statement.setString(2, file.path());
                statement.setString(3, ModelPackageChecksum.sha256(bytes));
                statement.setString(4, file.content());
                statement.setLong(5, bytes.length);
                statement.setTimestamp(6, Timestamp.from(now));
            }
        );
    }

    public List<FileRow> listFiles(UUID draftId) {
        return jdbcTemplate.query(
            """
            select path, content, content_checksum, byte_size
              from modeling_dbt_implementation_draft_file
             where draft_id = ?
             order by path
            """,
            FILE_MAPPER,
            draftId
        );
    }

    public Optional<DraftRow> markValidated(
        String tenantId,
        UUID modelSpecId,
        UUID draftId,
        String actorId,
        String expectedEtag,
        String nextEtag,
        String validatedChecksum,
        String projectChecksum,
        String bundleChecksum,
        String bundleManifest,
        String validationSummary,
        Instant now
    ) {
        int changed = jdbcTemplate.update(
            """
            update modeling_dbt_implementation_draft
               set status = 'VALIDATED', etag = ?, validated_checksum = ?, project_checksum = ?,
                   bundle_checksum = ?, bundle_manifest = ?, validation_summary = cast(? as jsonb),
                   last_modified_date = ?
             where tenant_id = ? and model_spec_id = ? and id = ? and actor_id = ?
               and etag = ? and expires_at > ? and status in ('DRAFT', 'VALIDATED')
            """,
            nextEtag,
            validatedChecksum,
            projectChecksum,
            bundleChecksum,
            bundleManifest,
            validationSummary,
            Timestamp.from(now),
            tenantId,
            modelSpecId,
            draftId,
            actorId,
            expectedEtag,
            Timestamp.from(now)
        );
        if (changed != 1) return Optional.empty();
        return findForActor(tenantId, modelSpecId, draftId, actorId);
    }

    public Optional<DraftRow> claimCommit(
        String tenantId,
        UUID modelSpecId,
        UUID draftId,
        String actorId,
        String expectedEtag,
        String validatedChecksum,
        String commitIdempotencyKey,
        String nextEtag,
        Instant now
    ) {
        int changed = jdbcTemplate.update(
            """
            update modeling_dbt_implementation_draft
               set status = 'COMMITTING', etag = ?, commit_idempotency_key = ?, last_modified_date = ?
             where tenant_id = ? and model_spec_id = ? and id = ? and actor_id = ?
               and etag = ? and expires_at > ? and status = 'VALIDATED'
               and validated_checksum = ?
            """,
            nextEtag,
            commitIdempotencyKey,
            Timestamp.from(now),
            tenantId,
            modelSpecId,
            draftId,
            actorId,
            expectedEtag,
            Timestamp.from(now),
            validatedChecksum
        );
        if (changed != 1) return Optional.empty();
        return findForActor(tenantId, modelSpecId, draftId, actorId);
    }

    public DraftRow completeCommit(
        String tenantId,
        UUID modelSpecId,
        UUID draftId,
        String actorId,
        String claimedEtag,
        String committedEtag,
        UUID implementationId,
        int implementationRevision,
        String implementationChecksum,
        int artifactCount,
        Instant now
    ) {
        int changed = jdbcTemplate.update(
            """
            update modeling_dbt_implementation_draft
               set status = 'COMMITTED', etag = ?, implementation_id = ?,
                   implementation_revision = ?, implementation_checksum = ?, artifact_count = ?,
                   committed_at = ?, last_modified_date = ?
             where tenant_id = ? and model_spec_id = ? and id = ? and actor_id = ?
               and etag = ? and status = 'COMMITTING'
            """,
            committedEtag,
            implementationId,
            implementationRevision,
            implementationChecksum,
            artifactCount,
            Timestamp.from(now),
            Timestamp.from(now),
            tenantId,
            modelSpecId,
            draftId,
            actorId,
            claimedEtag
        );
        if (changed != 1) throw new IllegalStateException("Advanced dbt draft commit completion lost its CAS claim");
        return findForActor(tenantId, modelSpecId, draftId, actorId).orElseThrow();
    }

    public int purgeExpiredBatch(Instant cutoff, int limit) {
        if (cutoff == null) throw new IllegalArgumentException("cutoff is required");
        if (limit < 1 || limit > 10_000) throw new IllegalArgumentException("limit must be 1..10000");
        return jdbcTemplate.update(
            """
            with expired as (
                select id
                  from modeling_dbt_implementation_draft
                 where expires_at <= ? and status <> 'COMMITTING'
                 order by expires_at, id
                 limit ?
                 for update skip locked
            )
            delete from modeling_dbt_implementation_draft draft
             using expired
             where draft.id = expired.id
            """,
            Timestamp.from(cutoff),
            limit
        );
    }

    private static String baseSelect() {
        return """
            select id, tenant_id, plan_id, model_spec_id, actor_id,
                   base_model_revision, base_model_checksum,
                   base_implementation_revision, base_implementation_checksum,
                   idempotency_key, request_hash, source_bundle_snapshot,
                   model_spec_snapshot, projection_summary, authoring_origin, status, etag, expires_at,
                   validated_checksum, project_checksum, bundle_checksum, bundle_manifest,
                   validation_summary, commit_idempotency_key,
                   implementation_id, implementation_revision, implementation_checksum,
                   artifact_count, committed_at, created_date, last_modified_date
              from modeling_dbt_implementation_draft
            """;
    }

    private static DraftRow draft(ResultSet resultSet, int rowNum) throws SQLException {
        return new DraftRow(
            resultSet.getObject("id", UUID.class),
            resultSet.getString("tenant_id"),
            resultSet.getObject("plan_id", UUID.class),
            resultSet.getObject("model_spec_id", UUID.class),
            resultSet.getString("actor_id"),
            resultSet.getInt("base_model_revision"),
            resultSet.getString("base_model_checksum"),
            resultSet.getObject("base_implementation_revision", Integer.class),
            resultSet.getString("base_implementation_checksum"),
            resultSet.getString("idempotency_key"),
            resultSet.getString("request_hash"),
            resultSet.getString("source_bundle_snapshot"),
            resultSet.getString("model_spec_snapshot"),
            resultSet.getString("projection_summary"),
            resultSet.getString("authoring_origin"),
            DraftState.valueOf(resultSet.getString("status")),
            resultSet.getString("etag"),
            instant(resultSet, "expires_at"),
            resultSet.getString("validated_checksum"),
            resultSet.getString("project_checksum"),
            resultSet.getString("bundle_checksum"),
            resultSet.getString("bundle_manifest"),
            resultSet.getString("validation_summary"),
            resultSet.getString("commit_idempotency_key"),
            resultSet.getObject("implementation_id", UUID.class),
            resultSet.getObject("implementation_revision", Integer.class),
            resultSet.getString("implementation_checksum"),
            resultSet.getInt("artifact_count"),
            instant(resultSet, "committed_at"),
            instant(resultSet, "created_date"),
            instant(resultSet, "last_modified_date")
        );
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        Timestamp value = resultSet.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    public record NewDraft(
        UUID id,
        String tenantId,
        UUID planId,
        UUID modelSpecId,
        String actorId,
        int baseModelRevision,
        String baseModelChecksum,
        Integer baseImplementationRevision,
        String baseImplementationChecksum,
        String idempotencyKey,
        String requestHash,
        String sourceBundleSnapshot,
        String modelSpecSnapshot,
        String projectionSummary,
        String authoringOrigin,
        String etag,
        Instant expiresAt,
        Instant createdAt
    ) {
        public NewDraft(
            UUID id,
            String tenantId,
            UUID planId,
            UUID modelSpecId,
            String actorId,
            int baseModelRevision,
            String baseModelChecksum,
            Integer baseImplementationRevision,
            String baseImplementationChecksum,
            String idempotencyKey,
            String requestHash,
            String sourceBundleSnapshot,
            String etag,
            Instant expiresAt,
            Instant createdAt
        ) {
            this(
                id,
                tenantId,
                planId,
                modelSpecId,
                actorId,
                baseModelRevision,
                baseModelChecksum,
                baseImplementationRevision,
                baseImplementationChecksum,
                idempotencyKey,
                requestHash,
                sourceBundleSnapshot,
                null,
                null,
                null,
                etag,
                expiresAt,
                createdAt
            );
        }
    }

    public record FileRow(String path, String content, String checksum, long byteSize) {}

    public record AuthoringMetadata(String modelSpecSnapshot, String projectionSummary, String authoringOrigin) {}

    public record DraftRow(
        UUID id,
        String tenantId,
        UUID planId,
        UUID modelSpecId,
        String actorId,
        int baseModelRevision,
        String baseModelChecksum,
        Integer baseImplementationRevision,
        String baseImplementationChecksum,
        String idempotencyKey,
        String requestHash,
        String sourceBundleSnapshot,
        String modelSpecSnapshot,
        String projectionSummary,
        String authoringOrigin,
        DraftState state,
        String etag,
        Instant expiresAt,
        String validatedChecksum,
        String projectChecksum,
        String bundleChecksum,
        String bundleManifest,
        String validationSummary,
        String commitIdempotencyKey,
        UUID implementationId,
        Integer implementationRevision,
        String implementationChecksum,
        int artifactCount,
        Instant committedAt,
        Instant createdAt,
        Instant updatedAt
    ) {
        public DraftRow(
            UUID id,
            String tenantId,
            UUID planId,
            UUID modelSpecId,
            String actorId,
            int baseModelRevision,
            String baseModelChecksum,
            Integer baseImplementationRevision,
            String baseImplementationChecksum,
            String idempotencyKey,
            String requestHash,
            String sourceBundleSnapshot,
            DraftState state,
            String etag,
            Instant expiresAt,
            String validatedChecksum,
            String projectChecksum,
            String bundleChecksum,
            String bundleManifest,
            String validationSummary,
            String commitIdempotencyKey,
            UUID implementationId,
            Integer implementationRevision,
            String implementationChecksum,
            int artifactCount,
            Instant committedAt,
            Instant createdAt,
            Instant updatedAt
        ) {
            this(
                id,
                tenantId,
                planId,
                modelSpecId,
                actorId,
                baseModelRevision,
                baseModelChecksum,
                baseImplementationRevision,
                baseImplementationChecksum,
                idempotencyKey,
                requestHash,
                sourceBundleSnapshot,
                null,
                null,
                null,
                state,
                etag,
                expiresAt,
                validatedChecksum,
                projectChecksum,
                bundleChecksum,
                bundleManifest,
                validationSummary,
                commitIdempotencyKey,
                implementationId,
                implementationRevision,
                implementationChecksum,
                artifactCount,
                committedAt,
                createdAt,
                updatedAt
            );
        }
    }
}
