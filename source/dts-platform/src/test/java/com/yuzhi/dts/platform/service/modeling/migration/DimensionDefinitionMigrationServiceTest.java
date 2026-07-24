package com.yuzhi.dts.platform.service.modeling.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.ReuseScope;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.Status;
import com.yuzhi.dts.platform.service.modeling.ModelSpecDomainReadAccessPort;
import com.yuzhi.dts.platform.service.modeling.migration.DimensionDefinitionMigrationService.Classification;
import com.yuzhi.dts.platform.service.modeling.migration.DimensionDefinitionMigrationService.DefinitionCreateResult;
import com.yuzhi.dts.platform.service.modeling.migration.DimensionDefinitionMigrationService.DefinitionFactory;
import com.yuzhi.dts.platform.service.modeling.migration.DimensionDefinitionMigrationService.DefinitionRef;
import com.yuzhi.dts.platform.service.modeling.migration.DimensionDefinitionMigrationService.LegacyDimension;
import com.yuzhi.dts.platform.service.modeling.migration.DimensionDefinitionMigrationService.MigrationBatch;
import com.yuzhi.dts.platform.service.modeling.migration.DimensionDefinitionMigrationService.MigrationExecution;
import com.yuzhi.dts.platform.service.modeling.migration.DimensionDefinitionMigrationService.MigrationRollback;
import com.yuzhi.dts.platform.service.modeling.migration.DimensionDefinitionMigrationService.MigrationStatus;
import com.yuzhi.dts.platform.service.modeling.migration.DimensionDefinitionMigrationService.MigrationStore;
import com.yuzhi.dts.platform.service.modeling.migration.DimensionDefinitionMigrationService.RollbackMapping;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DimensionDefinitionMigrationServiceTest {

    private static final String TENANT = "tenant-a";
    private static final UUID DOMAIN_A = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID DOMAIN_B = UUID.fromString("10000000-0000-0000-0000-000000000002");

    @Test
    void classifiesVisibleStatesWithDeterministicTotalsReasonsChecksumAndBatchId() {
        FakeStore store = new FakeStore();
        LegacyDimension automatic = source(1, DOMAIN_A, "Customer", true, null);
        LegacyDimension reusable = source(2, DOMAIN_A, "Region", true, null);
        LegacyDimension conflict = source(3, DOMAIN_A, "Calendar", true, null);
        LegacyDimension orphan = source(4, null, "Orphan", true, null);
        LegacyDimension invalid = source(5, DOMAIN_A, "Invalid", false, null);
        LegacyDimension mapped = source(6, DOMAIN_A, "Mapped", true, definition(90, DOMAIN_A, 1));
        store.sources.addAll(List.of(automatic, reusable, conflict, orphan, invalid, mapped));
        store.definitions.put(
            "region",
            List.of(definition(20, DOMAIN_A, 2, "Region business definition"))
        );
        store.definitions.put(
            "calendar",
            List.of(definition(30, DOMAIN_A, 1, "Different calendar semantics"))
        );

        DimensionDefinitionMigrationService service = service(store, new FakeDefinitions(store));
        DimensionDefinitionMigrationService.DryRunReport first = service.dryRun(TENANT, "alice");
        DimensionDefinitionMigrationService.DryRunReport second = service.dryRun(TENANT, "alice");

        assertThat(first.batchId()).isEqualTo("dimension-definitions-" + first.checksum().substring(0, 16));
        assertThat(first.checksum()).isEqualTo(second.checksum());
        assertThat(first.total()).isEqualTo(5);
        assertThat(first.automatic()).isEqualTo(1);
        assertThat(first.reusable()).isEqualTo(1);
        assertThat(first.conflicts()).isEqualTo(2);
        assertThat(first.orphans()).isZero();
        assertThat(first.classificationCounts()).containsEntry(Classification.ALREADY_MIGRATED.name(), 1L);
        assertThat(first.decisions()).extracting(item -> item.classification()).containsExactly(
            Classification.AUTO_MIGRATABLE,
            Classification.REUSABLE_EXISTING_DEFINITION,
            Classification.NAME_DOMAIN_CONFLICT,
            Classification.INVALID_LEGACY_PAYLOAD,
            Classification.ALREADY_MIGRATED
        );
        assertThat(first.decisions().get(2).reasons()).containsExactly("NAME_EXISTS_WITH_DIFFERENT_DEFINITION");
    }

    @Test
    void rejectsExecuteWhenAnyLockedSourceRevisionDriftsAfterDryRun() {
        FakeStore store = new FakeStore();
        LegacyDimension original = source(1, DOMAIN_A, "Customer", true, null);
        store.sources.add(original);
        FakeDefinitions definitions = new FakeDefinitions(store);
        DimensionDefinitionMigrationService service = service(store, definitions);
        DimensionDefinitionMigrationService.DryRunReport report = service.dryRun(TENANT, "alice");
        store.sources.set(0, new LegacyDimension(
            original.legacyModelSpecId(),
            original.revision() + 1,
            "b".repeat(64),
            original.domainId(),
            original.name(),
            original.definition(),
            original.hierarchies(),
            original.reuseScope(),
            true,
            null
        ));

        assertThatThrownBy(() -> service.execute(TENANT, report.batchId(), report.checksum(), "alice"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("DIMENSION_MIGRATION_BATCH_STALE");
        assertThat(definitions.created).isZero();
        assertThat(store.mappings).isEmpty();
    }

    @Test
    void replaysAnExecutedBatchWithoutCreatingAnotherDefinitionOrMapping() {
        FakeStore store = new FakeStore();
        LegacyDimension original = source(1, DOMAIN_A, "Customer", true, null);
        store.sources.add(original);
        FakeDefinitions definitions = new FakeDefinitions(store);
        DimensionDefinitionMigrationService service = service(store, definitions);
        DimensionDefinitionMigrationService.DryRunReport report = service.dryRun(TENANT, "alice");

        DimensionDefinitionMigrationService.MigrationExecution first = service.execute(
            TENANT,
            report.batchId(),
            report.checksum(),
            "alice"
        );
        DimensionDefinitionMigrationService.MigrationExecution replay = service.execute(
            TENANT,
            report.batchId(),
            report.checksum(),
            "alice"
        );

        assertThat(first.mappingsCreated()).isEqualTo(1);
        assertThat(first.definitionsCreated()).isEqualTo(1);
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.mappingsCreated()).isEqualTo(1);
        assertThat(definitions.created).isEqualTo(1);
        assertThat(store.mappings).hasSize(1);
        assertThat(store.sources.get(0).revision()).isEqualTo(original.revision());
        assertThat(store.sources.get(0).sourceChecksum()).isEqualTo(original.sourceChecksum());
        assertThat(service.dryRun(TENANT, "alice").checksum()).isNotEqualTo(report.checksum());
        assertThat(service.dryRun(TENANT, "alice").decisions())
            .extracting(item -> item.classification())
            .containsExactly(Classification.ALREADY_MIGRATED);
    }

    @Test
    void rejectsExecuteWhenTheSelectedReusableDefinitionChangesAfterDryRun() {
        FakeStore store = new FakeStore();
        store.sources.add(source(1, DOMAIN_A, "Customer", true, null));
        store.definitions.put(
            "customer",
            List.of(definition(10, DOMAIN_A, 1, "Customer business definition"))
        );
        DimensionDefinitionMigrationService service = service(store, new FakeDefinitions(store));
        DimensionDefinitionMigrationService.DryRunReport report = service.dryRun(TENANT, "alice");

        store.definitions.put(
            "customer",
            List.of(definition(10, DOMAIN_A, 2, "Customer business definition"))
        );

        assertThatThrownBy(() -> service.execute(TENANT, report.batchId(), report.checksum(), "alice"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("DIMENSION_MIGRATION_BATCH_STALE");
        assertThat(store.mappings).isEmpty();
    }

    @Test
    void filtersUnreadableDomainsAndFailsClosedWhenVisibilityCannotBeResolved() {
        FakeStore store = new FakeStore();
        store.sources.addAll(List.of(source(1, DOMAIN_A, "Customer", true, null), source(2, DOMAIN_B, "Payroll", true, null)));
        FakeDefinitions definitions = new FakeDefinitions(store);

        DimensionDefinitionMigrationService visibleOnlyA = service(store, definitions, Set.of(DOMAIN_A));
        assertThat(visibleOnlyA.dryRun(TENANT, "alice").decisions())
            .extracting(item -> item.source().domainId())
            .containsExactly(DOMAIN_A);

        DimensionDefinitionMigrationService inaccessible = new DimensionDefinitionMigrationService(
            store,
            definitions,
            new ModelSpecDomainReadAccessPort() {
                @Override
                public boolean canRead(UUID domainId) {
                    return false;
                }

                @Override
                public Set<UUID> visibleDomainIds() {
                    throw new IllegalStateException("security context unavailable");
                }
            },
            TENANT
        );
        assertThatThrownBy(() -> inaccessible.dryRun(TENANT, "alice"))
            .isInstanceOf(DimensionDefinitionMigrationService.MigrationAccessException.class);
    }

    @Test
    void rollsBackOnlyBatchMappingsAndUnusedDefinitionsAndReplaysTheAuditResult() {
        FakeStore store = new FakeStore();
        store.sources.addAll(
            List.of(
                source(1, DOMAIN_A, "Customer", true, null),
                source(2, DOMAIN_A, "Region", true, null)
            )
        );
        store.definitions.put(
            "region",
            List.of(definition(20, DOMAIN_A, 1, "Region business definition"))
        );
        FakeDefinitions definitions = new FakeDefinitions(store);
        DimensionDefinitionMigrationService service = service(store, definitions);
        DimensionDefinitionMigrationService.DryRunReport report = service.dryRun(TENANT, "alice");
        MigrationExecution execution = service.execute(
            TENANT,
            report.batchId(),
            report.checksum(),
            "alice"
        );

        MigrationRollback first = service.rollback(
            TENANT,
            report.batchId(),
            report.checksum(),
            "alice"
        );
        MigrationRollback replay = service.rollback(
            TENANT,
            report.batchId(),
            report.checksum(),
            "alice"
        );

        assertThat(execution.status()).isEqualTo(MigrationStatus.EXECUTED);
        assertThat(first.status()).isEqualTo(MigrationStatus.ROLLED_BACK);
        assertThat(first.mappingsDeleted()).isEqualTo(2);
        assertThat(first.definitionsDeleted()).isEqualTo(1);
        assertThat(first.definitionsRetained()).isZero();
        assertThat(replay.replayed()).isTrue();
        assertThat(store.mappings).isEmpty();
        assertThat(store.definitions.get("region")).hasSize(1);
    }

    @Test
    void createsAndExecutesAFreshAttemptAfterAnExactRollback() {
        FakeStore store = new FakeStore();
        store.sources.add(source(1, DOMAIN_A, "Customer", true, null));
        FakeDefinitions definitions = new FakeDefinitions(store);
        DimensionDefinitionMigrationService service = service(store, definitions);
        DimensionDefinitionMigrationService.DryRunReport original = service.dryRun(TENANT, "alice");
        service.execute(TENANT, original.batchId(), original.checksum(), "alice");
        service.rollback(TENANT, original.batchId(), original.checksum(), "alice");

        DimensionDefinitionMigrationService.DryRunReport retry = service.dryRun(TENANT, "alice");
        DimensionDefinitionMigrationService.DryRunReport retryReplay = service.dryRun(TENANT, "alice");
        MigrationExecution retried = service.execute(
            TENANT,
            retry.batchId(),
            retry.checksum(),
            "alice"
        );

        assertThat(retry.batchId()).isEqualTo(original.batchId() + "-retry-2");
        assertThat(retryReplay.batchId()).isEqualTo(retry.batchId());
        assertThat(retry.checksum()).isEqualTo(original.checksum());
        assertThat(retry.status()).isEqualTo(MigrationStatus.DRY_RUN);
        assertThat(retried.batchId()).isEqualTo(retry.batchId());
        assertThat(retried.status()).isEqualTo(MigrationStatus.EXECUTED);
        assertThat(retried.mappingsCreated()).isEqualTo(1);
        assertThat(retried.definitionsCreated()).isEqualTo(1);
    }

    @Test
    void blocksRollbackWhenAnyMappedDomainIsNoLongerVisible() {
        FakeStore store = new FakeStore();
        store.sources.add(source(1, DOMAIN_A, "Customer", true, null));
        FakeDefinitions definitions = new FakeDefinitions(store);
        DimensionDefinitionMigrationService writer = service(store, definitions);
        DimensionDefinitionMigrationService.DryRunReport report = writer.dryRun(TENANT, "alice");
        writer.execute(TENANT, report.batchId(), report.checksum(), "alice");

        DimensionDefinitionMigrationService hidden = service(store, definitions, Set.of(DOMAIN_B));

        assertThatThrownBy(() ->
            hidden.rollback(TENANT, report.batchId(), report.checksum(), "bob")
        )
            .isInstanceOf(DimensionDefinitionMigrationService.MigrationAccessException.class)
            .hasMessageContaining("not visible");
        assertThat(store.mappings).hasSize(1);
    }

    @Test
    void retainsABatchCreatedDefinitionWhenAnotherConsumerUsesIt() {
        FakeStore store = new FakeStore();
        LegacyDimension source = source(1, DOMAIN_A, "Customer", true, null);
        store.sources.add(source);
        FakeDefinitions definitions = new FakeDefinitions(store);
        DimensionDefinitionMigrationService service = service(store, definitions);
        DimensionDefinitionMigrationService.DryRunReport report = service.dryRun(TENANT, "alice");
        service.execute(TENANT, report.batchId(), report.checksum(), "alice");
        UUID definitionId = store.mappings.get(source.legacyModelSpecId()).id();
        store.inUseDefinitions.add(definitionId);

        MigrationRollback rollback = service.rollback(
            TENANT,
            report.batchId(),
            report.checksum(),
            "alice"
        );

        assertThat(rollback.mappingsDeleted()).isEqualTo(1);
        assertThat(rollback.definitionsDeleted()).isZero();
        assertThat(rollback.definitionsRetained()).isEqualTo(1);
        assertThat(store.definitions.values().stream().flatMap(List::stream).toList())
            .extracting(DefinitionRef::id)
            .contains(definitionId);
    }

    @Test
    void refusesToMergeSameDomainNamesWhoseLegacySemanticsDiffer() {
        FakeStore store = new FakeStore();
        LegacyDimension first = source(1, DOMAIN_A, "Customer", true, null);
        LegacyDimension second = new LegacyDimension(
            UUID.fromString("20000000-0000-0000-0000-000000000002"),
            1,
            "b".repeat(64),
            DOMAIN_A,
            "customer",
            "Different customer semantics",
            List.of(),
            ReuseScope.DOMAIN,
            true,
            null
        );
        store.sources.addAll(List.of(first, second));

        DimensionDefinitionMigrationService.DryRunReport report = service(
            store,
            new FakeDefinitions(store)
        ).dryRun(TENANT, "alice");

        assertThat(report.automatic()).isZero();
        assertThat(report.conflicts()).isEqualTo(2);
        assertThat(report.decisions())
            .allSatisfy(decision -> {
                assertThat(decision.classification()).isEqualTo(Classification.NAME_DOMAIN_CONFLICT);
                assertThat(decision.reasons()).containsExactly("LEGACY_NAME_CONTENT_CONFLICT");
            });
    }

    @Test
    void reusesOneBatchDefinitionOnlyWhenSameDomainLegacySemanticsAreEquivalent() {
        FakeStore store = new FakeStore();
        LegacyDimension first = source(1, DOMAIN_A, "Customer", true, null);
        LegacyDimension equivalent = new LegacyDimension(
            UUID.fromString("20000000-0000-0000-0000-000000000002"),
            1,
            "b".repeat(64),
            DOMAIN_A,
            "customer",
            first.definition(),
            first.hierarchies(),
            first.reuseScope(),
            true,
            null
        );
        store.sources.addAll(List.of(first, equivalent));
        FakeDefinitions definitions = new FakeDefinitions(store);
        DimensionDefinitionMigrationService service = service(store, definitions);
        DimensionDefinitionMigrationService.DryRunReport report = service.dryRun(TENANT, "alice");

        MigrationExecution execution = service.execute(
            TENANT,
            report.batchId(),
            report.checksum(),
            "alice"
        );

        assertThat(report.automatic()).isEqualTo(1);
        assertThat(report.reusable()).isEqualTo(1);
        assertThat(execution.definitionsCreated()).isEqualTo(1);
        assertThat(execution.definitionsReused()).isEqualTo(1);
        assertThat(execution.mappingsCreated()).isEqualTo(2);
        assertThat(store.mappings.values()).extracting(DefinitionRef::id).containsOnly(
            store.mappings.get(first.legacyModelSpecId()).id()
        );
    }

    @Test
    void allowsTheSameBusinessNameInAnotherDomainLikeTheDatabaseKeyDoes() {
        FakeStore store = new FakeStore();
        store.sources.add(source(1, DOMAIN_A, "Customer", true, null));
        store.definitions.put(
            "customer",
            List.of(definition(20, DOMAIN_B, 1, "Different domain semantics"))
        );

        DimensionDefinitionMigrationService.DryRunReport report = service(
            store,
            new FakeDefinitions(store)
        ).dryRun(TENANT, "alice");

        assertThat(report.automatic()).isEqualTo(1);
        assertThat(report.conflicts()).isZero();
        assertThat(report.decisions())
            .extracting(item -> item.classification())
            .containsExactly(Classification.AUTO_MIGRATABLE);
    }

    private static DimensionDefinitionMigrationService service(FakeStore store, FakeDefinitions definitions) {
        return service(store, definitions, Set.of(DOMAIN_A, DOMAIN_B));
    }

    private static DimensionDefinitionMigrationService service(FakeStore store, FakeDefinitions definitions, Set<UUID> visibleDomains) {
        return new DimensionDefinitionMigrationService(
            store,
            definitions,
            new ModelSpecDomainReadAccessPort() {
                @Override
                public boolean canRead(UUID domainId) {
                    return visibleDomains.contains(domainId);
                }

                @Override
                public Set<UUID> visibleDomainIds() {
                    return visibleDomains;
                }
            },
            TENANT
        );
    }

    private static LegacyDimension source(int sequence, UUID domain, String name, boolean valid, DefinitionRef mapping) {
        return new LegacyDimension(
            UUID.fromString("20000000-0000-0000-0000-00000000000" + sequence),
            1,
            ("a" + sequence).repeat(32),
            domain,
            name,
            name + " business definition",
            List.of(),
            ReuseScope.DOMAIN,
            valid,
            mapping
        );
    }

    private static DefinitionRef definition(int sequence, UUID domain, int revision) {
        return definition(sequence, domain, revision, null);
    }

    private static DefinitionRef definition(
        int sequence,
        UUID domain,
        int revision,
        String definition
    ) {
        return new DefinitionRef(
            UUID.fromString("30000000-0000-0000-0000-0000000000" + String.format("%02d", sequence)),
            domain,
            revision,
            Status.CURRENT,
            definition,
            List.of(),
            ReuseScope.DOMAIN
        );
    }

    private static final class FakeStore implements MigrationStore {

        private final List<LegacyDimension> sources = new ArrayList<>();
        private final Map<String, List<DefinitionRef>> definitions = new HashMap<>();
        private final Map<UUID, DefinitionRef> mappings = new HashMap<>();
        private final Map<UUID, MappingAudit> mappingAudits = new HashMap<>();
        private final Set<UUID> inUseDefinitions = new java.util.HashSet<>();
        private MigrationBatch batch;
        private long retryAttempt = 1;

        @Override
        public List<LegacyDimension> loadLegacyDimensions(String tenantId, boolean lockSources) {
            return sources
                .stream()
                .map(source -> new LegacyDimension(
                    source.legacyModelSpecId(),
                    source.revision(),
                    source.sourceChecksum(),
                    source.domainId(),
                    source.name(),
                    source.definition(),
                    source.hierarchies(),
                    source.reuseScope(),
                    source.validPayload(),
                    mappings.getOrDefault(source.legacyModelSpecId(), source.mapping())
                ))
                .toList();
        }

        @Override
        public List<DefinitionRef> findDefinitionsByName(String tenantId, String name, boolean lockDefinitions) {
            return definitions.getOrDefault(name.toLowerCase(), List.of());
        }

        @Override
        public MigrationBatch registerDryRun(
            String tenantId,
            DimensionDefinitionMigrationService.DryRunReport report,
            String actorId
        ) {
            if (batch == null || !batch.checksum().equals(report.checksum())) {
                retryAttempt = 1;
                batch = new MigrationBatch(
                    report.batchId(),
                    report.checksum(),
                    MigrationStatus.DRY_RUN,
                    null,
                    null
                );
            } else if (batch.status() == MigrationStatus.ROLLED_BACK) {
                retryAttempt++;
                batch = new MigrationBatch(
                    report.batchId() + "-retry-" + retryAttempt,
                    report.checksum(),
                    MigrationStatus.DRY_RUN,
                    null,
                    null
                );
            }
            return batch;
        }

        @Override
        public java.util.Optional<MigrationBatch> lockBatch(String tenantId, String batchId) {
            return batch == null || !batch.batchId().equals(batchId)
                ? java.util.Optional.empty()
                : java.util.Optional.of(batch);
        }

        @Override
        public void completeExecution(String tenantId, MigrationExecution execution, String actorId) {
            batch = new MigrationBatch(
                execution.batchId(),
                execution.checksum(),
                MigrationStatus.EXECUTED,
                execution,
                null
            );
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
            if (mappings.putIfAbsent(legacyModelSpecId, definition) != null) return false;
            mappingAudits.put(
                legacyModelSpecId,
                new MappingAudit(batchId, migrationChecksum, definitionCreatedByBatch)
            );
            return true;
        }

        @Override
        public List<RollbackMapping> loadRollbackMappings(String tenantId, String batchId) {
            return mappings
                .entrySet()
                .stream()
                .filter(entry -> batchId.equals(mappingAudits.get(entry.getKey()).batchId()))
                .map(entry -> {
                    LegacyDimension source = sources
                        .stream()
                        .filter(item -> item.legacyModelSpecId().equals(entry.getKey()))
                        .findFirst()
                        .orElseThrow();
                    MappingAudit audit = mappingAudits.get(entry.getKey());
                    return new RollbackMapping(
                        entry.getKey(),
                        entry.getValue().id(),
                        entry.getValue().revision(),
                        source.domainId(),
                        entry.getValue().domainId(),
                        audit.checksum(),
                        audit.definitionCreatedByBatch()
                    );
                })
                .toList();
        }

        @Override
        public long deleteMappings(String tenantId, String batchId, String migrationChecksum) {
            List<UUID> ids = mappingAudits
                .entrySet()
                .stream()
                .filter(entry ->
                    batchId.equals(entry.getValue().batchId()) &&
                    migrationChecksum.equals(entry.getValue().checksum())
                )
                .map(Map.Entry::getKey)
                .toList();
            ids.forEach(id -> {
                mappings.remove(id);
                mappingAudits.remove(id);
            });
            return ids.size();
        }

        @Override
        public long deleteUnusedMigrationDefinitions(
            String tenantId,
            Map<UUID, Integer> definitionRevisions
        ) {
            long deleted = 0;
            for (UUID definitionId : definitionRevisions.keySet()) {
                if (inUseDefinitions.contains(definitionId)) continue;
                boolean removed = definitions
                    .entrySet()
                    .removeIf(entry -> entry.getValue().stream().anyMatch(item -> item.id().equals(definitionId)));
                if (removed) deleted++;
            }
            return deleted;
        }

        @Override
        public void completeRollback(String tenantId, MigrationRollback rollback, String actorId) {
            batch = new MigrationBatch(
                rollback.batchId(),
                rollback.checksum(),
                MigrationStatus.ROLLED_BACK,
                batch.execution(),
                rollback
            );
        }

        private record MappingAudit(
            String batchId,
            String checksum,
            boolean definitionCreatedByBatch
        ) {}
    }

    private static final class FakeDefinitions implements DefinitionFactory {

        private final FakeStore store;
        private long created;

        private FakeDefinitions(FakeStore store) {
            this.store = store;
        }

        @Override
        public DefinitionCreateResult create(String tenantId, String actorId, LegacyDimension source, String batchId) {
            List<DefinitionRef> existing = store.definitions.get(source.name().toLowerCase());
            if (existing != null && !existing.isEmpty()) return new DefinitionCreateResult(existing.get(0), true);
            created++;
            DefinitionRef createdDefinition = definition(70 + (int) created, source.domainId(), 1);
            store.definitions.put(source.name().toLowerCase(), List.of(createdDefinition));
            return new DefinitionCreateResult(createdDefinition, false);
        }
    }
}
