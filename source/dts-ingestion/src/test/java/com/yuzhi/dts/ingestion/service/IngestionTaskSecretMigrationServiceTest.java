package com.yuzhi.dts.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.domain.IngestionTaskRevision;
import com.yuzhi.dts.ingestion.repository.IngestionTaskRepository;
import com.yuzhi.dts.ingestion.repository.IngestionTaskRevisionRepository;
import com.yuzhi.dts.ingestion.service.audit.IngestionSecretRestoreAuditService;
import com.yuzhi.dts.ingestion.service.infra.InfraSettingsCryptoService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.mockito.ArgumentMatchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class IngestionTaskSecretMigrationServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private IngestionTaskRepository taskRepository;
    @Mock private IngestionTaskRevisionRepository revisionRepository;
    @Mock private IngestionAccessContractService accessContractService;
    @Mock private InfraSettingsCryptoService cryptoService;
    @Mock private IngestionRequiresNewExecutor requiresNewExecutor;
    @Mock private IngestionSecretRestoreAuditService restoreAuditService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private IngestionTaskSecretMigrationService service;

    @BeforeEach
    void setUp() {
        service = new IngestionTaskSecretMigrationService(
            jdbcTemplate,
            taskRepository,
            revisionRepository,
            accessContractService,
            cryptoService,
            requiresNewExecutor,
            restoreAuditService,
            objectMapper
        );
        lenient().doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(requiresNewExecutor).executeWithoutResult(any(Runnable.class));
        lenient().when(requiresNewExecutor.execute(any())).thenAnswer(invocation ->
            invocation.<java.util.function.Supplier<?>>getArgument(0).get()
        );
        lenient().when(jdbcTemplate.update(
            contains("status=CASE WHEN ?='RESTORED'"),
            any(), any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(1);
    }

    @Test
    void historicalManagedTaskShouldSealThenRemoveRawCredentials() {
        IngestionTask task = new IngestionTask();
        task.setId(41L);
        task.setStatus("active");
        task.setSourceType("mysqlreader");
        task.setSourceDataSourceId(UUID.randomUUID());
        ObjectNode sourceConfig = objectMapper.createObjectNode()
            .put("schema", "finance")
            .put("tokenUrl", "https://id.example/oauth/token")
            .put("tokenPath", "$.access_token")
            .put("checkpointToken", "cursor-42")
            .put("password", "source-secret");
        sourceConfig.set("secrets", objectMapper.createObjectNode()
            .put("headerName", "X-API-Key")
            .put("value", "legacy-raw-api-key"));
        task.setSourceConfig(sourceConfig);
        task.setDestinationConfig(objectMapper.createObjectNode()
            .put("targetDataSourceId", UUID.randomUUID().toString())
            .put("password", "target-secret"));
        when(jdbcTemplate.queryForList(anyString(), any(Class.class))).thenReturn(List.of(41L));
        when(taskRepository.findByIdForUpdate(41L)).thenReturn(Optional.of(task));
        when(cryptoService.isEncryptionReady()).thenReturn(true);

        assertThat(service.migratePendingTasks()).isEqualTo(1);

        verify(accessContractService).recordDraftRevision(task, null, true);
        verify(accessContractService).activateDraftRevision(41L);
        verify(taskRepository).saveAndFlush(task);
        assertThat(task.getSourceConfig().has("password")).isFalse();
        assertThat(task.getSourceConfig().path("schema").asText()).isEqualTo("finance");
        assertThat(task.getSourceConfig().path("tokenUrl").asText()).isEqualTo("https://id.example/oauth/token");
        assertThat(task.getSourceConfig().path("tokenPath").asText()).isEqualTo("$.access_token");
        assertThat(task.getSourceConfig().path("checkpointToken").asText()).isEqualTo("cursor-42");
        assertThat(task.getSourceConfig().has("secrets")).isFalse();
        assertThat(task.getDestinationConfig().has("password")).isFalse();
        assertThat(task.getDestinationConfig().path("targetDataSourceId").asText()).isNotBlank();
        assertThat(recordedSql()).contains("SET status=?, migrated_at=CURRENT_TIMESTAMP");
    }

    @Test
    void missingEncryptionKeyShouldBlockWithoutCleaningOriginalTask() {
        IngestionTask task = new IngestionTask();
        task.setId(42L);
        task.setStatus("active");
        task.setSourceType("mysqlreader");
        task.setSourceConfig(objectMapper.createObjectNode().put("password", "must-remain-until-sealed"));
        when(jdbcTemplate.queryForList(anyString(), any(Class.class))).thenReturn(List.of(42L));
        when(taskRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(task));
        when(cryptoService.isEncryptionReady()).thenReturn(false);

        assertThat(service.migratePendingTasks()).isZero();

        verify(taskRepository, never()).saveAndFlush(any());
        verify(accessContractService, never()).recordDraftRevision(any(), any(), any(Boolean.class));
        assertThat(task.getSourceConfig().path("password").asText()).isEqualTo("must-remain-until-sealed");
        assertThat(recordedSql()).contains("SET status='BLOCKED'");
    }

    @Test
    void executionReadinessShouldAllowOnlyCleanOrEncryptedLegacyStates() {
        for (String allowed : List.of(
            IngestionTaskSecretMigrationService.STATUS_CLEAN,
            IngestionTaskSecretMigrationService.STATUS_LEGACY_ENCRYPTED
        )) {
            when(jdbcTemplate.queryForList(anyString(), any(Class.class), any(Long.class))).thenReturn(List.of(allowed));
            service.requireTaskReady(51L);
        }

        for (String denied : List.of(
            IngestionTaskSecretMigrationService.STATUS_PENDING,
            IngestionTaskSecretMigrationService.STATUS_BLOCKED,
            IngestionTaskSecretMigrationService.STATUS_RESTORED_COMPAT
        )) {
            when(jdbcTemplate.queryForList(anyString(), any(Class.class), any(Long.class))).thenReturn(List.of(denied));
            assertThatThrownBy(() -> service.requireTaskReady(51L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("INGESTION_TASK_SECRET_MIGRATION_" + denied);
        }
    }

    @Test
    void compatibilityRestoreShouldDryRunThenRestoreLatestSnapshotAndRemainIdempotent() throws Exception {
        Long taskId = 61L;
        UUID sourceId = UUID.randomUUID();
        IngestionTask task = compatibilityTask(taskId, sourceId, "finance");
        IngestionTaskRevision revision = sealedRevision(91L, task, sourceId, "finance", "old-secret");
        when(jdbcTemplate.queryForList(contains("restore_status"), eq(Long.class), any(Integer.class)))
            .thenReturn(List.of(taskId), List.of(taskId), List.of());
        stubMigrationState(IngestionTaskSecretMigrationService.STATUS_CLEAN, null);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(taskRepository.findByIdForUpdate(taskId)).thenReturn(Optional.of(task));
        when(revisionRepository.findAllByTaskIdOrderByRevisionNumberDesc(taskId)).thenReturn(List.of(revision));
        when(revisionRepository.findAllByTaskIdForUpdate(taskId)).thenReturn(List.of(revision));
        when(cryptoService.isEncryptionReady()).thenReturn(true);
        when(cryptoService.decryptStrict(revision.getRuntimeSnapshot(), revision.getRuntimeSnapshotIv()))
            .thenReturn(runtimeSnapshot(sourceId, "finance", "old-secret"));

        var preview = service.dryRunCompatibilityRestore(10, "xiezm");
        var restored = service.restoreCompatibilityBatch("release-2026-08-01", 10, "xiezm");
        var repeated = service.restoreCompatibilityBatch("release-2026-08-01", 10, "xiezm");

        assertThat(preview.ready()).isEqualTo(1);
        assertThat(restored.restored()).isEqualTo(1);
        assertThat(repeated.examined()).isZero();
        assertThat(task.getSourceConfig().path("password").asText()).isEqualTo("old-secret");
        verify(taskRepository).saveAndFlush(task);
        verify(accessContractService, never()).materializeExecutionTask(any(), any());
        verify(restoreAuditService).recordAttempt("xiezm", "release-2026-08-01", false, taskId, 91L, "checksum-91");
        verify(restoreAuditService).recordResult(
            "xiezm",
            "release-2026-08-01",
            false,
            taskId,
            91L,
            "checksum-91",
            "RESTORED",
            null
        );
        assertThat(recordedSql()).contains("'RESTORED_COMPAT'");
    }

    @Test
    void compatibilityRestoreShouldRejectPublicConfigDrift() throws Exception {
        Long taskId = 62L;
        UUID sourceId = UUID.randomUUID();
        IngestionTask task = compatibilityTask(taskId, sourceId, "changed-schema");
        IngestionTaskRevision revision = sealedRevision(92L, task, sourceId, "finance", "old-secret");
        when(jdbcTemplate.queryForList(contains("restore_status"), eq(Long.class), any(Integer.class)))
            .thenReturn(List.of(taskId));
        stubMigrationState(IngestionTaskSecretMigrationService.STATUS_CLEAN, null);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(revisionRepository.findAllByTaskIdOrderByRevisionNumberDesc(taskId)).thenReturn(List.of(revision));
        when(cryptoService.isEncryptionReady()).thenReturn(true);
        when(cryptoService.decryptStrict(revision.getRuntimeSnapshot(), revision.getRuntimeSnapshotIv()))
            .thenReturn(runtimeSnapshot(sourceId, "finance", "old-secret"));

        var preview = service.dryRunCompatibilityRestore(10, "xiezm");

        assertThat(preview.rejected()).isEqualTo(1);
        assertThat(preview.items().get(0).errorCode()).isEqualTo("CONFIG_EVIDENCE_DRIFT");
        verify(taskRepository, never()).saveAndFlush(any());
    }

    @Test
    void compatibilityRestoreShouldFailClosedForBlockedState() {
        Long blockedTaskId = 63L;
        IngestionTask blocked = compatibilityTask(blockedTaskId, UUID.randomUUID(), "finance");
        when(jdbcTemplate.queryForList(contains("restore_status"), eq(Long.class), any(Integer.class)))
            .thenReturn(List.of(blockedTaskId));
        stubMigrationState(IngestionTaskSecretMigrationService.STATUS_BLOCKED, null);
        when(taskRepository.findById(blockedTaskId)).thenReturn(Optional.of(blocked));
        when(revisionRepository.findAllByTaskIdOrderByRevisionNumberDesc(blockedTaskId)).thenReturn(List.of());

        var preview = service.dryRunCompatibilityRestore(10, "xiezm");

        assertThat(preview.rejected()).isEqualTo(1);
        assertThat(preview.items().get(0).errorCode()).isEqualTo("MIGRATION_STATUS_BLOCKED");
        verify(taskRepository, never()).saveAndFlush(any());
    }

    @Test
    void compatibilityRestoreShouldFailClosedWhenEncryptionKeyIsMissing() {
        Long taskId = 64L;
        IngestionTask task = compatibilityTask(taskId, UUID.randomUUID(), "finance");
        when(jdbcTemplate.queryForList(contains("restore_status"), eq(Long.class), any(Integer.class)))
            .thenReturn(List.of(taskId));
        stubMigrationState(IngestionTaskSecretMigrationService.STATUS_CLEAN, null);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(revisionRepository.findAllByTaskIdOrderByRevisionNumberDesc(taskId)).thenReturn(List.of());
        when(cryptoService.isEncryptionReady()).thenReturn(false);

        var preview = service.dryRunCompatibilityRestore(10, "xiezm");

        assertThat(preview.failed()).isEqualTo(1);
        assertThat(preview.items().get(0).errorCode()).isEqualTo("MISSING_ENCRYPTION_KEY");
        verify(taskRepository, never()).saveAndFlush(any());
    }

    @Test
    void compatibilityRestoreShouldFailClosedBeforeMutationWhenAttemptAuditCannotPersist() {
        Long taskId = 65L;
        IngestionTask task = compatibilityTask(taskId, UUID.randomUUID(), "finance");
        when(jdbcTemplate.queryForList(contains("restore_status"), eq(Long.class), any(Integer.class)))
            .thenReturn(List.of(taskId));
        stubMigrationState(IngestionTaskSecretMigrationService.STATUS_BLOCKED, null);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(revisionRepository.findAllByTaskIdOrderByRevisionNumberDesc(taskId)).thenReturn(List.of());
        doThrow(new IllegalStateException("audit unavailable"))
            .when(restoreAuditService)
            .recordAttempt("xiezm", "restore-65", false, taskId, null, null);

        assertThatThrownBy(() -> service.restoreCompatibilityBatch("restore-65", 10, "xiezm"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("audit unavailable");

        verify(taskRepository, never()).findByIdForUpdate(taskId);
        verify(taskRepository, never()).saveAndFlush(any());
    }

    @Test
    void concurrentReplayShouldKeepPersistedRevisionAndChecksumWithoutRewritingRestoreEvidence() {
        Long taskId = 66L;
        IngestionTask task = compatibilityTask(taskId, UUID.randomUUID(), "finance");
        when(jdbcTemplate.queryForList(contains("restore_status"), eq(Long.class), any(Integer.class)))
            .thenReturn(List.of(taskId));
        stubMigrationState(
            IngestionTaskSecretMigrationService.STATUS_RESTORED_COMPAT,
            "RESTORED",
            96L,
            "checksum-96"
        );
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(taskRepository.findByIdForUpdate(taskId)).thenReturn(Optional.of(task));
        when(revisionRepository.findAllByTaskIdOrderByRevisionNumberDesc(taskId)).thenReturn(List.of());
        when(revisionRepository.findAllByTaskIdForUpdate(taskId)).thenReturn(List.of());

        var replay = service.restoreCompatibilityBatch("restore-66", 10, "xiezm");

        assertThat(replay.restored()).isEqualTo(1);
        assertThat(replay.items().get(0).revisionId()).isEqualTo(96L);
        assertThat(recordedSql()).doesNotContain("status=CASE WHEN ?='RESTORED'");
        verify(taskRepository, never()).saveAndFlush(any());
        verify(restoreAuditService).recordAttempt("xiezm", "restore-66", false, taskId, 96L, "checksum-96");
        verify(restoreAuditService).recordResult(
            "xiezm", "restore-66", false, taskId, 96L, "checksum-96", "RESTORED", null
        );
    }

    private IngestionTask compatibilityTask(Long id, UUID sourceId, String schema) {
        IngestionTask task = new IngestionTask();
        task.setId(id);
        task.setSourceType("mysqlreader");
        task.setSourceDataSourceId(sourceId);
        task.setDestinationType("postgresqlwriter");
        task.setSourceConfig(objectMapper.createObjectNode().put("schema", schema));
        task.setDestinationConfig(objectMapper.createObjectNode().put("table", "ods_finance"));
        task.setAddaxConfig(objectMapper.createObjectNode().put("channel", 1));
        return task;
    }

    private IngestionTaskRevision sealedRevision(
        Long id,
        IngestionTask task,
        UUID sourceId,
        String schema,
        String password
    ) {
        IngestionTaskRevision revision = new IngestionTaskRevision();
        revision.setId(id);
        revision.setRevisionNumber(2);
        revision.setRuntimeSnapshot(new byte[] { 1, 2, 3 });
        revision.setRuntimeSnapshotIv(new byte[] { 4, 5, 6 });
        revision.setRuntimeSnapshotKeyVersion("v1");
        revision.setEffectiveConfigChecksum("checksum-" + id);
        ObjectNode evidence = objectMapper.createObjectNode();
        evidence.put("sourceType", "mysqlreader");
        evidence.put("sourceDataSourceId", sourceId.toString());
        evidence.put("destinationType", "postgresqlwriter");
        evidence.set("sourceConfig", objectMapper.createObjectNode().put("schema", schema));
        evidence.set("destinationConfig", objectMapper.createObjectNode().put("table", "ods_finance"));
        evidence.set("addaxConfig", objectMapper.createObjectNode().put("channel", 1));
        revision.setTaskSnapshot(evidence);
        return revision;
    }

    private byte[] runtimeSnapshot(UUID sourceId, String schema, String password) throws Exception {
        ObjectNode snapshot = objectMapper.createObjectNode();
        snapshot.put("sourceType", "mysqlreader");
        snapshot.put("sourceDataSourceId", sourceId.toString());
        snapshot.put("destinationType", "postgresqlwriter");
        snapshot.set("sourceConfig", objectMapper.createObjectNode().put("schema", schema).put("password", password));
        snapshot.set("destinationConfig", objectMapper.createObjectNode().put("table", "ods_finance"));
        snapshot.set("addaxConfig", objectMapper.createObjectNode().put("channel", 1));
        return objectMapper.writeValueAsBytes(snapshot);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void stubMigrationState(String status, String restoreStatus) {
        stubMigrationState(status, restoreStatus, null, null);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void stubMigrationState(String status, String restoreStatus, Long revisionId, String checksum) {
        lenient().when(jdbcTemplate.query(
            contains("SELECT status, restore_status"),
            ArgumentMatchers.any(org.springframework.jdbc.core.RowMapper.class),
            any(Long.class)
        )).thenAnswer(invocation -> {
            org.springframework.jdbc.core.RowMapper mapper = invocation.getArgument(1);
            java.sql.ResultSet rows = org.mockito.Mockito.mock(java.sql.ResultSet.class);
            when(rows.getString(1)).thenReturn(status);
            when(rows.getString(2)).thenReturn(restoreStatus);
            when(rows.getObject(3, Long.class)).thenReturn(revisionId);
            when(rows.getString(4)).thenReturn(checksum);
            return List.of(mapper.mapRow(rows, 0));
        });
    }

    private String recordedSql() {
        return org.mockito.Mockito.mockingDetails(jdbcTemplate).getInvocations().stream()
            .filter(invocation -> "update".equals(invocation.getMethod().getName()))
            .map(invocation -> {
                Object sql = invocation.getArgument(0);
                return String.valueOf(sql);
            })
            .reduce("", (left, right) -> left + "\n" + right);
    }
}
