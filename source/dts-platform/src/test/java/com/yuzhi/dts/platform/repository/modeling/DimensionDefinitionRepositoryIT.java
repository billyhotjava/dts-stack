package com.yuzhi.dts.platform.repository.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuzhi.dts.platform.IntegrationTest;
import com.yuzhi.dts.platform.repository.modeling.DimensionDefinitionRepository.ExpectedVersion;
import com.yuzhi.dts.platform.repository.modeling.DimensionDefinitionRepository.StoredDimensionDefinition;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.CreateCommand;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.ReuseScope;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.Status;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.View;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
@Transactional
class DimensionDefinitionRepositoryIT {

    private static final String SYSTEM_CODE = "dim_00000000000000000000000000000001";

    @Autowired
    private DimensionDefinitionRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void atomicallyInsertsHeadAndRevisionAndAtomicallyAdvancesCas() {
        String tenant = tenant("atomic");
        UUID domainId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        CreateCommand command = command(domainId, "atomic-create");
        View first = view(definitionId, domainId, SYSTEM_CODE, "Customer", Status.DRAFT, 1, hash('a'), now);

        assertThat(repository.insert(tenant, "owner-1", command, first, hash('1'))).isEqualTo(1);
        assertLedgerCounts(tenant, definitionId, 1, 1);
        assertThat(repository.insert(tenant, "owner-1", command, first, hash('1'))).isZero();
        assertLedgerCounts(tenant, definitionId, 1, 1);

        StoredDimensionDefinition storedFirst = repository.findCurrent(tenant, definitionId).orElseThrow();
        assertThat(storedFirst.toView(0)).usingRecursiveComparison().isEqualTo(first);
        assertThat(repository.findByIdempotencyKey(tenant, command.idempotencyKey())).contains(storedFirst);
        assertThat(repository.listCurrent(tenant, domainId, Status.DRAFT)).containsExactly(storedFirst);

        View retired = view(
            definitionId,
            domainId,
            SYSTEM_CODE,
            "Customer retired",
            Status.RETIRED,
            2,
            hash('b'),
            now.plusSeconds(60)
        );
        assertThat(repository.compareAndSet(
            tenant,
            "owner-2",
            new ExpectedVersion(definitionId, 1, first.checksum()),
            retired
        )).isEqualTo(1);
        assertLedgerCounts(tenant, definitionId, 1, 2);
        assertThat(repository.compareAndSet(
            tenant,
            "owner-2",
            new ExpectedVersion(definitionId, 1, first.checksum()),
            retired
        )).isZero();

        assertThat(repository.findRevision(tenant, definitionId, 1))
            .get()
            .extracting(StoredDimensionDefinition::name, StoredDimensionDefinition::checksum)
            .containsExactly("Customer", first.checksum());
        assertThat(repository.findRevision(tenant, definitionId, 2))
            .get()
            .extracting(StoredDimensionDefinition::name, StoredDimensionDefinition::checksum)
            .containsExactly("Customer retired", retired.checksum());
        assertThat(repository.findCurrent("another-tenant", definitionId)).isEmpty();
    }

    @Test
    void rejectsCasWhenImmutableSystemCodeDomainOrCreatedAtChanges() {
        String tenant = tenant("immutable-cas");
        UUID domainId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-07-24T00:00:00Z");
        View first = view(definitionId, domainId, SYSTEM_CODE, "Customer", Status.DRAFT, 1, hash('a'), createdAt);
        inNewTransaction(() -> repository.insert(tenant, "owner-1", command(domainId, "immutable-cas"), first, hash('1')));
        ExpectedVersion expected = new ExpectedVersion(definitionId, 1, first.checksum());
        View replacement = view(
            definitionId,
            domainId,
            SYSTEM_CODE,
            "Customer current",
            Status.CURRENT,
            2,
            hash('b'),
            createdAt.plusSeconds(60)
        );

        assertThat(repository.compareAndSet(
            tenant,
            "owner-2",
            expected,
            withIdentity(replacement, "dim_00000000000000000000000000000002", domainId, createdAt)
        )).isZero();
        assertThat(repository.compareAndSet(
            tenant,
            "owner-2",
            expected,
            withIdentity(replacement, SYSTEM_CODE, UUID.randomUUID(), createdAt)
        )).isZero();
        assertThat(repository.compareAndSet(
            tenant,
            "owner-2",
            expected,
            withIdentity(replacement, SYSTEM_CODE, domainId, createdAt.plusSeconds(1))
        )).isZero();

        assertLedgerCounts(tenant, definitionId, 1, 1);
        assertThat(repository.findCurrent(tenant, definitionId)).get().extracting(StoredDimensionDefinition::revision).isEqualTo(1);
    }

    @Test
    void serializesConcurrentIdempotentInsertWithoutDuplicateLedgerRows() throws Exception {
        String tenant = tenant("concurrent-idempotency");
        UUID domainId = UUID.randomUUID();
        View first = view(
            UUID.randomUUID(),
            domainId,
            SYSTEM_CODE,
            "Customer",
            Status.DRAFT,
            1,
            hash('a'),
            Instant.parse("2026-07-24T00:00:00Z")
        );
        CreateCommand command = command(domainId, "concurrent-key");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> firstResult = executor.submit(() -> concurrentInsert(ready, start, tenant, command, first));
            Future<Integer> secondResult = executor.submit(() -> concurrentInsert(ready, start, tenant, command, first));
            ready.await();
            start.countDown();

            assertThat(List.of(firstResult.get(), secondResult.get())).containsExactlyInAnyOrder(0, 1);
        } finally {
            executor.shutdownNow();
        }
        assertLedgerCounts(tenant, first.id(), 1, 1);
    }

    @Test
    void storesAllDimensionAuditInstantsAsTimestampWithTimeZone() {
        assertThat(
            jdbcTemplate.queryForList(
                """
                select table_name, column_name, data_type
                  from information_schema.columns
                 where table_schema = 'public'
                   and (
                       (table_name = 'modeling_dimension_definition'
                        and column_name in ('created_date', 'last_modified_date'))
                       or
                       (table_name in (
                           'modeling_dimension_definition_revision',
                           'modeling_dimension_definition_legacy_map'
                        ) and column_name = 'created_date')
                   )
                 order by table_name, column_name
                """
            )
        )
            .hasSize(4)
            .allSatisfy(column -> assertThat(column.get("data_type")).isEqualTo("timestamp with time zone"));
    }

    @Test
    void exposesNoPublicUnguardedRevisionAppend() {
        assertThat(Arrays.stream(DimensionDefinitionRepository.class.getMethods()).map(method -> method.getName()))
            .doesNotContain("appendRevision");
    }

    @Test
    void rejectsWrongIdentityAndNonContiguousReplacementBeforeSql() {
        UUID domainId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        ExpectedVersion expected = new ExpectedVersion(definitionId, 1, hash('a'));

        assertThatThrownBy(() ->
            repository.compareAndSet(
                tenant("wrong-id"),
                "owner-1",
                expected,
                view(UUID.randomUUID(), domainId, SYSTEM_CODE, "Wrong", Status.DRAFT, 2, hash('b'), now)
            )
        ).hasRootCauseInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
            repository.compareAndSet(
                tenant("skip"),
                "owner-1",
                expected,
                view(definitionId, domainId, SYSTEM_CODE, "Skipped", Status.DRAFT, 3, hash('b'), now)
            )
        ).hasRootCauseInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
            repository.compareAndSet(
                tenant("backward"),
                "owner-1",
                expected,
                view(definitionId, domainId, SYSTEM_CODE, "Backward", Status.DRAFT, 1, hash('b'), now)
            )
        ).hasRootCauseInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
            repository.insert(
                tenant("initial-revision"),
                "owner-1",
                command(domainId, "initial-revision"),
                view(definitionId, domainId, SYSTEM_CODE, "Invalid initial", Status.DRAFT, 2, hash('b'), now),
                hash('1')
            )
        ).hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rollsBackHeadCasWhenImmutableReplacementRevisionConflicts() {
        String tenant = tenant("cas-conflict");
        UUID domainId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        View first = view(definitionId, domainId, SYSTEM_CODE, "Customer", Status.DRAFT, 1, hash('a'), now);
        inNewTransaction(() -> repository.insert(tenant, "owner-1", command(domainId, "cas-conflict"), first, hash('1')));
        inNewTransaction(() -> {
            insertRevision(tenant, view(
                definitionId,
                domainId,
                SYSTEM_CODE,
                "Conflicting revision",
                Status.RETIRED,
                2,
                hash('c'),
                now.plusSeconds(30)
            ));
            return null;
        });

        View replacement = view(
            definitionId,
            domainId,
            SYSTEM_CODE,
            "Expected replacement",
            Status.RETIRED,
            2,
            hash('b'),
            now.plusSeconds(60)
        );
        assertThatThrownBy(() ->
            inNewTransaction(() ->
                repository.compareAndSet(
                    tenant,
                    "owner-2",
                    new ExpectedVersion(definitionId, 1, first.checksum()),
                    replacement
                )
            )
        ).isInstanceOf(DataIntegrityViolationException.class);

        StoredDimensionDefinition stored = repository.findCurrent(tenant, definitionId).orElseThrow();
        assertThat(stored.revision()).isEqualTo(1);
        assertThat(stored.checksum()).isEqualTo(first.checksum());
    }

    @Test
    void rejectsIdempotencyReplayWhenRequestHashDiffers() {
        String tenant = tenant("idempotency-hash");
        UUID domainId = UUID.randomUUID();
        View first = view(UUID.randomUUID(), domainId, SYSTEM_CODE, "Customer", Status.DRAFT, 1, hash('a'), Instant.now());
        CreateCommand command = command(domainId, "same-key");
        inNewTransaction(() -> repository.insert(tenant, "owner-1", command, first, hash('1')));

        assertThatThrownBy(() ->
            inNewTransaction(() -> repository.insert(tenant, "owner-1", command, first, hash('2')))
        ).isInstanceOf(DataIntegrityViolationException.class);
        assertLedgerCounts(tenant, first.id(), 1, 1);
    }

    @Test
    void enforcesGeneratedSystemCodeUniquenessPerTenant() {
        UUID domainId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        String tenant = tenant("code");

        inNewTransaction(() ->
            repository.insert(
                tenant,
                "owner-1",
                command(domainId, "code-1"),
                view(UUID.randomUUID(), domainId, SYSTEM_CODE, "Customer", Status.DRAFT, 1, hash('c'), now),
                hash('2')
            )
        );
        inNewTransaction(() ->
            repository.insert(
                tenant + "-other",
                "owner-1",
                command(domainId, "code-2"),
                view(UUID.randomUUID(), domainId, SYSTEM_CODE, "Customer", Status.DRAFT, 1, hash('d'), now),
                hash('3')
            )
        );

        assertThatThrownBy(() ->
            inNewTransaction(() ->
                repository.insert(
                    tenant,
                    "owner-1",
                    command(domainId, "code-3"),
                    view(UUID.randomUUID(), domainId, SYSTEM_CODE, "Duplicate", Status.DRAFT, 1, hash('e'), now),
                    hash('4')
                )
            )
        ).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() ->
            inNewTransaction(() ->
                repository.insert(
                    tenant("invalid-code"),
                    "owner-1",
                    command(domainId, "invalid-code"),
                    view(UUID.randomUUID(), domainId, "CUSTOMER", "Invalid", Status.DRAFT, 1, hash('f'), now),
                    hash('5')
                )
            )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsCrossTenantMissingHalfNullAndNonDimensionHeadReferences() {
        String tenant = tenant("head-ref");
        UUID domainId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();
        View first = view(UUID.randomUUID(), domainId, SYSTEM_CODE, "Customer", Status.DRAFT, 1, hash('a'), Instant.now());
        definitionId = first.id();
        UUID finalDefinitionId = definitionId;
        inNewTransaction(() -> repository.insert(tenant, "owner-1", command(domainId, "head-ref"), first, hash('1')));

        assertThatThrownBy(() ->
            inNewTransaction(() -> seedModelHead(tenant + "-other", "DIMENSION", finalDefinitionId, 1))
        ).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() ->
            inNewTransaction(() -> seedModelHead(tenant, "DIMENSION", finalDefinitionId, 99))
        ).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() ->
            inNewTransaction(() -> seedModelHead(tenant, "DIMENSION", finalDefinitionId, null))
        ).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() ->
            inNewTransaction(() -> seedModelHead(tenant, "DIMENSION", null, 1))
        ).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() ->
            inNewTransaction(() -> seedModelHead(tenant, "FACT", finalDefinitionId, 1))
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void failsClosedForInvalidCanonicalRevisionReferenceSnapshots() {
        String tenant = tenant("revision-ref");
        UUID domainId = UUID.randomUUID();
        View first = view(UUID.randomUUID(), domainId, SYSTEM_CODE, "Customer", Status.DRAFT, 1, hash('a'), Instant.now());
        inNewTransaction(() -> repository.insert(tenant, "owner-1", command(domainId, "revision-ref"), first, hash('1')));
        UUID modelSpecId = inNewTransaction(() -> seedModelHead(tenant, "DIMENSION", null, null));

        assertInvalidModelRevision(tenant, modelSpecId, 101, 1, "{\"modelType\":\"DIMENSION\"}", first.id());
        assertInvalidModelRevision(tenant, modelSpecId, 102, 2, "{}", first.id());
        assertInvalidModelRevision(tenant, modelSpecId, 103, 2, "{\"modelType\":\"FACT\"}", first.id());
        assertInvalidModelRevision(tenant, modelSpecId, 104, 2, "{\"modelType\":\"DIMENSION\"}", first.id(), null);
        assertInvalidModelRevision(tenant, modelSpecId, 105, 2, "{\"modelType\":\"DIMENSION\"}", null, 1);
    }

    @Test
    void enforcesBothLegacyMapForeignKeys() {
        String tenant = tenant("legacy-map");
        UUID domainId = UUID.randomUUID();
        View first = view(UUID.randomUUID(), domainId, SYSTEM_CODE, "Customer", Status.DRAFT, 1, hash('a'), Instant.now());
        inNewTransaction(() -> repository.insert(tenant, "owner-1", command(domainId, "legacy-map"), first, hash('1')));
        UUID modelSpecId = inNewTransaction(() -> seedModelHead(tenant, "DIMENSION", null, null));
        UUID secondModelSpecId = inNewTransaction(() -> seedModelHead(tenant, "DIMENSION", null, null));

        inNewTransaction(() -> {
            insertLegacyMap(tenant, modelSpecId, first.id(), 1);
            return null;
        });
        assertThatThrownBy(() ->
            inNewTransaction(() -> {
                insertLegacyMap(tenant, UUID.randomUUID(), first.id(), 1);
                return null;
            })
        ).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() ->
            inNewTransaction(() -> {
                insertLegacyMap(tenant, secondModelSpecId, first.id(), 99);
                return null;
            })
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void preventsDeletingReferencedHeadAndCurrentRevision() {
        String tenant = tenant("delete");
        UUID domainId = UUID.randomUUID();
        View first = view(UUID.randomUUID(), domainId, SYSTEM_CODE, "Customer", Status.DRAFT, 1, hash('a'), Instant.now());
        inNewTransaction(() -> repository.insert(tenant, "owner-1", command(domainId, "delete"), first, hash('1')));

        assertThatThrownBy(() ->
            inNewTransaction(() ->
                jdbcTemplate.update(
                    "delete from modeling_dimension_definition where tenant_id = ? and id = ?",
                    tenant,
                    first.id()
                )
            )
        ).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() ->
            inNewTransaction(() ->
                jdbcTemplate.update(
                    """
                    delete from modeling_dimension_definition_revision
                     where tenant_id = ? and dimension_definition_id = ? and revision = 1
                    """,
                    tenant,
                    first.id()
                )
            )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void retirementKeepsModelUsage() {
        String tenant = tenant("usage");
        UUID domainId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        View first = view(UUID.randomUUID(), domainId, SYSTEM_CODE, "Customer", Status.DRAFT, 1, hash('a'), now);
        inNewTransaction(() -> repository.insert(tenant, "owner-1", command(domainId, "usage"), first, hash('1')));
        UUID modelSpecId = inNewTransaction(() -> seedModelHead(tenant, "DIMENSION", first.id(), 1));
        View retired = view(
            first.id(),
            domainId,
            SYSTEM_CODE,
            "Customer retired",
            Status.RETIRED,
            2,
            hash('b'),
            now.plusSeconds(60)
        );

        assertThat(repository.compareAndSet(
            tenant,
            "owner-2",
            new ExpectedVersion(first.id(), 1, first.checksum()),
            retired
        )).isEqualTo(1);

        assertThat(repository.usageCount(tenant, first.id())).isEqualTo(1);
        assertThat(repository.findCurrent(tenant, first.id())).get().extracting(StoredDimensionDefinition::status).isEqualTo(Status.RETIRED);
        assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from modeling_model_spec where tenant_id = ? and id = ?",
                Integer.class,
                tenant,
                modelSpecId
            )
        ).isEqualTo(1);
    }

    private void assertInvalidModelRevision(
        String tenant,
        UUID modelSpecId,
        int revision,
        int contractVersion,
        String snapshot,
        UUID definitionId
    ) {
        assertInvalidModelRevision(tenant, modelSpecId, revision, contractVersion, snapshot, definitionId, 1);
    }

    private void assertInvalidModelRevision(
        String tenant,
        UUID modelSpecId,
        int revision,
        int contractVersion,
        String snapshot,
        UUID definitionId,
        Integer definitionRevision
    ) {
        assertThatThrownBy(() ->
            inNewTransaction(() -> {
                jdbcTemplate.update(
                    """
                    insert into modeling_model_spec_revision (
                        id, model_spec_id, revision, spec_json, status, content_checksum,
                        created_date, last_modified_date, tenant_id, contract_version,
                        snapshot_json, created_by, dimension_definition_id, dimension_definition_revision
                    ) values (?, ?, ?, '{}', 'DRAFT', ?, current_timestamp, current_timestamp,
                              ?, ?, cast(? as jsonb), 'owner-1', ?, ?)
                    """,
                    UUID.randomUUID(),
                    modelSpecId,
                    revision,
                    hash('f'),
                    tenant,
                    contractVersion,
                    snapshot,
                    definitionId,
                    definitionRevision
                );
                return null;
            })
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID seedModelHead(String tenant, String modelType, UUID definitionId, Integer definitionRevision) {
        UUID businessObjectId = UUID.randomUUID();
        UUID modelSpecId = UUID.randomUUID();
        jdbcTemplate.update(
            """
            insert into modeling_business_object (
                id, tenant_id, code, name, object_kind, process_id, status, version
            ) values (?, ?, ?, 'Customer', 'ENTITY', ?, 'DRAFT', 1)
            """,
            businessObjectId,
            tenant,
            "bo_" + businessObjectId.toString().replace("-", ""),
            "process-" + modelSpecId
        );
        jdbcTemplate.update(
            """
            insert into modeling_model_spec (
                id, tenant_id, object_id, process_id, layer, model_type, implementation_mode,
                name, status, revision, version, dimension_definition_id, dimension_definition_revision
            ) values (?, ?, ?, ?, 'DIM', ?, 'DESIGNER_GENERATED',
                      ?, 'DRAFT', 1, 1, ?, ?)
            """,
            modelSpecId,
            tenant,
            businessObjectId,
            "process-" + modelSpecId,
            modelType,
            "model-" + modelSpecId,
            definitionId,
            definitionRevision
        );
        return modelSpecId;
    }

    private void insertRevision(String tenant, View view) {
        jdbcTemplate.update(
            """
            insert into modeling_dimension_definition_revision (
                id, tenant_id, dimension_definition_id, revision, system_code, domain_id, name,
                definition, owner_id, reuse_scope, hierarchies_json, status, content_checksum,
                snapshot_json, created_by, created_date
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast('[]' as jsonb), ?, ?, cast('{}' as jsonb),
                      'owner-1', current_timestamp)
            """,
            UUID.randomUUID(),
            tenant,
            view.id(),
            view.revision(),
            view.systemCode(),
            view.domainId(),
            view.name(),
            view.definition(),
            view.ownerId(),
            view.reuseScope().name(),
            view.status().name(),
            view.checksum()
        );
    }

    private void insertLegacyMap(String tenant, UUID modelSpecId, UUID definitionId, int definitionRevision) {
        jdbcTemplate.update(
            """
            insert into modeling_dimension_definition_legacy_map (
                id, tenant_id, legacy_model_spec_id, dimension_definition_id,
                dimension_definition_revision, migration_batch_id, classification, created_by, created_date
            ) values (?, ?, ?, ?, ?, 'batch-1', 'AUTO_DIMENSION', 'owner-1', current_timestamp)
            """,
            UUID.randomUUID(),
            tenant,
            modelSpecId,
            definitionId,
            definitionRevision
        );
    }

    private void assertLedgerCounts(String tenant, UUID definitionId, int heads, int revisions) {
        assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from modeling_dimension_definition where tenant_id = ? and id = ?",
                Integer.class,
                tenant,
                definitionId
            )
        ).isEqualTo(heads);
        assertThat(
            jdbcTemplate.queryForObject(
                """
                select count(*) from modeling_dimension_definition_revision
                 where tenant_id = ? and dimension_definition_id = ?
                """,
                Integer.class,
                tenant,
                definitionId
            )
        ).isEqualTo(revisions);
    }

    private <T> T inNewTransaction(Supplier<T> action) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template.execute(status -> action.get());
    }

    private int concurrentInsert(
        CountDownLatch ready,
        CountDownLatch start,
        String tenant,
        CreateCommand command,
        View view
    ) throws InterruptedException {
        ready.countDown();
        start.await();
        return inNewTransaction(() -> repository.insert(tenant, "owner-1", command, view, hash('1')));
    }

    private static String tenant(String prefix) {
        return "dimension-" + prefix + "-" + UUID.randomUUID();
    }

    private static String hash(char value) {
        return String.valueOf(value).repeat(64);
    }

    private static CreateCommand command(UUID domainId, String idempotencyKey) {
        return new CreateCommand(
            domainId,
            "Customer",
            "Reusable customer dimension",
            "owner-1",
            ReuseScope.DOMAIN,
            List.of(),
            idempotencyKey
        );
    }

    private static View view(
        UUID id,
        UUID domainId,
        String systemCode,
        String name,
        Status status,
        int revision,
        String checksum,
        Instant updatedAt
    ) {
        return new View(
            id,
            systemCode,
            domainId,
            name,
            "Reusable customer dimension",
            "owner-1",
            ReuseScope.DOMAIN,
            List.of(),
            status,
            revision,
            checksum,
            0,
            Instant.parse("2026-07-24T00:00:00Z"),
            updatedAt
        );
    }

    private static View withIdentity(View view, String systemCode, UUID domainId, Instant createdAt) {
        return new View(
            view.id(),
            systemCode,
            domainId,
            view.name(),
            view.definition(),
            view.ownerId(),
            view.reuseScope(),
            view.hierarchies(),
            view.status(),
            view.revision(),
            view.checksum(),
            view.usageCount(),
            createdAt,
            view.updatedAt()
        );
    }
}
