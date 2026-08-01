package com.yuzhi.dts.ingestion.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.domain.IngestionTaskRevision;
import com.yuzhi.dts.ingestion.repository.IngestionTaskRepository;
import com.yuzhi.dts.ingestion.repository.IngestionTaskRevisionRepository;
import com.yuzhi.dts.ingestion.service.audit.IngestionSecretRestoreAuditService;
import com.yuzhi.dts.ingestion.service.infra.InfraSettingsCryptoService;
import com.yuzhi.dts.ingestion.service.security.IngestionSensitiveConfigSupport;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Idempotently seals historical task credentials into the existing AES-GCM
 * revision snapshot before removing them from the compatibility task row.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class IngestionTaskSecretMigrationService implements ApplicationRunner, HealthIndicator {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_CLEAN = "CLEAN";
    public static final String STATUS_LEGACY_ENCRYPTED = "LEGACY_ENCRYPTED";
    public static final String STATUS_BLOCKED = "BLOCKED";
    public static final String STATUS_RESTORED_COMPAT = "RESTORED_COMPAT";

    private static final Logger LOG = LoggerFactory.getLogger(IngestionTaskSecretMigrationService.class);
    private static final String ERROR_MISSING_KEY = "MISSING_ENCRYPTION_KEY";
    private static final String ERROR_SEAL_FAILED = "REVISION_SEAL_FAILED";
    private static final String RESTORE_READY = "READY";
    private static final String RESTORE_RESTORED = "RESTORED";
    private static final String RESTORE_NOT_REQUIRED = "NOT_REQUIRED";
    private static final String RESTORE_REJECTED = "REJECTED";
    private static final String RESTORE_FAILED = "FAILED";
    private static final int MAX_RESTORE_BATCH_SIZE = 100;
    private static final Pattern RESTORE_BATCH_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    private final JdbcTemplate jdbcTemplate;
    private final IngestionTaskRepository taskRepository;
    private final IngestionTaskRevisionRepository revisionRepository;
    private final IngestionAccessContractService accessContractService;
    private final InfraSettingsCryptoService cryptoService;
    private final IngestionRequiresNewExecutor requiresNewExecutor;
    private final IngestionSecretRestoreAuditService restoreAuditService;
    private final ObjectMapper objectMapper;
    private volatile boolean startupSweepComplete;

    public IngestionTaskSecretMigrationService(
        JdbcTemplate jdbcTemplate,
        IngestionTaskRepository taskRepository,
        IngestionTaskRevisionRepository revisionRepository,
        IngestionAccessContractService accessContractService,
        InfraSettingsCryptoService cryptoService,
        IngestionRequiresNewExecutor requiresNewExecutor,
        IngestionSecretRestoreAuditService restoreAuditService,
        ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.taskRepository = taskRepository;
        this.revisionRepository = revisionRepository;
        this.accessContractService = accessContractService;
        this.cryptoService = cryptoService;
        this.requiresNewExecutor = requiresNewExecutor;
        this.restoreAuditService = restoreAuditService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        migratePendingTasks();
    }

    public int migratePendingTasks() {
        synchronizeMissingRows();
        List<Long> taskIds = jdbcTemplate.queryForList(
            "SELECT task_id FROM ingestion_task_secret_migration "
                + "WHERE status IN ('PENDING', 'BLOCKED') ORDER BY task_id",
            Long.class
        );
        int migrated = 0;
        for (Long taskId : taskIds) {
            try {
                requiresNewExecutor.executeWithoutResult(() -> migrateOne(taskId));
                migrated++;
            } catch (RuntimeException ex) {
                String errorCode = cryptoService.isEncryptionReady() ? ERROR_SEAL_FAILED : ERROR_MISSING_KEY;
                markBlocked(taskId, errorCode);
                LOG.warn("Ingestion task secret migration blocked: taskId={} errorCode={}", taskId, errorCode);
            }
        }
        startupSweepComplete = true;
        return migrated;
    }

    public void markNewTaskClean(Long taskId) {
        if (taskId == null) {
            throw new IllegalArgumentException("task id is required");
        }
        jdbcTemplate.update(
            "INSERT INTO ingestion_task_secret_migration(task_id, status, migrated_at, error_code, updated_at) "
                + "VALUES (?, 'CLEAN', CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP) "
                + "ON CONFLICT (task_id) DO NOTHING",
            taskId
        );
    }

    public void requireTaskReady(Long taskId) {
        String status = statusForTask(taskId);
        if (!STATUS_CLEAN.equals(status) && !STATUS_LEGACY_ENCRYPTED.equals(status)) {
            throw new IllegalStateException("INGESTION_TASK_SECRET_MIGRATION_" + status);
        }
    }

    public String statusForTask(Long taskId) {
        if (taskId == null) {
            return STATUS_PENDING;
        }
        List<String> statuses = jdbcTemplate.queryForList(
            "SELECT status FROM ingestion_task_secret_migration WHERE task_id = ?",
            String.class,
            taskId
        );
        return statuses.isEmpty() ? STATUS_PENDING : normalizeStatus(statuses.get(0));
    }

    /**
     * Read-only compatibility preview. This never restores credentials and never
     * mutates migration state.
     */
    public CompatibilityRestoreBatch dryRunCompatibilityRestore(int limit, String actor) {
        int boundedLimit = requireRestoreLimit(limit);
        String batchId = "dry-run:" + java.util.UUID.randomUUID();
        List<CompatibilityRestoreItem> items = new ArrayList<>();
        List<Long> taskIds = restoreCandidateTaskIds(boundedLimit);
        if (taskIds.isEmpty()) {
            persistEmptyBatchAudit(actor, batchId, true);
        }
        for (Long taskId : taskIds) {
            CompatibilityRestorePlan plan = inspectCompatibilityRestore(taskId, false);
            CompatibilityRestoreItem item = plan.item();
            persistAuditAttempt(actor, batchId, true, taskId, item.revisionId(), plan.configChecksum());
            persistAuditResult(actor, batchId, true, item, plan.configChecksum());
            items.add(item);
        }
        return summarizeCompatibilityRestore(batchId, true, boundedLimit, items);
    }

    /**
     * Explicit downgrade operation. Each task is independently revalidated and
     * restored in a bounded transaction; no startup path invokes this method.
     */
    public CompatibilityRestoreBatch restoreCompatibilityBatch(String batchId, int limit, String actor) {
        String safeBatchId = requireRestoreBatchId(batchId);
        int boundedLimit = requireRestoreLimit(limit);
        List<CompatibilityRestoreItem> items = new ArrayList<>();
        List<Long> taskIds = restoreCandidateTaskIds(boundedLimit);
        if (taskIds.isEmpty()) {
            persistEmptyBatchAudit(actor, safeBatchId, false);
        }
        for (Long taskId : taskIds) {
            CompatibilityRestorePlan preview = inspectCompatibilityRestore(taskId, false);
            persistAuditAttempt(
                actor,
                safeBatchId,
                false,
                taskId,
                preview.item().revisionId(),
                preview.configChecksum()
            );
            try {
                items.add(requiresNewExecutor.execute(() -> restoreCompatibilityTask(taskId, safeBatchId, actor)));
            } catch (RuntimeException ex) {
                CompatibilityRestoreItem failed = new CompatibilityRestoreItem(
                    taskId,
                    preview.item().revisionId(),
                    RESTORE_FAILED,
                    "RESTORE_TRANSACTION_FAILED"
                );
                requiresNewExecutor.executeWithoutResult(() -> {
                    markRestoreFailure(taskId, safeBatchId, failed, preview.configChecksum());
                    restoreAuditService.recordResult(
                        actor,
                        safeBatchId,
                        false,
                        taskId,
                        failed.revisionId(),
                        preview.configChecksum(),
                        failed.outcome(),
                        failed.errorCode()
                    );
                });
                items.add(failed);
            }
        }
        CompatibilityRestoreBatch result = summarizeCompatibilityRestore(safeBatchId, false, boundedLimit, items);
        LOG.info(
            "Ingestion compatibility restore batch completed: batchId={} examined={} restored={} rejected={} failed={}",
            safeBatchId,
            result.examined(),
            result.restored(),
            result.rejected(),
            result.failed()
        );
        return result;
    }

    @Override
    public Health health() {
        long pending = countStatusOrMissing(STATUS_PENDING);
        long blocked = countStatusOrMissing(STATUS_BLOCKED);
        long restoredCompat = countStatusOrMissing(STATUS_RESTORED_COMPAT);
        Health.Builder builder = startupSweepComplete && pending == 0 && blocked == 0 && restoredCompat == 0
            ? Health.up()
            : Health.down();
        return builder
            .withDetail("pending", pending)
            .withDetail("blocked", blocked)
            .withDetail("restoredCompat", restoredCompat)
            .withDetail("startupSweepComplete", startupSweepComplete)
            .build();
    }

    private void synchronizeMissingRows() {
        requiresNewExecutor.executeWithoutResult(() -> jdbcTemplate.update(
            "INSERT INTO ingestion_task_secret_migration(task_id, status, updated_at) "
                + "SELECT id, 'PENDING', CURRENT_TIMESTAMP FROM ingestion_task "
                + "ON CONFLICT (task_id) DO NOTHING"
        ));
    }

    private void migrateOne(Long taskId) {
        IngestionTask task = taskRepository.findByIdForUpdate(taskId)
            .orElseThrow(() -> new IllegalStateException("INGESTION_TASK_NOT_FOUND"));
        boolean sourceRaw = IngestionSensitiveConfigSupport.containsRawSecrets(task.getSourceConfig());
        boolean destinationRaw = IngestionSensitiveConfigSupport.containsRawSecrets(task.getDestinationConfig());
        boolean addaxRaw = IngestionSensitiveConfigSupport.containsRawSecrets(task.getAddaxConfig());
        if (!sourceRaw && !destinationRaw && !addaxRaw) {
            updateState(taskId, STATUS_CLEAN, null);
            return;
        }
        if (!cryptoService.isEncryptionReady()) {
            throw new IllegalStateException(ERROR_MISSING_KEY);
        }

        boolean legacyEncrypted = requiresLegacyEncryptedRuntime(task, sourceRaw, destinationRaw, addaxRaw);
        accessContractService.recordDraftRevision(task, null, true);
        if (isActiveOrPaused(task.getStatus())) {
            accessContractService.activateDraftRevision(taskId);
        }

        task.setSourceConfig(copyOrNull(IngestionSensitiveConfigSupport.stripRawSecrets(task.getSourceConfig())));
        task.setDestinationConfig(copyOrNull(IngestionSensitiveConfigSupport.stripRawSecrets(task.getDestinationConfig())));
        task.setAddaxConfig(copyOrNull(IngestionSensitiveConfigSupport.stripRawSecrets(task.getAddaxConfig())));
        taskRepository.saveAndFlush(task);
        updateState(taskId, legacyEncrypted ? STATUS_LEGACY_ENCRYPTED : STATUS_CLEAN, null);
    }

    private CompatibilityRestoreItem restoreCompatibilityTask(Long taskId, String batchId, String actor) {
        CompatibilityRestorePlan plan = inspectCompatibilityRestore(taskId, true);
        CompatibilityRestoreItem item = plan.item();
        if (RESTORE_READY.equals(item.outcome())) {
            RestoredSnapshot restored = plan.restoredSnapshot();
            IngestionTask target = plan.compatibilityTask();
            target.setSourceConfig(copyOrNull(restored.sourceConfig()));
            target.setDestinationConfig(copyOrNull(restored.destinationConfig()));
            target.setAddaxConfig(copyOrNull(restored.addaxConfig()));
            taskRepository.saveAndFlush(target);
            item = new CompatibilityRestoreItem(taskId, item.revisionId(), RESTORE_RESTORED, null);
        } else if ("ALREADY_RESTORED".equals(item.outcome())) {
            item = new CompatibilityRestoreItem(taskId, item.revisionId(), RESTORE_RESTORED, null);
        }
        if (!"ALREADY_RESTORED".equals(plan.item().outcome())) {
            recordRestoreOutcome(taskId, batchId, item, plan.configChecksum());
        }
        restoreAuditService.recordResult(
            actor,
            batchId,
            false,
            taskId,
            item.revisionId(),
            plan.configChecksum(),
            item.outcome(),
            item.errorCode()
        );
        return item;
    }

    private CompatibilityRestorePlan inspectCompatibilityRestore(Long taskId, boolean lockForRestore) {
        IngestionTask compatibilityTask = (lockForRestore
            ? taskRepository.findByIdForUpdate(taskId)
            : taskRepository.findById(taskId))
            .orElseThrow(() -> new IllegalStateException("INGESTION_TASK_NOT_FOUND"));
        List<IngestionTaskRevision> revisions = lockForRestore
            ? revisionRepository.findAllByTaskIdForUpdate(taskId)
            : revisionRepository.findAllByTaskIdOrderByRevisionNumberDesc(taskId);
        MigrationRestoreState migrationState = loadMigrationRestoreState(taskId, lockForRestore);
        if (STATUS_RESTORED_COMPAT.equals(migrationState.migrationStatus())
            && RESTORE_RESTORED.equals(migrationState.restoreStatus())) {
            return new CompatibilityRestorePlan(
                compatibilityTask,
                null,
                migrationState.restoreConfigChecksum(),
                new CompatibilityRestoreItem(
                    taskId,
                    migrationState.restoreRevisionId(),
                    "ALREADY_RESTORED",
                    null
                )
            );
        }
        if (!STATUS_CLEAN.equals(migrationState.migrationStatus())
            && !STATUS_LEGACY_ENCRYPTED.equals(migrationState.migrationStatus())) {
            return rejectedPlan(compatibilityTask, "MIGRATION_STATUS_" + migrationState.migrationStatus());
        }
        if (!cryptoService.isEncryptionReady()) {
            return failedPlan(compatibilityTask, ERROR_MISSING_KEY);
        }

        IngestionTaskRevision selected = null;
        RestoredSnapshot restoredSnapshot = null;
        for (IngestionTaskRevision revision : revisions) {
            if (revision.getRuntimeSnapshot() == null
                || revision.getRuntimeSnapshotIv() == null
                || !StringUtils.hasText(revision.getRuntimeSnapshotKeyVersion())) {
                continue;
            }
            try {
                restoredSnapshot = decryptRestoreSnapshot(revision);
                selected = revision;
                break;
            } catch (IOException | GeneralSecurityException | IllegalArgumentException ignored) {
                // Try the next older sealed revision. Details may contain provider
                // information and are deliberately not logged or returned.
            }
        }
        if (selected == null || restoredSnapshot == null) {
            return failedPlan(compatibilityTask, "NO_DECRYPTABLE_REVISION");
        }
        boolean restoredContainsSecrets = containsCompatibilitySecrets(restoredSnapshot);
        if (!restoredContainsSecrets) {
            return new CompatibilityRestorePlan(
                compatibilityTask,
                restoredSnapshot,
                selected.getEffectiveConfigChecksum(),
                new CompatibilityRestoreItem(taskId, selected.getId(), RESTORE_NOT_REQUIRED, null)
            );
        }
        if (!compatibilityEvidenceMatches(compatibilityTask, selected.getTaskSnapshot(), restoredSnapshot)) {
            return new CompatibilityRestorePlan(
                compatibilityTask,
                restoredSnapshot,
                selected.getEffectiveConfigChecksum(),
                new CompatibilityRestoreItem(taskId, selected.getId(), RESTORE_REJECTED, "CONFIG_EVIDENCE_DRIFT")
            );
        }
        if (containsCompatibilitySecrets(compatibilityTask)) {
            String outcome = compatibilityConfigsEqual(compatibilityTask, restoredSnapshot)
                ? "ALREADY_RESTORED"
                : RESTORE_REJECTED;
            String errorCode = RESTORE_REJECTED.equals(outcome) ? "COMPATIBILITY_SECRETS_ALREADY_CHANGED" : null;
            return new CompatibilityRestorePlan(
                compatibilityTask,
                restoredSnapshot,
                selected.getEffectiveConfigChecksum(),
                new CompatibilityRestoreItem(taskId, selected.getId(), outcome, errorCode)
            );
        }
        return new CompatibilityRestorePlan(
            compatibilityTask,
            restoredSnapshot,
            selected.getEffectiveConfigChecksum(),
            new CompatibilityRestoreItem(taskId, selected.getId(), RESTORE_READY, null)
        );
    }

    private MigrationRestoreState loadMigrationRestoreState(Long taskId, boolean forUpdate) {
        String sql = "SELECT status, restore_status, restore_revision_id, restore_config_checksum "
            + "FROM ingestion_task_secret_migration WHERE task_id=?"
            + (forUpdate ? " FOR UPDATE" : "");
        List<MigrationRestoreState> states = jdbcTemplate.query(
            sql,
            (rows, rowNum) -> new MigrationRestoreState(
                normalizeStatus(rows.getString(1)),
                normalizeNullable(rows.getString(2)),
                rows.getObject(3, Long.class),
                rows.getString(4)
            ),
            taskId
        );
        return states.isEmpty() ? new MigrationRestoreState(STATUS_PENDING, null, null, null) : states.get(0);
    }

    private List<Long> restoreCandidateTaskIds(int limit) {
        return jdbcTemplate.queryForList(
            "SELECT task_id FROM ingestion_task_secret_migration "
                + "WHERE restore_status IS NULL OR restore_status IN ('FAILED', 'REJECTED') "
                + "ORDER BY task_id LIMIT ?",
            Long.class,
            limit
        );
    }

    private void recordRestoreOutcome(
        Long taskId,
        String batchId,
        CompatibilityRestoreItem item,
        String configChecksum
    ) {
        String persistentStatus = persistentRestoreStatus(item.outcome());
        int updated = jdbcTemplate.update(
            "UPDATE ingestion_task_secret_migration SET "
                + "status=CASE WHEN ?='RESTORED' THEN 'RESTORED_COMPAT' ELSE status END, "
                + "restore_status=?, restore_batch_id=?, "
                + "restore_revision_id=?, restore_config_checksum=?, "
                + "restored_at=CASE WHEN ?='RESTORED' THEN CURRENT_TIMESTAMP ELSE NULL END, "
                + "restore_error_code=?, updated_at=CURRENT_TIMESTAMP WHERE task_id=? "
                + "AND status IN ('CLEAN','LEGACY_ENCRYPTED') "
                + "AND (restore_status IS NULL OR restore_status IN ('FAILED','REJECTED'))",
            persistentStatus,
            persistentStatus,
            batchId,
            item.revisionId(),
            configChecksum,
            persistentStatus,
            item.errorCode(),
            taskId
        );
        if (updated != 1) {
            throw new IllegalStateException("INGESTION_SECRET_RESTORE_STATE_CONFLICT");
        }
    }

    private void markRestoreFailure(
        Long taskId,
        String batchId,
        CompatibilityRestoreItem failed,
        String configChecksum
    ) {
        int updated = jdbcTemplate.update(
            "UPDATE ingestion_task_secret_migration SET restore_status='FAILED', restore_batch_id=?, "
                + "restore_revision_id=?, restore_config_checksum=?, restored_at=NULL, "
                + "restore_error_code=?, updated_at=CURRENT_TIMESTAMP WHERE task_id=? "
                + "AND NOT (status='RESTORED_COMPAT' AND restore_status='RESTORED')",
            batchId,
            failed.revisionId(),
            configChecksum,
            failed.errorCode(),
            taskId
        );
        if (updated == 0 && isPersistedRestoreComplete(taskId)) {
            return;
        }
        if (updated != 1) {
            throw new IllegalStateException("INGESTION_SECRET_RESTORE_FAILURE_STATE_NOT_PERSISTED");
        }
    }

    private boolean isPersistedRestoreComplete(Long taskId) {
        Long count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM ingestion_task_secret_migration "
                + "WHERE task_id=? AND status='RESTORED_COMPAT' AND restore_status='RESTORED'",
            Long.class,
            taskId
        );
        return count != null && count == 1L;
    }

    private void persistAuditAttempt(
        String actor,
        String batchId,
        boolean dryRun,
        Long taskId,
        Long revisionId,
        String configChecksum
    ) {
        requiresNewExecutor.executeWithoutResult(() -> restoreAuditService.recordAttempt(
            actor,
            batchId,
            dryRun,
            taskId,
            revisionId,
            configChecksum
        ));
    }

    private void persistAuditResult(
        String actor,
        String batchId,
        boolean dryRun,
        CompatibilityRestoreItem item,
        String configChecksum
    ) {
        requiresNewExecutor.executeWithoutResult(() -> restoreAuditService.recordResult(
            actor,
            batchId,
            dryRun,
            item.taskId(),
            item.revisionId(),
            configChecksum,
            item.outcome(),
            item.errorCode()
        ));
    }

    private void persistEmptyBatchAudit(String actor, String batchId, boolean dryRun) {
        persistAuditAttempt(actor, batchId, dryRun, null, null, null);
        requiresNewExecutor.executeWithoutResult(() -> restoreAuditService.recordResult(
            actor,
            batchId,
            dryRun,
            null,
            null,
            null,
            RESTORE_NOT_REQUIRED,
            null
        ));
    }

    private CompatibilityRestorePlan rejectedPlan(IngestionTask task, String errorCode) {
        return new CompatibilityRestorePlan(
            task,
            null,
            null,
            new CompatibilityRestoreItem(task.getId(), null, RESTORE_REJECTED, errorCode)
        );
    }

    private CompatibilityRestorePlan failedPlan(IngestionTask task, String errorCode) {
        return new CompatibilityRestorePlan(
            task,
            null,
            null,
            new CompatibilityRestoreItem(task.getId(), null, RESTORE_FAILED, errorCode)
        );
    }

    private RestoredSnapshot decryptRestoreSnapshot(IngestionTaskRevision revision)
        throws IOException, GeneralSecurityException {
        byte[] plain = cryptoService.decryptStrict(revision.getRuntimeSnapshot(), revision.getRuntimeSnapshotIv());
        JsonNode snapshot = objectMapper.readTree(plain);
        if (snapshot == null || !snapshot.isObject()) {
            throw new IllegalArgumentException("runtime snapshot must be an object");
        }
        return new RestoredSnapshot(
            copySnapshotConfig(snapshot, "sourceConfig"),
            copySnapshotConfig(snapshot, "destinationConfig"),
            copySnapshotConfig(snapshot, "addaxConfig"),
            snapshotText(snapshot, "sourceType"),
            snapshotText(snapshot, "sourceDataSourceId"),
            snapshotText(snapshot, "destinationType")
        );
    }

    private boolean compatibilityEvidenceMatches(
        IngestionTask current,
        JsonNode revisionEvidence,
        RestoredSnapshot restored
    ) {
        return Objects.equals(current.getSourceType(), restored.sourceType())
            && Objects.equals(
                current.getSourceDataSourceId() == null ? null : current.getSourceDataSourceId().toString(),
                restored.sourceDataSourceId()
            )
            && Objects.equals(current.getDestinationType(), restored.destinationType())
            && Objects.equals(
                IngestionSensitiveConfigSupport.stripRawSecrets(current.getSourceConfig()),
                IngestionSensitiveConfigSupport.stripRawSecrets(restored.sourceConfig())
            )
            && Objects.equals(
                IngestionSensitiveConfigSupport.stripRawSecrets(current.getDestinationConfig()),
                IngestionSensitiveConfigSupport.stripRawSecrets(restored.destinationConfig())
            )
            && Objects.equals(
                IngestionSensitiveConfigSupport.stripRawSecrets(current.getAddaxConfig()),
                IngestionSensitiveConfigSupport.stripRawSecrets(restored.addaxConfig())
            )
            && revisionEvidenceMatches(revisionEvidence, restored);
    }

    private boolean revisionEvidenceMatches(JsonNode evidence, RestoredSnapshot restored) {
        return evidence != null
            && evidence.isObject()
            && Objects.equals(snapshotText(evidence, "sourceType"), restored.sourceType())
            && Objects.equals(snapshotText(evidence, "sourceDataSourceId"), restored.sourceDataSourceId())
            && Objects.equals(snapshotText(evidence, "destinationType"), restored.destinationType())
            && Objects.equals(
                IngestionSensitiveConfigSupport.stripRawSecrets(copySnapshotConfig(evidence, "sourceConfig")),
                IngestionSensitiveConfigSupport.stripRawSecrets(restored.sourceConfig())
            )
            && Objects.equals(
                IngestionSensitiveConfigSupport.stripRawSecrets(copySnapshotConfig(evidence, "destinationConfig")),
                IngestionSensitiveConfigSupport.stripRawSecrets(restored.destinationConfig())
            )
            && Objects.equals(
                IngestionSensitiveConfigSupport.stripRawSecrets(copySnapshotConfig(evidence, "addaxConfig")),
                IngestionSensitiveConfigSupport.stripRawSecrets(restored.addaxConfig())
            );
    }

    private boolean compatibilityConfigsEqual(IngestionTask current, RestoredSnapshot restored) {
        return Objects.equals(current.getSourceConfig(), restored.sourceConfig())
            && Objects.equals(current.getDestinationConfig(), restored.destinationConfig())
            && Objects.equals(current.getAddaxConfig(), restored.addaxConfig());
    }

    private boolean containsCompatibilitySecrets(IngestionTask task) {
        return task != null && (
            IngestionSensitiveConfigSupport.containsRawSecrets(task.getSourceConfig())
                || IngestionSensitiveConfigSupport.containsRawSecrets(task.getDestinationConfig())
                || IngestionSensitiveConfigSupport.containsRawSecrets(task.getAddaxConfig())
        );
    }

    private boolean containsCompatibilitySecrets(RestoredSnapshot snapshot) {
        return snapshot != null && (
            IngestionSensitiveConfigSupport.containsRawSecrets(snapshot.sourceConfig())
                || IngestionSensitiveConfigSupport.containsRawSecrets(snapshot.destinationConfig())
                || IngestionSensitiveConfigSupport.containsRawSecrets(snapshot.addaxConfig())
        );
    }

    private JsonNode copySnapshotConfig(JsonNode snapshot, String field) {
        JsonNode value = snapshot == null ? null : snapshot.get(field);
        return value == null || value.isNull() ? null : value.deepCopy();
    }

    private String snapshotText(JsonNode snapshot, String field) {
        JsonNode value = snapshot == null ? null : snapshot.get(field);
        return value == null || value.isNull() || !value.isValueNode() ? null : value.asText();
    }

    private String persistentRestoreStatus(String outcome) {
        return switch (outcome) {
            case RESTORE_RESTORED, "ALREADY_RESTORED" -> RESTORE_RESTORED;
            case RESTORE_NOT_REQUIRED -> RESTORE_NOT_REQUIRED;
            case RESTORE_REJECTED -> RESTORE_REJECTED;
            default -> RESTORE_FAILED;
        };
    }

    private CompatibilityRestoreBatch summarizeCompatibilityRestore(
        String batchId,
        boolean dryRun,
        int requestedLimit,
        List<CompatibilityRestoreItem> items
    ) {
        int restored = (int) items.stream().filter(item -> RESTORE_RESTORED.equals(item.outcome())).count();
        int ready = (int) items.stream().filter(item -> RESTORE_READY.equals(item.outcome())).count();
        int rejected = (int) items.stream().filter(item -> RESTORE_REJECTED.equals(item.outcome())).count();
        int failed = (int) items.stream().filter(item -> RESTORE_FAILED.equals(item.outcome())).count();
        return new CompatibilityRestoreBatch(
            batchId,
            dryRun,
            requestedLimit,
            items.size(),
            ready,
            restored,
            rejected,
            failed,
            List.copyOf(items)
        );
    }

    private int requireRestoreLimit(int limit) {
        if (limit < 1 || limit > MAX_RESTORE_BATCH_SIZE) {
            throw new IllegalArgumentException("restore limit must be between 1 and " + MAX_RESTORE_BATCH_SIZE);
        }
        return limit;
    }

    private String requireRestoreBatchId(String batchId) {
        if (!StringUtils.hasText(batchId) || !RESTORE_BATCH_ID.matcher(batchId.trim()).matches()) {
            throw new IllegalArgumentException("restore batch id is invalid");
        }
        return batchId.trim();
    }

    private void markBlocked(Long taskId, String errorCode) {
        try {
            requiresNewExecutor.executeWithoutResult(() -> jdbcTemplate.update(
                "UPDATE ingestion_task_secret_migration "
                    + "SET status='BLOCKED', migrated_at=NULL, error_code=?, updated_at=CURRENT_TIMESTAMP "
                    + "WHERE task_id=?",
                errorCode,
                taskId
            ));
        } catch (RuntimeException stateFailure) {
            LOG.error("Failed to persist ingestion task secret migration state: taskId={}", taskId);
        }
    }

    private void updateState(Long taskId, String status, String errorCode) {
        jdbcTemplate.update(
            "UPDATE ingestion_task_secret_migration "
                + "SET status=?, migrated_at=CURRENT_TIMESTAMP, error_code=?, updated_at=CURRENT_TIMESTAMP WHERE task_id=?",
            status,
            errorCode,
            taskId
        );
    }

    private long countStatusOrMissing(String status) {
        if (STATUS_PENDING.equals(status)) {
            Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM ingestion_task t "
                    + "LEFT JOIN ingestion_task_secret_migration m ON m.task_id=t.id "
                    + "WHERE m.task_id IS NULL OR m.status='PENDING'",
                Long.class
            );
            return count == null ? 0L : count;
        }
        Long count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM ingestion_task_secret_migration WHERE status=?",
            Long.class,
            status
        );
        return count == null ? 0L : count;
    }

    private boolean requiresLegacyEncryptedRuntime(
        IngestionTask task,
        boolean sourceRaw,
        boolean destinationRaw,
        boolean addaxRaw
    ) {
        if (addaxRaw) {
            return true;
        }
        if (sourceRaw && (task.getSourceDataSourceId() == null || isFileSource(task.getSourceType()))) {
            return true;
        }
        return destinationRaw && !hasManagedDestination(task.getDestinationConfig());
    }

    private boolean hasManagedDestination(JsonNode config) {
        if (config == null || !config.isObject()) {
            return false;
        }
        for (String key : List.of("targetDataSourceId", "destinationDataSourceId", "dataSourceId")) {
            if (StringUtils.hasText(config.path(key).asText(null))) {
                return true;
            }
        }
        return false;
    }

    private boolean isFileSource(String sourceType) {
        if (!StringUtils.hasText(sourceType)) {
            return false;
        }
        String normalized = sourceType.trim().toLowerCase(Locale.ROOT);
        return List.of("excel", "csv", "txt", "excelreader", "txtfilereader").contains(normalized);
    }

    private boolean isActiveOrPaused(String status) {
        return "active".equalsIgnoreCase(status) || "paused".equalsIgnoreCase(status);
    }

    private JsonNode copyOrNull(JsonNode value) {
        return value == null || value.isNull() ? null : value.deepCopy();
    }

    private String normalizeStatus(String status) {
        return StringUtils.hasText(status) ? status.trim().toUpperCase(Locale.ROOT) : STATUS_PENDING;
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private record MigrationRestoreState(
        String migrationStatus,
        String restoreStatus,
        Long restoreRevisionId,
        String restoreConfigChecksum
    ) {}

    private record RestoredSnapshot(
        JsonNode sourceConfig,
        JsonNode destinationConfig,
        JsonNode addaxConfig,
        String sourceType,
        String sourceDataSourceId,
        String destinationType
    ) {}

    private record CompatibilityRestorePlan(
        IngestionTask compatibilityTask,
        RestoredSnapshot restoredSnapshot,
        String configChecksum,
        CompatibilityRestoreItem item
    ) {}

    public record CompatibilityRestoreItem(Long taskId, Long revisionId, String outcome, String errorCode) {}

    public record CompatibilityRestoreBatch(
        String batchId,
        boolean dryRun,
        int requestedLimit,
        int examined,
        int ready,
        int restored,
        int rejected,
        int failed,
        List<CompatibilityRestoreItem> items
    ) {}
}
