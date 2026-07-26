package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.repository.modeling.DimensionDefinitionRepository;
import com.yuzhi.dts.platform.repository.modeling.DimensionDefinitionRepository.StoredDimensionDefinition;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionApplicationService.CreateResult;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionApplicationService.ExpectedVersion;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.CreateCommand;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.ReuseScope;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.Status;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.UpdateCommand;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.View;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DimensionDefinitionApplicationServiceTest {

    private static final String TENANT = "server-tenant";
    private static final String ACTOR = "alice";
    private static final UUID DOMAIN_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID DEFINITION_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-07-24T00:00:00Z");

    @Mock
    private DimensionDefinitionRepository repository;

    @Mock
    private ModelSpecDomainWriteAccessPort domainWriteAccess;

    @Mock
    private ModelSpecDomainReadAccessPort domainReadAccess;

    private ObjectMapper objectMapper;
    private DimensionDefinitionApplicationService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new DimensionDefinitionApplicationService(
            repository,
            objectMapper,
            domainWriteAccess,
            domainReadAccess,
            Clock.fixed(NOW, ZoneOffset.UTC),
            () -> DEFINITION_ID
        );
        lenient().when(domainWriteAccess.canMaintain(DOMAIN_ID)).thenReturn(true);
        lenient().when(domainReadAccess.canRead(DOMAIN_ID)).thenReturn(true);
        lenient().when(domainReadAccess.visibleDomainIds()).thenReturn(Set.of(DOMAIN_ID));
        lenient().when(repository.usageCount(TENANT, DEFINITION_ID)).thenReturn(0L);
    }

    @Test
    void createsDraftWithServerGeneratedCodeAndAuthenticatedAuditActor() {
        CreateCommand command = createCommand("create-customer", "Customer");
        when(repository.findByIdempotencyKey(TENANT, command.idempotencyKey())).thenReturn(Optional.empty());
        when(repository.insert(eq(TENANT), eq(ACTOR), eq(command), any(), anyString())).thenReturn(1);

        CreateResult result = service.create(TENANT, ACTOR, command);

        assertThat(result.replayed()).isFalse();
        assertThat(result.dimensionDefinition())
            .extracting(
                View::id,
                View::systemCode,
                View::ownerId,
                View::status,
                View::revision,
                View::usageCount,
                View::createdAt,
                View::updatedAt
            )
            .containsExactly(
                DEFINITION_ID,
                "dim_30000000000000000000000000000001",
                "business-owner",
                Status.DRAFT,
                1,
                0L,
                NOW,
                NOW
            );
        assertThat(result.dimensionDefinition().checksum()).matches("[0-9a-f]{64}");
        verify(repository).insert(eq(TENANT), eq(ACTOR), eq(command), eq(result.dimensionDefinition()), anyString());
    }

    @Test
    void replaysOriginalCreateAndRejectsDifferentContentForTheSameKey() throws Exception {
        CreateCommand command = createCommand("create-customer", "Customer");
        when(repository.findByIdempotencyKey(TENANT, command.idempotencyKey())).thenReturn(Optional.empty());
        when(repository.insert(eq(TENANT), eq(ACTOR), eq(command), any(), anyString())).thenReturn(1);
        CreateResult created = service.create(TENANT, ACTOR, command);

        ArgumentCaptor<String> requestHash = ArgumentCaptor.forClass(String.class);
        verify(repository).insert(eq(TENANT), eq(ACTOR), eq(command), any(), requestHash.capture());
        StoredDimensionDefinition stored = stored(
            created.dimensionDefinition(),
            requestHash.getValue(),
            objectMapper.writeValueAsString(created.dimensionDefinition())
        );
        when(repository.findByIdempotencyKey(TENANT, command.idempotencyKey())).thenReturn(Optional.of(stored));
        when(repository.findRevision(TENANT, DEFINITION_ID, 1)).thenReturn(Optional.of(stored));

        CreateResult replay = service.create(TENANT, ACTOR, command);

        assertThat(replay.replayed()).isTrue();
        assertThat(replay.dimensionDefinition()).isEqualTo(created.dimensionDefinition());
        assertThatThrownBy(() -> service.create(TENANT, ACTOR, createCommand(command.idempotencyKey(), "Changed")))
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("DIMENSION_DEFINITION_IDEMPOTENCY_CONFLICT");
    }

    @Test
    void convergesWhenConcurrentInsertLosesAfterTheInitialLookup() throws Exception {
        CreateCommand command = createCommand("concurrent-customer", "Customer");
        View original = createPersisted(command);
        ArgumentCaptor<String> requestHash = ArgumentCaptor.forClass(String.class);
        verify(repository).insert(eq(TENANT), eq(ACTOR), eq(command), any(), requestHash.capture());
        StoredDimensionDefinition stored = stored(
            original,
            requestHash.getValue(),
            objectMapper.writeValueAsString(original)
        );

        reset(repository);
        when(repository.findByIdempotencyKey(TENANT, command.idempotencyKey()))
            .thenReturn(Optional.empty(), Optional.of(stored));
        when(repository.insert(eq(TENANT), eq(ACTOR), eq(command), any(), anyString())).thenReturn(0);
        when(repository.findRevision(TENANT, DEFINITION_ID, 1)).thenReturn(Optional.of(stored));

        CreateResult replay = service.create(TENANT, ACTOR, command);

        assertThat(replay.replayed()).isTrue();
        assertThat(replay.dimensionDefinition()).isEqualTo(original);
    }

    @Test
    void enforcesCategoryWriteAndReadVisibilityWithoutLeakingHiddenDefinitions() {
        CreateCommand command = createCommand("forbidden", "Customer");
        when(repository.findByIdempotencyKey(TENANT, command.idempotencyKey())).thenReturn(Optional.empty());
        when(domainWriteAccess.canMaintain(DOMAIN_ID)).thenReturn(false);

        assertCode(
            () -> service.create(TENANT, ACTOR, command),
            "DIMENSION_DEFINITION_DOMAIN_FORBIDDEN"
        );
        verify(repository, never()).insert(any(), any(), any(), any(), any());

        reset(repository);
        when(domainReadAccess.canRead(DOMAIN_ID)).thenReturn(false);
        View hidden = view(Status.DRAFT, 1, "a".repeat(64), NOW, NOW, "Customer");
        when(repository.findCurrent(TENANT, DEFINITION_ID)).thenReturn(Optional.of(stored(hidden, null, null)));

        assertCode(
            () -> service.get(TENANT, DEFINITION_ID),
            "DIMENSION_DEFINITION_NOT_FOUND"
        );
        assertThat(service.list(TENANT, DOMAIN_ID, null, 0, 50)).isEmpty();

        when(repository.findCurrent(TENANT, DEFINITION_ID)).thenReturn(Optional.of(stored(hidden, null, null)));
        assertCode(
            () -> service.update(
                TENANT,
                ACTOR,
                DEFINITION_ID,
                expected(hidden),
                updateCommand("Hidden")
            ),
            "DIMENSION_DEFINITION_DOMAIN_FORBIDDEN"
        );
    }

    @Test
    void hidesDefinitionIdentityWhenAnIdempotencyReplayIsNotVisible() {
        CreateCommand command = createCommand("hidden-replay", "Customer");
        View hidden = view(Status.DRAFT, 1, "a".repeat(64), NOW, NOW, "Customer");
        when(repository.findByIdempotencyKey(TENANT, command.idempotencyKey()))
            .thenReturn(
                Optional.of(
                    stored(
                        hidden,
                        "hidden-request-hash",
                        "{\"id\":\"" + DEFINITION_ID + "\",\"secret\":\"must-not-return\"}"
                    )
                )
            );
        when(domainReadAccess.canRead(DOMAIN_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.create(TENANT, ACTOR, command))
            .isInstanceOf(ModelSpecException.class)
            .satisfies(error -> {
                ModelSpecException notFound = (ModelSpecException) error;
                assertThat(notFound.code()).isEqualTo("DIMENSION_DEFINITION_NOT_FOUND");
                assertThat(notFound.details()).isEqualTo(Map.of());
            });
        verify(repository, never()).findRevision(any(), any(), anyInt());
    }

    @Test
    void boundsListQueriesAndLoadsUsageCountsInOneBatch() {
        assertCode(
            () -> service.list(TENANT, null, null, -1, 50),
            "DIMENSION_DEFINITION_LIST_WINDOW_INVALID"
        );
        assertCode(
            () -> service.list(TENANT, null, null, 0, 101),
            "DIMENSION_DEFINITION_LIST_WINDOW_INVALID"
        );

        View visible = view(Status.CURRENT, 2, "b".repeat(64), NOW, NOW.plusSeconds(1), "Customer");
        when(repository.listCurrent(TENANT, null, Status.CURRENT, Set.of(DOMAIN_ID), 10, 25))
            .thenReturn(List.of(stored(visible, null, null)));
        when(repository.usageCounts(TENANT, List.of(DEFINITION_ID))).thenReturn(Map.of(DEFINITION_ID, 3L));

        assertThat(service.list(TENANT, null, Status.CURRENT, 10, 25))
            .singleElement()
            .extracting(View::usageCount)
            .isEqualTo(3L);
        verify(repository).usageCounts(TENANT, List.of(DEFINITION_ID));
    }

    @Test
    void rejectsInvalidCreateAndUpdateCommandsBeforePersistence() {
        assertCode(
            () -> service.create(
                TENANT,
                ACTOR,
                new CreateCommand(null, " ", " ", " ", null, List.of(), " ")
            ),
            "DIMENSION_DEFINITION_VALIDATION_FAILED"
        );
        verify(repository, never()).insert(any(), any(), any(), any(), any());

        View current = view(Status.DRAFT, 1, "a".repeat(64), NOW, NOW, "Customer");
        when(repository.findCurrent(TENANT, DEFINITION_ID)).thenReturn(Optional.of(stored(current, null, null)));
        when(repository.usageCount(TENANT, DEFINITION_ID)).thenReturn(0L);
        assertCode(
            () -> service.update(
                TENANT,
                ACTOR,
                DEFINITION_ID,
                expected(current),
                new UpdateCommand(" ", " ", " ", null, List.of())
            ),
            "DIMENSION_DEFINITION_VALIDATION_FAILED"
        );
        verify(repository, never()).compareAndSet(any(), any(), any(), any());
    }

    @Test
    void treatsEquivalentPutAsNoOpAndReturnsCurrentVersionOnStaleCas() {
        CreateCommand create = createCommand("update-customer", "Customer");
        View current = createPersisted(create);
        reset(repository);
        when(repository.findCurrent(TENANT, DEFINITION_ID)).thenReturn(Optional.of(stored(current, null, null)));
        when(repository.usageCount(TENANT, DEFINITION_ID)).thenReturn(0L);

        View unchanged = service.update(
            TENANT,
            ACTOR,
            DEFINITION_ID,
            expected(current),
            updateCommand("Customer")
        );

        assertThat(unchanged).isEqualTo(current);
        verify(repository, never()).compareAndSet(any(), any(), any(), any());

        View latest = view(
            Status.DRAFT,
            2,
            "b".repeat(64),
            current.createdAt(),
            current.updatedAt().plusSeconds(1),
            "Server version"
        );
        when(repository.findCurrent(TENANT, DEFINITION_ID))
            .thenReturn(Optional.of(stored(current, null, null)), Optional.of(stored(latest, null, null)));
        when(repository.compareAndSet(eq(TENANT), eq(ACTOR), any(), any())).thenReturn(0);
        assertThatThrownBy(() ->
            service.update(
                TENANT,
                ACTOR,
                DEFINITION_ID,
                expected(current),
                updateCommand("Customer changed")
            )
        )
            .isInstanceOf(ModelSpecException.class)
            .satisfies(error -> {
                ModelSpecException conflict = (ModelSpecException) error;
                assertThat(conflict.code()).isEqualTo("DIMENSION_DEFINITION_REVISION_CONFLICT");
                assertThat(conflict.details()).isEqualTo(
                    java.util.Map.of(
                        "currentRevision",
                        2,
                        "currentChecksum",
                        latest.checksum(),
                        "currentEtag",
                        DimensionDefinitionApplicationService.etag(latest)
                    )
                );
            });
    }

    @Test
    void normalizesDatabaseInstantsAndAnchorsReplayToImmutableRevisionOne() throws Exception {
        Instant nanosecondInstant = Instant.parse("2026-07-24T00:00:00.123456789Z");
        DimensionDefinitionApplicationService nanoService = new DimensionDefinitionApplicationService(
            repository,
            objectMapper,
            domainWriteAccess,
            domainReadAccess,
            Clock.fixed(nanosecondInstant, ZoneOffset.UTC),
            () -> DEFINITION_ID
        );
        CreateCommand command = createCommand("nano-replay", "Customer");
        when(repository.findByIdempotencyKey(TENANT, command.idempotencyKey())).thenReturn(Optional.empty());
        when(repository.insert(eq(TENANT), eq(ACTOR), eq(command), any(), anyString())).thenReturn(1);

        View original = nanoService.create(TENANT, ACTOR, command).dimensionDefinition();
        assertThat(original.createdAt()).isEqualTo(Instant.parse("2026-07-24T00:00:00.123456Z"));
        ArgumentCaptor<String> requestHash = ArgumentCaptor.forClass(String.class);
        verify(repository).insert(eq(TENANT), eq(ACTOR), eq(command), any(), requestHash.capture());
        StoredDimensionDefinition revisionOne = stored(
            original,
            requestHash.getValue(),
            objectMapper.writeValueAsString(original)
        );
        when(repository.findByIdempotencyKey(TENANT, command.idempotencyKey()))
            .thenReturn(Optional.of(revisionOne));
        when(repository.findRevision(TENANT, DEFINITION_ID, 1)).thenReturn(Optional.of(revisionOne));

        assertThat(nanoService.create(TENANT, ACTOR, command).dimensionDefinition()).isEqualTo(original);

        reset(repository);
        CreateCommand tamperedCommand = createCommand("tampered-snapshot", "Tampered");
        when(repository.findByIdempotencyKey(TENANT, tamperedCommand.idempotencyKey())).thenReturn(Optional.empty());
        when(repository.insert(eq(TENANT), eq(ACTOR), eq(tamperedCommand), any(), anyString())).thenReturn(1);
        View selfConsistentTampered = nanoService
            .create(TENANT, ACTOR, tamperedCommand)
            .dimensionDefinition();

        reset(repository);
        StoredDimensionDefinition tamperedHead = stored(
            original,
            requestHash.getValue(),
            objectMapper.writeValueAsString(selfConsistentTampered)
        );
        when(repository.findByIdempotencyKey(TENANT, command.idempotencyKey()))
            .thenReturn(Optional.of(tamperedHead));
        when(repository.findRevision(TENANT, DEFINITION_ID, 1)).thenReturn(Optional.of(revisionOne));
        assertCode(
            () -> nanoService.create(TENANT, ACTOR, command),
            "DIMENSION_DEFINITION_IDEMPOTENCY_SNAPSHOT_INVALID"
        );
    }

    @Test
    void producesTheSameCanonicalHashesAcrossObjectMapperPresentationSettings() {
        CreateCommand command = createCommand("canonical-hash", "Customer");
        when(repository.findByIdempotencyKey(TENANT, command.idempotencyKey())).thenReturn(Optional.empty());
        when(repository.insert(eq(TENANT), eq(ACTOR), eq(command), any(), anyString())).thenReturn(1);
        View compactView = service.create(TENANT, ACTOR, command).dimensionDefinition();
        ArgumentCaptor<String> compactHash = ArgumentCaptor.forClass(String.class);
        verify(repository).insert(eq(TENANT), eq(ACTOR), eq(command), any(), compactHash.capture());

        reset(repository);
        ObjectMapper prettyMapper = new ObjectMapper().findAndRegisterModules();
        prettyMapper.enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);
        prettyMapper.setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);
        DimensionDefinitionApplicationService prettyService = new DimensionDefinitionApplicationService(
            repository,
            prettyMapper,
            domainWriteAccess,
            domainReadAccess,
            Clock.fixed(NOW, ZoneOffset.UTC),
            () -> DEFINITION_ID
        );
        when(repository.findByIdempotencyKey(TENANT, command.idempotencyKey())).thenReturn(Optional.empty());
        when(repository.insert(eq(TENANT), eq(ACTOR), eq(command), any(), anyString())).thenReturn(1);

        View prettyView = prettyService.create(TENANT, ACTOR, command).dimensionDefinition();
        ArgumentCaptor<String> prettyHash = ArgumentCaptor.forClass(String.class);
        verify(repository).insert(eq(TENANT), eq(ACTOR), eq(command), any(), prettyHash.capture());

        assertThat(prettyView.checksum()).isEqualTo(compactView.checksum());
        assertThat(prettyHash.getValue()).isEqualTo(compactHash.getValue());
    }

    @Test
    void confirmsThenRetiresWithCasAndKeepsRetiredDefinitionReadable() {
        View draft = createPersisted(createCommand("lifecycle", "Customer"));
        draft = new View(
            draft.id(),
            draft.systemCode(),
            draft.domainId(),
            draft.name(),
            draft.definition(),
            draft.ownerId(),
            draft.reuseScope(),
            draft.hierarchies(),
            draft.status(),
            draft.revision(),
            draft.checksum(),
            draft.usageCount(),
            draft.createdAt(),
            draft.updatedAt(),
            DimensionDefinitionContract.ScopeType.DOMAIN,
            null,
            List.of(
                new DimensionDefinitionContract.AttributeSemantic(
                    "CUSTOMER_ID",
                    "Customer id",
                    "Stable customer business key",
                    true,
                    null,
                    null,
                    1
                )
            )
        );
        reset(repository);
        when(repository.findCurrent(TENANT, DEFINITION_ID)).thenReturn(Optional.of(stored(draft, null, null)));
        when(repository.usageCount(TENANT, DEFINITION_ID)).thenReturn(2L);
        when(repository.compareAndSet(eq(TENANT), eq(ACTOR), any(), any())).thenReturn(1);

        View current = service.confirm(TENANT, ACTOR, DEFINITION_ID, expected(draft));

        assertThat(current.status()).isEqualTo(Status.CURRENT);
        assertThat(current.revision()).isEqualTo(2);
        assertThat(current.usageCount()).isEqualTo(2);

        reset(repository);
        when(repository.findCurrent(TENANT, DEFINITION_ID)).thenReturn(Optional.of(stored(current, null, null)));
        when(repository.usageCount(TENANT, DEFINITION_ID)).thenReturn(2L);
        when(repository.compareAndSet(eq(TENANT), eq(ACTOR), any(), any())).thenReturn(1);

        View retired = service.retire(TENANT, ACTOR, DEFINITION_ID, expected(current));

        assertThat(retired.status()).isEqualTo(Status.RETIRED);
        assertThat(retired.revision()).isEqualTo(3);

        reset(repository);
        when(repository.findCurrent(TENANT, DEFINITION_ID)).thenReturn(Optional.of(stored(retired, null, null)));
        when(repository.usageCount(TENANT, DEFINITION_ID)).thenReturn(2L);
        assertThat(service.get(TENANT, DEFINITION_ID).status()).isEqualTo(Status.RETIRED);
        assertCode(
            () -> service.update(
                TENANT,
                ACTOR,
                DEFINITION_ID,
                expected(retired),
                updateCommand("Cannot edit")
            ),
            "DIMENSION_DEFINITION_RETIRED"
        );
        assertCode(
            () -> service.retire(TENANT, ACTOR, DEFINITION_ID, expected(retired)),
            "DIMENSION_DEFINITION_TRANSITION_INVALID"
        );
    }

    @Test
    void requiresAuthenticatedActorAndTranslatesPersistenceConflicts() {
        CreateCommand command = createCommand("actor", "Customer");
        assertCode(
            () -> service.create(TENANT, " ", command),
            "DIMENSION_DEFINITION_ACTOR_REQUIRED"
        );

        when(repository.findByIdempotencyKey(TENANT, command.idempotencyKey())).thenReturn(Optional.empty());
        when(repository.insert(eq(TENANT), eq(ACTOR), eq(command), any(), anyString()))
            .thenThrow(new org.springframework.dao.DataIntegrityViolationException("uk_dimension_definition_system_code"));
        assertCode(
            () -> service.create(TENANT, ACTOR, command),
            "DIMENSION_DEFINITION_PERSISTENCE_CONFLICT"
        );

        reset(repository);
        when(repository.findByIdempotencyKey(TENANT, command.idempotencyKey())).thenReturn(Optional.empty());
        when(repository.insert(eq(TENANT), eq(ACTOR), eq(command), any(), anyString()))
            .thenThrow(
                new org.springframework.dao.DataIntegrityViolationException(
                    "Dimension definition idempotency key was already used with a different request hash"
                )
            );
        assertCode(
            () -> service.create(TENANT, ACTOR, command),
            "DIMENSION_DEFINITION_IDEMPOTENCY_CONFLICT"
        );

        reset(repository);
        when(repository.findByIdempotencyKey(TENANT, command.idempotencyKey())).thenReturn(Optional.empty());
        when(repository.insert(eq(TENANT), eq(ACTOR), eq(command), any(), anyString()))
            .thenThrow(
                new org.springframework.dao.DataIntegrityViolationException(
                    "duplicate key violates uk_dimension_definition_tenant_domain_name_ci"
                )
            );
        assertCode(
            () -> service.create(TENANT, ACTOR, command),
            "DIMENSION_DEFINITION_NAME_CONFLICT"
        );
    }

    @Test
    void rejectsDuplicateNamesOnCreateAndUpdateUsingTheDatabaseUniquenessKey() {
        CreateCommand create = createCommand("duplicate-create", "Customer");
        when(repository.findByIdempotencyKey(TENANT, create.idempotencyKey())).thenReturn(Optional.empty());
        when(repository.existsByDomainAndName(TENANT, DOMAIN_ID, "Customer", null)).thenReturn(true);

        assertCode(
            () -> service.create(TENANT, ACTOR, create),
            "DIMENSION_DEFINITION_NAME_CONFLICT"
        );
        verify(repository, never()).insert(any(), any(), any(), any(), any());

        reset(repository);
        View current = view(Status.DRAFT, 1, "a".repeat(64), NOW, NOW, "Customer");
        when(repository.findCurrent(TENANT, DEFINITION_ID))
            .thenReturn(Optional.of(stored(current, null, null)));
        when(repository.usageCount(TENANT, DEFINITION_ID)).thenReturn(0L);
        when(repository.existsByDomainAndName(TENANT, DOMAIN_ID, "Account", DEFINITION_ID))
            .thenReturn(true);

        assertCode(
            () ->
                service.update(
                    TENANT,
                    ACTOR,
                    DEFINITION_ID,
                    expected(current),
                    updateCommand("Account")
                ),
            "DIMENSION_DEFINITION_NAME_CONFLICT"
        );
        verify(repository, never()).compareAndSet(any(), any(), any(), any());
    }

    private View createPersisted(CreateCommand command) {
        when(repository.findByIdempotencyKey(TENANT, command.idempotencyKey())).thenReturn(Optional.empty());
        when(repository.insert(eq(TENANT), eq(ACTOR), eq(command), any(), anyString())).thenReturn(1);
        return service.create(TENANT, ACTOR, command).dimensionDefinition();
    }

    private static CreateCommand createCommand(String idempotencyKey, String name) {
        return new CreateCommand(
            DOMAIN_ID,
            name,
            "Reusable customer dimension",
            "business-owner",
            ReuseScope.DOMAIN,
            List.of(),
            idempotencyKey
        );
    }

    private static UpdateCommand updateCommand(String name) {
        return new UpdateCommand(
            name,
            "Reusable customer dimension",
            "business-owner",
            ReuseScope.DOMAIN,
            List.of()
        );
    }

    private static ExpectedVersion expected(View view) {
        return new ExpectedVersion(view.id(), view.revision(), view.checksum());
    }

    private static View view(
        Status status,
        int revision,
        String checksum,
        Instant createdAt,
        Instant updatedAt,
        String name
    ) {
        return new View(
            DEFINITION_ID,
            "dim_30000000000000000000000000000001",
            DOMAIN_ID,
            name,
            "Reusable customer dimension",
            "business-owner",
            ReuseScope.DOMAIN,
            List.of(),
            status,
            revision,
            checksum,
            0,
            createdAt,
            updatedAt
        );
    }

    private static StoredDimensionDefinition stored(View view, String requestHash, String responseSnapshot) {
        return new StoredDimensionDefinition(
            true,
            TENANT,
            view.id(),
            view.systemCode(),
            view.domainId(),
            view.name(),
            view.definition(),
            view.ownerId(),
            view.reuseScope(),
            view.hierarchies(),
            view.scopeType(),
            view.dataMartId(),
            view.attributes(),
            view.status(),
            view.revision(),
            view.checksum(),
            view.checksum(),
            responseSnapshot,
            "key",
            requestHash,
            responseSnapshot,
            view.createdAt(),
            view.updatedAt()
        );
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation, String code) {
        assertThatThrownBy(operation)
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo(code);
    }
}
