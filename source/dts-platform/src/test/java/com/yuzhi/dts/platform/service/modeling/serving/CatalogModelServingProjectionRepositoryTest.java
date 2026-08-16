package com.yuzhi.dts.platform.service.modeling.serving;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.repository.modeling.CatalogModelServingProjectionRepository;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetType;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelServingContract.ModelServingProjection;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelServingContract.PublishedRef;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelServingContract.ServingRef;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelServingContract.SuccessfulPublicationCommand;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class CatalogModelServingProjectionRepositoryTest {

    private static final String TENANT = "tenant-a";
    private static final UUID MODEL_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-02T10:00:00Z");

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void claimsOnlyDueServingRowsWithSkipLockedAndBoundedBatch() {
        JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
        CatalogModelServingProjectionRepository repository = new CatalogModelServingProjectionRepository(
            jdbc,
            new ObjectMapper().findAndRegisterModules()
        );
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        repository.claimSyncCandidates(101, NOW, Duration.ofMinutes(2));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), arguments.capture());
        assertThat(sql.getValue())
            .contains("for update skip locked")
            .contains("serving_ref is not null")
            .contains("sync_attempts < 5")
            .contains("sync_status = 'SYNC_PENDING'")
            .contains("sync_status = 'SYNC_FAILED'");
        assertThat(arguments.getValue()[2]).isEqualTo(100);
        assertThat(arguments.getValue()[3]).isEqualTo(Timestamp.from(NOW.plus(Duration.ofMinutes(2))));
    }

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void advancingLatestPublishedDoesNotOverwriteOldServing() {
        JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
        CatalogModelServingProjectionRepository repository = new CatalogModelServingProjectionRepository(
            jdbc,
            new ObjectMapper().findAndRegisterModules()
        );
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
            .thenReturn(List.of(projection()));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        var mutation = repository.projectLatestPublished(command());

        assertThat(mutation.latestPublishedChanged()).isTrue();
        assertThat(mutation.servingChanged()).isFalse();
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture(), any(Object[].class));
        assertThat(sql.getValue()).contains("latest_published_ref").doesNotContain("set serving_ref");
    }

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void republishingSameModelAndImplementationRevisionAdvancesCandidateWithoutOverwritingServing() {
        JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
        CatalogModelServingProjectionRepository repository = new CatalogModelServingProjectionRepository(
            jdbc,
            new ObjectMapper().findAndRegisterModules()
        );
        ModelServingProjection current = projectionWithSameRevisionFromPreviousCandidate();
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
            .thenReturn(List.of(current));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        var mutation = repository.projectLatestPublished(command());

        assertThat(mutation.latestPublishedChanged()).isTrue();
        assertThat(mutation.servingChanged()).isFalse();
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(sql.capture(), arguments.capture());
        assertThat(sql.getValue()).contains("latest_published_ref").doesNotContain("set serving_ref");
        assertThat(arguments.getValue()[0].toString())
            .contains(command().candidateId().toString())
            .contains("\"candidateVersion\":12")
            .contains("\"modelRevision\":5")
            .contains("\"implementationRevision\":8");
        assertThat(current.servingRef()).isEqualTo(projection().servingRef());
    }

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void republishingSameRevisionWithDifferentChecksumRemainsRejected() {
        JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
        CatalogModelServingProjectionRepository repository = new CatalogModelServingProjectionRepository(
            jdbc,
            new ObjectMapper().findAndRegisterModules()
        );
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
            .thenReturn(List.of(projectionWithSameRevisionFromPreviousCandidate()));

        SuccessfulPublicationCommand conflicting = new SuccessfulPublicationCommand(
            TENANT, MODEL_ID, 5, "f".repeat(64), 8, "e".repeat(64),
            command().candidateId(), 12, command().catalogAssetType(), command().catalogAssetKey(),
            command().sourceId(), command().adapter(), command().physicalAssetId(), NOW
        );

        assertThatThrownBy(() -> repository.projectLatestPublished(conflicting))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("CATALOG_MODEL_LATEST_PUBLISHED_CONFLICT");
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void missingCurrentBuildEvidenceCannotChangeExistingServing() {
        JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
        CatalogModelServingProjectionRepository repository = new CatalogModelServingProjectionRepository(
            jdbc,
            new ObjectMapper().findAndRegisterModules()
        );
        ModelServingProjection current = projectionWithCurrentLatest();
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
            .thenReturn(List.of(current), List.of());

        var mutation = repository.promoteServing(command());

        assertThat(mutation.servingNotReady()).isTrue();
        assertThat(mutation.servingChanged()).isFalse();
        assertThat(current.servingRef()).isEqualTo(projection().servingRef());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, org.mockito.Mockito.times(2)).query(sql.capture(), any(RowMapper.class), arguments.capture());
        assertThat(sql.getAllValues().get(1))
            .contains("c.version = ?")
            .contains("order by d2.candidate_version desc, d2.attempt desc")
            .doesNotContain("o.release_candidate_version = ?")
            .doesNotContain("c.version = o.release_candidate_version");
        assertThat(arguments.getAllValues().get(1))
            .containsSequence(TENANT, command().candidateId(), command().candidateVersion(), MODEL_ID);
    }

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void candidatePreviewUsesLatestSuccessfulBuildPinWithoutEquatingLifecycleVersion() {
        JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
        CatalogModelServingProjectionRepository repository = new CatalogModelServingProjectionRepository(
            jdbc,
            new ObjectMapper().findAndRegisterModules()
        );
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        repository.findLatestSuccessfulCandidateEvidence(
            TENANT,
            MODEL_ID,
            5,
            "d".repeat(64),
            8,
            "e".repeat(64)
        );

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), any(Object[].class));
        assertThat(sql.getValue())
            .contains("order by o.release_candidate_version desc, d.attempt desc")
            .contains("order by d2.candidate_version desc, d2.attempt desc")
            .doesNotContain("c.version = o.release_candidate_version");
    }

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void promotionStoresBuildEvidenceVersionSeparatelyFromGovernanceVersion() throws Exception {
        JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
        CatalogModelServingProjectionRepository repository = new CatalogModelServingProjectionRepository(
            jdbc,
            new ObjectMapper().findAndRegisterModules()
        );
        ResultSet evidence = relationEvidenceRow(7);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
            .thenReturn(List.of(projectionWithCurrentLatest()))
            .thenAnswer(invocation -> {
                RowMapper mapper = invocation.getArgument(1);
                return List.of(mapper.mapRow(evidence, 0));
            });
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        var mutation = repository.promoteServing(command());

        assertThat(mutation.servingChanged()).isTrue();
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(sql.capture(), arguments.capture());
        assertThat(sql.getValue()).contains("latest_published_ref = cast(? as jsonb)");
        assertThat(arguments.getValue()[0].toString())
            .contains("\"candidateVersion\":7");
        assertThat(arguments.getValue()[1].toString())
            .contains("\"candidateVersion\":12")
            .contains("\"evidenceCandidateVersion\":7");
    }

    private static ResultSet relationEvidenceRow(int evidenceCandidateVersion) throws Exception {
        ResultSet row = org.mockito.Mockito.mock(ResultSet.class);
        UUID candidateId = command().candidateId();
        UUID pipelineRunId = UUID.fromString("50000000-0000-0000-0000-000000000002");
        UUID evidenceId = UUID.fromString("60000000-0000-0000-0000-000000000002");
        UUID physicalAssetId = UUID.fromString("40000000-0000-0000-0000-000000000003");
        when(row.getString("tenant_id")).thenReturn(TENANT);
        when(row.getObject("relation_evidence_id", UUID.class)).thenReturn(evidenceId);
        when(row.getObject("model_spec_id", UUID.class)).thenReturn(MODEL_ID);
        when(row.getInt("model_revision")).thenReturn(5);
        when(row.getString("model_checksum")).thenReturn("d".repeat(64));
        when(row.getInt("implementation_revision")).thenReturn(8);
        when(row.getString("implementation_checksum")).thenReturn("e".repeat(64));
        when(row.getObject("candidate_id", UUID.class)).thenReturn(candidateId);
        when(row.getInt("candidate_version")).thenReturn(evidenceCandidateVersion);
        when(row.getString("candidate_status")).thenReturn("PUBLISHING");
        when(row.getInt("attempt")).thenReturn(3);
        when(row.getString("dispatch_status")).thenReturn("COMPLETED");
        when(row.getObject("pipeline_run_id", UUID.class)).thenReturn(pipelineRunId);
        when(row.getString("pipeline_status")).thenReturn("BUILT");
        when(row.getInt("observation_attempt")).thenReturn(2);
        when(row.getString("adapter")).thenReturn("postgres");
        when(row.getString("credential_version_ref")).thenReturn("credential:v1");
        when(row.getString("database_name")).thenReturn("warehouse");
        when(row.getString("schema_name")).thenReturn("finance");
        when(row.getString("identifier")).thenReturn("dwd_budget_v2");
        when(row.getString("actual_type")).thenReturn("TABLE");
        when(row.getBoolean("relation_exists")).thenReturn(true);
        when(row.getBoolean("verified")).thenReturn(true);
        when(row.getString("actual_columns")).thenReturn("[]");
        when(row.getString("evidence_checksum")).thenReturn("f".repeat(64));
        when(row.getTimestamp("observed_at")).thenReturn(Timestamp.from(NOW));
        when(row.getObject("physical_asset_id", UUID.class)).thenReturn(physicalAssetId);
        when(row.getObject("physical_source_id", UUID.class)).thenReturn(command().sourceId());
        return row;
    }

    private static SuccessfulPublicationCommand command() {
        return new SuccessfulPublicationCommand(
            TENANT, MODEL_ID, 5, "d".repeat(64), 8, "e".repeat(64),
            UUID.fromString("20000000-0000-0000-0000-000000000002"), 12,
            CatalogAssetType.SEMANTIC_MODEL, "semantic-model:" + MODEL_ID,
            UUID.fromString("10000000-0000-0000-0000-000000000001"), "postgres",
            UUID.fromString("40000000-0000-0000-0000-000000000002"), NOW
        );
    }

    private static ModelServingProjection projection() {
        PublishedRef latest = new PublishedRef(
            MODEL_ID, 4, "a".repeat(64), 7, "b".repeat(64),
            UUID.fromString("20000000-0000-0000-0000-000000000001"), 11,
            9,
            UUID.fromString("40000000-0000-0000-0000-000000000001"), NOW.minusSeconds(60)
        );
        ServingRef serving = new ServingRef(
            MODEL_ID, 4, "a".repeat(64), 7, "b".repeat(64),
            UUID.fromString("20000000-0000-0000-0000-000000000001"), 9, 2,
            UUID.fromString("50000000-0000-0000-0000-000000000001"), 1,
            UUID.fromString("60000000-0000-0000-0000-000000000001"), "c".repeat(64),
            UUID.fromString("40000000-0000-0000-0000-000000000001"),
            UUID.fromString("10000000-0000-0000-0000-000000000001"),
            "postgres", "warehouse", "finance", "dwd_budget", NOW.minusSeconds(60)
        );
        return new ModelServingProjection(
            TENANT, MODEL_ID, CatalogAssetType.SEMANTIC_MODEL,
            "semantic-model:" + MODEL_ID, latest, serving, 3, "SYNCED", NOW.minusSeconds(60)
        );
    }

    private static ModelServingProjection projectionWithCurrentLatest() {
        ModelServingProjection old = projection();
        PublishedRef latest = new PublishedRef(
            MODEL_ID, 5, "d".repeat(64), 8, "e".repeat(64),
            UUID.fromString("20000000-0000-0000-0000-000000000002"), 12,
            null,
            null, NOW
        );
        return new ModelServingProjection(
            old.tenantId(), old.modelSpecId(), old.catalogAssetType(), old.catalogAssetKey(),
            latest, old.servingRef(), old.version(), old.syncStatus(), old.updatedAt()
        );
    }

    private static ModelServingProjection projectionWithSameRevisionFromPreviousCandidate() {
        ModelServingProjection old = projection();
        PublishedRef latest = new PublishedRef(
            MODEL_ID, 5, "d".repeat(64), 8, "e".repeat(64),
            UUID.fromString("20000000-0000-0000-0000-000000000001"), 6,
            2,
            null, NOW.minusSeconds(60)
        );
        return new ModelServingProjection(
            old.tenantId(), old.modelSpecId(), old.catalogAssetType(), old.catalogAssetKey(),
            latest, old.servingRef(), old.version(), old.syncStatus(), old.updatedAt()
        );
    }
}
