package com.yuzhi.dts.platform.service.catalog;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.security.SecurityLevelCatalog;
import com.yuzhi.dts.platform.domain.catalog.CatalogClassificationSnapshot;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.security.policy.AssetAction;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

@Service
@Transactional
public class CatalogLifecycleControlService {

    private static final List<String> ACTION_TYPES = List.of(
        "CREATE",
        "STORE",
        "ARCHIVE",
        "TRASH",
        "RESTORE",
        "PERMANENT_DESTROY"
    );
    private static final List<String> HIGH_LEVELS = List.of("SECRET", "CONFIDENTIAL");

    private final JdbcTemplate jdbcTemplate;
    private final CatalogDatasetRepository datasetRepository;
    private final CatalogClassificationService classificationService;
    private final CatalogClassificationPropagationService propagationService;
    private final List<CatalogManagedCopyDestructionAdapter> destructionAdapters;
    private final ObjectMapper objectMapper;
    private final AccessChecker accessChecker;

    public CatalogLifecycleControlService(
        JdbcTemplate jdbcTemplate,
        CatalogDatasetRepository datasetRepository,
        CatalogClassificationService classificationService,
        CatalogClassificationPropagationService propagationService,
        List<CatalogManagedCopyDestructionAdapter> destructionAdapters,
        ObjectMapper objectMapper,
        AccessChecker accessChecker
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.datasetRepository = datasetRepository;
        this.classificationService = classificationService;
        this.propagationService = propagationService;
        this.destructionAdapters = List.copyOf(destructionAdapters);
        this.objectMapper = objectMapper;
        this.accessChecker = accessChecker;
    }

    public ActionView submit(SubmitCommand command, String actor) {
        Objects.requireNonNull(command, "command");
        String actionType = actionType(command.actionType());
        String requester = required(actor, "actor", 128);
        String payloadChecksum = checksum(command.payloadChecksum());
        CatalogDataset dataset = requiredDataset(command.datasetId());
        requireAction(dataset, assetAction(actionType));
        CatalogClassificationSnapshot seal = currentSeal(dataset);
        requireSeal(command.sealId(), command.sealVersion(), seal);

        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofHours(actionExpiryHours(actionType, seal.getEffectiveLevel())));
        String policy = approvalPolicy(actionType, seal.getEffectiveLevel(), dataset);
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("approvalPolicy", policy);
        evidence.put("sourceType", dataset.getType());
        evidence.put("retentionDays", normalizeRetention(command.retentionDays()));
        jdbcTemplate.update(
            """
            insert into catalog_lifecycle_control_action (
                id, dataset_id, action_type, stage, status, payload_checksum,
                seal_id, seal_version, effective_level, requester, requester_dept,
                reason, expires_at, evidence_json, record_version,
                created_by, created_date, last_modified_by, last_modified_date
            ) values (?, ?, ?, ?, 'PENDING', ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), 0, ?, ?, ?, ?)
            """,
            id,
            dataset.getId(),
            actionType,
            stage(actionType),
            payloadChecksum,
            seal.getId(),
            version(seal),
            seal.getEffectiveLevel(),
            requester,
            trim(command.requesterDept()),
            trim(command.reason()),
            Timestamp.from(expiresAt),
            json(evidence),
            auditActor(requester),
            Timestamp.from(now),
            auditActor(requester),
            Timestamp.from(now)
        );
        appendEvent(
            dataset.getId(),
            stage(actionType),
            actionType + "_REQUESTED",
            "PENDING",
            "CONTROL_ACTION",
            id.toString(),
            seal,
            null,
            evidence,
            requester
        );
        return get(id);
    }

    /**
     * Compatibility bridge for the stable catalog_lifecycle_request API. The legacy workflow
     * decision remains authoritative, while execution is projected into the new trash/event facts.
     */
    public ActionView recordLegacyTrash(
        UUID datasetId,
        UUID legacyRequestId,
        String requester,
        String approver,
        Integer retentionDays
    ) {
        CatalogDataset dataset = requiredDataset(datasetId);
        requireAction(dataset, AssetAction.DELETE);
        CatalogClassificationSnapshot seal = currentSeal(dataset);
        UUID actionId = UUID.randomUUID();
        Instant now = Instant.now();
        String effectiveRequester = required(requester, "requester", 128);
        String effectiveApprover = required(approver, "approver", 128);
        if (effectiveRequester.equalsIgnoreCase(effectiveApprover)) {
            throw new IllegalStateException("Requester cannot approve the same lifecycle action");
        }
        Map<String, Object> evidence = Map.of(
            "legacyRequestId",
            legacyRequestId,
            "approvalPolicy",
            "LEGACY_WORKFLOW_ADAPTER",
            "retentionDays",
            normalizeRetention(retentionDays)
        );
        jdbcTemplate.update(
            """
            insert into catalog_lifecycle_control_action (
                id, dataset_id, action_type, stage, status, payload_checksum,
                seal_id, seal_version, effective_level, requester, reason,
                first_approved_by, first_approved_at, expires_at, evidence_json,
                record_version, created_by, created_date, last_modified_by, last_modified_date
            ) values (
                ?, ?, 'TRASH', 'DESTROY', 'APPROVED', ?, ?, ?, ?, ?,
                'Legacy lifecycle request adapter', ?, ?, ?, cast(? as jsonb),
                0, ?, ?, ?, ?
            )
            """,
            actionId,
            datasetId,
            sha256("legacy-lifecycle:" + legacyRequestId),
            seal.getId(),
            version(seal),
            seal.getEffectiveLevel(),
            effectiveRequester,
            effectiveApprover,
            Timestamp.from(now),
            Timestamp.from(now.plus(Duration.ofHours(1))),
            json(evidence),
            auditActor(effectiveRequester),
            Timestamp.from(now),
            auditActor(effectiveApprover),
            Timestamp.from(now)
        );
        trash(lockAction(actionId), effectiveApprover);
        return get(actionId);
    }

    public ApprovalResult approve(UUID actionId, String actor, String notes) {
        String approver = required(actor, "actor", 128);
        ActionRow row = lockAction(actionId);
        requireAction(requiredDataset(row.datasetId()), assetAction(row.actionType()));
        if (!List.of("PENDING", "SECOND_APPROVAL_PENDING").contains(row.status())) {
            throw new IllegalStateException("Lifecycle action is not awaiting approval");
        }
        if (Instant.now().isAfter(row.expiresAt())) {
            expire(row, approver);
            throw new IllegalStateException("Lifecycle action approval has expired");
        }
        if (approver.equalsIgnoreCase(row.requester())) {
            throw new IllegalStateException("Requester cannot approve the same lifecycle action");
        }
        boolean secondRequired = requiresSecondApproval(row);
        Instant now = Instant.now();
        if ("PENDING".equals(row.status()) && secondRequired) {
            jdbcTemplate.update(
                """
                update catalog_lifecycle_control_action
                   set status='SECOND_APPROVAL_PENDING',
                       first_approved_by=?, first_approved_at=?, decision_notes=?,
                       record_version=record_version+1,
                       last_modified_by=?, last_modified_date=?
                 where id=? and record_version=?
                """,
                approver,
                Timestamp.from(now),
                trim(notes),
                auditActor(approver),
                Timestamp.from(now),
                row.id(),
                row.recordVersion()
            );
            appendEvent(
                row.datasetId(),
                row.stage(),
                row.actionType() + "_FIRST_APPROVED",
                "SECOND_APPROVAL_PENDING",
                "CONTROL_ACTION",
                row.id().toString(),
                currentSeal(requiredDataset(row.datasetId())),
                null,
                Map.of("approver", approver),
                approver
            );
            return new ApprovalResult(get(row.id()), null);
        }
        if (
            "SECOND_APPROVAL_PENDING".equals(row.status()) &&
            row.firstApprovedBy() != null &&
            approver.equalsIgnoreCase(row.firstApprovedBy())
        ) {
            throw new IllegalStateException("Second approver must be different from the first approver");
        }
        int updated = jdbcTemplate.update(
            """
            update catalog_lifecycle_control_action
               set status='APPROVED',
                   first_approved_by=coalesce(first_approved_by, ?),
                   first_approved_at=coalesce(first_approved_at, ?),
                   second_approved_by=case when first_approved_by is null then second_approved_by else ? end,
                   second_approved_at=case when first_approved_by is null then second_approved_at else ? end,
                   decision_notes=?, record_version=record_version+1,
                   last_modified_by=?, last_modified_date=?
             where id=? and record_version=?
            """,
            approver,
            Timestamp.from(now),
            secondRequired ? approver : null,
            secondRequired ? Timestamp.from(now) : null,
            trim(notes),
            auditActor(approver),
            Timestamp.from(now),
            row.id(),
            row.recordVersion()
        );
        if (updated != 1) {
            throw new IllegalStateException("Lifecycle action approval changed concurrently");
        }
        ActionRow approved = lockAction(row.id());
        appendEvent(
            approved.datasetId(),
            approved.stage(),
            approved.actionType() + "_APPROVED",
            "APPROVED",
            "CONTROL_ACTION",
            approved.id().toString(),
            currentSeal(requiredDataset(approved.datasetId())),
            null,
            Map.of("approver", approver, "dualControl", secondRequired),
            approver
        );
        String token = executeApproved(approved, approver);
        return new ApprovalResult(get(row.id()), token);
    }

    public ActionView reject(UUID actionId, String actor, String notes) {
        String approver = required(actor, "actor", 128);
        ActionRow row = lockAction(actionId);
        if (!List.of("PENDING", "SECOND_APPROVAL_PENDING").contains(row.status())) {
            throw new IllegalStateException("Lifecycle action is not awaiting approval");
        }
        if (approver.equalsIgnoreCase(row.requester())) {
            throw new IllegalStateException("Requester cannot reject the same lifecycle action");
        }
        Instant now = Instant.now();
        jdbcTemplate.update(
            """
            update catalog_lifecycle_control_action
               set status='REJECTED', decision_notes=?, record_version=record_version+1,
                   last_modified_by=?, last_modified_date=?
             where id=? and record_version=?
            """,
            trim(notes),
            auditActor(approver),
            Timestamp.from(now),
            row.id(),
            row.recordVersion()
        );
        appendEvent(
            row.datasetId(),
            row.stage(),
            row.actionType() + "_REJECTED",
            "REJECTED",
            "CONTROL_ACTION",
            row.id().toString(),
            currentSeal(requiredDataset(row.datasetId())),
            null,
            Map.of("approver", approver),
            approver
        );
        return get(row.id());
    }

    public ActionView retryPermanentDestruction(UUID actionId, String actor) {
        String executor = required(actor, "actor", 128);
        ActionRow row = lockAction(actionId);
        if (
            !"PERMANENT_DESTROY".equals(row.actionType()) ||
            !"FAILED".equals(row.status()) ||
            row.firstApprovedBy() == null ||
            row.secondApprovedBy() == null
        ) {
            throw new IllegalStateException("Only an approved failed permanent destruction can be retried");
        }
        requireAction(requiredDataset(row.datasetId()), AssetAction.DESTROY);
        Instant now = Instant.now();
        jdbcTemplate.update(
            """
            update catalog_lifecycle_control_action
               set status='APPROVED', error_message=null,
                   record_version=record_version+1,
                   last_modified_by=?, last_modified_date=?
             where id=? and record_version=?
            """,
            auditActor(executor),
            Timestamp.from(now),
            row.id(),
            row.recordVersion()
        );
        destroy(lockAction(row.id()), executor);
        return get(row.id());
    }

    public TokenConsumption consume(ConsumeTokenCommand command, String actor) {
        Objects.requireNonNull(command, "command");
        String consumer = required(actor, "actor", 128);
        String tokenHash = sha256(required(command.token(), "token", 512));
        TokenRow token = jdbcTemplate
            .query(
                """
                select id, action_id, dataset_id, action_type, payload_checksum,
                       seal_id, seal_version, expires_at, consumed_at, revoked_at
                  from catalog_lifecycle_execution_token
                 where token_hash=?
                 for update
                """,
                (rs, rowNum) -> tokenRow(rs),
                tokenHash
            )
            .stream()
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Lifecycle execution token is invalid"));
        if (token.consumedAt() != null || token.revokedAt() != null || Instant.now().isAfter(token.expiresAt())) {
            throw new IllegalStateException("Lifecycle execution token is consumed, revoked or expired");
        }
        if (
            !Objects.equals(token.datasetId(), command.datasetId()) ||
            !Objects.equals(token.actionType(), actionType(command.actionType())) ||
            !Objects.equals(token.payloadChecksum(), checksum(command.payloadChecksum()))
        ) {
            throw new IllegalStateException("Lifecycle execution token does not match the requested write");
        }
        CatalogClassificationSnapshot current = currentSeal(requiredDataset(token.datasetId()));
        requireSeal(token.sealId(), token.sealVersion(), current);
        Instant now = Instant.now();
        jdbcTemplate.update(
            "update catalog_lifecycle_execution_token set consumed_at=?, consumed_by=? where id=? and consumed_at is null",
            Timestamp.from(now),
            consumer,
            token.id()
        );
        jdbcTemplate.update(
            """
            update catalog_lifecycle_control_action
               set status='EXECUTED', executed_by=?, executed_at=?,
                   record_version=record_version+1, last_modified_by=?, last_modified_date=?
             where id=? and status='APPROVED'
            """,
            consumer,
            Timestamp.from(now),
            auditActor(consumer),
            Timestamp.from(now),
            token.actionId()
        );
        appendEvent(
            token.datasetId(),
            stage(token.actionType()),
            token.actionType() + "_EXECUTED",
            "EXECUTED",
            "EXECUTION_TOKEN",
            token.id().toString(),
            current,
            null,
            Map.of("payloadChecksum", token.payloadChecksum()),
            consumer
        );
        return new TokenConsumption(token.actionId(), token.datasetId(), token.actionType(), now);
    }

    @Transactional(readOnly = true)
    public ActionView get(UUID actionId) {
        return jdbcTemplate
            .query(
                """
                select id, dataset_id, action_type, stage, status, payload_checksum,
                       seal_id, seal_version, effective_level, requester, requester_dept,
                       reason, first_approved_by, first_approved_at, second_approved_by,
                       second_approved_at, decision_notes, expires_at, execution_token_id,
                       executed_by, executed_at, evidence_json::text, error_message,
                       record_version, created_date
                  from catalog_lifecycle_control_action
                 where id=?
                """,
                (rs, rowNum) -> view(rs),
                actionId
            )
            .stream()
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Lifecycle action does not exist"));
    }

    @Transactional(readOnly = true)
    public List<ActionView> list(UUID datasetId, String status, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        String sql =
            """
            select id, dataset_id, action_type, stage, status, payload_checksum,
                   seal_id, seal_version, effective_level, requester, requester_dept,
                   reason, first_approved_by, first_approved_at, second_approved_by,
                   second_approved_at, decision_notes, expires_at, execution_token_id,
                   executed_by, executed_at, evidence_json::text, error_message,
                   record_version, created_date
              from catalog_lifecycle_control_action
             where (?::uuid is null or dataset_id=?)
               and (?::varchar is null or status=?)
             order by created_date desc
             limit ?
            """;
        String normalizedStatus = trim(status);
        return jdbcTemplate.query(sql, (rs, rowNum) -> view(rs), datasetId, datasetId, normalizedStatus, normalizedStatus, safeLimit);
    }

    @Scheduled(cron = "${dts.catalog.lifecycle.trash-expiry-cron:0 0 * * * *}")
    public void markDestructionCandidates() {
        Instant now = Instant.now();
        List<UUID> datasets = jdbcTemplate.query(
            """
            update catalog_lifecycle_trash_fact
               set status='DESTRUCTION_CANDIDATE', last_modified_by='system', last_modified_date=?
             where status='TRASHED' and retain_until<=?
            returning dataset_id
            """,
            (rs, rowNum) -> rs.getObject(1, UUID.class),
            Timestamp.from(now),
            Timestamp.from(now)
        );
        for (UUID datasetId : datasets) {
            CatalogDataset dataset = requiredDataset(datasetId);
            appendEvent(
                datasetId,
                "DESTROY",
                "PERMANENT_DESTROY_CANDIDATE",
                "DESTRUCTION_CANDIDATE",
                "TRASH_RETENTION",
                datasetId.toString(),
                currentSeal(dataset),
                null,
                Map.of("autoExecution", false),
                "system"
            );
        }
    }

    private String executeApproved(ActionRow action, String actor) {
        return switch (action.actionType()) {
            case "CREATE", "STORE" -> issueToken(action, actor);
            case "ARCHIVE" -> {
                archive(action, actor);
                yield null;
            }
            case "TRASH" -> {
                trash(action, actor);
                yield null;
            }
            case "RESTORE" -> {
                restore(action, actor);
                yield null;
            }
            case "PERMANENT_DESTROY" -> {
                destroy(action, actor);
                yield null;
            }
            default -> throw new IllegalStateException("Unsupported lifecycle action");
        };
    }

    private String issueToken(ActionRow action, String actor) {
        String rawToken = UUID.randomUUID().toString() + "." + UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();
        Instant now = Instant.now();
        Instant expires = now.plus(Duration.ofMinutes(30));
        jdbcTemplate.update(
            """
            insert into catalog_lifecycle_execution_token (
                id, action_id, token_hash, dataset_id, action_type, payload_checksum,
                seal_id, seal_version, expires_at, created_by, created_date
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            tokenId,
            action.id(),
            sha256(rawToken),
            action.datasetId(),
            action.actionType(),
            action.payloadChecksum(),
            action.sealId(),
            action.sealVersion(),
            Timestamp.from(expires),
            auditActor(actor),
            Timestamp.from(now)
        );
        jdbcTemplate.update(
            """
            update catalog_lifecycle_control_action
               set execution_token_id=?, record_version=record_version+1,
                   last_modified_by=?, last_modified_date=?
             where id=? and status='APPROVED'
            """,
            tokenId,
            auditActor(actor),
            Timestamp.from(now),
            action.id()
        );
        return rawToken;
    }

    private void archive(ActionRow action, String actor) {
        CatalogDataset dataset = requiredDataset(action.datasetId());
        dataset.setLifecycleStatus("ARCHIVED");
        datasetRepository.save(dataset);
        completeAction(action.id(), actor, Map.of("lifecycleStatus", "ARCHIVED"));
    }

    private void trash(ActionRow action, String actor) {
        CatalogDataset dataset = requiredDataset(action.datasetId());
        CatalogClassificationSnapshot seal = currentSeal(dataset);
        Map<String, Object> actionEvidence = parseMap(action.evidenceJson());
        int retentionDays = normalizeRetention((Integer) actionEvidence.get("retentionDays"));
        Instant now = Instant.now();
        Instant retainUntil = now.plus(Duration.ofDays(retentionDays));
        jdbcTemplate.update(
            """
            insert into catalog_lifecycle_trash_fact (
                id, dataset_id, status, trash_action_id, previous_enabled,
                previous_lifecycle_status, retain_until, seal_id, seal_version,
                effective_level, created_by, created_date, last_modified_by, last_modified_date
            ) values (?, ?, 'TRASHED', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (dataset_id) do update
               set status='TRASHED', trash_action_id=excluded.trash_action_id,
                   previous_enabled=excluded.previous_enabled,
                   previous_lifecycle_status=excluded.previous_lifecycle_status,
                   retain_until=excluded.retain_until, seal_id=excluded.seal_id,
                   seal_version=excluded.seal_version, effective_level=excluded.effective_level,
                   restored_action_id=null, restored_at=null, destroyed_action_id=null, destroyed_at=null,
                   last_modified_by=excluded.last_modified_by, last_modified_date=excluded.last_modified_date
             where catalog_lifecycle_trash_fact.status in ('RESTORED','TRASHED','DESTRUCTION_CANDIDATE')
            """,
            UUID.randomUUID(),
            dataset.getId(),
            action.id(),
            Boolean.TRUE.equals(dataset.getEnabled()),
            dataset.getLifecycleStatus(),
            Timestamp.from(retainUntil),
            seal.getId(),
            version(seal),
            seal.getEffectiveLevel(),
            auditActor(actor),
            Timestamp.from(now),
            auditActor(actor),
            Timestamp.from(now)
        );
        disableDataset(dataset, "TRASHED", now);
        completeAction(action.id(), actor, Map.of("retainUntil", retainUntil, "accessRevoked", true));
    }

    private void restore(ActionRow action, String actor) {
        CatalogDataset dataset = requiredDataset(action.datasetId());
        TrashRow trash = lockTrash(dataset.getId());
        if (!List.of("TRASHED", "DESTRUCTION_CANDIDATE", "RESTORE_REQUESTED").contains(trash.status())) {
            throw new IllegalStateException("Dataset is not restorable");
        }
        CatalogClassificationSnapshot current = currentSeal(dataset);
        if (!SecurityLevelCatalog.isDataAtLeast(current.getEffectiveLevel(), trash.effectiveLevel())) {
            throw new IllegalStateException("Current classification is lower than the pre-trash classification");
        }
        Integer conflicts = jdbcTemplate.queryForObject(
            """
            select count(*) from catalog_dataset
             where id<>? and enabled=true
               and source_id is not distinct from ?
               and lower(coalesce(hive_database,''))=lower(coalesce(?, ''))
               and lower(coalesce(hive_table,name))=lower(coalesce(?, ''))
            """,
            Integer.class,
            dataset.getId(),
            dataset.getSourceId(),
            dataset.getHiveDatabase(),
            dataset.getHiveTable() == null ? dataset.getName() : dataset.getHiveTable()
        );
        if (conflicts != null && conflicts > 0) {
            throw new IllegalStateException("Restore is blocked by an active dataset name conflict");
        }
        Instant now = Instant.now();
        dataset.setEnabled(trash.previousEnabled());
        dataset.setLifecycleStatus(trash.previousLifecycleStatus() == null ? "ACTIVE" : trash.previousLifecycleStatus());
        datasetRepository.save(dataset);
        jdbcTemplate.update(
            """
            update catalog_lifecycle_trash_fact
               set status='RESTORED', restored_action_id=?, restored_at=?,
                   last_modified_by=?, last_modified_date=?
             where dataset_id=?
            """,
            action.id(),
            Timestamp.from(now),
            auditActor(actor),
            Timestamp.from(now),
            dataset.getId()
        );
        completeAction(action.id(), actor, Map.of("restoredClassification", current.getEffectiveLevel()));
    }

    private void destroy(ActionRow action, String actor) {
        CatalogDataset dataset = requiredDataset(action.datasetId());
        TrashRow trash = lockTrash(dataset.getId());
        if (!List.of("TRASHED", "DESTRUCTION_CANDIDATE").contains(trash.status())) {
            failAction(action.id(), actor, "Permanent destruction requires a retained trash copy");
            return;
        }
        CatalogClassificationPropagationService.ImpactExplanation impact = propagationService.explainImpact(dataset.getId());
        if (
            impact.cycleDetected() ||
            !impact.downstreamDatasetIds().isEmpty() ||
            !impact.blockers().isEmpty()
        ) {
            failAction(
                action.id(),
                actor,
                "Missing lineage, downstream lineage or a cycle blocks permanent destruction"
            );
            return;
        }
        CatalogManagedCopyDestructionAdapter adapter = destructionAdapters
            .stream()
            .filter(candidate -> candidate.supports(dataset))
            .findFirst()
            .orElse(null);
        if (adapter == null) {
            failAction(action.id(), actor, "No safe managed-copy destruction adapter supports this dataset");
            return;
        }
        CatalogManagedCopyDestructionAdapter.DestructionResult result = adapter.destroy(dataset, action.id().toString());
        int attempt = nextProofAttempt(action.id());
        Instant now = Instant.now();
        String manifest = json(
            Map.of(
                "objects",
                result.destroyedObjects(),
                "evidence",
                result.evidence(),
                "datasetAssetKey",
                CatalogAssetKey.dataset(dataset)
            )
        );
        String proofStatus = result.completed() && !result.externalSourceTouched() ? "SUCCEEDED" : "FAILED";
        jdbcTemplate.update(
            """
            insert into catalog_destruction_proof (
                id, action_id, dataset_id, attempt_no, adapter_code,
                object_manifest, manifest_checksum, first_approved_by,
                second_approved_by, executed_by, result_status,
                external_source_touched, failure_message, executed_at,
                created_by, created_date
            ) values (?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            UUID.randomUUID(),
            action.id(),
            dataset.getId(),
            attempt,
            result.adapterCode(),
            manifest,
            sha256(manifest),
            action.firstApprovedBy(),
            action.secondApprovedBy(),
            actor,
            proofStatus,
            result.externalSourceTouched(),
            trim(result.failureMessage()),
            Timestamp.from(now),
            auditActor(actor),
            Timestamp.from(now)
        );
        if (!"SUCCEEDED".equals(proofStatus)) {
            failAction(action.id(), actor, result.failureMessage());
            return;
        }
        disableDataset(dataset, "DESTROYED", now);
        jdbcTemplate.update(
            """
            update catalog_lifecycle_trash_fact
               set status='DESTROYED', destroyed_action_id=?, destroyed_at=?,
                   last_modified_by=?, last_modified_date=?
             where dataset_id=?
            """,
            action.id(),
            Timestamp.from(now),
            auditActor(actor),
            Timestamp.from(now),
            dataset.getId()
        );
        completeAction(
            action.id(),
            actor,
            Map.of("adapter", result.adapterCode(), "manifestChecksum", sha256(manifest), "externalSourceTouched", false)
        );
    }

    private void disableDataset(CatalogDataset dataset, String lifecycleStatus, Instant now) {
        dataset.setEnabled(Boolean.FALSE);
        dataset.setLifecycleStatus(lifecycleStatus);
        datasetRepository.save(dataset);
        jdbcTemplate.update(
            "update catalog_dataset_grant set valid_to=least(coalesce(valid_to, ?), ?) where dataset_id=?",
            Timestamp.from(now),
            Timestamp.from(now),
            dataset.getId()
        );
        jdbcTemplate.update(
            """
            update asset_grant
               set valid_to=least(coalesce(valid_to, ?), ?)
             where upper(asset_type) in ('DATASET','CATALOG_DATASET') and asset_id=?
            """,
            Timestamp.from(now),
            Timestamp.from(now),
            dataset.getId().toString()
        );
        jdbcTemplate.update(
            """
            update catalog_dataset_job
               set status='CANCELLED', finished_at=coalesce(finished_at, ?),
                   message=coalesce(message, 'Cancelled by lifecycle control'),
                   last_modified_by='system', last_modified_date=?
             where dataset_id=? and upper(status) in ('PENDING','QUEUED','RUNNING')
            """,
            Timestamp.from(now),
            Timestamp.from(now),
            dataset.getId()
        );
    }

    private void completeAction(UUID actionId, String actor, Map<String, Object> evidence) {
        Instant now = Instant.now();
        ActionRow action = lockAction(actionId);
        jdbcTemplate.update(
            """
            update catalog_lifecycle_control_action
               set status='EXECUTED', executed_by=?, executed_at=?,
                   evidence_json=coalesce(evidence_json, '{}'::jsonb) || cast(? as jsonb),
                   error_message=null, record_version=record_version+1,
                   last_modified_by=?, last_modified_date=?
             where id=?
            """,
            actor,
            Timestamp.from(now),
            json(evidence),
            auditActor(actor),
            Timestamp.from(now),
            actionId
        );
        appendEvent(
            action.datasetId(),
            action.stage(),
            action.actionType() + "_EXECUTED",
            "EXECUTED",
            "CONTROL_ACTION",
            action.id().toString(),
            currentSeal(requiredDataset(action.datasetId())),
            null,
            evidence,
            actor
        );
    }

    private void failAction(UUID actionId, String actor, String message) {
        Instant now = Instant.now();
        ActionRow action = lockAction(actionId);
        jdbcTemplate.update(
            """
            update catalog_lifecycle_control_action
               set status='FAILED', executed_by=?, executed_at=?, error_message=?,
                   record_version=record_version+1, last_modified_by=?, last_modified_date=?
             where id=?
            """,
            actor,
            Timestamp.from(now),
            truncate(message, 2048),
            auditActor(actor),
            Timestamp.from(now),
            actionId
        );
        appendEvent(
            action.datasetId(),
            action.stage(),
            action.actionType() + "_FAILED",
            "FAILED",
            "CONTROL_ACTION",
            action.id().toString(),
            currentSeal(requiredDataset(action.datasetId())),
            null,
            Map.of("error", String.valueOf(message)),
            actor
        );
    }

    private void expire(ActionRow action, String actor) {
        Instant now = Instant.now();
        jdbcTemplate.update(
            """
            update catalog_lifecycle_control_action
               set status='EXPIRED', record_version=record_version+1,
                   last_modified_by=?, last_modified_date=?
             where id=? and status in ('PENDING','SECOND_APPROVAL_PENDING')
            """,
            auditActor(actor),
            Timestamp.from(now),
            action.id()
        );
    }

    private void appendEvent(
        UUID datasetId,
        String stage,
        String eventType,
        String status,
        String requestSource,
        String requestRef,
        CatalogClassificationSnapshot seal,
        Long dataVolume,
        Map<String, Object> evidence,
        String actor
    ) {
        Instant now = Instant.now();
        jdbcTemplate.update(
            """
            insert into catalog_lifecycle_event (
                id, dataset_id, stage, event_type, status, request_source,
                request_ref, seal_id, seal_version, effective_level, data_volume,
                evidence_json, actor, occurred_at, created_by, created_date
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?, ?)
            """,
            UUID.randomUUID(),
            datasetId,
            stage,
            eventType,
            status,
            requestSource,
            requestRef,
            seal == null ? null : seal.getId(),
            seal == null ? null : version(seal),
            seal == null ? null : seal.getEffectiveLevel(),
            dataVolume,
            json(evidence),
            actor,
            Timestamp.from(now),
            auditActor(actor),
            Timestamp.from(now)
        );
    }

    private ActionRow lockAction(UUID actionId) {
        return jdbcTemplate
            .query(
                """
                select id, dataset_id, action_type, stage, status, payload_checksum,
                       seal_id, seal_version, effective_level, requester,
                       first_approved_by, second_approved_by, expires_at,
                       evidence_json::text, record_version
                  from catalog_lifecycle_control_action
                 where id=?
                 for update
                """,
                (rs, rowNum) -> actionRow(rs),
                actionId
            )
            .stream()
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Lifecycle action does not exist"));
    }

    private TrashRow lockTrash(UUID datasetId) {
        return jdbcTemplate
            .query(
                """
                select dataset_id, status, previous_enabled, previous_lifecycle_status,
                       retain_until, seal_id, seal_version, effective_level
                  from catalog_lifecycle_trash_fact
                 where dataset_id=?
                 for update
                """,
                (rs, rowNum) ->
                    new TrashRow(
                        rs.getObject("dataset_id", UUID.class),
                        rs.getString("status"),
                        rs.getBoolean("previous_enabled"),
                        rs.getString("previous_lifecycle_status"),
                        rs.getTimestamp("retain_until").toInstant(),
                        rs.getObject("seal_id", UUID.class),
                        rs.getLong("seal_version"),
                        rs.getString("effective_level")
                    ),
                datasetId
            )
            .stream()
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Dataset has no retained trash copy"));
    }

    private CatalogDataset requiredDataset(UUID datasetId) {
        if (datasetId == null) {
            throw new IllegalArgumentException("datasetId is required");
        }
        return datasetRepository
            .findById(datasetId)
            .orElseThrow(() -> new IllegalArgumentException("Dataset does not exist: " + datasetId));
    }

    private void requireAction(CatalogDataset dataset, AssetAction action) {
        if (!accessChecker.canPerform(dataset, action)) {
            throw new AccessDeniedException("asset_action_not_allowed:" + action.code());
        }
    }

    private AssetAction assetAction(String lifecycleAction) {
        return switch (actionType(lifecycleAction)) {
            case "CREATE" -> AssetAction.CREATE;
            case "STORE", "RESTORE" -> AssetAction.COPY;
            case "ARCHIVE" -> AssetAction.ARCHIVE;
            case "TRASH" -> AssetAction.DELETE;
            case "PERMANENT_DESTROY" -> AssetAction.DESTROY;
            default -> throw new IllegalArgumentException("Unsupported lifecycle action type: " + lifecycleAction);
        };
    }

    private CatalogClassificationSnapshot currentSeal(CatalogDataset dataset) {
        return classificationService
            .resolve("ASSET", CatalogAssetKey.dataset(dataset))
            .orElseThrow(() -> new IllegalStateException("Dataset has no sealed classification"));
    }

    private void requireSeal(UUID expectedId, long expectedVersion, CatalogClassificationSnapshot current) {
        if (
            expectedId == null ||
            !expectedId.equals(current.getId()) ||
            expectedVersion != version(current) ||
            !CatalogClassificationService.STATUS_PROPAGATED.equals(current.getPropagationStatus())
        ) {
            throw new IllegalStateException("Classification seal is missing, stale or not fully propagated");
        }
    }

    private boolean requiresSecondApproval(ActionRow action) {
        return "PERMANENT_DESTROY".equals(action.actionType()) || HIGH_LEVELS.contains(action.effectiveLevel());
    }

    private String approvalPolicy(String actionType, String level, CatalogDataset dataset) {
        if ("PERMANENT_DESTROY".equals(actionType)) {
            return "DUAL_CONTROL_DESTRUCTION";
        }
        if (HIGH_LEVELS.contains(level)) {
            return "DUAL_CONTROL_HIGH_CLASSIFICATION";
        }
        return "OWNER_DEPARTMENT_APPROVAL";
    }

    private int actionExpiryHours(String actionType, String level) {
        return "PERMANENT_DESTROY".equals(actionType) || HIGH_LEVELS.contains(level) ? 24 : 72;
    }

    private int normalizeRetention(Integer days) {
        if (days == null || days < 1) {
            return 30;
        }
        return Math.min(days, 3650);
    }

    private int nextProofAttempt(UUID actionId) {
        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from catalog_destruction_proof where action_id=?",
            Integer.class,
            actionId
        );
        return (count == null ? 0 : count) + 1;
    }

    private String actionType(String value) {
        String normalized = required(value, "actionType", 32).toUpperCase(Locale.ROOT);
        if (!ACTION_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported lifecycle action type: " + normalized);
        }
        return normalized;
    }

    private String stage(String actionType) {
        return switch (actionType) {
            case "CREATE" -> "CREATE";
            case "STORE" -> "STORAGE";
            case "ARCHIVE" -> "ARCHIVE";
            case "TRASH", "RESTORE", "PERMANENT_DESTROY" -> "DESTROY";
            default -> throw new IllegalArgumentException("Unsupported lifecycle stage");
        };
    }

    private long version(CatalogClassificationSnapshot snapshot) {
        return snapshot.getRecordVersion() == null ? 0L : snapshot.getRecordVersion();
    }

    private ActionRow actionRow(ResultSet rs) throws SQLException {
        return new ActionRow(
            rs.getObject("id", UUID.class),
            rs.getObject("dataset_id", UUID.class),
            rs.getString("action_type"),
            rs.getString("stage"),
            rs.getString("status"),
            rs.getString("payload_checksum"),
            rs.getObject("seal_id", UUID.class),
            rs.getLong("seal_version"),
            rs.getString("effective_level"),
            rs.getString("requester"),
            rs.getString("first_approved_by"),
            rs.getString("second_approved_by"),
            rs.getTimestamp("expires_at").toInstant(),
            rs.getString("evidence_json"),
            rs.getLong("record_version")
        );
    }

    private TokenRow tokenRow(ResultSet rs) throws SQLException {
        return new TokenRow(
            rs.getObject("id", UUID.class),
            rs.getObject("action_id", UUID.class),
            rs.getObject("dataset_id", UUID.class),
            rs.getString("action_type"),
            rs.getString("payload_checksum"),
            rs.getObject("seal_id", UUID.class),
            rs.getLong("seal_version"),
            rs.getTimestamp("expires_at").toInstant(),
            rs.getTimestamp("consumed_at") == null ? null : rs.getTimestamp("consumed_at").toInstant(),
            rs.getTimestamp("revoked_at") == null ? null : rs.getTimestamp("revoked_at").toInstant()
        );
    }

    private ActionView view(ResultSet rs) throws SQLException {
        return new ActionView(
            rs.getObject("id", UUID.class),
            rs.getObject("dataset_id", UUID.class),
            rs.getString("action_type"),
            rs.getString("stage"),
            rs.getString("status"),
            rs.getString("payload_checksum"),
            rs.getObject("seal_id", UUID.class),
            rs.getLong("seal_version"),
            rs.getString("effective_level"),
            rs.getString("requester"),
            rs.getString("requester_dept"),
            rs.getString("reason"),
            rs.getString("first_approved_by"),
            timestamp(rs, "first_approved_at"),
            rs.getString("second_approved_by"),
            timestamp(rs, "second_approved_at"),
            rs.getString("decision_notes"),
            timestamp(rs, "expires_at"),
            rs.getObject("execution_token_id", UUID.class),
            rs.getString("executed_by"),
            timestamp(rs, "executed_at"),
            parseMap(rs.getString("evidence_json")),
            rs.getString("error_message"),
            rs.getLong("record_version"),
            timestamp(rs, "created_date")
        );
    }

    private Instant timestamp(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column) == null ? null : rs.getTimestamp(column).toInstant();
    }

    private Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception failure) {
            return Map.of("unparsedEvidence", json);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception failure) {
            throw new IllegalStateException("Unable to serialize lifecycle evidence", failure);
        }
    }

    private String checksum(String value) {
        String normalized = required(value, "payloadChecksum", 64).toLowerCase(Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("payloadChecksum must be SHA-256");
        }
        return normalized;
    }

    private String required(String value, String field, int max) {
        String normalized = trim(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        if (normalized.length() > max) {
            throw new IllegalArgumentException(field + " exceeds " + max + " characters");
        }
        return normalized;
    }

    private String trim(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private String truncate(String value, int max) {
        String normalized = trim(value);
        return normalized == null || normalized.length() <= max ? normalized : normalized.substring(0, max);
    }

    private String auditActor(String actor) {
        String normalized = trim(actor);
        if (normalized == null) {
            return "system";
        }
        return normalized.length() <= 50 ? normalized : normalized.substring(0, 50);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new IllegalStateException("Unable to calculate lifecycle checksum", failure);
        }
    }

    private record ActionRow(
        UUID id,
        UUID datasetId,
        String actionType,
        String stage,
        String status,
        String payloadChecksum,
        UUID sealId,
        long sealVersion,
        String effectiveLevel,
        String requester,
        String firstApprovedBy,
        String secondApprovedBy,
        Instant expiresAt,
        String evidenceJson,
        long recordVersion
    ) {}

    private record TokenRow(
        UUID id,
        UUID actionId,
        UUID datasetId,
        String actionType,
        String payloadChecksum,
        UUID sealId,
        long sealVersion,
        Instant expiresAt,
        Instant consumedAt,
        Instant revokedAt
    ) {}

    private record TrashRow(
        UUID datasetId,
        String status,
        boolean previousEnabled,
        String previousLifecycleStatus,
        Instant retainUntil,
        UUID sealId,
        long sealVersion,
        String effectiveLevel
    ) {}

    public record SubmitCommand(
        UUID datasetId,
        String actionType,
        String payloadChecksum,
        UUID sealId,
        long sealVersion,
        String requesterDept,
        String reason,
        Integer retentionDays
    ) {}

    public record ConsumeTokenCommand(
        String token,
        UUID datasetId,
        String actionType,
        String payloadChecksum
    ) {}

    public record ApprovalResult(ActionView action, String executionToken) {}

    public record TokenConsumption(UUID actionId, UUID datasetId, String actionType, Instant consumedAt) {}

    public record ActionView(
        UUID id,
        UUID datasetId,
        String actionType,
        String stage,
        String status,
        String payloadChecksum,
        UUID sealId,
        long sealVersion,
        String effectiveLevel,
        String requester,
        String requesterDept,
        String reason,
        String firstApprovedBy,
        Instant firstApprovedAt,
        String secondApprovedBy,
        Instant secondApprovedAt,
        String decisionNotes,
        Instant expiresAt,
        UUID executionTokenId,
        String executedBy,
        Instant executedAt,
        Map<String, Object> evidence,
        String errorMessage,
        long recordVersion,
        Instant createdAt
    ) {}
}
