package com.yuzhi.dts.platform.service.catalog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogTag;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTagRepository;
import com.yuzhi.dts.platform.service.catalog.CatalogTagMigrationStore.BatchRecord;
import com.yuzhi.dts.platform.service.catalog.CatalogTagMigrationStore.RelationEvidence;
import com.yuzhi.dts.platform.service.catalog.CatalogTagMigrationStore.RelationKey;
import com.yuzhi.dts.platform.service.catalog.dto.CatalogTagMigrationExecution;
import com.yuzhi.dts.platform.service.catalog.dto.CatalogTagMigrationRelation;
import com.yuzhi.dts.platform.service.catalog.dto.CatalogTagMigrationReport;
import com.yuzhi.dts.platform.service.catalog.dto.CatalogTagMigrationRollback;
import com.yuzhi.dts.platform.service.catalog.dto.CatalogTagMigrationTokenIssue;
import com.yuzhi.dts.platform.service.catalog.dto.CatalogTagProtectedEvidence;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CatalogTagMigrationService {

    private static final String ASSET_TYPE = CatalogAssetType.DATASET.name();
    private static final String BATCH_PREFIX = "catalog-tags-";
    private static final Pattern BATCH_PATTERN = Pattern.compile("catalog-tags-[0-9a-f]{24}");
    private static final Pattern CHECKSUM_PATTERN = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern TOKEN_SPLIT_PATTERN = Pattern.compile("[,，;；\\s]+");
    static final int MAX_PLAN_DATASETS = 50_000;
    static final int MAX_PLAN_TAGS = 10_000;
    static final int MAX_PLAN_DECISIONS = 50_000;
    private static final int PLAN_PAGE_SIZE = 500;
    private static final int EXISTING_RELATION_LOOKUP_BATCH_SIZE = 500;
    private static final Set<String> KNOWN_API_EVIDENCE_KEYS = Set.of(
        "origin",
        "connectionId",
        "taskId",
        "taskName",
        "resourceId",
        "qualifiedName",
        "taskRevision",
        "executionSequence",
        "configChecksum",
        "fieldSnapshotChecksum",
        "executionId",
        "batchId",
        "executionStatus",
        "landingStatus",
        "landingTruth",
        "rowsWritten",
        "observedAt",
        "checkpoint"
    );

    private final CatalogDatasetRepository datasetRepository;
    private final CatalogTagRepository tagRepository;
    private final CatalogTagMigrationStore store;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public CatalogTagMigrationService(
        CatalogDatasetRepository datasetRepository,
        CatalogTagRepository tagRepository,
        CatalogTagMigrationStore store,
        ObjectMapper objectMapper
    ) {
        this(datasetRepository, tagRepository, store, objectMapper, Clock.systemUTC());
    }

    CatalogTagMigrationService(
        CatalogDatasetRepository datasetRepository,
        CatalogTagRepository tagRepository,
        CatalogTagMigrationStore store,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        this.datasetRepository = Objects.requireNonNull(datasetRepository, "datasetRepository");
        this.tagRepository = Objects.requireNonNull(tagRepository, "tagRepository");
        this.store = Objects.requireNonNull(store, "store");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional(readOnly = true)
    public CatalogTagMigrationReport dryRun() {
        return plan();
    }

    @Transactional
    public CatalogTagMigrationExecution execute(
        String batchId,
        String expectedChecksum,
        String actor
    ) {
        String requestedBatch = requireBatchId(batchId);
        String checksum = requireChecksum(expectedChecksum);
        String currentActor = requireActor(actor);
        requireBatchMatchesChecksum(requestedBatch, checksum);
        store.acquireMigrationLock();

        Optional<BatchRecord> existing = store.lockBatch(requestedBatch);
        if (existing.isPresent()) {
            BatchRecord batch = existing.orElseThrow();
            requireLedgerChecksum(batch, checksum);
            if ("EXECUTED".equals(batch.status())) {
                return readExecutionEvidence(batch).asResponse(true);
            }
            if ("ROLLED_BACK".equals(batch.status())) {
                throw conflict("MIGRATION_BATCH_ROLLED_BACK: rolled-back batches are terminal");
            }
            throw conflict("MIGRATION_BATCH_STATUS_INVALID: batch is not replayable");
        }

        store.lockMigrationInputs();
        CatalogTagMigrationReport report = plan();
        if (!requestedBatch.equals(report.batchId()) || !checksum.equals(report.checksum())) {
            throw conflict("MIGRATION_PLAN_DRIFT: source text, tag definitions, or relation decisions changed");
        }

        Instant now = clock.instant();
        BatchRecord batch = new BatchRecord(
            requestedBatch,
            checksum,
            "EXECUTING",
            json(report),
            null,
            null,
            currentActor,
            now
        );
        if (store.insertBatch(batch) != 1) {
            throw conflict("MIGRATION_BATCH_CONFLICT: batch ledger could not be created");
        }

        List<RelationEvidence> createdRelations = new ArrayList<>();
        long skipped = 0;
        for (CatalogTagMigrationRelation relation : report.relations()) {
            RelationKey key = new RelationKey(
                relation.tagId(),
                relation.assetType(),
                relation.assetKey()
            );
            Optional<RelationEvidence> inserted = store.insertRelation(
                key,
                requestedBatch,
                currentActor,
                now
            );
            if (inserted.isPresent()) {
                createdRelations.add(inserted.orElseThrow());
            } else {
                skipped++;
            }
        }

        ExecutionEvidence evidence = new ExecutionEvidence(
            requestedBatch,
            checksum,
            report.matchedTokenCount(),
            report.unmatchedTokens().size(),
            report.ambiguousTokens().size(),
            report.protectedEvidence().size(),
            createdRelations.size(),
            skipped,
            List.copyOf(createdRelations)
        );
        if (store.markExecuted(requestedBatch, json(evidence), currentActor, now) != 1) {
            throw conflict("MIGRATION_BATCH_STATUS_DRIFT: execution ledger was not in EXECUTING state");
        }
        return evidence.asResponse(false);
    }

    @Transactional
    public CatalogTagMigrationRollback rollback(
        String batchId,
        String expectedChecksum,
        String actor
    ) {
        String requestedBatch = requireBatchId(batchId);
        String checksum = requireChecksum(expectedChecksum);
        String currentActor = requireActor(actor);
        requireBatchMatchesChecksum(requestedBatch, checksum);
        store.acquireMigrationLock();

        BatchRecord batch = store
            .lockBatch(requestedBatch)
            .orElseThrow(() ->
                conflict("MIGRATION_BATCH_NOT_EXECUTED: execute the accepted dry-run before rollback")
            );
        requireLedgerChecksum(batch, checksum);
        if ("ROLLED_BACK".equals(batch.status())) {
            return readRollbackEvidence(batch, true);
        }
        if (!"EXECUTED".equals(batch.status())) {
            throw conflict("MIGRATION_BATCH_STATUS_INVALID: only EXECUTED batches can be rolled back");
        }

        ExecutionEvidence execution = readExecutionEvidence(batch);
        List<RelationEvidence> currentRelations = store.findRelationsByBatch(requestedBatch);
        if (!sameEvidence(execution.createdRelations(), currentRelations)) {
            throw conflict("ROLLBACK_EVIDENCE_DRIFT: batch relation evidence no longer matches execution");
        }

        int deleted = store.deleteRelationsByBatch(requestedBatch);
        if (deleted != currentRelations.size()) {
            throw conflict("ROLLBACK_EVIDENCE_DRIFT: relation count changed while rolling back");
        }
        CatalogTagMigrationRollback rollback = new CatalogTagMigrationRollback(
            requestedBatch,
            checksum,
            "ROLLED_BACK",
            false,
            execution.matched(),
            execution.unmatched(),
            execution.ambiguous(),
            execution.protectedEvidence(),
            execution.created(),
            execution.skipped(),
            deleted
        );
        if (
            store.markRolledBack(
                requestedBatch,
                json(rollback),
                currentActor,
                clock.instant()
            ) !=
            1
        ) {
            throw conflict("MIGRATION_BATCH_STATUS_DRIFT: execution ledger was not in EXECUTED state");
        }
        return rollback;
    }

    private CatalogTagMigrationReport plan() {
        TagNameIndex tagIndex = loadTagNameIndex();
        List<PlanDecision> decisions = new ArrayList<>();
        MessageDigest digest = sha256();
        updateDigest(digest, "catalog-tag-migration-plan-v1");
        long datasetCount = 0;
        long nonEmptyDatasetCount = 0;
        long tokenCount = 0;
        long matchedTokenCount = 0;
        long expectedDatasetCount = -1;
        int pageNumber = 0;
        while (true) {
            Page<CatalogDataset> page = datasetRepository.findAll(
                PageRequest.of(
                    pageNumber,
                    PLAN_PAGE_SIZE,
                    Sort.by(Sort.Order.asc("id"))
                )
            );
            expectedDatasetCount = requireStableCapacity(
                expectedDatasetCount,
                page.getTotalElements(),
                MAX_PLAN_DATASETS,
                "MIGRATION_DATASET_CAPACITY_EXCEEDED",
                "MIGRATION_DATASET_SNAPSHOT_DRIFT"
            );
            for (CatalogDataset dataset : page.getContent()) {
                datasetCount++;
                UUID datasetId = requireDatasetId(dataset);
                String assetKey = CatalogAssetKey.dataset(dataset);
                String legacyTags = dataset.getTags();
                updateDigest(digest, "DATASET");
                updateDigest(digest, datasetId.toString());
                updateNullableDigest(digest, legacyTags);
                updateDigest(digest, assetKey);
                if (!StringUtils.hasText(legacyTags)) {
                    continue;
                }
                nonEmptyDatasetCount++;
                String protectedFormat = protectedEvidenceFormat(legacyTags);
                if (protectedFormat != null) {
                    addDecision(
                        decisions,
                        PlanDecision.protectedEvidence(
                            datasetId,
                            assetKey,
                            legacyTags,
                            protectedFormat
                        )
                    );
                    continue;
                }

                for (String token : parseTokens(legacyTags)) {
                    tokenCount++;
                    List<CatalogTag> disabledCandidates = tagIndex
                        .disabledByName()
                        .getOrDefault(token, List.of());
                    if (!disabledCandidates.isEmpty()) {
                        addDecision(
                            decisions,
                            PlanDecision.disabled(
                                datasetId,
                                assetKey,
                                legacyTags,
                                token,
                                disabledCandidates
                            )
                        );
                        continue;
                    }
                    List<CatalogTag> candidates = tagIndex
                        .enabledByName()
                        .getOrDefault(token, List.of());
                    if (candidates.isEmpty()) {
                        addDecision(
                            decisions,
                            PlanDecision.unmatched(
                                datasetId,
                                assetKey,
                                legacyTags,
                                token
                            )
                        );
                    } else if (candidates.size() > 1) {
                        addDecision(
                            decisions,
                            PlanDecision.ambiguous(
                                datasetId,
                                assetKey,
                                legacyTags,
                                token,
                                candidates
                            )
                        );
                    } else {
                        matchedTokenCount++;
                        addDecision(
                            decisions,
                            PlanDecision.matched(
                                datasetId,
                                assetKey,
                                legacyTags,
                                token,
                                candidates.getFirst()
                            )
                        );
                    }
                }
            }
            if (!page.hasNext()) {
                break;
            }
            pageNumber++;
        }
        if (datasetCount != expectedDatasetCount) {
            throw conflict(
                "MIGRATION_DATASET_SNAPSHOT_DRIFT: dataset count changed during paged planning"
            );
        }

        List<RelationKey> plannedKeys = decisions
            .stream()
            .filter(PlanDecision::matched)
            .map(PlanDecision::relationKey)
            .toList();
        Set<RelationKey> existing = findExistingRelations(plannedKeys);
        List<PlanDecision> resolvedDecisions = decisions
            .stream()
            .map(decision -> decision.withExisting(
                decision.matched() && existing.contains(decision.relationKey())
            ))
            .toList();
        String checksum = checksum(digest, resolvedDecisions);
        String batchId = BATCH_PREFIX + checksum.substring(0, 24);

        List<CatalogTagMigrationTokenIssue> unmatched = resolvedDecisions
            .stream()
            .filter(decision ->
                decision.kind() == DecisionKind.UNMATCHED_NAME ||
                decision.kind() == DecisionKind.DISABLED_NAME
            )
            .map(PlanDecision::asIssue)
            .toList();
        List<CatalogTagMigrationTokenIssue> ambiguous = resolvedDecisions
            .stream()
            .filter(decision -> decision.kind() == DecisionKind.AMBIGUOUS_NAME)
            .map(PlanDecision::asIssue)
            .toList();
        List<CatalogTagProtectedEvidence> protectedEvidence = resolvedDecisions
            .stream()
            .filter(decision -> decision.kind() == DecisionKind.PROTECTED_MACHINE_EVIDENCE)
            .map(PlanDecision::asProtectedEvidence)
            .toList();
        List<CatalogTagMigrationRelation> relations = resolvedDecisions
            .stream()
            .filter(PlanDecision::matched)
            .map(PlanDecision::asRelation)
            .toList();
        long existingRelationCount = relations.stream().filter(CatalogTagMigrationRelation::existing).count();

        return new CatalogTagMigrationReport(
            batchId,
            checksum,
            datasetCount,
            nonEmptyDatasetCount,
            tokenCount,
            matchedTokenCount,
            List.copyOf(unmatched),
            List.copyOf(ambiguous),
            List.copyOf(protectedEvidence),
            relations.size(),
            existingRelationCount,
            List.copyOf(relations)
        );
    }

    private TagNameIndex loadTagNameIndex() {
        List<CatalogTag> tags = new ArrayList<>();
        long expectedTagCount = -1;
        int pageNumber = 0;
        while (true) {
            Page<CatalogTag> page = tagRepository.findAll(
                PageRequest.of(
                    pageNumber,
                    PLAN_PAGE_SIZE,
                    Sort.by(Sort.Order.asc("id"))
                )
            );
            expectedTagCount = requireStableCapacity(
                expectedTagCount,
                page.getTotalElements(),
                MAX_PLAN_TAGS,
                "MIGRATION_TAG_CAPACITY_EXCEEDED",
                "MIGRATION_TAG_SNAPSHOT_DRIFT"
            );
            tags.addAll(page.getContent());
            if (!page.hasNext()) {
                break;
            }
            pageNumber++;
        }
        if (tags.size() != expectedTagCount) {
            throw conflict(
                "MIGRATION_TAG_SNAPSHOT_DRIFT: tag count changed during paged planning"
            );
        }
        return tagsByExactName(tags);
    }

    private TagNameIndex tagsByExactName(Collection<CatalogTag> tags) {
        List<CatalogTag> sorted = tags == null
            ? List.of()
            : tags
                .stream()
                .filter(Objects::nonNull)
                .filter(tag -> tag.getId() != null && StringUtils.hasText(tag.getName()))
                .sorted(Comparator.comparing(tag -> tag.getId().toString()))
                .toList();
        Map<String, List<CatalogTag>> enabled = new LinkedHashMap<>();
        Map<String, List<CatalogTag>> disabled = new LinkedHashMap<>();
        for (CatalogTag tag : sorted) {
            Map<String, List<CatalogTag>> target = tag.isEnabled()
                ? enabled
                : disabled;
            target
                .computeIfAbsent(
                    tag.getName().trim(),
                    ignored -> new ArrayList<>()
                )
                .add(tag);
        }
        enabled.replaceAll((name, matches) -> List.copyOf(matches));
        disabled.replaceAll((name, matches) -> List.copyOf(matches));
        return new TagNameIndex(
            Map.copyOf(enabled),
            Map.copyOf(disabled)
        );
    }

    private List<String> parseTokens(String legacyTags) {
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        for (String part : TOKEN_SPLIT_PATTERN.split(legacyTags)) {
            if (StringUtils.hasText(part)) {
                tokens.add(part.trim());
            }
        }
        return List.copyOf(tokens);
    }

    private String protectedEvidenceFormat(String legacyTags) {
        String value = legacyTags.trim();
        if (isJsonObject(value)) {
            return "JSON_OBJECT";
        }
        if (isKnownKeyValueEvidence(value)) {
            return "KEY_VALUE_EVIDENCE";
        }
        return null;
    }

    private boolean isJsonObject(String value) {
        if (!value.startsWith("{") || !value.endsWith("}")) {
            return false;
        }
        try {
            JsonNode node = objectMapper.readTree(value);
            return node != null && node.isObject();
        } catch (JsonProcessingException exception) {
            return false;
        }
    }

    private boolean isKnownKeyValueEvidence(String value) {
        boolean foundAssignment = false;
        boolean foundKnownKey = false;
        for (String part : value.split(";", -1)) {
            String item = part.trim();
            if (item.isEmpty()) {
                continue;
            }
            int separator = item.indexOf('=');
            if (separator <= 0 || separator == item.length() - 1) {
                return false;
            }
            foundAssignment = true;
            String key = item.substring(0, separator).trim();
            foundKnownKey |= KNOWN_API_EVIDENCE_KEYS.contains(key);
        }
        return foundAssignment && foundKnownKey;
    }

    private void addDecision(
        List<PlanDecision> decisions,
        PlanDecision decision
    ) {
        if (decisions.size() >= MAX_PLAN_DECISIONS) {
            throw conflict(
                "MIGRATION_DECISION_CAPACITY_EXCEEDED: migration plan exceeds " +
                MAX_PLAN_DECISIONS +
                " token decisions"
            );
        }
        decisions.add(decision);
    }

    private long requireStableCapacity(
        long expected,
        long observed,
        int maximum,
        String capacityReason,
        String driftReason
    ) {
        if (observed > maximum) {
            throw conflict(
                capacityReason + ": catalog contains " + observed +
                " rows; maximum is " + maximum
            );
        }
        if (expected >= 0 && expected != observed) {
            throw conflict(
                driftReason + ": row count changed during paged planning"
            );
        }
        return observed;
    }

    private Set<RelationKey> findExistingRelations(
        List<RelationKey> plannedKeys
    ) {
        if (plannedKeys.isEmpty()) {
            return Set.of();
        }
        Set<RelationKey> existing = new LinkedHashSet<>();
        for (
            int start = 0;
            start < plannedKeys.size();
            start += EXISTING_RELATION_LOOKUP_BATCH_SIZE
        ) {
            int end = Math.min(
                start + EXISTING_RELATION_LOOKUP_BATCH_SIZE,
                plannedKeys.size()
            );
            existing.addAll(
                store.findExistingRelations(plannedKeys.subList(start, end))
            );
        }
        return Set.copyOf(existing);
    }

    private String checksum(
        MessageDigest digest,
        List<PlanDecision> decisions
    ) {
        for (PlanDecision decision : decisions) {
            updateDigest(digest, "DECISION");
            updateDigest(digest, decision.datasetId().toString());
            updateDigest(digest, decision.assetKey());
            updateNullableDigest(digest, decision.legacyTags());
            updateDigest(digest, decision.kind().name());
            updateNullableDigest(digest, decision.token());
            updateNullableDigest(digest, decision.protectedFormat());
            for (UUID candidateId : decision.candidateTagIds()) {
                updateDigest(digest, candidateId.toString());
            }
            updateNullableDigest(
                digest,
                decision.targetTag() == null ? null : decision.targetTag().getId().toString()
            );
            updateNullableDigest(
                digest,
                decision.targetTag() == null ? null : decision.targetTag().getCode()
            );
            updateNullableDigest(
                digest,
                decision.targetTag() == null ? null : decision.targetTag().getName()
            );
            updateDigest(digest, Boolean.toString(decision.existing()));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private void requireLedgerChecksum(BatchRecord batch, String expectedChecksum) {
        if (!expectedChecksum.equals(batch.checksum())) {
            throw conflict("MIGRATION_BATCH_CHECKSUM_MISMATCH: ledger checksum differs");
        }
    }

    private void requireBatchMatchesChecksum(String batchId, String checksum) {
        if (!batchId.equals(BATCH_PREFIX + checksum.substring(0, 24))) {
            throw conflict("MIGRATION_BATCH_CHECKSUM_MISMATCH: batch id does not match checksum");
        }
    }

    private ExecutionEvidence readExecutionEvidence(BatchRecord batch) {
        if (!StringUtils.hasText(batch.executionJson())) {
            throw conflict("MIGRATION_EXECUTION_EVIDENCE_MISSING: executed batch has no evidence");
        }
        try {
            ExecutionEvidence evidence = objectMapper.readValue(
                batch.executionJson(),
                ExecutionEvidence.class
            );
            if (
                !batch.batchId().equals(evidence.batchId()) ||
                !batch.checksum().equals(evidence.checksum()) ||
                evidence.createdRelations() == null
            ) {
                throw conflict("MIGRATION_EXECUTION_EVIDENCE_INVALID: evidence identity differs");
            }
            return evidence;
        } catch (JsonProcessingException exception) {
            throw conflict("MIGRATION_EXECUTION_EVIDENCE_INVALID: evidence cannot be decoded");
        }
    }

    private CatalogTagMigrationRollback readRollbackEvidence(BatchRecord batch, boolean replayed) {
        if (!StringUtils.hasText(batch.rollbackJson())) {
            throw conflict("MIGRATION_ROLLBACK_EVIDENCE_MISSING: rolled-back batch has no evidence");
        }
        try {
            CatalogTagMigrationRollback rollback = objectMapper.readValue(
                batch.rollbackJson(),
                CatalogTagMigrationRollback.class
            );
            if (
                !batch.batchId().equals(rollback.batchId()) ||
                !batch.checksum().equals(rollback.checksum())
            ) {
                throw conflict("MIGRATION_ROLLBACK_EVIDENCE_INVALID: evidence identity differs");
            }
            return new CatalogTagMigrationRollback(
                rollback.batchId(),
                rollback.checksum(),
                rollback.status(),
                replayed,
                rollback.matched(),
                rollback.unmatched(),
                rollback.ambiguous(),
                rollback.protectedEvidence(),
                rollback.created(),
                rollback.skipped(),
                rollback.deleted()
            );
        } catch (JsonProcessingException exception) {
            throw conflict("MIGRATION_ROLLBACK_EVIDENCE_INVALID: evidence cannot be decoded");
        }
    }

    private boolean sameEvidence(
        List<RelationEvidence> expected,
        List<RelationEvidence> actual
    ) {
        if (expected == null || actual == null || expected.size() != actual.size()) {
            return false;
        }
        return new LinkedHashSet<>(expected).equals(new LinkedHashSet<>(actual));
    }

    private String requireBatchId(String value) {
        if (!StringUtils.hasText(value) || !BATCH_PATTERN.matcher(value.trim()).matches()) {
            throw new IllegalArgumentException(
                "batchId must use catalog-tags- followed by 24 lowercase hexadecimal characters"
            );
        }
        return value.trim();
    }

    private String requireChecksum(String value) {
        if (!StringUtils.hasText(value) || !CHECKSUM_PATTERN.matcher(value.trim()).matches()) {
            throw new IllegalArgumentException("expectedChecksum must be a lowercase SHA-256 checksum");
        }
        return value.trim();
    }

    private String requireActor(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("authenticated actor is required");
        }
        String actor = value.trim();
        if (actor.length() > 50) {
            throw new IllegalArgumentException("authenticated actor must not exceed 50 characters");
        }
        return actor;
    }

    private UUID requireDatasetId(CatalogDataset dataset) {
        if (dataset == null || dataset.getId() == null) {
            throw new IllegalStateException("MIGRATION_DATASET_ID_REQUIRED");
        }
        return dataset.getId();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("MIGRATION_EVIDENCE_SERIALIZATION_FAILED", exception);
        }
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void updateNullableDigest(MessageDigest digest, String value) {
        if (value == null) {
            digest.update((byte) 0);
            return;
        }
        digest.update((byte) 1);
        updateDigest(digest, value);
    }

    private void updateDigest(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private CatalogTagMigrationConflictException conflict(String message) {
        return new CatalogTagMigrationConflictException(message);
    }

    private enum DecisionKind {
        MATCHED,
        UNMATCHED_NAME,
        DISABLED_NAME,
        AMBIGUOUS_NAME,
        PROTECTED_MACHINE_EVIDENCE,
    }

    private record TagNameIndex(
        Map<String, List<CatalogTag>> enabledByName,
        Map<String, List<CatalogTag>> disabledByName
    ) {}

    private record PlanDecision(
        UUID datasetId,
        String assetKey,
        String legacyTags,
        DecisionKind kind,
        String token,
        String protectedFormat,
        List<UUID> candidateTagIds,
        CatalogTag targetTag,
        boolean existing
    ) {

        static PlanDecision protectedEvidence(
            UUID datasetId,
            String assetKey,
            String legacyTags,
            String protectedFormat
        ) {
            return new PlanDecision(
                datasetId,
                assetKey,
                legacyTags,
                DecisionKind.PROTECTED_MACHINE_EVIDENCE,
                null,
                protectedFormat,
                List.of(),
                null,
                false
            );
        }

        static PlanDecision unmatched(
            UUID datasetId,
            String assetKey,
            String legacyTags,
            String token
        ) {
            return new PlanDecision(
                datasetId,
                assetKey,
                legacyTags,
                DecisionKind.UNMATCHED_NAME,
                token,
                null,
                List.of(),
                null,
                false
            );
        }

        static PlanDecision disabled(
            UUID datasetId,
            String assetKey,
            String legacyTags,
            String token,
            List<CatalogTag> candidates
        ) {
            return new PlanDecision(
                datasetId,
                assetKey,
                legacyTags,
                DecisionKind.DISABLED_NAME,
                token,
                null,
                candidates.stream().map(CatalogTag::getId).toList(),
                null,
                false
            );
        }

        static PlanDecision ambiguous(
            UUID datasetId,
            String assetKey,
            String legacyTags,
            String token,
            List<CatalogTag> candidates
        ) {
            return new PlanDecision(
                datasetId,
                assetKey,
                legacyTags,
                DecisionKind.AMBIGUOUS_NAME,
                token,
                null,
                candidates.stream().map(CatalogTag::getId).toList(),
                null,
                false
            );
        }

        static PlanDecision matched(
            UUID datasetId,
            String assetKey,
            String legacyTags,
            String token,
            CatalogTag targetTag
        ) {
            return new PlanDecision(
                datasetId,
                assetKey,
                legacyTags,
                DecisionKind.MATCHED,
                token,
                null,
                List.of(targetTag.getId()),
                targetTag,
                false
            );
        }

        boolean matched() {
            return kind == DecisionKind.MATCHED;
        }

        RelationKey relationKey() {
            if (!matched() || targetTag == null) {
                throw new IllegalStateException("only matched decisions have relation keys");
            }
            return new RelationKey(targetTag.getId(), ASSET_TYPE, assetKey);
        }

        PlanDecision withExisting(boolean value) {
            return new PlanDecision(
                datasetId,
                assetKey,
                legacyTags,
                kind,
                token,
                protectedFormat,
                candidateTagIds,
                targetTag,
                value
            );
        }

        CatalogTagMigrationTokenIssue asIssue() {
            return new CatalogTagMigrationTokenIssue(
                datasetId,
                assetKey,
                token,
                kind.name(),
                List.copyOf(candidateTagIds)
            );
        }

        CatalogTagProtectedEvidence asProtectedEvidence() {
            return new CatalogTagProtectedEvidence(
                datasetId,
                assetKey,
                protectedFormat,
                legacyTags
            );
        }

        CatalogTagMigrationRelation asRelation() {
            return new CatalogTagMigrationRelation(
                datasetId,
                targetTag.getId(),
                targetTag.getCode(),
                targetTag.getName(),
                ASSET_TYPE,
                assetKey,
                existing
            );
        }
    }

    private record ExecutionEvidence(
        String batchId,
        String checksum,
        long matched,
        long unmatched,
        long ambiguous,
        long protectedEvidence,
        long created,
        long skipped,
        List<RelationEvidence> createdRelations
    ) {

        CatalogTagMigrationExecution asResponse(boolean replayed) {
            return new CatalogTagMigrationExecution(
                batchId,
                checksum,
                "EXECUTED",
                replayed,
                matched,
                unmatched,
                ambiguous,
                protectedEvidence,
                created,
                skipped
            );
        }
    }
}
