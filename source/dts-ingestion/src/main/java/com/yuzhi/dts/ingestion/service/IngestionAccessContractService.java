package com.yuzhi.dts.ingestion.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.yuzhi.dts.ingestion.domain.IngestionAccessDefaultPolicy;
import com.yuzhi.dts.ingestion.domain.IngestionExecution;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.domain.IngestionTaskRevision;
import com.yuzhi.dts.ingestion.repository.IngestionAccessDefaultPolicyRepository;
import com.yuzhi.dts.ingestion.repository.IngestionTaskRepository;
import com.yuzhi.dts.ingestion.repository.IngestionTaskRevisionRepository;
import com.yuzhi.dts.ingestion.security.SecurityUtils;
import com.yuzhi.dts.ingestion.service.dto.IngestionAccessDefaultPolicyDTO;
import com.yuzhi.dts.ingestion.service.dto.IngestionEffectiveConfigDTO;
import com.yuzhi.dts.ingestion.service.dto.IngestionTaskDTO;
import com.yuzhi.dts.ingestion.service.dto.IngestionTaskRevisionDTO;
import com.yuzhi.dts.ingestion.service.infra.InfraSettingsCryptoService;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Additive control-plane seam for versioned access plans. Runtime engines continue
 * to consume {@link IngestionTask}; revisions only freeze the resolved contract
 * selected by admission and execution.
 */
@Service
@Transactional
public class IngestionAccessContractService {

    public static final String GLOBAL_POLICY_KEY = "GLOBAL";
    public static final String POLICY_ACTIVE = "ACTIVE";
    public static final String REVISION_DRAFT = "DRAFT";
    public static final String REVISION_ACTIVE = "ACTIVE";
    public static final String REVISION_SUPERSEDED = "SUPERSEDED";
    public static final String REVISION_LEGACY_UNSEALED = "LEGACY_UNSEALED";

    private static final Set<String> SECRET_KEYS = Set.of(
        "password",
        "passwd",
        "pwd",
        "secret",
        "token",
        "accesstoken",
        "refreshtoken",
        "apikey",
        "api-key",
        "authorization",
        "clientsecret",
        "privatekey"
    );
    private static final Pattern URL_USER_INFO = Pattern.compile("(?i)(//)[^/@\\s:]+:[^/@\\s]+@");
    private static final Pattern SENSITIVE_VALUE = Pattern.compile(
        "(?i)([?;&](?:password|passwd|pwd|secret|token|access_token|refresh_token|api[_-]?key)=)[^&#;\\s]+"
    );
    private static final Pattern AUTH_VALUE = Pattern.compile("(?i)^\\s*(?:bearer|basic)\\s+.+$");

    private final IngestionAccessDefaultPolicyRepository policyRepository;
    private final IngestionTaskRevisionRepository revisionRepository;
    private final IngestionTaskRepository taskRepository;
    private final ObjectMapper objectMapper;
    private final InfraSettingsCryptoService cryptoService;

    public IngestionAccessContractService(
        IngestionAccessDefaultPolicyRepository policyRepository,
        IngestionTaskRevisionRepository revisionRepository,
        IngestionTaskRepository taskRepository,
        ObjectMapper objectMapper,
        InfraSettingsCryptoService cryptoService
    ) {
        this.policyRepository = policyRepository;
        this.revisionRepository = revisionRepository;
        this.taskRepository = taskRepository;
        this.objectMapper = objectMapper;
        this.cryptoService = cryptoService;
    }

    @Transactional(readOnly = true)
    public IngestionAccessDefaultPolicyDTO getActiveDefaultPolicy() {
        return toPolicyDto(loadActivePolicy());
    }

    public IngestionTaskRevision recordDraftRevision(IngestionTask task, String qualityPolicyRef) {
        return recordDraftRevision(task, qualityPolicyRef, true);
    }

    public IngestionTaskRevision recordDraftRevision(
        IngestionTask task,
        String qualityPolicyRef,
        boolean inheritPreviousQualityPolicyRef
    ) {
        if (task == null || task.getId() == null) {
            throw new IllegalArgumentException("Persisted ingestion task is required before creating a revision");
        }
        List<IngestionTaskRevision> locked = revisionRepository.findAllByTaskIdForUpdate(task.getId());
        IngestionTaskRevision previous = locked.stream()
            .max(Comparator.comparing(IngestionTaskRevision::getRevisionNumber))
            .orElse(null);
        for (IngestionTaskRevision revision : locked) {
            if (REVISION_DRAFT.equals(revision.getState())) {
                revision.setState(REVISION_SUPERSEDED);
            }
        }
        if (!locked.isEmpty()) {
            revisionRepository.saveAll(locked);
        }

        IngestionAccessDefaultPolicy policy = loadActivePolicy();
        String sourceKind = resolveSourceKind(task.getSourceType(), task.getSourceConfig());
        JsonNode snapshot = buildTaskSnapshot(task, sourceKind);
        JsonNode effectiveConfig = buildEffectiveConfig(policy.getDefaults(), sourceKind, snapshot);

        IngestionTaskRevision revision = new IngestionTaskRevision();
        // A draft candidate materialized from an encrypted revision is detached by
        // design. Persist the relationship through a managed reference while using
        // the candidate itself only as the snapshot source.
        revision.setTask(taskRepository.getReferenceById(task.getId()));
        revision.setRevisionNumber(locked.stream().map(IngestionTaskRevision::getRevisionNumber).max(Integer::compareTo).orElse(0) + 1);
        revision.setState(REVISION_DRAFT);
        revision.setSourceKind(sourceKind);
        revision.setTaskSnapshot(snapshot);
        revision.setEffectiveConfig(effectiveConfig);
        revision.setEffectiveConfigChecksum(checksum(effectiveConfig));
        revision.setDefaultPolicyVersion(policy.getVersion());
        revision.setDefaultPolicyChecksum(policy.getChecksum());
        revision.setClassificationSeal(
            task.getClassificationSeal() == null || task.getClassificationSeal().isNull()
                ? null
                : sanitize(task.getClassificationSeal())
        );
        revision.setFieldClassifications(
            task.getFieldClassifications() == null || task.getFieldClassifications().isNull()
                ? null
                : sanitize(task.getFieldClassifications())
        );
        captureRuntimeSnapshot(revision, task);
        revision.setQualityPolicyRef(resolveQualityPolicyRef(qualityPolicyRef, previous, inheritPreviousQualityPolicyRef));
        revision.setCreatedBy(currentOperator(task));
        revision.setCreatedAt(Instant.now());
        return revisionRepository.save(revision);
    }

    public IngestionTaskRevision activateDraftRevision(Long taskId) {
        if (taskId == null) {
            throw new IllegalArgumentException("Task id is required");
        }
        List<IngestionTaskRevision> locked = revisionRepository.findAllByTaskIdForUpdate(taskId);
        IngestionTaskRevision draft = locked.stream()
            .filter(revision -> REVISION_DRAFT.equals(revision.getState()))
            .max(Comparator.comparing(IngestionTaskRevision::getRevisionNumber))
            .orElseThrow(() -> new IllegalStateException("Task has no draft revision to admit: " + taskId));
        List<IngestionTaskRevision> superseded = new ArrayList<>();
        for (IngestionTaskRevision revision : locked) {
            if (REVISION_ACTIVE.equals(revision.getState()) && !revision.getId().equals(draft.getId())) {
                revision.setState(REVISION_SUPERSEDED);
                superseded.add(revision);
            }
        }
        // Flush the old ACTIVE state first. Otherwise PostgreSQL may evaluate the
        // partial unique index before Hibernate updates the previous active row.
        if (!superseded.isEmpty()) {
            revisionRepository.saveAll(superseded);
            revisionRepository.flush();
        }
        draft.setState(REVISION_ACTIVE);
        draft.setActivatedBy(SecurityUtils.getCurrentUserLogin().orElse("system"));
        draft.setActivatedAt(Instant.now());
        return revisionRepository.save(draft);
    }

    public IngestionTaskRevision bindActiveRevision(IngestionExecution execution, IngestionTask task) {
        IngestionTaskRevision revision = requireActiveRevision(task);
        execution.setTaskRevisionId(revision.getId());
        execution.setRevisionNumber(revision.getRevisionNumber());
        execution.setEffectiveConfigChecksum(revision.getEffectiveConfigChecksum());
        execution.setQualityPolicyRef(revision.getQualityPolicyRef());
        return revision;
    }

    @Transactional(readOnly = true)
    public java.util.Optional<IngestionTaskRevision> findLatestDraftRevision(Long taskId) {
        if (taskId == null) {
            return java.util.Optional.empty();
        }
        return revisionRepository.findFirstByTaskIdAndStateOrderByRevisionNumberDesc(taskId, REVISION_DRAFT);
    }

    @Transactional(readOnly = true)
    public IngestionTask materializeLatestDraft(IngestionTask activeTask) {
        if (activeTask == null || activeTask.getId() == null) {
            throw new IllegalArgumentException("Persisted ingestion task is required");
        }
        IngestionTaskRevision draft = findLatestDraftRevision(activeTask.getId())
            .orElseThrow(() -> new IllegalStateException("Task has no draft revision: " + activeTask.getId()));
        return materializeRuntimeTask(activeTask, draft);
    }

    /**
     * Rehydrates the exact plan bound to an execution. The compatibility task row
     * is used only for identity/audit fields; executable configuration comes from
     * the immutable revision selected when the execution was created.
     */
    @Transactional(readOnly = true)
    public IngestionTask materializeExecutionTask(IngestionTask persistedTask, Long revisionId) {
        if (persistedTask == null || persistedTask.getId() == null) {
            throw new IllegalArgumentException("Persisted ingestion task is required");
        }
        if (revisionId == null) {
            return copyTask(persistedTask);
        }
        IngestionTaskRevision revision = revisionRepository.findById(revisionId)
            .orElseThrow(() -> new IllegalStateException("Execution revision not found: " + revisionId));
        if (revision.getTask() == null || !persistedTask.getId().equals(revision.getTask().getId())) {
            throw new IllegalStateException("Execution revision does not belong to task: " + persistedTask.getId());
        }
        if (REVISION_LEGACY_UNSEALED.equals(revision.getState())
            || revision.getRuntimeSnapshot() == null
            || revision.getRuntimeSnapshotIv() == null) {
            throw new IllegalStateException("Execution revision is not formally sealed: " + revisionId);
        }
        return materializeRuntimeTask(persistedTask, revision);
    }

    /** Stores admission-generated, revision-scoped runtime artifact references before activation. */
    public IngestionTaskRevision refreshDraftRuntimeSnapshot(Long taskId, IngestionTask admittedPlan) {
        if (taskId == null || admittedPlan == null || !taskId.equals(admittedPlan.getId())) {
            throw new IllegalArgumentException("Draft task and admitted plan identity must match");
        }
        IngestionTaskRevision draft = revisionRepository.findAllByTaskIdForUpdate(taskId).stream()
            .filter(revision -> REVISION_DRAFT.equals(revision.getState()))
            .max(Comparator.comparing(IngestionTaskRevision::getRevisionNumber))
            .orElseThrow(() -> new IllegalStateException("Task has no draft revision: " + taskId));
        captureRuntimeSnapshot(draft, admittedPlan);
        return revisionRepository.save(draft);
    }

    @Transactional(readOnly = true)
    public List<IngestionTaskRevisionDTO> getTaskRevisions(Long taskId) {
        requireTask(taskId);
        return revisionRepository.findAllByTaskIdOrderByRevisionNumberDesc(taskId).stream().map(this::toRevisionDto).toList();
    }

    @Transactional(readOnly = true)
    public IngestionEffectiveConfigDTO getEffectiveConfig(Long taskId) {
        IngestionTask task = requireTask(taskId);
        IngestionTaskRevision revision = findDetailRevisionOptional(taskId, task.getStatus())
            .orElseThrow(() -> new IllegalStateException("Task has no access revision: " + taskId));
        return toEffectiveConfigDto(taskId, revision);
    }

    @Transactional(readOnly = true)
    public java.util.Optional<UUID> findQualityDatasetId(Long taskId, String taskStatus) {
        return findDetailRevisionOptional(taskId, taskStatus)
            .map(IngestionTaskRevision::getQualityPolicyRef)
            .filter(StringUtils::hasText)
            .map(IngestionAccessContractService::parseQualityDatasetRef);
    }

    public static UUID parseQualityDatasetRef(String qualityPolicyRef) {
        if (!StringUtils.hasText(qualityPolicyRef) || !qualityPolicyRef.trim().startsWith("dataset:")) {
            throw new IllegalArgumentException("qualityPolicyRef must use dataset:<uuid> and reference official quality bindings");
        }
        String value = qualityPolicyRef.trim().substring("dataset:".length()).trim();
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("qualityPolicyRef contains an invalid dataset UUID", ex);
        }
    }

    @Transactional(readOnly = true)
    public IngestionTaskDTO enrichTaskDto(IngestionTaskDTO dto) {
        if (dto == null || dto.getId() == null) {
            return dto;
        }
        findDetailRevisionOptional(dto.getId(), dto.getStatus()).ifPresent(revision -> {
            applyTaskSnapshot(dto, revision);
            applyRevision(dto, revision);
        });
        return dto;
    }

    @Transactional(readOnly = true)
    public List<IngestionTaskDTO> enrichTaskDtos(List<IngestionTaskDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return dtos;
        }
        List<Long> taskIds = dtos.stream().map(IngestionTaskDTO::getId).filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, List<IngestionTaskRevision>> byTask = new HashMap<>();
        revisionRepository.findAllByTaskIdInOrderByTaskIdAscRevisionNumberDesc(taskIds)
            .forEach(revision -> byTask.computeIfAbsent(revision.getTask().getId(), ignored -> new ArrayList<>()).add(revision));
        for (IngestionTaskDTO dto : dtos) {
            List<IngestionTaskRevision> revisions = byTask.get(dto.getId());
            if (revisions == null || revisions.isEmpty()) {
                continue;
            }
            selectPreferred(revisions, dto.getStatus()).ifPresent(revision -> {
                // An ACTIVE row remains the canonical list item while it has a draft
                // edit. A genuinely draft task, however, must display its latest
                // snapshot instead of the stale compatibility row.
                if (!"active".equalsIgnoreCase(dto.getStatus())) {
                    applyTaskSnapshot(dto, revision);
                }
                applyRevision(dto, revision);
            });
        }
        return dtos;
    }

    public static String resolveSourceKind(String sourceType, JsonNode sourceConfig) {
        String normalized = sourceType == null ? "" : sourceType.trim().toLowerCase(Locale.ROOT);
        String readerType = sourceConfig == null ? "" : sourceConfig.path("readerType").asText("").trim().toLowerCase(Locale.ROOT);
        String value = normalized + " " + readerType;
        if (value.contains("http") || value.contains("api") || value.contains("rest")) {
            return "api";
        }
        if (
            value.contains("file") ||
            value.contains("excel") ||
            value.contains("csv") ||
            value.contains("txt") ||
            value.contains("jsonreader")
        ) {
            return "file";
        }
        return "database";
    }

    private IngestionTaskRevision requireActiveRevision(IngestionTask task) {
        if (task == null || task.getId() == null) {
            throw new IllegalArgumentException("Persisted ingestion task is required");
        }
        return revisionRepository.findFirstByTaskIdAndStateOrderByRevisionNumberDesc(task.getId(), REVISION_ACTIVE)
            .orElseGet(() -> {
                if (!"active".equalsIgnoreCase(task.getStatus())) {
                    throw new IllegalStateException("Task has no active revision: " + task.getId());
                }
                recordDraftRevision(task, null);
                return activateDraftRevision(task.getId());
            });
    }

    private IngestionTask requireTask(Long taskId) {
        return taskRepository.findById(taskId).orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
    }

    private java.util.Optional<IngestionTaskRevision> findDetailRevisionOptional(Long taskId, String taskStatus) {
        java.util.Optional<IngestionTaskRevision> draft = revisionRepository.findFirstByTaskIdAndStateOrderByRevisionNumberDesc(
            taskId,
            REVISION_DRAFT
        );
        return draft.isPresent() ? draft : findPreferredRevisionOptional(taskId, taskStatus);
    }

    private java.util.Optional<IngestionTaskRevision> findPreferredRevisionOptional(Long taskId, String taskStatus) {
        if ("active".equalsIgnoreCase(taskStatus)) {
            java.util.Optional<IngestionTaskRevision> active = revisionRepository.findFirstByTaskIdAndStateOrderByRevisionNumberDesc(
                taskId,
                REVISION_ACTIVE
            );
            if (active.isPresent()) {
                return active;
            }
        }
        java.util.Optional<IngestionTaskRevision> draft = revisionRepository.findFirstByTaskIdAndStateOrderByRevisionNumberDesc(
            taskId,
            REVISION_DRAFT
        );
        return draft.isPresent() ? draft : revisionRepository.findFirstByTaskIdOrderByRevisionNumberDesc(taskId);
    }

    private java.util.Optional<IngestionTaskRevision> selectPreferred(List<IngestionTaskRevision> revisions, String taskStatus) {
        if ("active".equalsIgnoreCase(taskStatus)) {
            java.util.Optional<IngestionTaskRevision> active = revisions.stream()
                .filter(revision -> REVISION_ACTIVE.equals(revision.getState()))
                .max(Comparator.comparing(IngestionTaskRevision::getRevisionNumber));
            if (active.isPresent()) {
                return active;
            }
        }
        java.util.Optional<IngestionTaskRevision> draft = revisions.stream()
            .filter(revision -> REVISION_DRAFT.equals(revision.getState()))
            .max(Comparator.comparing(IngestionTaskRevision::getRevisionNumber));
        return draft.isPresent() ? draft : revisions.stream().max(Comparator.comparing(IngestionTaskRevision::getRevisionNumber));
    }

    private IngestionAccessDefaultPolicy loadActivePolicy() {
        return policyRepository.findFirstByPolicyKeyAndStatusOrderByVersionDesc(GLOBAL_POLICY_KEY, POLICY_ACTIVE)
            .orElseThrow(() -> new IllegalStateException("Active GLOBAL ingestion access default policy is not configured"));
    }

    private JsonNode buildTaskSnapshot(IngestionTask task, String sourceKind) {
        ObjectNode snapshot = objectMapper.createObjectNode();
        put(snapshot, "name", task.getName());
        put(snapshot, "description", task.getDescription());
        put(snapshot, "sourceKind", sourceKind);
        put(snapshot, "sourceType", task.getSourceType());
        put(snapshot, "sourceDataSourceId", task.getSourceDataSourceId() == null ? null : task.getSourceDataSourceId().toString());
        set(snapshot, "sourceConfig", task.getSourceConfig());
        put(snapshot, "destinationType", task.getDestinationType());
        set(snapshot, "destinationConfig", task.getDestinationConfig());
        put(snapshot, "syncMode", task.getSyncMode());
        put(snapshot, "syncSchedule", task.getSyncSchedule());
        set(snapshot, "tableMapping", task.getTableMapping());
        set(snapshot, "syncConfig", task.getSyncConfig());
        set(snapshot, "graphDsl", task.getGraphDsl());
        set(snapshot, "classificationSeal", task.getClassificationSeal());
        set(snapshot, "fieldClassifications", task.getFieldClassifications());
        set(snapshot, "addaxConfig", task.getAddaxConfig());
        snapshot.put("airflowEnabled", Boolean.TRUE.equals(task.getAirflowEnabled()));
        put(snapshot, "dbtModelSelector", task.getDbtModelSelector());
        put(snapshot, "dbtDagSelector", task.getDbtDagSelector());
        snapshot.put("qualityPreCheckEnabled", Boolean.TRUE.equals(task.getQualityPreCheckEnabled()));
        put(snapshot, "stagingTableName", task.getStagingTableName());
        put(snapshot, "preCheckStatus", task.getPreCheckStatus());
        return sanitize(snapshot);
    }

    private void captureRuntimeSnapshot(IngestionTaskRevision revision, IngestionTask task) {
        try {
            byte[] plain = objectMapper.writeValueAsBytes(buildRuntimeTaskSnapshot(task));
            byte[] iv = cryptoService.randomIv();
            revision.setRuntimeSnapshot(cryptoService.encryptStrict(plain, iv));
            revision.setRuntimeSnapshotIv(iv);
            revision.setRuntimeSnapshotKeyVersion(cryptoService.currentKeyVersion());
        } catch (JsonProcessingException | GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to encrypt ingestion task revision runtime snapshot", ex);
        }
    }

    private ObjectNode buildRuntimeTaskSnapshot(IngestionTask task) {
        ObjectNode snapshot = objectMapper.createObjectNode();
        put(snapshot, "name", task.getName());
        put(snapshot, "description", task.getDescription());
        put(snapshot, "sourceType", task.getSourceType());
        put(snapshot, "sourceDataSourceId", task.getSourceDataSourceId() == null ? null : task.getSourceDataSourceId().toString());
        setRaw(snapshot, "sourceConfig", task.getSourceConfig());
        put(snapshot, "destinationType", task.getDestinationType());
        setRaw(snapshot, "destinationConfig", task.getDestinationConfig());
        put(snapshot, "syncMode", task.getSyncMode());
        put(snapshot, "syncSchedule", task.getSyncSchedule());
        setRaw(snapshot, "tableMapping", task.getTableMapping());
        setRaw(snapshot, "syncConfig", task.getSyncConfig());
        setRaw(snapshot, "graphDsl", task.getGraphDsl());
        setRaw(snapshot, "classificationSeal", task.getClassificationSeal());
        setRaw(snapshot, "fieldClassifications", task.getFieldClassifications());
        setRaw(snapshot, "addaxConfig", task.getAddaxConfig());
        put(snapshot, "addaxJobPath", task.getAddaxJobPath());
        if (task.getAirflowEnabled() != null) {
            snapshot.put("airflowEnabled", task.getAirflowEnabled());
        }
        put(snapshot, "dbtModelSelector", task.getDbtModelSelector());
        put(snapshot, "dbtDagSelector", task.getDbtDagSelector());
        put(snapshot, "status", task.getStatus());
        put(snapshot, "airflowDagId", task.getAirflowDagId());
        if (task.getQualityPreCheckEnabled() != null) {
            snapshot.put("qualityPreCheckEnabled", task.getQualityPreCheckEnabled());
        }
        put(snapshot, "stagingTableName", task.getStagingTableName());
        put(snapshot, "preCheckStatus", task.getPreCheckStatus());
        return snapshot;
    }

    private IngestionTask materializeRuntimeTask(IngestionTask persistedTask, IngestionTaskRevision revision) {
        if (revision.getRuntimeSnapshot() == null || revision.getRuntimeSnapshotIv() == null) {
            // Liquibase cannot encrypt historical rows because the application key is not
            // available inside SQL. A migrated draft still matches the persisted task row.
            return copyTask(persistedTask);
        }
        try {
            byte[] plain = cryptoService.decryptStrict(revision.getRuntimeSnapshot(), revision.getRuntimeSnapshotIv());
            JsonNode snapshot = objectMapper.readTree(plain);
            if (snapshot == null || !snapshot.isObject()) {
                throw new IllegalStateException("Ingestion task revision runtime snapshot is invalid");
            }
            return taskFromSnapshot(persistedTask, snapshot, revision);
        } catch (IOException | GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to decrypt ingestion task revision runtime snapshot", ex);
        }
    }

    private IngestionTask taskFromSnapshot(IngestionTask persistedTask, JsonNode snapshot, IngestionTaskRevision revision) {
        IngestionTask task = new IngestionTask();
        task.setId(persistedTask.getId());
        task.setName(text(snapshot, "name", persistedTask.getName()));
        task.setDescription(text(snapshot, "description", persistedTask.getDescription()));
        task.setSourceType(text(snapshot, "sourceType", persistedTask.getSourceType()));
        task.setSourceDataSourceId(uuid(snapshot, "sourceDataSourceId", persistedTask.getSourceDataSourceId()));
        task.setSourceConfig(json(snapshot, "sourceConfig", persistedTask.getSourceConfig()));
        task.setDestinationType(text(snapshot, "destinationType", persistedTask.getDestinationType()));
        task.setDestinationConfig(json(snapshot, "destinationConfig", persistedTask.getDestinationConfig()));
        task.setSyncMode(text(snapshot, "syncMode", persistedTask.getSyncMode()));
        task.setSyncSchedule(text(snapshot, "syncSchedule", persistedTask.getSyncSchedule()));
        task.setTableMapping(json(snapshot, "tableMapping", persistedTask.getTableMapping()));
        task.setSyncConfig(json(snapshot, "syncConfig", persistedTask.getSyncConfig()));
        task.setGraphDsl(json(snapshot, "graphDsl", persistedTask.getGraphDsl()));
        task.setClassificationSeal(json(snapshot, "classificationSeal", revision.getClassificationSeal()));
        task.setFieldClassifications(json(snapshot, "fieldClassifications", revision.getFieldClassifications()));
        task.setAddaxConfig(json(snapshot, "addaxConfig", persistedTask.getAddaxConfig()));
        task.setAddaxJobPath(text(snapshot, "addaxJobPath", persistedTask.getAddaxJobPath()));
        task.setAirflowEnabled(bool(snapshot, "airflowEnabled", persistedTask.getAirflowEnabled()));
        task.setAirflowDagId(text(snapshot, "airflowDagId", persistedTask.getAirflowDagId()));
        task.setDbtModelSelector(text(snapshot, "dbtModelSelector", persistedTask.getDbtModelSelector()));
        task.setDbtDagSelector(text(snapshot, "dbtDagSelector", persistedTask.getDbtDagSelector()));
        task.setStatus(REVISION_DRAFT.equals(revision.getState()) ? "draft" : "active");
        task.setQualityPreCheckEnabled(bool(snapshot, "qualityPreCheckEnabled", persistedTask.getQualityPreCheckEnabled()));
        task.setStagingTableName(text(snapshot, "stagingTableName", persistedTask.getStagingTableName()));
        task.setPreCheckStatus(text(snapshot, "preCheckStatus", persistedTask.getPreCheckStatus()));
        return task;
    }

    private IngestionTask copyTask(IngestionTask source) {
        return taskFromSnapshot(source, buildRuntimeTaskSnapshot(source), newLegacyRevision(source));
    }

    private IngestionTaskRevision newLegacyRevision(IngestionTask task) {
        IngestionTaskRevision revision = new IngestionTaskRevision();
        revision.setState("draft".equalsIgnoreCase(task.getStatus()) ? REVISION_DRAFT : REVISION_ACTIVE);
        revision.setClassificationSeal(task.getClassificationSeal());
        revision.setFieldClassifications(task.getFieldClassifications());
        return revision;
    }

    private JsonNode buildEffectiveConfig(JsonNode policyDefaults, String sourceKind, JsonNode snapshot) {
        ObjectNode selectedDefaults = objectMapper.createObjectNode();
        if (policyDefaults != null && policyDefaults.isObject()) {
            merge(selectedDefaults, policyDefaults.path("common"));
            merge(selectedDefaults, policyDefaults.path(sourceKind));
        }
        ObjectNode effective = objectMapper.createObjectNode();
        effective.set("defaults", sanitize(selectedDefaults));
        effective.set("task", sanitize(snapshot));
        return canonicalize(effective);
    }

    private void merge(ObjectNode target, JsonNode overlay) {
        if (overlay == null || !overlay.isObject()) {
            return;
        }
        overlay.fields().forEachRemaining(entry -> {
            JsonNode current = target.get(entry.getKey());
            if (current != null && current.isObject() && entry.getValue().isObject()) {
                merge((ObjectNode) current, entry.getValue());
            } else {
                target.set(entry.getKey(), entry.getValue().deepCopy());
            }
        });
    }

    private JsonNode sanitize(JsonNode node) {
        if (node == null || node.isNull()) {
            return objectMapper.nullNode();
        }
        if (node.isObject()) {
            ObjectNode sanitized = objectMapper.createObjectNode();
            node.fields().forEachRemaining(entry -> {
                String normalizedKey = entry.getKey().replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
                if (!isSecretKey(normalizedKey)) {
                    sanitized.set(entry.getKey(), sanitize(entry.getValue()));
                }
            });
            return sanitized;
        }
        if (node.isArray()) {
            ArrayNode sanitized = objectMapper.createArrayNode();
            node.forEach(item -> sanitized.add(sanitize(item)));
            return sanitized;
        }
        if (node.isTextual()) {
            if (AUTH_VALUE.matcher(node.asText()).matches()) {
                return TextNode.valueOf("[redacted]");
            }
            String value = URL_USER_INFO.matcher(node.asText()).replaceAll("$1[redacted]@");
            value = SENSITIVE_VALUE.matcher(value).replaceAll("$1[redacted]");
            return TextNode.valueOf(value);
        }
        return node.deepCopy();
    }

    private boolean isSecretKey(String normalizedKey) {
        return SECRET_KEYS.contains(normalizedKey)
            || normalizedKey.contains("password")
            || normalizedKey.contains("secret")
            || normalizedKey.contains("token")
            || normalizedKey.contains("apikey")
            || normalizedKey.contains("authorization")
            || normalizedKey.contains("privatekey");
    }

    private JsonNode canonicalize(JsonNode node) {
        if (node == null || node.isNull()) {
            return objectMapper.nullNode();
        }
        if (node.isObject()) {
            ObjectNode sorted = objectMapper.createObjectNode();
            Map<String, JsonNode> fields = new java.util.TreeMap<>();
            node.fields().forEachRemaining(entry -> fields.put(entry.getKey(), entry.getValue()));
            fields.forEach((key, value) -> sorted.set(key, canonicalize(value)));
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode array = objectMapper.createArrayNode();
            node.forEach(item -> array.add(canonicalize(item)));
            return array;
        }
        return node.deepCopy();
    }

    private String checksum(JsonNode value) {
        try {
            byte[] serialized = objectMapper.writeValueAsBytes(canonicalize(value));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(serialized));
        } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Failed to calculate ingestion access config checksum", ex);
        }
    }

    private String resolveQualityPolicyRef(
        String requested,
        IngestionTaskRevision previous,
        boolean inheritPreviousQualityPolicyRef
    ) {
        if (StringUtils.hasText(requested)) {
            String trimmed = requested.trim();
            if (trimmed.length() > 200) {
                throw new IllegalArgumentException("qualityPolicyRef exceeds 200 characters");
            }
            parseQualityDatasetRef(trimmed);
            return trimmed;
        }
        return previous == null || !inheritPreviousQualityPolicyRef ? null : previous.getQualityPolicyRef();
    }

    private String currentOperator(IngestionTask task) {
        return SecurityUtils.getCurrentUserLogin()
            .filter(StringUtils::hasText)
            .orElseGet(() -> StringUtils.hasText(task.getLastModifiedBy()) ? task.getLastModifiedBy() : "system");
    }

    private IngestionAccessDefaultPolicyDTO toPolicyDto(IngestionAccessDefaultPolicy policy) {
        return new IngestionAccessDefaultPolicyDTO(
            policy.getPolicyKey(),
            policy.getVersion(),
            policy.getStatus(),
            sanitize(policy.getDefaults()),
            policy.getChecksum(),
            policy.getActivatedAt()
        );
    }

    private IngestionTaskRevisionDTO toRevisionDto(IngestionTaskRevision revision) {
        return new IngestionTaskRevisionDTO(
            revision.getRevisionNumber(),
            revision.getState(),
            revision.getSourceKind(),
            revision.getEffectiveConfigChecksum(),
            revision.getDefaultPolicyVersion(),
            revision.getDefaultPolicyChecksum(),
            revision.getQualityPolicyRef(),
            revision.getCreatedBy(),
            revision.getCreatedAt(),
            revision.getActivatedBy(),
            revision.getActivatedAt()
        );
    }

    private IngestionEffectiveConfigDTO toEffectiveConfigDto(Long taskId, IngestionTaskRevision revision) {
        return new IngestionEffectiveConfigDTO(
            taskId,
            revision.getRevisionNumber(),
            revision.getState(),
            revision.getSourceKind(),
            sanitize(revision.getEffectiveConfig()),
            revision.getEffectiveConfigChecksum(),
            revision.getDefaultPolicyVersion(),
            revision.getDefaultPolicyChecksum(),
            revision.getQualityPolicyRef()
        );
    }

    private void applyRevision(IngestionTaskDTO dto, IngestionTaskRevision revision) {
        dto.setRevisionNumber(revision.getRevisionNumber());
        dto.setRevisionState(revision.getState());
        dto.setEffectiveConfig(sanitize(revision.getEffectiveConfig()));
        dto.setEffectiveConfigChecksum(revision.getEffectiveConfigChecksum());
        dto.setDefaultPolicyVersion(revision.getDefaultPolicyVersion());
        dto.setDefaultPolicyChecksum(revision.getDefaultPolicyChecksum());
        dto.setQualityPolicyRef(revision.getQualityPolicyRef());
    }

    private void applyTaskSnapshot(IngestionTaskDTO dto, IngestionTaskRevision revision) {
        JsonNode snapshot = revision.getTaskSnapshot();
        if (snapshot == null || !snapshot.isObject()) {
            return;
        }
        dto.setName(text(snapshot, "name", dto.getName()));
        dto.setDescription(text(snapshot, "description", dto.getDescription()));
        dto.setSourceType(text(snapshot, "sourceType", dto.getSourceType()));
        dto.setSourceDataSourceId(uuid(snapshot, "sourceDataSourceId", dto.getSourceDataSourceId()));
        dto.setSourceConfig(json(snapshot, "sourceConfig", dto.getSourceConfig()));
        dto.setDestinationType(text(snapshot, "destinationType", dto.getDestinationType()));
        dto.setDestinationConfig(json(snapshot, "destinationConfig", dto.getDestinationConfig()));
        dto.setSyncMode(text(snapshot, "syncMode", dto.getSyncMode()));
        dto.setSyncSchedule(text(snapshot, "syncSchedule", dto.getSyncSchedule()));
        dto.setTableMapping(json(snapshot, "tableMapping", dto.getTableMapping()));
        dto.setSyncConfig(json(snapshot, "syncConfig", dto.getSyncConfig()));
        dto.setGraphDsl(json(snapshot, "graphDsl", dto.getGraphDsl()));
        dto.setAddaxConfig(json(snapshot, "addaxConfig", dto.getAddaxConfig()));
        dto.setAirflowEnabled(bool(snapshot, "airflowEnabled", dto.getAirflowEnabled()));
        dto.setDbtModelSelector(text(snapshot, "dbtModelSelector", dto.getDbtModelSelector()));
        dto.setDbtDagSelector(text(snapshot, "dbtDagSelector", dto.getDbtDagSelector()));
        dto.setQualityPreCheckEnabled(bool(snapshot, "qualityPreCheckEnabled", dto.getQualityPreCheckEnabled()));
        dto.setStagingTableName(text(snapshot, "stagingTableName", dto.getStagingTableName()));
        dto.setPreCheckStatus(text(snapshot, "preCheckStatus", dto.getPreCheckStatus()));
        dto.setClassificationSeal(copy(revision.getClassificationSeal()));
        dto.setFieldClassifications(copy(revision.getFieldClassifications()));
        dto.setStatus(REVISION_DRAFT.equals(revision.getState()) ? "draft" : dto.getStatus());
    }

    private void put(ObjectNode target, String key, String value) {
        if (StringUtils.hasText(value)) {
            target.put(key, value);
        }
    }

    private void set(ObjectNode target, String key, JsonNode value) {
        if (value != null && !value.isNull()) {
            target.set(key, sanitize(value));
        }
    }

    private void setRaw(ObjectNode target, String key, JsonNode value) {
        if (value != null && !value.isNull()) {
            target.set(key, value.deepCopy());
        }
    }

    private String text(JsonNode snapshot, String key, String fallback) {
        JsonNode value = snapshot == null ? null : snapshot.get(key);
        return value == null || value.isNull() ? fallback : value.asText();
    }

    private Boolean bool(JsonNode snapshot, String key, Boolean fallback) {
        JsonNode value = snapshot == null ? null : snapshot.get(key);
        return value == null || value.isNull() ? fallback : value.asBoolean();
    }

    private UUID uuid(JsonNode snapshot, String key, UUID fallback) {
        String value = text(snapshot, key, null);
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Ingestion task revision contains an invalid " + key, ex);
        }
    }

    private JsonNode json(JsonNode snapshot, String key, JsonNode fallback) {
        JsonNode value = snapshot == null ? null : snapshot.get(key);
        return value == null || value.isNull() ? copy(fallback) : value.deepCopy();
    }

    private JsonNode copy(JsonNode value) {
        return value == null || value.isNull() ? null : value.deepCopy();
    }
}
