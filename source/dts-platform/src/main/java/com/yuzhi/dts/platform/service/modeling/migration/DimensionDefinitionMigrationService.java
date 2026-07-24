package com.yuzhi.dts.platform.service.modeling.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionApplicationService;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.CreateCommand;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.HierarchyLevelSemantic;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.HierarchySemantic;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.ReuseScope;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.Status;
import com.yuzhi.dts.platform.service.modeling.ModelSpecDomainReadAccessPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Backfills reusable dimension definitions without rewriting the ModelSpec ledger that supplied them.
 *
 * <p>The legacy-map row is the only write-side reference: it pins the new definition revision to the
 * immutable legacy ModelSpec identity. In particular, this service never updates a ModelSpec head,
 * revision, snapshot, or its nullable dimension-definition columns.
 */
@Service
public class DimensionDefinitionMigrationService {

    private static final String BATCH_PREFIX = "dimension-definitions-";

    private final MigrationStore store;
    private final DefinitionFactory definitions;
    private final ModelSpecDomainReadAccessPort domainReadAccess;
    private final String defaultTenantId;

    public DimensionDefinitionMigrationService(
        JdbcTemplate jdbc,
        ObjectMapper objectMapper,
        DimensionDefinitionApplicationService definitions,
        ModelSpecDomainReadAccessPort domainReadAccess,
        @Value("${dts.platform.modeling.default-tenant-id:default}") String defaultTenantId
    ) {
        this(new JdbcMigrationStore(jdbc, objectMapper), new ApplicationDefinitionFactory(definitions), domainReadAccess, defaultTenantId);
    }

    DimensionDefinitionMigrationService(
        MigrationStore store,
        DefinitionFactory definitions,
        ModelSpecDomainReadAccessPort domainReadAccess,
        String defaultTenantId
    ) {
        this.store = Objects.requireNonNull(store, "store");
        this.definitions = Objects.requireNonNull(definitions, "definitions");
        this.domainReadAccess = Objects.requireNonNull(domainReadAccess, "domainReadAccess");
        this.defaultTenantId = requireText(defaultTenantId, "default tenant id");
    }

    public String defaultTenantId() {
        return defaultTenantId;
    }

    @Transactional
    public DryRunReport dryRun(String tenantId, String actorId) {
        String tenant = requireTenant(tenantId);
        String actor = requireActor(actorId);
        DryRunReport planned = plan(tenant, false, visibleDomainIds());
        MigrationBatch batch = store.registerDryRun(tenant, planned, actor);
        if (!Objects.equals(batch.checksum(), planned.checksum())) {
            throw new IllegalStateException(
                "DIMENSION_MIGRATION_BATCH_CHECKSUM_MISMATCH: batch identity does not match its audit ledger"
            );
        }
        return planned.withBatchId(batch.batchId()).withStatus(batch.status());
    }

    /**
     * Re-plans while holding a shared lock on every visible candidate source head. The accepted digest
     * covers both source content and the chosen migration decision, so changing a reusable definition or
     * creating a legacy map after dry-run is rejected before any write.
     */
    @Transactional
    public MigrationExecution execute(String tenantId, String batchId, String expectedChecksum, String actorId) {
        String tenant = requireTenant(tenantId);
        String actor = requireActor(actorId);
        String requestedBatch = requireText(batchId, "batchId");
        String requestedChecksum = requireText(expectedChecksum, "expectedChecksum");
        MigrationBatch batch = store
            .lockBatch(tenant, requestedBatch)
            .orElseThrow(() ->
                new IllegalStateException("DIMENSION_MIGRATION_DRY_RUN_REQUIRED: migration batch was not registered")
            );
        requireBatchChecksum(batch, requestedBatch, requestedChecksum);
        if (batch.status() == MigrationStatus.EXECUTED) {
            if (batch.execution() == null) {
                throw new IllegalStateException("DIMENSION_MIGRATION_BATCH_INVALID: execution evidence is missing");
            }
            return batch.execution().asReplay();
        }
        if (batch.status() == MigrationStatus.ROLLED_BACK) {
            throw new IllegalStateException("DIMENSION_MIGRATION_BATCH_ROLLED_BACK: rolled-back batches are terminal");
        }
        DryRunReport report = plan(tenant, true, visibleDomainIds());
        if (!matchesPlanBatch(requestedBatch, report.batchId())) {
            throw new IllegalStateException("DIMENSION_MIGRATION_BATCH_STALE: dry-run batch identity changed");
        }
        if (!Objects.equals(requestedChecksum, report.checksum())) {
            throw new IllegalStateException("DIMENSION_MIGRATION_CHECKSUM_MISMATCH: dry-run checksum changed");
        }

        long mappingsCreated = 0;
        long definitionsCreated = 0;
        long definitionsReused = 0;
        Map<DefinitionKey, DefinitionRef> batchDefinitions = new LinkedHashMap<>();
        for (MigrationDecision decision : report.decisions()) {
            DefinitionKey key = DefinitionKey.of(decision.source());
            boolean createdByBatch = false;
            DefinitionRef target = switch (decision.classification()) {
                case AUTO_MIGRATABLE -> {
                    DefinitionCreateResult created = definitions.create(tenant, actor, decision.source(), requestedBatch);
                    if (created.replayed()) definitionsReused++;
                    else {
                        definitionsCreated++;
                        createdByBatch = true;
                    }
                    batchDefinitions.put(key, created.definition());
                    yield created.definition();
                }
                case REUSABLE_EXISTING_DEFINITION -> {
                    definitionsReused++;
                    batchDefinitions.put(key, decision.target());
                    yield decision.target();
                }
                case REUSABLE_BATCH_DEFINITION -> {
                    DefinitionRef created = batchDefinitions.get(key);
                    if (created == null) {
                        throw new IllegalStateException(
                            "DIMENSION_MIGRATION_BATCH_INVALID: reusable batch definition has no predecessor"
                        );
                    }
                    definitionsReused++;
                    yield created;
                }
                default -> null;
            };
            if (
                target != null &&
                store.insertMapping(
                    tenant,
                    decision.source().legacyModelSpecId(),
                    target,
                    requestedBatch,
                    report.checksum(),
                    decision.classification(),
                    createdByBatch,
                    actor
                )
            ) {
                mappingsCreated++;
            }
        }
        MigrationExecution execution = new MigrationExecution(
            requestedBatch,
            report.checksum(),
            false,
            mappingsCreated,
            definitionsCreated,
            definitionsReused,
            report.total(),
            report.classificationCounts(),
            MigrationStatus.EXECUTED
        );
        store.completeExecution(tenant, execution, actor);
        return execution;
    }

    @Transactional
    public MigrationRollback rollback(String tenantId, String batchId, String expectedChecksum, String actorId) {
        String tenant = requireTenant(tenantId);
        String actor = requireActor(actorId);
        String requestedBatch = requireText(batchId, "batchId");
        String requestedChecksum = requireText(expectedChecksum, "expectedChecksum");
        MigrationBatch batch = store
            .lockBatch(tenant, requestedBatch)
            .orElseThrow(() ->
                new IllegalStateException("DIMENSION_MIGRATION_BATCH_NOT_FOUND: migration batch was not found")
            );
        requireBatchChecksum(batch, requestedBatch, requestedChecksum);
        if (batch.status() == MigrationStatus.ROLLED_BACK) {
            if (batch.rollback() == null) {
                throw new IllegalStateException("DIMENSION_MIGRATION_BATCH_INVALID: rollback evidence is missing");
            }
            return batch.rollback().asReplay();
        }
        if (batch.status() != MigrationStatus.EXECUTED || batch.execution() == null) {
            throw new IllegalStateException(
                "DIMENSION_MIGRATION_ROLLBACK_NOT_EXECUTED: only an executed batch can be rolled back"
            );
        }

        List<RollbackMapping> mappings = store.loadRollbackMappings(tenant, requestedBatch);
        if (mappings.size() != batch.execution().mappingsCreated()) {
            throw new IllegalStateException(
                "DIMENSION_MIGRATION_ROLLBACK_LEDGER_DRIFT: batch mapping count no longer matches execution evidence"
            );
        }
        Set<UUID> visibleDomains = visibleDomainIds();
        boolean hidden = mappings
            .stream()
            .anyMatch(mapping ->
                mapping.sourceDomainId() == null ||
                mapping.definitionDomainId() == null ||
                !mapping.sourceDomainId().equals(mapping.definitionDomainId()) ||
                !visibleDomains.contains(mapping.sourceDomainId())
            );
        if (hidden) {
            throw new MigrationAccessException("migration batch contains a business category that is not visible");
        }
        boolean checksumDrift = mappings
            .stream()
            .anyMatch(mapping -> !Objects.equals(requestedChecksum, mapping.migrationChecksum()));
        if (checksumDrift) {
            throw new IllegalStateException(
                "DIMENSION_MIGRATION_ROLLBACK_LEDGER_DRIFT: mapping checksum no longer matches the batch"
            );
        }

        Map<UUID, Integer> createdDefinitions = mappings
            .stream()
            .filter(RollbackMapping::definitionCreatedByBatch)
            .collect(
                java.util.stream.Collectors.toMap(
                    RollbackMapping::dimensionDefinitionId,
                    RollbackMapping::dimensionDefinitionRevision,
                    Math::max,
                    LinkedHashMap::new
                )
            );
        long mappingsDeleted = store.deleteMappings(tenant, requestedBatch, requestedChecksum);
        if (mappingsDeleted != mappings.size()) {
            throw new IllegalStateException(
                "DIMENSION_MIGRATION_ROLLBACK_LEDGER_DRIFT: batch mappings changed during rollback"
            );
        }
        long definitionsDeleted = store.deleteUnusedMigrationDefinitions(tenant, createdDefinitions);
        MigrationRollback rollback = new MigrationRollback(
            requestedBatch,
            requestedChecksum,
            false,
            mappingsDeleted,
            definitionsDeleted,
            createdDefinitions.size() - definitionsDeleted,
            MigrationStatus.ROLLED_BACK
        );
        store.completeRollback(tenant, rollback, actor);
        return rollback;
    }

    private DryRunReport plan(String tenantId, boolean lockSources, Set<UUID> visibleDomains) {
        List<LegacyDimension> sources = store
            .loadLegacyDimensions(tenantId, lockSources)
            .stream()
            .filter(source -> source.domainId() != null && visibleDomains.contains(source.domainId()))
            .sorted(Comparator.comparing(LegacyDimension::legacyModelSpecId))
            .toList();
        Map<DefinitionKey, List<LegacyDimension>> sourceGroups = sources
            .stream()
            .filter(source -> source.mapping() == null && source.domainId() != null && source.validPayload())
            .collect(
                java.util.stream.Collectors.groupingBy(
                    DefinitionKey::of,
                    LinkedHashMap::new,
                    java.util.stream.Collectors.toList()
                )
            );
        List<MigrationDecision> decisions = new java.util.ArrayList<>();
        for (LegacyDimension source : sources) {
            List<LegacyDimension> peers =
                source.mapping() == null &&
                source.domainId() != null &&
                source.validPayload()
                    ? sourceGroups.getOrDefault(DefinitionKey.of(source), List.of(source))
                    : List.of(source);
            decisions.add(classify(tenantId, source, peers, lockSources));
        }
        String checksum = checksum(decisions);
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Classification classification : Classification.values()) {
            counts.put(classification.name(), decisions.stream().filter(item -> item.classification() == classification).count());
        }
        long automatic = counts.get(Classification.AUTO_MIGRATABLE.name());
        long reusable =
            counts.get(Classification.REUSABLE_EXISTING_DEFINITION.name()) +
            counts.get(Classification.REUSABLE_BATCH_DEFINITION.name());
        long conflicts = counts.get(Classification.NAME_DOMAIN_CONFLICT.name()) + counts.get(Classification.INVALID_LEGACY_PAYLOAD.name());
        long orphans = counts.get(Classification.MISSING_DOMAIN.name());
        return new DryRunReport(
            BATCH_PREFIX + checksum.substring(0, 16),
            checksum,
            sources.size(),
            automatic,
            reusable,
            conflicts,
            orphans,
            Map.copyOf(counts),
            decisions,
            MigrationStatus.DRY_RUN
        );
    }

    private MigrationDecision classify(
        String tenantId,
        LegacyDimension source,
        List<LegacyDimension> peers,
        boolean lockDefinitions
    ) {
        if (source.mapping() != null) {
            return decision(source, Classification.ALREADY_MIGRATED, source.mapping(), "LEGACY_MAP_EXISTS");
        }
        if (source.domainId() == null) {
            return decision(source, Classification.MISSING_DOMAIN, null, "DOMAIN_REQUIRED");
        }
        if (!source.validPayload()) {
            return decision(source, Classification.INVALID_LEGACY_PAYLOAD, null, "LEGACY_DIMENSION_PAYLOAD_INVALID");
        }
        List<DefinitionRef> candidates = store.findDefinitionsByName(tenantId, source.name(), lockDefinitions);
        DefinitionRef reusable = candidates
            .stream()
            .filter(candidate -> source.domainId().equals(candidate.domainId()))
            .filter(candidate -> candidate.status() != Status.RETIRED)
            .filter(candidate -> semanticallyMatches(source, candidate))
            .sorted(Comparator.comparingInt(DefinitionRef::revision).reversed().thenComparing(DefinitionRef::id))
            .findFirst()
            .orElse(null);
        if (reusable != null) {
            return decision(source, Classification.REUSABLE_EXISTING_DEFINITION, reusable, "REUSABLE_DEFINITION_EXISTS");
        }
        boolean hasDifferentDefinitionInDomain = candidates
            .stream()
            .anyMatch(candidate -> source.domainId().equals(candidate.domainId()));
        if (hasDifferentDefinitionInDomain) {
            return decision(
                source,
                Classification.NAME_DOMAIN_CONFLICT,
                null,
                "NAME_EXISTS_WITH_DIFFERENT_DEFINITION"
            );
        }
        if (peers.size() > 1) {
            LegacyDimension first = peers.get(0);
            boolean equivalent = peers.stream().allMatch(peer -> semanticallyMatches(first, peer));
            if (!equivalent) {
                return decision(
                    source,
                    Classification.NAME_DOMAIN_CONFLICT,
                    null,
                    "LEGACY_NAME_CONTENT_CONFLICT"
                );
            }
            if (!source.legacyModelSpecId().equals(first.legacyModelSpecId())) {
                return decision(
                    source,
                    Classification.REUSABLE_BATCH_DEFINITION,
                    null,
                    "REUSABLE_BATCH_DEFINITION"
                );
            }
        }
        return decision(source, Classification.AUTO_MIGRATABLE, null, "AUTO_MIGRATABLE");
    }

    private static boolean semanticallyMatches(LegacyDimension source, DefinitionRef candidate) {
        return (
            Objects.equals(trimToNull(source.definition()), trimToNull(candidate.definition())) &&
            Objects.equals(source.reuseScope(), candidate.reuseScope()) &&
            Objects.equals(source.hierarchies(), candidate.hierarchies())
        );
    }

    private static boolean semanticallyMatches(LegacyDimension first, LegacyDimension second) {
        return (
            Objects.equals(trimToNull(first.definition()), trimToNull(second.definition())) &&
            Objects.equals(first.reuseScope(), second.reuseScope()) &&
            Objects.equals(first.hierarchies(), second.hierarchies())
        );
    }

    private static void requireBatchChecksum(MigrationBatch batch, String batchId, String checksum) {
        if (!Objects.equals(batch.batchId(), batchId) || !Objects.equals(batch.checksum(), checksum)) {
            throw new IllegalStateException(
                "DIMENSION_MIGRATION_BATCH_CHECKSUM_MISMATCH: batch identity does not match its audit ledger"
            );
        }
    }

    private static boolean matchesPlanBatch(String requestedBatch, String canonicalBatch) {
        if (Objects.equals(requestedBatch, canonicalBatch)) return true;
        String retryPrefix = canonicalBatch + "-retry-";
        if (!requestedBatch.startsWith(retryPrefix)) return false;
        String attempt = requestedBatch.substring(retryPrefix.length());
        return !attempt.isEmpty() && attempt.chars().allMatch(Character::isDigit);
    }

    private static MigrationDecision decision(LegacyDimension source, Classification classification, DefinitionRef target, String reason) {
        return new MigrationDecision(source, classification, target, List.of(reason));
    }

    private static String checksum(List<MigrationDecision> decisions) {
        StringBuilder canonical = new StringBuilder();
        for (MigrationDecision decision : decisions) {
            LegacyDimension source = decision.source();
            DefinitionRef target = decision.target();
            DefinitionRef mapping = source.mapping();
            token(
                canonical,
                source.legacyModelSpecId(),
                source.revision(),
                source.sourceChecksum(),
                source.domainId(),
                source.name(),
                source.definition(),
                source.hierarchies(),
                source.reuseScope(),
                source.validPayload(),
                decision.classification(),
                target == null ? null : target.id(),
                target == null ? null : target.domainId(),
                target == null ? null : target.revision(),
                target == null ? null : target.status(),
                target == null ? null : target.definition(),
                target == null ? null : target.hierarchies(),
                target == null ? null : target.reuseScope(),
                mapping == null ? "UNMAPPED" : "MAPPED",
                mapping == null ? null : mapping.id(),
                mapping == null ? null : mapping.domainId(),
                mapping == null ? null : mapping.revision(),
                mapping == null ? null : mapping.status()
            );
        }
        return sha256(canonical.toString());
    }

    private Set<UUID> visibleDomainIds() {
        try {
            Set<UUID> visible = domainReadAccess.visibleDomainIds();
            if (visible == null) throw new MigrationAccessException("domain visibility is unavailable");
            return visible.stream().filter(Objects::nonNull).collect(java.util.stream.Collectors.toUnmodifiableSet());
        } catch (MigrationAccessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new MigrationAccessException("domain visibility is unavailable");
        }
    }

    private static void token(StringBuilder target, Object... values) {
        for (Object value : values) {
            String text = String.valueOf(value);
            target.append(text.length()).append(':').append(text).append('|');
        }
        target.append('\n');
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String requireTenant(String tenantId) {
        return requireText(tenantId, "server tenant id");
    }

    private static String requireActor(String actorId) {
        return requireText(actorId, "authenticated actor");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new MigrationAccessException(name + " is required");
        return value.trim();
    }

    public enum Classification {
        AUTO_MIGRATABLE,
        REUSABLE_EXISTING_DEFINITION,
        REUSABLE_BATCH_DEFINITION,
        NAME_DOMAIN_CONFLICT,
        MISSING_DOMAIN,
        INVALID_LEGACY_PAYLOAD,
        ALREADY_MIGRATED,
    }

    public enum MigrationStatus {
        DRY_RUN,
        EXECUTED,
        ROLLED_BACK,
    }

    public record DefinitionRef(
        UUID id,
        UUID domainId,
        int revision,
        Status status,
        String definition,
        List<HierarchySemantic> hierarchies,
        ReuseScope reuseScope
    ) {
        public DefinitionRef {
            hierarchies = hierarchies == null ? List.of() : List.copyOf(hierarchies);
        }

        public DefinitionRef(UUID id, UUID domainId, int revision, Status status) {
            this(id, domainId, revision, status, null, List.of(), null);
        }
    }

    public record LegacyDimension(
        UUID legacyModelSpecId,
        int revision,
        String sourceChecksum,
        UUID domainId,
        String name,
        String definition,
        List<HierarchySemantic> hierarchies,
        ReuseScope reuseScope,
        boolean validPayload,
        DefinitionRef mapping
    ) {
        public LegacyDimension {
            hierarchies = hierarchies == null ? List.of() : List.copyOf(hierarchies);
            sourceChecksum = sourceChecksum == null ? "" : sourceChecksum;
            name = trimToNull(name);
            definition = trimToNull(definition);
            reuseScope = reuseScope == null ? ReuseScope.DOMAIN : reuseScope;
        }
    }

    public record MigrationDecision(LegacyDimension source, Classification classification, DefinitionRef target, List<String> reasons) {
        public MigrationDecision {
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
        }
    }

    public record DryRunReport(
        String batchId,
        String checksum,
        long total,
        long automatic,
        long reusable,
        long conflicts,
        long orphans,
        Map<String, Long> classificationCounts,
        List<MigrationDecision> decisions,
        MigrationStatus status
    ) {
        public DryRunReport {
            classificationCounts = classificationCounts == null ? Map.of() : Map.copyOf(classificationCounts);
            decisions = decisions == null ? List.of() : List.copyOf(decisions);
            status = status == null ? MigrationStatus.DRY_RUN : status;
        }

        public DryRunReport(
            String batchId,
            String checksum,
            long total,
            long automatic,
            long reusable,
            long conflicts,
            long orphans,
            Map<String, Long> classificationCounts,
            List<MigrationDecision> decisions
        ) {
            this(
                batchId,
                checksum,
                total,
                automatic,
                reusable,
                conflicts,
                orphans,
                classificationCounts,
                decisions,
                MigrationStatus.DRY_RUN
            );
        }

        DryRunReport withStatus(MigrationStatus status) {
            return new DryRunReport(
                batchId,
                checksum,
                total,
                automatic,
                reusable,
                conflicts,
                orphans,
                classificationCounts,
                decisions,
                status
            );
        }

        DryRunReport withBatchId(String replacementBatchId) {
            return new DryRunReport(
                replacementBatchId,
                checksum,
                total,
                automatic,
                reusable,
                conflicts,
                orphans,
                classificationCounts,
                decisions,
                status
            );
        }
    }

    public record MigrationExecution(
        String batchId,
        String checksum,
        boolean replayed,
        long mappingsCreated,
        long definitionsCreated,
        long definitionsReused,
        long sourceTotal,
        Map<String, Long> classificationCounts,
        MigrationStatus status
    ) {
        public MigrationExecution {
            classificationCounts = classificationCounts == null ? Map.of() : Map.copyOf(classificationCounts);
            status = status == null ? MigrationStatus.EXECUTED : status;
        }

        public MigrationExecution(
            String batchId,
            String checksum,
            boolean replayed,
            long mappingsCreated,
            long definitionsCreated,
            long definitionsReused,
            long sourceTotal,
            Map<String, Long> classificationCounts
        ) {
            this(
                batchId,
                checksum,
                replayed,
                mappingsCreated,
                definitionsCreated,
                definitionsReused,
                sourceTotal,
                classificationCounts,
                MigrationStatus.EXECUTED
            );
        }

        MigrationExecution asReplay() {
            return new MigrationExecution(
                batchId,
                checksum,
                true,
                mappingsCreated,
                definitionsCreated,
                definitionsReused,
                sourceTotal,
                classificationCounts,
                status
            );
        }
    }

    public record MigrationRollback(
        String batchId,
        String checksum,
        boolean replayed,
        long mappingsDeleted,
        long definitionsDeleted,
        long definitionsRetained,
        MigrationStatus status
    ) {
        public MigrationRollback {
            status = status == null ? MigrationStatus.ROLLED_BACK : status;
        }

        MigrationRollback asReplay() {
            return new MigrationRollback(
                batchId,
                checksum,
                true,
                mappingsDeleted,
                definitionsDeleted,
                definitionsRetained,
                status
            );
        }
    }

    record MigrationBatch(
        String batchId,
        String checksum,
        MigrationStatus status,
        MigrationExecution execution,
        MigrationRollback rollback
    ) {}

    record RollbackMapping(
        UUID legacyModelSpecId,
        UUID dimensionDefinitionId,
        int dimensionDefinitionRevision,
        UUID sourceDomainId,
        UUID definitionDomainId,
        String migrationChecksum,
        boolean definitionCreatedByBatch
    ) {}

    private record DefinitionKey(UUID domainId, String normalizedName) {
        private static DefinitionKey of(LegacyDimension source) {
            return new DefinitionKey(
                source.domainId(),
                source.name() == null ? "" : source.name().toLowerCase(Locale.ROOT)
            );
        }
    }

    interface MigrationStore {
        List<LegacyDimension> loadLegacyDimensions(String tenantId, boolean lockSources);

        List<DefinitionRef> findDefinitionsByName(String tenantId, String name, boolean lockDefinitions);

        MigrationBatch registerDryRun(String tenantId, DryRunReport report, String actorId);

        java.util.Optional<MigrationBatch> lockBatch(String tenantId, String batchId);

        void completeExecution(String tenantId, MigrationExecution execution, String actorId);

        boolean insertMapping(
            String tenantId,
            UUID legacyModelSpecId,
            DefinitionRef definition,
            String batchId,
            String migrationChecksum,
            Classification classification,
            boolean definitionCreatedByBatch,
            String actorId
        );

        List<RollbackMapping> loadRollbackMappings(String tenantId, String batchId);

        long deleteMappings(String tenantId, String batchId, String migrationChecksum);

        long deleteUnusedMigrationDefinitions(
            String tenantId,
            Map<UUID, Integer> definitionRevisions
        );

        void completeRollback(String tenantId, MigrationRollback rollback, String actorId);
    }

    interface DefinitionFactory {
        DefinitionCreateResult create(String tenantId, String actorId, LegacyDimension source, String batchId);
    }

    record DefinitionCreateResult(DefinitionRef definition, boolean replayed) {}

    private static final class ApplicationDefinitionFactory implements DefinitionFactory {

        private final DimensionDefinitionApplicationService definitions;

        private ApplicationDefinitionFactory(DimensionDefinitionApplicationService definitions) {
            this.definitions = definitions;
        }

        @Override
        public DefinitionCreateResult create(String tenantId, String actorId, LegacyDimension source, String batchId) {
            DimensionDefinitionApplicationService.CreateResult created = definitions.create(
                tenantId,
                actorId,
                new CreateCommand(
                    source.domainId(),
                    source.name(),
                    source.definition(),
                    actorId,
                    source.reuseScope(),
                    source.hierarchies(),
                    "dimension-migration:" + source.legacyModelSpecId()
                )
            );
            return new DefinitionCreateResult(
                new DefinitionRef(
                    created.dimensionDefinition().id(),
                    created.dimensionDefinition().domainId(),
                    created.dimensionDefinition().revision(),
                    created.dimensionDefinition().status(),
                    created.dimensionDefinition().definition(),
                    created.dimensionDefinition().hierarchies(),
                    created.dimensionDefinition().reuseScope()
                ),
                created.replayed()
            );
        }
    }

    private static final class JdbcMigrationStore implements MigrationStore {

        private final JdbcTemplate jdbc;
        private final ObjectMapper objectMapper;

        private JdbcMigrationStore(JdbcTemplate jdbc, ObjectMapper objectMapper) {
            this.jdbc = jdbc;
            this.objectMapper = objectMapper;
        }

        @Override
        public List<LegacyDimension> loadLegacyDimensions(String tenantId, boolean lockSources) {
            String lock = lockSources ? " for share of s" : "";
            return jdbc.query(
                """
                select s.id, s.revision, s.current_checksum, s.domain_id, s.name, s.description,
                       s.dimension_profile::text as dimension_profile,
                       m.dimension_definition_id as mapped_definition_id,
                       m.dimension_definition_revision as mapped_definition_revision,
                       d.domain_id as mapped_domain_id, d.status as mapped_status
                  from modeling_model_spec s
                  left join modeling_dimension_definition_legacy_map m
                    on m.tenant_id = s.tenant_id and m.legacy_model_spec_id = s.id
                  left join modeling_dimension_definition d
                    on d.tenant_id = m.tenant_id and d.id = m.dimension_definition_id
                 where s.tenant_id = ?
                   and s.contract_version = 2
                   and s.model_type = 'DIMENSION'
                   and s.dimension_definition_id is null
                 order by s.id
                """ + lock,
                (row, rowNumber) -> legacyDimension(row.getObject("id", UUID.class), row.getInt("revision"), row.getString("current_checksum"), row.getObject("domain_id", UUID.class), row.getString("name"), row.getString("description"), row.getString("dimension_profile"), mapped(row.getObject("mapped_definition_id", UUID.class), row.getObject("mapped_domain_id", UUID.class), row.getObject("mapped_definition_revision", Integer.class), row.getString("mapped_status"))),
                tenantId
            );
        }

        @Override
        public List<DefinitionRef> findDefinitionsByName(String tenantId, String name, boolean lockDefinitions) {
            String lock = lockDefinitions ? " for share of d" : "";
            return jdbc.query(
                """
                select d.id, d.domain_id, d.revision, d.status, d.definition,
                       d.hierarchies_json::text as hierarchies_json, d.reuse_scope
                  from modeling_dimension_definition d
                 where d.tenant_id = ? and lower(d.name) = lower(?)
                 order by d.revision desc, d.id
                """ + lock,
                (row, rowNumber) -> new DefinitionRef(
                    row.getObject("id", UUID.class),
                    row.getObject("domain_id", UUID.class),
                    row.getInt("revision"),
                    Status.valueOf(row.getString("status")),
                    row.getString("definition"),
                    hierarchies(row.getString("hierarchies_json")),
                    ReuseScope.valueOf(row.getString("reuse_scope"))
                ),
                tenantId,
                name
            );
        }

        @Override
        public MigrationBatch registerDryRun(String tenantId, DryRunReport report, String actorId) {
            insertDryRun(tenantId, report, actorId);
            MigrationBatch canonical = findBatch(tenantId, report.batchId(), true)
                .orElseThrow(() ->
                    new IllegalStateException("DIMENSION_MIGRATION_BATCH_INVALID: dry-run audit row is missing")
                );
            if (!Objects.equals(canonical.checksum(), report.checksum()) || canonical.status() != MigrationStatus.ROLLED_BACK) {
                return canonical;
            }
            java.util.Optional<MigrationBatch> activeAttempt = findActiveBatchByChecksum(
                tenantId,
                report.checksum()
            );
            if (activeAttempt.isPresent()) {
                return activeAttempt.orElseThrow();
            }

            long retryAttempt = nextRetryAttempt(tenantId, report.checksum());
            while (true) {
                String retryBatchId = report.batchId() + "-retry-" + retryAttempt;
                DryRunReport retryReport = report.withBatchId(retryBatchId);
                if (insertDryRun(tenantId, retryReport, actorId)) {
                    return findBatch(tenantId, retryBatchId, false)
                        .orElseThrow(() ->
                            new IllegalStateException(
                                "DIMENSION_MIGRATION_BATCH_INVALID: retry dry-run audit row is missing"
                            )
                        );
                }
                retryAttempt++;
            }
        }

        private boolean insertDryRun(String tenantId, DryRunReport report, String actorId) {
            Timestamp now = Timestamp.from(Instant.now());
            return jdbc.update(
                """
                insert into modeling_dimension_definition_migration_batch (
                    tenant_id, migration_batch_id, migration_checksum, status, dry_run_json,
                    created_by, created_date, last_modified_by, last_modified_date
                ) values (?, ?, ?, 'DRY_RUN', cast(? as jsonb), ?, ?, ?, ?)
                on conflict (tenant_id, migration_batch_id) do nothing
                """,
                tenantId,
                report.batchId(),
                report.checksum(),
                json(report),
                actorId,
                now,
                actorId,
                now
            ) == 1;
        }

        private java.util.Optional<MigrationBatch> findActiveBatchByChecksum(
            String tenantId,
            String checksum
        ) {
            return jdbc
                .query(
                    """
                    select migration_batch_id, migration_checksum, status,
                           execution_json::text as execution_json,
                           rollback_json::text as rollback_json
                      from modeling_dimension_definition_migration_batch
                     where tenant_id = ?
                       and migration_checksum = ?
                       and status in ('DRY_RUN', 'EXECUTED')
                     order by last_modified_date desc, migration_batch_id desc
                     limit 1
                     for update
                    """,
                    (row, rowNumber) ->
                        new MigrationBatch(
                            row.getString("migration_batch_id"),
                            row.getString("migration_checksum"),
                            MigrationStatus.valueOf(row.getString("status")),
                            read(row.getString("execution_json"), MigrationExecution.class),
                            read(row.getString("rollback_json"), MigrationRollback.class)
                        ),
                    tenantId,
                    checksum
                )
                .stream()
                .findFirst();
        }

        private long nextRetryAttempt(String tenantId, String checksum) {
            Long attempts = jdbc.queryForObject(
                """
                select count(*)
                  from modeling_dimension_definition_migration_batch
                 where tenant_id = ? and migration_checksum = ?
                """,
                Long.class,
                tenantId,
                checksum
            );
            return (attempts == null ? 0 : attempts) + 1;
        }

        @Override
        public java.util.Optional<MigrationBatch> lockBatch(String tenantId, String batchId) {
            return findBatch(tenantId, batchId, true);
        }

        @Override
        public void completeExecution(String tenantId, MigrationExecution execution, String actorId) {
            int updated = jdbc.update(
                """
                update modeling_dimension_definition_migration_batch
                   set status = 'EXECUTED', execution_json = cast(? as jsonb),
                       last_modified_by = ?, last_modified_date = ?
                 where tenant_id = ?
                   and migration_batch_id = ?
                   and migration_checksum = ?
                   and status = 'DRY_RUN'
                """,
                json(execution),
                actorId,
                Timestamp.from(Instant.now()),
                tenantId,
                execution.batchId(),
                execution.checksum()
            );
            if (updated != 1) {
                throw new IllegalStateException(
                    "DIMENSION_MIGRATION_BATCH_STATE_CONFLICT: dry-run batch could not become executed"
                );
            }
        }

        @Override
        public boolean insertMapping(
            String tenantId,
            UUID legacyModelSpecId,
            DefinitionRef definition,
            String batchId,
            String migrationChecksum,
            Classification classification,
            boolean definitionCreatedByBatch,
            String actorId
        ) {
            return jdbc.update(
                """
                insert into modeling_dimension_definition_legacy_map (
                    id, tenant_id, legacy_model_spec_id, dimension_definition_id,
                    dimension_definition_revision, migration_batch_id, migration_checksum,
                    classification, definition_created_by_batch, created_by, created_date
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (tenant_id, legacy_model_spec_id) do nothing
                """,
                UUID.randomUUID(),
                tenantId,
                legacyModelSpecId,
                definition.id(),
                definition.revision(),
                batchId,
                migrationChecksum,
                classification.name(),
                definitionCreatedByBatch,
                actorId,
                Timestamp.from(Instant.now())
            ) == 1;
        }

        @Override
        public List<RollbackMapping> loadRollbackMappings(String tenantId, String batchId) {
            return jdbc.query(
                """
                select m.legacy_model_spec_id, m.dimension_definition_id,
                       m.dimension_definition_revision,
                       s.domain_id as source_domain_id, d.domain_id as definition_domain_id,
                       m.migration_checksum, m.definition_created_by_batch
                  from modeling_dimension_definition_legacy_map m
                  join modeling_model_spec s
                    on s.tenant_id = m.tenant_id and s.id = m.legacy_model_spec_id
                  join modeling_dimension_definition d
                    on d.tenant_id = m.tenant_id and d.id = m.dimension_definition_id
                 where m.tenant_id = ? and m.migration_batch_id = ?
                 order by m.legacy_model_spec_id
                 for update of m
                """,
                (row, rowNumber) ->
                    new RollbackMapping(
                        row.getObject("legacy_model_spec_id", UUID.class),
                        row.getObject("dimension_definition_id", UUID.class),
                        row.getInt("dimension_definition_revision"),
                        row.getObject("source_domain_id", UUID.class),
                        row.getObject("definition_domain_id", UUID.class),
                        row.getString("migration_checksum"),
                        row.getBoolean("definition_created_by_batch")
                    ),
                tenantId,
                batchId
            );
        }

        @Override
        public long deleteMappings(String tenantId, String batchId, String migrationChecksum) {
            return jdbc.update(
                """
                delete from modeling_dimension_definition_legacy_map
                 where tenant_id = ? and migration_batch_id = ? and migration_checksum = ?
                """,
                tenantId,
                batchId,
                migrationChecksum
            );
        }

        @Override
        public long deleteUnusedMigrationDefinitions(
            String tenantId,
            Map<UUID, Integer> definitionRevisions
        ) {
            long deleted = 0;
            for (Map.Entry<UUID, Integer> candidate : definitionRevisions.entrySet()) {
                UUID definitionId = candidate.getKey();
                Boolean used = jdbc.queryForObject(
                    """
                    select exists (
                        select 1
                          from modeling_model_spec s
                         where s.tenant_id = ? and s.dimension_definition_id = ?
                        union all
                        select 1
                          from modeling_model_spec_revision r
                         where r.tenant_id = ? and r.dimension_definition_id = ?
                        union all
                        select 1
                          from modeling_dimension_definition_legacy_map m
                         where m.tenant_id = ? and m.dimension_definition_id = ?
                    )
                    """,
                    Boolean.class,
                    tenantId,
                    definitionId,
                    tenantId,
                    definitionId,
                    tenantId,
                    definitionId
                );
                if (Boolean.TRUE.equals(used)) continue;
                jdbc.execute(
                    "set constraints fk_dimension_definition_revision_head, fk_dimension_definition_current_revision deferred"
                );
                int headDeleted = jdbc.update(
                    """
                    delete from modeling_dimension_definition
                     where tenant_id = ?
                       and id = ?
                       and revision = ?
                       and idempotency_key like 'dimension-migration:%'
                    """,
                    tenantId,
                    definitionId,
                    candidate.getValue()
                );
                if (headDeleted == 0) continue;
                jdbc.update(
                    """
                    delete from modeling_dimension_definition_revision
                     where tenant_id = ? and dimension_definition_id = ?
                    """,
                    tenantId,
                    definitionId
                );
                deleted++;
            }
            return deleted;
        }

        @Override
        public void completeRollback(String tenantId, MigrationRollback rollback, String actorId) {
            int updated = jdbc.update(
                """
                update modeling_dimension_definition_migration_batch
                   set status = 'ROLLED_BACK', rollback_json = cast(? as jsonb),
                       last_modified_by = ?, last_modified_date = ?
                 where tenant_id = ?
                   and migration_batch_id = ?
                   and migration_checksum = ?
                   and status = 'EXECUTED'
                """,
                json(rollback),
                actorId,
                Timestamp.from(Instant.now()),
                tenantId,
                rollback.batchId(),
                rollback.checksum()
            );
            if (updated != 1) {
                throw new IllegalStateException(
                    "DIMENSION_MIGRATION_BATCH_STATE_CONFLICT: executed batch could not become rolled back"
                );
            }
        }

        private java.util.Optional<MigrationBatch> findBatch(
            String tenantId,
            String batchId,
            boolean lock
        ) {
            String lockClause = lock ? " for update" : "";
            return jdbc
                .query(
                    """
                    select migration_batch_id, migration_checksum, status,
                           execution_json::text as execution_json,
                           rollback_json::text as rollback_json
                      from modeling_dimension_definition_migration_batch
                     where tenant_id = ? and migration_batch_id = ?
                    """ + lockClause,
                    (row, rowNumber) ->
                        new MigrationBatch(
                            row.getString("migration_batch_id"),
                            row.getString("migration_checksum"),
                            MigrationStatus.valueOf(row.getString("status")),
                            read(row.getString("execution_json"), MigrationExecution.class),
                            read(row.getString("rollback_json"), MigrationRollback.class)
                        ),
                    tenantId,
                    batchId
                )
                .stream()
                .findFirst();
        }

        private String json(Object value) {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (Exception exception) {
                throw new IllegalStateException("Migration audit evidence cannot be serialized", exception);
            }
        }

        private <T> T read(String value, Class<T> type) {
            if (value == null || value.isBlank()) return null;
            try {
                return objectMapper.readValue(value, type);
            } catch (Exception exception) {
                throw new IllegalStateException("Migration audit evidence cannot be read", exception);
            }
        }

        private LegacyDimension legacyDimension(
            UUID id,
            int revision,
            String checksum,
            UUID domainId,
            String name,
            String definition,
            String profile,
            DefinitionRef mapping
        ) {
            try {
                ProfileProjection projection = profile(profile);
                boolean valid = hasText(name) && hasText(definition) && projection.valid();
                return new LegacyDimension(id, revision, checksum, domainId, name, definition, projection.hierarchies(), projection.reuseScope(), valid, mapping);
            } catch (RuntimeException exception) {
                return new LegacyDimension(id, revision, checksum, domainId, name, definition, List.of(), ReuseScope.DOMAIN, false, mapping);
            }
        }

        private List<HierarchySemantic> hierarchies(String value) {
            if (value == null || value.isBlank()) return List.of();
            try {
                List<HierarchySemantic> result = objectMapper
                    .readerForListOf(HierarchySemantic.class)
                    .readValue(value);
                return List.copyOf(result);
            } catch (Exception exception) {
                throw new IllegalStateException("Stored dimension definition hierarchy payload is invalid", exception);
            }
        }

        private ProfileProjection profile(String value) {
            if (value == null || value.isBlank()) return new ProfileProjection(List.of(), ReuseScope.DOMAIN, true);
            try {
                JsonNode profile = objectMapper.readTree(value);
                if (!profile.isObject()) return new ProfileProjection(List.of(), ReuseScope.DOMAIN, false);
                ReuseScope scope = profile.hasNonNull("reuseScope")
                    ? ReuseScope.valueOf(profile.get("reuseScope").asText().toUpperCase(Locale.ROOT))
                    : ReuseScope.DOMAIN;
                JsonNode hierarchies = profile.get("hierarchies");
                if (hierarchies == null || hierarchies.isNull()) return new ProfileProjection(List.of(), scope, true);
                if (!hierarchies.isArray()) return new ProfileProjection(List.of(), scope, false);
                List<HierarchySemantic> projected = new java.util.ArrayList<>();
                for (int hierarchyIndex = 0; hierarchyIndex < hierarchies.size(); hierarchyIndex++) {
                    JsonNode hierarchy = hierarchies.get(hierarchyIndex);
                    if (
                        !hierarchy.isObject() ||
                        !hierarchy.path("code").isTextual() ||
                        !hierarchy.path("name").isTextual() ||
                        !hasText(hierarchy.path("code").asText()) ||
                        !hasText(hierarchy.path("name").asText())
                    ) {
                        return new ProfileProjection(List.of(), scope, false);
                    }
                    JsonNode levels = hierarchy.get("levels");
                    if (levels == null || !levels.isArray() || levels.size() == 0) return new ProfileProjection(List.of(), scope, false);
                    List<HierarchyLevelSemantic> projectedLevels = new java.util.ArrayList<>();
                    for (int levelIndex = 0; levelIndex < levels.size(); levelIndex++) {
                        JsonNode level = levels.get(levelIndex);
                        JsonNode fieldName = level == null ? null : level.get("fieldName");
                        JsonNode order = level == null ? null : level.get("order");
                        if (fieldName == null || !fieldName.isTextual() || !hasText(fieldName.asText()) || order == null || !order.isIntegralNumber() || !order.canConvertToInt()) {
                            return new ProfileProjection(List.of(), scope, false);
                        }
                        String label = fieldName.asText().trim();
                        projectedLevels.add(new HierarchyLevelSemantic(semanticCode(label, "LEVEL_" + (levelIndex + 1)), label, order.asInt()));
                    }
                    projected.add(
                        new HierarchySemantic(
                            semanticCode(hierarchy.get("code").asText(), "HIERARCHY_" + (hierarchyIndex + 1)),
                            hierarchy.get("name").asText().trim(),
                            projectedLevels
                        )
                    );
                }
                return new ProfileProjection(projected, scope, validHierarchies(projected));
            } catch (Exception exception) {
                return new ProfileProjection(List.of(), ReuseScope.DOMAIN, false);
            }
        }

        private static DefinitionRef mapped(UUID id, UUID domainId, Integer revision, String status) {
            if (id == null || domainId == null || revision == null || status == null) return null;
            return new DefinitionRef(id, domainId, revision, Status.valueOf(status));
        }
    }

    private record ProfileProjection(List<HierarchySemantic> hierarchies, ReuseScope reuseScope, boolean valid) {}

    private static boolean validHierarchies(List<HierarchySemantic> hierarchies) {
        if (hierarchies == null) return false;
        LinkedHashSet<String> hierarchyCodes = new LinkedHashSet<>();
        for (HierarchySemantic hierarchy : hierarchies) {
            if (hierarchy == null || !hasText(hierarchy.code()) || !hasText(hierarchy.name()) || hierarchy.levels() == null || hierarchy.levels().isEmpty() || !hierarchyCodes.add(hierarchy.code())) return false;
            LinkedHashSet<String> levelCodes = new LinkedHashSet<>();
            LinkedHashSet<Integer> orders = new LinkedHashSet<>();
            for (HierarchyLevelSemantic level : hierarchy.levels()) {
                if (level == null || !hasText(level.code()) || !hasText(level.name()) || level.order() < 1 || !levelCodes.add(level.code()) || !orders.add(level.order())) return false;
            }
            for (int order = 1; order <= hierarchy.levels().size(); order++) {
                if (!orders.contains(order)) return false;
            }
        }
        return true;
    }

    private static String semanticCode(String value, String fallback) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        normalized = normalized.replaceAll("^_+|_+$", "");
        if (normalized.isBlank() || !Character.isLetter(normalized.charAt(0))) normalized = fallback;
        if (normalized.length() > 64) normalized = normalized.substring(0, 64);
        return normalized;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static final class MigrationAccessException extends RuntimeException {
        public MigrationAccessException(String message) {
            super(message);
        }
    }
}
