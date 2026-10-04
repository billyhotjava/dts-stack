package com.yuzhi.dts.platform.repository.modeling;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariDataSource;
import com.yuzhi.dts.platform.IntegrationTest;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryAuditView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CommandEventType;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CommandEventView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CurrentModelReference;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.EntryView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.VersionConflictView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
@Transactional
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=4")
class ModelReleaseCandidateRepositoryIT {

    private static final Instant CREATED_AT = Instant.parse("2026-07-24T00:00:00Z");

    @Test
    void schemaCandidatesNeverHideOrdinaryWorkbenchOrOlderActiveModelClaims() {
        String tenant = tenant("schema-scope");
        UUID plan = UUID.randomUUID();
        UUID normalModel = UUID.randomUUID();
        UUID[] schemaModels = { UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID() };
        seedPlanAndModels(tenant, plan);
        UUID domain = UUID.randomUUID();
        jdbcTemplate.update("insert into catalog_domain (id, name, code, lifecycle_status, access_policy) values (?, ?, ?, 'ACTIVE', 'PUBLIC')",
            domain, "Schema scope domain", "scope_" + domain.toString().replace("-", ""));
        for (UUID model : List.of(normalModel, schemaModels[0], schemaModels[1], schemaModels[2])) {
            jdbcTemplate.update("""
                insert into modeling_model_spec (
                    id, tenant_id, plan_id, layer, warehouse_layer_code, model_type,
                    implementation_mode, name, status, revision, version, created_date,
                    last_modified_date, contract_version, domain_id, current_checksum,
                    idempotency_key, idempotency_request_hash, idempotency_response_snapshot
                ) values (?, ?, ?, 'DWD', 'DWD', 'FACT', 'DESIGNER_GENERATED', ?, 'DRAFT', 1, 1,
                    current_timestamp, current_timestamp, 2, ?, ?, ?, ?, cast('{}' as jsonb))
                """, model, tenant, plan, "Schema scope " + model, domain, checksum(model), "model-" + model, hash('b'));
            jdbcTemplate.update("""
                insert into modeling_model_spec_revision (
                    id, model_spec_id, revision, status, content_checksum, created_date,
                    last_modified_date, tenant_id, contract_version, snapshot_json, created_by
                ) values (?, ?, 1, 'DRAFT', ?, current_timestamp, current_timestamp, ?, 2,
                    cast('{}' as jsonb), 'owner-1')
                """, UUID.randomUUID(), model, checksum(model), tenant);
        }
        UUID normalId = UUID.randomUUID();
        var normal = candidate(tenant, normalId, plan, DeliveryStatus.BUILT, 1, createdAudit(), CREATED_AT,
            List.of(entry(tenant, normalId, plan, normalModel, DeliveryStatus.BUILT, 1, checksum(normalModel), null, 0)));
        repository.insert(normal);
        UUID oldestSchema = null;
        for (int i = 0; i < schemaModels.length; i++) {
            UUID id = UUID.randomUUID();
            if (i == 0) oldestSchema = id;
            var schema = new CandidateView(id, tenant, plan, "TEST", DeliveryStatus.BUILT, 1,
                "candidate-" + id, hash('1'), createdAudit(), "owner-1", CREATED_AT.plusSeconds(i + 1),
                List.of(entry(tenant, id, plan, schemaModels[i], DeliveryStatus.BUILT, 1, checksum(schemaModels[i]), null, 0)),
                com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateOrigin.SCHEMA_ONLY_INTENT,
                null, null, null, null);
            repository.insert(schema);
        }
        assertThat(repository.listForWorkbench(tenant, plan)).extracting(CandidateView::id).containsExactly(normalId);
        assertThat(repository.listActiveForPlan(tenant, plan)).hasSize(4).extracting(CandidateView::id)
            .contains(normalId, oldestSchema);
        assertThat(repository.findLatestForModelCurrentRevision(tenant, plan, schemaModels[0], 1,
            checksum(schemaModels[0]), "TEST")).get().extracting(CandidateView::id).isEqualTo(oldestSchema);
    }

    @Autowired
    private ModelReleaseCandidateRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DataSource dataSource;

    @Test
    void exposesStableEmptyLoadedHistoricalAndPagedUiFixtures() {
        String tenant = tenant("loaded");
        UUID planId = UUID.randomUUID();
        UUID firstModel = UUID.randomUUID();
        UUID secondModel = UUID.randomUUID();
        seedPlanAndModels(tenant, planId, firstModel, secondModel);
        UUID implementationId = seedImplementation(tenant, planId, firstModel);

        UUID emptyPlanId = UUID.randomUUID();
        seedPlanAndModels(tenant, emptyPlanId);
        UUID emptyId = UUID.randomUUID();
        CandidateView empty = candidate(
            tenant,
            emptyId,
            emptyPlanId,
            DeliveryStatus.DRAFT,
            1,
            createdAudit(),
            CREATED_AT,
            List.of()
        );
        repository.insert(empty);
        assertThat(repository.find(tenant, emptyId)).contains(empty);
        assertThat(empty.entries()).isEmpty();

        UUID loadedId = UUID.randomUUID();
        CandidateView loaded = candidate(
            tenant,
            loadedId,
            planId,
            DeliveryStatus.DRAFT,
            1,
            createdAudit(),
            CREATED_AT.plusSeconds(10),
            List.of(
                entry(tenant, loadedId, planId, secondModel, DeliveryStatus.DRAFT, 1, checksum(secondModel), null, 1),
                entry(
                    tenant,
                    loadedId,
                    planId,
                    firstModel,
                    DeliveryStatus.DRAFT,
                    1,
                    checksum(firstModel),
                    implementationId,
                    0
                )
            )
        );
        repository.insert(loaded);
        assertThat(loaded.entries()).extracting(EntryView::sortOrder).containsExactly(0, 1);
        assertThatThrownBy(() ->
            candidate(
                tenant,
                UUID.randomUUID(),
                planId,
                DeliveryStatus.APPROVED,
                1,
                createdAudit(),
                CREATED_AT,
                List.of()
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("submitted audit");
        assertThatThrownBy(() ->
            candidate(
                tenant,
                UUID.randomUUID(),
                planId,
                DeliveryStatus.APPROVED,
                1,
                new DeliveryAuditView(
                    "owner-1",
                    CREATED_AT,
                    "same-actor",
                    CREATED_AT.plusSeconds(1),
                    "same-actor",
                    CREATED_AT.plusSeconds(2),
                    null,
                    null
                ),
                CREATED_AT.plusSeconds(2),
                List.of()
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("submitter");

        UUID rejectedId = UUID.randomUUID();
        CandidateView rejected = candidate(
            tenant,
            rejectedId,
            planId,
            DeliveryStatus.REJECTED,
            3,
            publishedAudit(),
            CREATED_AT.plusSeconds(60),
            List.of(entry(
                tenant,
                rejectedId,
                planId,
                firstModel,
                DeliveryStatus.REJECTED,
                1,
                checksum(firstModel),
                null,
                0
            ))
        );
        UUID rolledBackId = UUID.randomUUID();
        CandidateView rolledBack = candidate(
            tenant,
            rolledBackId,
            planId,
            DeliveryStatus.ROLLED_BACK,
            4,
            publishedAudit(),
            CREATED_AT.plusSeconds(120),
            List.of(entry(
                tenant,
                rolledBackId,
                planId,
                secondModel,
                DeliveryStatus.ROLLED_BACK,
                1,
                checksum(secondModel),
                null,
                0
            ))
        );
        repository.insert(rejected);
        repository.insert(rolledBack);

        assertThat(repository.find("another-tenant", loadedId)).isEmpty();
        assertThat(repository.list(tenant, planId, null, 0, 10))
            .extracting(CandidateView::id)
            .containsExactly(rolledBackId, rejectedId, loadedId);
        assertThat(repository.list(tenant, planId, null, 1, 2))
            .extracting(CandidateView::id)
            .containsExactly(rejectedId, loadedId);
        assertThat(repository.list(tenant, planId, DeliveryStatus.REJECTED, 0, 10)).containsExactly(rejected);
        assertThat(repository.listForWorkbench(tenant, planId))
            .extracting(CandidateView::id)
            .containsExactly(loadedId, rolledBackId);
        assertThatThrownBy(() -> repository.list(tenant, planId, null, 0, 201))
            .isInstanceOf(InvalidDataAccessApiUsageException.class)
            .hasRootCauseInstanceOf(IllegalArgumentException.class)
            .hasRootCauseMessage("limit must be between 1 and 200");
    }

    @Test
    void activePlanConstraintRejectsASecondActiveCandidateButKeepsTerminalHistory() {
        String tenant = tenant("active-plan");
        UUID planId = UUID.randomUUID();
        UUID modelId = UUID.randomUUID();
        seedPlanAndModels(tenant, planId, modelId);

        UUID activeId = UUID.randomUUID();
        assertThat(repository.insert(draftCandidate(tenant, activeId, planId, modelId))).isEqualTo(1);
        assertThat(repository.insert(draftCandidate(tenant, UUID.randomUUID(), planId, modelId))).isZero();

        UUID rejectedId = UUID.randomUUID();
        CandidateView rejected = candidate(
            tenant,
            rejectedId,
            planId,
            DeliveryStatus.REJECTED,
            3,
            publishedAudit(),
            CREATED_AT.plusSeconds(60),
            List.of(entry(
                tenant,
                rejectedId,
                planId,
                modelId,
                DeliveryStatus.REJECTED,
                1,
                checksum(modelId),
                null,
                0
            ))
        );
        assertThat(repository.insert(rejected)).isEqualTo(1);
        assertThat(repository.list(tenant, planId, null, 0, 10))
            .extracting(CandidateView::id)
            .containsExactly(rejectedId, activeId);
    }

    @Test
    void resolvesOnlyCanonicalV2ModelsWithTheExactCurrentRevisionAndChecksum() {
        String tenant = tenant("canonical-reference");
        UUID planId = UUID.randomUUID();
        UUID legacyModelId = UUID.randomUUID();
        UUID canonicalModelId = UUID.randomUUID();
        UUID revisionMismatchModelId = UUID.randomUUID();
        UUID checksumMismatchModelId = UUID.randomUUID();
        String canonicalChecksum = hash('c');
        seedPlanAndModels(tenant, planId, legacyModelId);
        seedCanonicalV2Model(tenant, planId, canonicalModelId, canonicalChecksum);
        seedCanonicalV2Model(tenant, planId, revisionMismatchModelId, hash('e'));
        seedCanonicalV2Model(tenant, planId, checksumMismatchModelId, hash('f'));
        jdbcTemplate.update(
            "update modeling_model_spec set revision = 3 where tenant_id = ? and id = ?",
            tenant,
            revisionMismatchModelId
        );
        jdbcTemplate.update(
            "update modeling_model_spec set current_checksum = ? where tenant_id = ? and id = ?",
            hash('a'),
            tenant,
            checksumMismatchModelId
        );

        Map<UUID, CurrentModelReference> references = repository.findCurrentModelReferences(
            tenant,
            planId,
            List.of(canonicalModelId, legacyModelId, revisionMismatchModelId, checksumMismatchModelId)
        );

        assertThat(references).containsOnlyKeys(canonicalModelId);
        assertThat(references.get(canonicalModelId))
            .extracting(
                CurrentModelReference::planId,
                CurrentModelReference::revision,
                CurrentModelReference::checksum,
                CurrentModelReference::implementationMode
            )
            .containsExactly(planId, 2, canonicalChecksum, ImplementationMode.DBT_MANAGED);
        assertThat(
            repository.findCurrentModelReferencesForRead(
                tenant,
                planId,
                List.of(canonicalModelId, legacyModelId, revisionMismatchModelId, checksumMismatchModelId)
            )
        )
            .isEqualTo(references);
    }

    @Test
    void detectsVersionConflictsAndDeletesOnlyUnreferencedDrafts() {
        String tenant = tenant("version");
        UUID planId = UUID.randomUUID();
        UUID modelId = UUID.randomUUID();
        seedPlanAndModels(tenant, planId, modelId);
        UUID candidateId = UUID.randomUUID();
        CandidateView candidate = draftCandidate(tenant, candidateId, planId, modelId);
        repository.insert(candidate);

        Instant modifiedAt = CREATED_AT.plusSeconds(60);
        assertThat(
            repository.compareAndSetVersion(
                tenant,
                candidateId,
                1,
                DeliveryStatus.BUILDING,
                candidate.audit(),
                "builder-1",
                modifiedAt
            )
        )
            .isEqualTo(1);
        assertThat(
            repository.compareAndSetVersion(
                tenant,
                candidateId,
                1,
                DeliveryStatus.BUILT,
                candidate.audit(),
                "builder-1",
                modifiedAt.plusSeconds(1)
            )
        )
            .isZero();
        CandidateView current = repository.find(tenant, candidateId).orElseThrow();
        VersionConflictView conflict = VersionConflictView.of(
            candidateId,
            1,
            current.version(),
            current.status()
        );
        VersionConflictView clientAhead = VersionConflictView.of(
            candidateId,
            3,
            current.version(),
            current.status()
        );
        assertThat(conflict)
            .extracting(
                VersionConflictView::expectedVersion,
                VersionConflictView::currentVersion,
                VersionConflictView::currentStatus,
                VersionConflictView::errorCode
            )
            .containsExactly(
                1,
                2,
                DeliveryStatus.BUILDING,
                "MODEL_RELEASE_CANDIDATE_VERSION_CONFLICT"
            );
        assertThat(clientAhead)
            .extracting(VersionConflictView::expectedVersion, VersionConflictView::currentVersion)
            .containsExactly(3, 2);
        assertThatThrownBy(() ->
            VersionConflictView.of(candidateId, current.version(), current.version(), current.status())
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("differ");
        assertThat(repository.deleteDraft(tenant, candidateId, 2)).isZero();

        UUID deletionPlanId = UUID.randomUUID();
        UUID deletionModelId = UUID.randomUUID();
        seedPlanAndModels(tenant, deletionPlanId, deletionModelId);
        UUID draftId = UUID.randomUUID();
        repository.insert(draftCandidate(tenant, draftId, deletionPlanId, deletionModelId));
        assertThat(repository.deleteDraft(tenant, draftId, 1)).isEqualTo(1);
        assertThat(repository.find(tenant, draftId)).isEmpty();
    }

    @Test
    void appendsAuditStagesOnceAndRejectsClearOrTamperAttempts() {
        String tenant = tenant("audit");
        UUID planId = UUID.randomUUID();
        UUID modelId = UUID.randomUUID();
        seedPlanAndModels(tenant, planId, modelId);
        UUID candidateId = UUID.randomUUID();
        CandidateView candidate = candidate(
            tenant,
            candidateId,
            planId,
            DeliveryStatus.QUALITY_PASSED,
            1,
            createdAudit(),
            CREATED_AT,
            List.of(entry(
                tenant,
                candidateId,
                planId,
                modelId,
                DeliveryStatus.QUALITY_PASSED,
                1,
                checksum(modelId),
                null,
                0
            ))
        );
        repository.insert(candidate);

        DeliveryAuditView submitted = new DeliveryAuditView(
            "owner-1",
            CREATED_AT,
            "submitter-1",
            CREATED_AT.plusSeconds(10),
            null,
            null,
            null,
            null
        );
        assertThat(
            repository.compareAndSetVersion(
                tenant,
                candidateId,
                1,
                DeliveryStatus.REVIEW_PENDING,
                submitted,
                "submitter-1",
                CREATED_AT.plusSeconds(10)
            )
        )
            .isEqualTo(1);

        DeliveryAuditView approved = new DeliveryAuditView(
            "owner-1",
            CREATED_AT,
            "submitter-1",
            CREATED_AT.plusSeconds(10),
            "reviewer-1",
            CREATED_AT.plusSeconds(20),
            null,
            null
        );
        assertThat(
            repository.compareAndSetVersion(
                tenant,
                candidateId,
                2,
                DeliveryStatus.APPROVED,
                approved,
                "reviewer-1",
                CREATED_AT.plusSeconds(20)
            )
        )
            .isEqualTo(1);

        DeliveryAuditView tampered = new DeliveryAuditView(
            "owner-1",
            CREATED_AT,
            "intruder-1",
            CREATED_AT.plusSeconds(10),
            "reviewer-1",
            CREATED_AT.plusSeconds(20),
            null,
            null
        );
        assertThat(
            repository.compareAndSetVersion(
                tenant,
                candidateId,
                3,
                DeliveryStatus.APPROVED,
                tampered,
                "intruder-1",
                CREATED_AT.plusSeconds(30)
            )
        )
            .isZero();
        assertThat(
            repository.compareAndSetVersion(
                tenant,
                candidateId,
                3,
                DeliveryStatus.APPROVED,
                createdAudit(),
                "owner-1",
                CREATED_AT.plusSeconds(30)
            )
        )
            .isZero();

        assertThat(
            repository.compareAndSetVersion(
                tenant,
                candidateId,
                3,
                DeliveryStatus.PUBLISHING,
                approved,
                "operator-1",
                CREATED_AT.plusSeconds(30)
            )
        )
            .isEqualTo(1);
        DeliveryAuditView published = new DeliveryAuditView(
            "owner-1",
            CREATED_AT,
            "submitter-1",
            CREATED_AT.plusSeconds(10),
            "reviewer-1",
            CREATED_AT.plusSeconds(20),
            "operator-1",
            CREATED_AT.plusSeconds(40)
        );
        assertThat(
            repository.compareAndSetVersion(
                tenant,
                candidateId,
                4,
                DeliveryStatus.PARTIAL,
                published,
                "operator-1",
                CREATED_AT.plusSeconds(40)
            )
        )
            .isEqualTo(1);
        assertThat(repository.find(tenant, candidateId))
            .get()
            .extracting(CandidateView::version, CandidateView::status, item -> item.audit().publishedBy())
            .containsExactly(5, DeliveryStatus.PARTIAL, "operator-1");
    }

    @Test
    void createsANewDraftReplacementWithoutMutatingTheRolledBackCandidateAudit() {
        String tenant = tenant("replacement");
        UUID planId = UUID.randomUUID();
        UUID modelId = UUID.randomUUID();
        UUID rolledBackId = UUID.randomUUID();
        UUID replacementId = UUID.randomUUID();
        seedPlanAndModels(tenant, planId, modelId);

        DeliveryAuditView rolledBackAudit = publishedAudit();
        CandidateView rolledBack = candidate(
            tenant,
            rolledBackId,
            planId,
            DeliveryStatus.ROLLED_BACK,
            5,
            rolledBackAudit,
            CREATED_AT.plusSeconds(60),
            List.of(entry(
                tenant,
                rolledBackId,
                planId,
                modelId,
                DeliveryStatus.ROLLED_BACK,
                1,
                checksum(modelId),
                null,
                0
            ))
        );
        DeliveryAuditView replacementAudit = new DeliveryAuditView(
            "owner-1",
            CREATED_AT.plusSeconds(120),
            null,
            null,
            null,
            null,
            null,
            null
        );
        CandidateView replacement = candidate(
            tenant,
            replacementId,
            planId,
            DeliveryStatus.DRAFT,
            1,
            replacementAudit,
            CREATED_AT.plusSeconds(120),
            List.of(entry(
                tenant,
                replacementId,
                planId,
                modelId,
                DeliveryStatus.DRAFT,
                1,
                checksum(modelId),
                null,
                0
            ))
        );

        repository.insert(rolledBack);
        repository.insert(replacement);

        CandidateView storedRolledBack = repository.find(tenant, rolledBackId).orElseThrow();
        CandidateView storedReplacement = repository.find(tenant, replacementId).orElseThrow();
        assertThat(storedRolledBack.id()).isNotEqualTo(storedReplacement.id());
        assertThat(storedRolledBack)
            .extracting(CandidateView::status, CandidateView::version, CandidateView::audit)
            .containsExactly(DeliveryStatus.ROLLED_BACK, 5, rolledBackAudit);
        assertThat(storedReplacement)
            .extracting(CandidateView::status, CandidateView::version, CandidateView::audit)
            .containsExactly(DeliveryStatus.DRAFT, 1, replacementAudit);
        assertThat(storedReplacement.audit())
            .extracting(
                DeliveryAuditView::submittedBy,
                DeliveryAuditView::submittedAt,
                DeliveryAuditView::approvedBy,
                DeliveryAuditView::approvedAt,
                DeliveryAuditView::publishedBy,
                DeliveryAuditView::publishedAt
            )
            .containsOnlyNulls();
    }

    @Test
    void rejectsEveryOutOfScopeRevisionChecksumAndImplementationReferenceAtomically() {
        assertCandidateRejected("fk_model_release_candidate_entry_revision", () -> {
            String tenant = tenant("missing-revision");
            UUID planId = UUID.randomUUID();
            UUID modelId = UUID.randomUUID();
            seedPlanAndModels(tenant, planId, modelId);
            UUID candidateId = UUID.randomUUID();
            repository.insert(candidate(
                tenant,
                candidateId,
                planId,
                DeliveryStatus.DRAFT,
                1,
                createdAudit(),
                CREATED_AT,
                List.of(entry(
                    tenant,
                    candidateId,
                    planId,
                    modelId,
                    DeliveryStatus.DRAFT,
                    2,
                    checksum(modelId),
                    null,
                    0
                ))
            ));
        });
        assertCandidateRejected("fk_model_release_candidate_entry_revision", () -> {
            String tenant = tenant("wrong-checksum");
            UUID planId = UUID.randomUUID();
            UUID modelId = UUID.randomUUID();
            seedPlanAndModels(tenant, planId, modelId);
            UUID candidateId = UUID.randomUUID();
            repository.insert(candidate(
                tenant,
                candidateId,
                planId,
                DeliveryStatus.DRAFT,
                1,
                createdAudit(),
                CREATED_AT,
                List.of(entry(
                    tenant,
                    candidateId,
                    planId,
                    modelId,
                    DeliveryStatus.DRAFT,
                    1,
                    hash('f'),
                    null,
                    0
                ))
            ));
        });
        assertCandidateRejected("fk_model_release_candidate_entry_model_scope", () -> {
            String tenant = tenant("cross-plan");
            UUID candidatePlan = UUID.randomUUID();
            UUID modelPlan = UUID.randomUUID();
            UUID modelId = UUID.randomUUID();
            seedPlanAndModels(tenant, candidatePlan);
            seedPlanAndModels(tenant, modelPlan, modelId);
            UUID candidateId = UUID.randomUUID();
            repository.insert(candidate(
                tenant,
                candidateId,
                candidatePlan,
                DeliveryStatus.DRAFT,
                1,
                createdAudit(),
                CREATED_AT,
                List.of(entry(
                    tenant,
                    candidateId,
                    candidatePlan,
                    modelId,
                    DeliveryStatus.DRAFT,
                    1,
                    checksum(modelId),
                    null,
                    0
                ))
            ));
        });
        assertCandidateRejected("fk_model_release_candidate_entry_model_scope", () -> {
            String tenant = tenant("model-mode");
            UUID planId = UUID.randomUUID();
            UUID modelId = UUID.randomUUID();
            seedPlanAndModels(tenant, planId, modelId);
            UUID candidateId = UUID.randomUUID();
            repository.insert(candidate(
                tenant,
                candidateId,
                planId,
                DeliveryStatus.DRAFT,
                1,
                createdAudit(),
                CREATED_AT,
                List.of(entry(
                    tenant,
                    candidateId,
                    planId,
                    modelId,
                    DeliveryStatus.DRAFT,
                    1,
                    checksum(modelId),
                    null,
                    ImplementationMode.DBT_MANAGED,
                    0
                ))
            ));
        });
        assertCandidateRejected("fk_model_release_candidate_entry_implementation", () -> {
            String tenant = tenant("implementation-ownership");
            UUID planId = UUID.randomUUID();
            UUID modelId = UUID.randomUUID();
            seedPlanAndModels(tenant, planId, ImplementationMode.DBT_MANAGED, modelId);
            UUID implementationId = seedImplementation(
                tenant,
                planId,
                modelId,
                ImplementationMode.DESIGNER_GENERATED
            );
            UUID candidateId = UUID.randomUUID();
            repository.insert(candidate(
                tenant,
                candidateId,
                planId,
                DeliveryStatus.DRAFT,
                1,
                createdAudit(),
                CREATED_AT,
                List.of(entry(
                    tenant,
                    candidateId,
                    planId,
                    modelId,
                    DeliveryStatus.DRAFT,
                    1,
                    checksum(modelId),
                    implementationId,
                    ImplementationMode.DBT_MANAGED,
                    0
                ))
            ));
        });
        assertCandidateRejected("fk_model_release_candidate_entry_implementation", () -> {
            String tenant = tenant("dangling-implementation");
            UUID planId = UUID.randomUUID();
            UUID modelId = UUID.randomUUID();
            seedPlanAndModels(tenant, planId, modelId);
            UUID candidateId = UUID.randomUUID();
            repository.insert(candidate(
                tenant,
                candidateId,
                planId,
                DeliveryStatus.DRAFT,
                1,
                createdAudit(),
                CREATED_AT,
                List.of(entry(
                    tenant,
                    candidateId,
                    planId,
                    modelId,
                    DeliveryStatus.DRAFT,
                    1,
                    checksum(modelId),
                    UUID.randomUUID(),
                    0
                ))
            ));
        });
    }

    @Test
    void rollsBackTheCandidateHeaderWhenAnEntryCrossesTheTenantBoundary() {
        String candidateTenant = tenant("atomic");
        String foreignTenant = tenant("foreign");
        UUID candidatePlan = UUID.randomUUID();
        UUID foreignPlan = UUID.randomUUID();
        UUID foreignModel = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();

        assertThatThrownBy(() ->
            inNewTransaction(() -> {
                seedPlanAndModels(candidateTenant, candidatePlan);
                seedPlanAndModels(foreignTenant, foreignPlan, foreignModel);
                repository.insert(candidate(
                    candidateTenant,
                    candidateId,
                    candidatePlan,
                    DeliveryStatus.DRAFT,
                    1,
                    createdAudit(),
                    CREATED_AT,
                    List.of(entry(
                        candidateTenant,
                        candidateId,
                        candidatePlan,
                        foreignModel,
                        DeliveryStatus.DRAFT,
                        1,
                        checksum(foreignModel),
                        null,
                        0
                    ))
                ));
                return null;
            })
        )
            .isInstanceOf(DataIntegrityViolationException.class)
            .satisfies(error ->
                assertThat(((DataIntegrityViolationException) error).getMostSpecificCause().getMessage())
                    .contains("fk_model_release_candidate_entry_model_scope")
            );
        assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from modeling_model_release_candidate where tenant_id = ? and id = ?",
                Integer.class,
                candidateTenant,
                candidateId
            )
        )
            .isZero();
    }

    @Test
    void databaseRejectsDuplicateModelAndOrderWhenTheContractIsBypassed() {
        String tenant = tenant("unique");
        UUID planId = UUID.randomUUID();
        UUID firstModel = UUID.randomUUID();
        UUID secondModel = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();

        assertThatThrownBy(() ->
            inNewTransaction(() -> {
                seedPlanAndModels(tenant, planId, firstModel, secondModel);
                repository.insert(draftCandidate(tenant, candidateId, planId, firstModel));
                insertRawEntry(tenant, candidateId, planId, firstModel, 1);
                return null;
            })
        )
            .isInstanceOf(DataIntegrityViolationException.class)
            .satisfies(error ->
                assertThat(((DataIntegrityViolationException) error).getMostSpecificCause().getMessage())
                    .contains("uk_model_release_candidate_entry_model")
            );

        assertThatThrownBy(() ->
            inNewTransaction(() -> {
                seedPlanAndModels(tenant, planId, firstModel, secondModel);
                repository.insert(draftCandidate(tenant, candidateId, planId, firstModel));
                insertRawEntry(tenant, candidateId, planId, secondModel, 0);
                return null;
            })
        )
            .isInstanceOf(DataIntegrityViolationException.class)
            .satisfies(error ->
                assertThat(((DataIntegrityViolationException) error).getMostSpecificCause().getMessage())
                    .contains("uk_model_release_candidate_entry_order")
            );
    }

    @Test
    void restrictsRawCandidateModelRevisionAndPlanDeletes() {
        assertCandidateRejected("fk_model_release_candidate_entry_candidate_scope", () -> {
            Scope scope = seedScope("delete-candidate");
            repository.insert(draftCandidate(scope.tenant(), scope.candidateId(), scope.planId(), scope.modelId()));
            jdbcTemplate.update(
                "delete from modeling_model_release_candidate where tenant_id = ? and id = ?",
                scope.tenant(),
                scope.candidateId()
            );
        });
        assertCandidateRejected("fk_model_release_candidate_entry_revision", () -> {
            Scope scope = seedScope("delete-model-revision");
            repository.insert(draftCandidate(scope.tenant(), scope.candidateId(), scope.planId(), scope.modelId()));
            jdbcTemplate.update(
                """
                delete from modeling_model_spec_revision
                 where tenant_id = ? and model_spec_id = ? and revision = 1
                """,
                scope.tenant(),
                scope.modelId()
            );
        });
        assertCandidateRejected("fk_modeling_model_spec_revision_spec", () -> {
            Scope scope = seedScope("delete-model");
            repository.insert(draftCandidate(scope.tenant(), scope.candidateId(), scope.planId(), scope.modelId()));
            jdbcTemplate.update(
                "delete from modeling_model_spec where tenant_id = ? and id = ?",
                scope.tenant(),
                scope.modelId()
            );
        });
        assertCandidateRejected("fk_model_release_candidate_plan", () -> {
            String tenant = tenant("delete-plan");
            UUID planId = UUID.randomUUID();
            seedPlanAndModels(tenant, planId);
            UUID candidateId = UUID.randomUUID();
            repository.insert(candidate(
                tenant,
                candidateId,
                planId,
                DeliveryStatus.DRAFT,
                1,
                createdAudit(),
                CREATED_AT,
                List.of()
            ));
            jdbcTemplate.update(
                "delete from modeling_warehouse_plan where tenant_id = ? and id = ?",
                tenant,
                planId
            );
        });
    }

    @Test
    void databaseRejectsRawNullIdempotencyHashMissingPublishedAuditAndWhitespaceRoleBypass() {
        assertCandidateRejected("ck_model_release_candidate_identity", () -> {
            String tenant = tenant("raw-idempotency");
            UUID planId = UUID.randomUUID();
            seedPlanAndModels(tenant, planId);
            insertRawCandidateHeader(
                tenant,
                planId,
                DeliveryStatus.DRAFT,
                "request-key",
                null,
                null,
                null,
                null,
                null
            );
        });
        assertCandidateRejected("ck_model_release_candidate_audit", () -> {
            String tenant = tenant("raw-published");
            UUID planId = UUID.randomUUID();
            seedPlanAndModels(tenant, planId);
            insertRawCandidateHeader(
                tenant,
                planId,
                DeliveryStatus.PUBLISHED,
                null,
                null,
                null,
                null,
                null,
                null
            );
        });
        assertCandidateRejected("ck_model_release_candidate_audit", () -> {
            String tenant = tenant("raw-sod");
            UUID planId = UUID.randomUUID();
            seedPlanAndModels(tenant, planId);
            insertRawCandidateHeader(
                tenant,
                planId,
                DeliveryStatus.APPROVED,
                null,
                null,
                "alice",
                CREATED_AT.plusSeconds(1),
                "alice ",
                CREATED_AT.plusSeconds(2)
            );
        });
    }

    @Test
    void transitionPersistsHeaderEntriesAndCommandReceiptAsOnePostgresWrite() {
        String tenant = tenant("command-atomic");
        UUID planId = UUID.randomUUID();
        UUID modelId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        seedPlanAndModels(tenant, planId, modelId);
        CandidateView draft = draftCandidate(tenant, candidateId, planId, modelId);
        repository.insert(draft);
        CommandEventView command = command(
            tenant,
            candidateId,
            planId,
            2,
            "command-atomic-key",
            DeliveryStatus.DRAFT,
            DeliveryStatus.BUILDING
        );

        assertThat(
            repository.transitionAndAppend(
                draft,
                1,
                DeliveryStatus.BUILDING,
                draft.audit(),
                "builder-1",
                CREATED_AT.plusSeconds(1),
                command
            )
        )
            .isEqualTo(1);

        CandidateView stored = repository.find(tenant, candidateId).orElseThrow();
        assertThat(stored.version()).isEqualTo(2);
        assertThat(stored.status()).isEqualTo(DeliveryStatus.BUILDING);
        assertThat(stored.entries()).extracting(EntryView::status).containsOnly(DeliveryStatus.BUILDING);
        assertThat(repository.findCommandByIdempotencyKey(tenant, "command-atomic-key"))
            .get()
            .extracting(CommandEventView::candidateVersion, CommandEventView::fromStatus, CommandEventView::toStatus)
            .containsExactly(2, DeliveryStatus.DRAFT, DeliveryStatus.BUILDING);
    }

    @Test
    void tenantCommandKeyCollisionRollsBackHeaderAndEntriesInPostgres() {
        String tenant = tenant("command-collision");
        UUID firstPlan = UUID.randomUUID();
        UUID firstModel = UUID.randomUUID();
        UUID firstCandidate = UUID.randomUUID();
        UUID secondPlan = UUID.randomUUID();
        UUID secondModel = UUID.randomUUID();
        UUID secondCandidate = UUID.randomUUID();
        try {
            inNewTransaction(() -> {
                seedPlanAndModels(tenant, firstPlan, firstModel);
                seedPlanAndModels(tenant, secondPlan, secondModel);
                repository.insert(draftCandidate(tenant, firstCandidate, firstPlan, firstModel));
                repository.insert(draftCandidate(tenant, secondCandidate, secondPlan, secondModel));
                return null;
            });
            inNewTransaction(() -> {
                CandidateView first = repository.find(tenant, firstCandidate).orElseThrow();
                repository.transitionAndAppend(
                    first,
                    1,
                    DeliveryStatus.BUILDING,
                    first.audit(),
                    "builder-1",
                    CREATED_AT.plusSeconds(1),
                    command(
                        tenant,
                        firstCandidate,
                        firstPlan,
                        2,
                        "tenant-global-command-key",
                        DeliveryStatus.DRAFT,
                        DeliveryStatus.BUILDING
                    )
                );
                return null;
            });

            assertThatThrownBy(() ->
                inNewTransaction(() -> {
                    CandidateView second = repository.find(tenant, secondCandidate).orElseThrow();
                    repository.transitionAndAppend(
                        second,
                        1,
                        DeliveryStatus.BUILDING,
                        second.audit(),
                        "builder-2",
                        CREATED_AT.plusSeconds(2),
                        command(
                            tenant,
                            secondCandidate,
                            secondPlan,
                            2,
                            "tenant-global-command-key",
                            DeliveryStatus.DRAFT,
                            DeliveryStatus.BUILDING
                        )
                    );
                    return null;
                })
            )
                .isInstanceOf(ModelReleaseCandidateRepository.IdempotencyCollisionException.class);

            CandidateView unchanged = inNewTransaction(() ->
                repository.find(tenant, secondCandidate).orElseThrow()
            );
            assertThat(unchanged.version()).isEqualTo(1);
            assertThat(unchanged.status()).isEqualTo(DeliveryStatus.DRAFT);
            assertThat(unchanged.entries()).extracting(EntryView::status).containsOnly(DeliveryStatus.DRAFT);
            assertThat(
                inNewTransaction(() ->
                    jdbcTemplate.queryForObject(
                        "select count(*) from modeling_model_release_candidate_command where tenant_id = ?",
                        Integer.class,
                        tenant
                    )
                )
            )
                .isEqualTo(1);
        } finally {
            cleanupCommittedTenant(tenant);
        }
    }

    @Test
    void allowsExactlyOneOfTwoRequiresNewTransactionsToAdvanceTheSameVersion() throws Exception {
        HikariDataSource hikari = dataSource.unwrap(HikariDataSource.class);
        assertThat(hikari.getMaximumPoolSize())
            .as("the release candidate concurrency test needs at least two database connections")
            .isGreaterThanOrEqualTo(2);
        String tenant = tenant("concurrent");
        UUID planId = UUID.randomUUID();
        UUID modelId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        inNewTransaction(() -> {
            seedPlanAndModels(tenant, planId, modelId);
            repository.insert(draftCandidate(tenant, candidateId, planId, modelId));
            return null;
        });

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Supplier<Integer> update = () ->
                inNewTransaction(() -> {
                    ready.countDown();
                    await(start);
                    return repository.compareAndSetVersion(
                        tenant,
                        candidateId,
                        1,
                        DeliveryStatus.BUILDING,
                        createdAudit(),
                        "builder-1",
                        CREATED_AT.plusSeconds(1)
                    );
                });
            Future<Integer> first = executor.submit(update::get);
            Future<Integer> second = executor.submit(update::get);
            assertThat(ready.await(10, SECONDS)).isTrue();
            start.countDown();
            assertThat(Arrays.asList(first.get(10, SECONDS), second.get(10, SECONDS)))
                .containsExactlyInAnyOrder(0, 1);
            assertThat(repository.find(tenant, candidateId))
                .get()
                .extracting(CandidateView::version, CandidateView::status)
                .containsExactly(2, DeliveryStatus.BUILDING);
        } finally {
            executor.shutdownNow();
            inNewTransaction(() -> {
                jdbcTemplate.update(
                    "delete from modeling_model_release_candidate_entry where tenant_id = ? and candidate_id = ?",
                    tenant,
                    candidateId
                );
                jdbcTemplate.update(
                    "delete from modeling_model_release_candidate where tenant_id = ? and id = ?",
                    tenant,
                    candidateId
                );
                jdbcTemplate.update(
                    "delete from modeling_model_spec_revision where tenant_id = ? and model_spec_id = ?",
                    tenant,
                    modelId
                );
                jdbcTemplate.update(
                    "delete from modeling_model_spec where tenant_id = ? and id = ?",
                    tenant,
                    modelId
                );
                jdbcTemplate.update(
                    "delete from modeling_business_object where tenant_id = ?",
                    tenant
                );
                jdbcTemplate.update(
                    "delete from modeling_warehouse_plan where tenant_id = ? and id = ?",
                    tenant,
                    planId
                );
                return null;
            });
        }
    }

    private static CommandEventView command(
        String tenant,
        UUID candidateId,
        UUID planId,
        int candidateVersion,
        String idempotencyKey,
        DeliveryStatus fromStatus,
        DeliveryStatus toStatus
    ) {
        return new CommandEventView(
            UUID.randomUUID(),
            tenant,
            candidateId,
            planId,
            candidateVersion,
            CommandEventType.STATUS_CHANGED,
            fromStatus,
            toStatus,
            "builder-1",
            CREATED_AT.plusSeconds(candidateVersion),
            "PostgreSQL atomic transition fixture",
            idempotencyKey,
            hash('c'),
            "{\"candidate\":{\"status\":\"" + toStatus.name() + "\"}}"
        );
    }

    private void cleanupCommittedTenant(String tenant) {
        inNewTransaction(() -> {
            jdbcTemplate.execute(
                "alter table modeling_model_release_candidate_command disable trigger " +
                "trg_model_release_candidate_command_append_only"
            );
            jdbcTemplate.execute(
                "alter table modeling_model_release_candidate_command disable trigger " +
                "trg_model_release_candidate_command_no_truncate"
            );
            try {
                jdbcTemplate.update(
                    "delete from modeling_model_release_candidate_command where tenant_id = ?",
                    tenant
                );
                jdbcTemplate.update(
                    "delete from modeling_model_release_candidate_entry where tenant_id = ?",
                    tenant
                );
                jdbcTemplate.update(
                    "delete from modeling_model_release_candidate where tenant_id = ?",
                    tenant
                );
                jdbcTemplate.update(
                    "delete from modeling_model_spec_revision where tenant_id = ?",
                    tenant
                );
                jdbcTemplate.update("delete from modeling_model_spec where tenant_id = ?", tenant);
                jdbcTemplate.update("delete from modeling_business_object where tenant_id = ?", tenant);
                jdbcTemplate.update("delete from modeling_warehouse_plan where tenant_id = ?", tenant);
            } finally {
                jdbcTemplate.execute(
                    "alter table modeling_model_release_candidate_command enable trigger " +
                    "trg_model_release_candidate_command_append_only"
                );
                jdbcTemplate.execute(
                    "alter table modeling_model_release_candidate_command enable trigger " +
                    "trg_model_release_candidate_command_no_truncate"
                );
            }
            return null;
        });
    }

    private Scope seedScope(String suffix) {
        String tenant = tenant(suffix);
        UUID planId = UUID.randomUUID();
        UUID modelId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        seedPlanAndModels(tenant, planId, modelId);
        return new Scope(tenant, planId, modelId, candidateId);
    }

    private CandidateView draftCandidate(String tenant, UUID candidateId, UUID planId, UUID modelId) {
        return candidate(
            tenant,
            candidateId,
            planId,
            DeliveryStatus.DRAFT,
            1,
            createdAudit(),
            CREATED_AT,
            List.of(entry(
                tenant,
                candidateId,
                planId,
                modelId,
                DeliveryStatus.DRAFT,
                1,
                checksum(modelId),
                null,
                0
            ))
        );
    }

    private CandidateView candidate(
        String tenant,
        UUID candidateId,
        UUID planId,
        DeliveryStatus status,
        int version,
        DeliveryAuditView audit,
        Instant lastModifiedAt,
        List<EntryView> entries
    ) {
        return new CandidateView(
            candidateId,
            tenant,
            planId,
            "TEST",
            status,
            version,
            "candidate-" + candidateId,
            hash('1'),
            audit,
            "owner-1",
            lastModifiedAt,
            entries
        );
    }

    private static EntryView entry(
        String tenant,
        UUID candidateId,
        UUID planId,
        UUID modelId,
        DeliveryStatus status,
        int revision,
        String checksum,
        UUID implementationId,
        int sortOrder
    ) {
        return entry(
            tenant,
            candidateId,
            planId,
            modelId,
            status,
            revision,
            checksum,
            implementationId,
            ImplementationMode.DESIGNER_GENERATED,
            sortOrder
        );
    }

    private static EntryView entry(
        String tenant,
        UUID candidateId,
        UUID planId,
        UUID modelId,
        DeliveryStatus status,
        int revision,
        String checksum,
        UUID implementationId,
        ImplementationMode implementationMode,
        int sortOrder
    ) {
        return new EntryView(
            UUID.randomUUID(),
            tenant,
            candidateId,
            planId,
            modelId,
            revision,
            checksum,
            implementationId,
            implementationMode,
            status,
            sortOrder,
            sortOrder == 0 ? "Primary model" : "Dependent model"
        );
    }

    private static DeliveryAuditView createdAudit() {
        return new DeliveryAuditView("owner-1", CREATED_AT, null, null, null, null, null, null);
    }

    private static DeliveryAuditView publishedAudit() {
        return new DeliveryAuditView(
            "owner-1",
            CREATED_AT,
            "submitter-1",
            CREATED_AT.plusSeconds(10),
            "reviewer-1",
            CREATED_AT.plusSeconds(20),
            "operator-1",
            CREATED_AT.plusSeconds(30)
        );
    }

    private void seedPlanAndModels(String tenant, UUID planId, UUID... modelIds) {
        seedPlanAndModels(tenant, planId, ImplementationMode.DESIGNER_GENERATED, modelIds);
    }

    private void seedPlanAndModels(
        String tenant,
        UUID planId,
        ImplementationMode implementationMode,
        UUID... modelIds
    ) {
        String suffix = planId.toString().replace("-", "");
        jdbcTemplate.update(
            """
            insert into modeling_warehouse_plan (
                id, tenant_id, owner, code, name, owner_id, onboarding_mode, lifecycle_status,
                status, version, created_date, last_modified_date
            ) values (?, ?, 'owner-1', ?, 'Release plan', 'owner-1', 'BUSINESS_FIRST', 'DRAFT',
                      'DRAFT', 1, current_timestamp, current_timestamp)
            """,
            planId,
            tenant,
            "release_" + suffix
        );
        for (UUID modelId : modelIds) {
            UUID objectId = UUID.randomUUID();
            jdbcTemplate.update(
                """
                insert into modeling_business_object (
                    id, tenant_id, owner, code, name, object_kind, status, version,
                    created_date, last_modified_date
                ) values (?, ?, 'owner-1', ?, 'Release object', 'BUSINESS_OBJECT', 'DRAFT', 1,
                          current_timestamp, current_timestamp)
                """,
                objectId,
                tenant,
                "object_" + modelId.toString().replace("-", "")
            );
            jdbcTemplate.update(
                """
                insert into modeling_model_spec (
                    id, tenant_id, object_id, plan_id, process_id, layer, model_type,
                    implementation_mode, name, status, revision, version, created_date, last_modified_date
                ) values (?, ?, ?, ?, 'release-process', 'DWD', 'FACT', ?,
                          'Release model', 'DRAFT', 1, 1, current_timestamp, current_timestamp)
                """,
                modelId,
                tenant,
                objectId,
                planId,
                implementationMode.name()
            );
            jdbcTemplate.update(
                """
                insert into modeling_model_spec_revision (
                    id, model_spec_id, revision, spec_json, status, content_checksum, created_date,
                    last_modified_date, tenant_id, contract_version, created_by
                ) values (?, ?, 1, '{}', 'DRAFT', ?, current_timestamp, current_timestamp, ?, 1, 'owner-1')
                """,
                UUID.randomUUID(),
                modelId,
                checksum(modelId),
                tenant
            );
        }
    }

    private void seedCanonicalV2Model(String tenant, UUID planId, UUID modelId, String currentChecksum) {
        UUID domainId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into catalog_domain (id, name, code, lifecycle_status, access_policy) values (?, ?, ?, 'ACTIVE', 'PUBLIC')",
            domainId,
            "Release candidate domain",
            "RELEASE_" + domainId.toString().replace("-", "")
        );
        jdbcTemplate.update(
            """
            insert into modeling_model_spec (
                id, tenant_id, object_id, plan_id, process_id, layer, model_type,
                implementation_mode, name, status, revision, version, created_date, last_modified_date,
                contract_version, domain_id, current_checksum, idempotency_key,
                idempotency_request_hash, idempotency_response_snapshot
            ) values (
                ?, ?, null, ?, null, 'DWD', 'FACT', 'DBT_MANAGED', ?,
                'DRAFT', 2, 1, current_timestamp, current_timestamp, 2, ?, ?, ?,
                ?, cast('{}' as jsonb)
            )
            """,
            modelId,
            tenant,
            planId,
            "Canonical release model " + modelId,
            domainId,
            currentChecksum,
            "canonical-" + modelId,
            hash('d')
        );
        jdbcTemplate.update(
            """
            insert into modeling_model_spec_revision (
                id, model_spec_id, revision, spec_json, status, content_checksum, created_date,
                last_modified_date, tenant_id, contract_version, snapshot_json, created_by
            ) values (
                ?, ?, 2, null, 'DRAFT', ?, current_timestamp, current_timestamp, ?, 2,
                cast('{}' as jsonb), 'owner-1'
            )
            """,
            UUID.randomUUID(),
            modelId,
            currentChecksum,
            tenant
        );
    }

    private void insertRawEntry(String tenant, UUID candidateId, UUID planId, UUID modelId, int sortOrder) {
        jdbcTemplate.update(
            """
            insert into modeling_model_release_candidate_entry (
                id, tenant_id, candidate_id, plan_id, model_spec_id, revision, checksum,
                implementation_mode, status, sort_order
            ) values (?, ?, ?, ?, ?, 1, ?, 'DESIGNER_GENERATED', 'DRAFT', ?)
            """,
            UUID.randomUUID(),
            tenant,
            candidateId,
            planId,
            modelId,
            checksum(modelId),
            sortOrder
        );
    }

    private void insertRawCandidateHeader(
        String tenant,
        UUID planId,
        DeliveryStatus status,
        String idempotencyKey,
        String requestHash,
        String submittedBy,
        Instant submittedAt,
        String approvedBy,
        Instant approvedAt
    ) {
        jdbcTemplate.update(
            """
            insert into modeling_model_release_candidate (
                id, tenant_id, plan_id, environment, status, version, idempotency_key, request_hash,
                created_by, created_date, submitted_by, submitted_date, approved_by, approved_date,
                last_modified_by, last_modified_date
            ) values (?, ?, ?, 'TEST', ?, 1, ?, ?, 'owner-1', ?, ?, ?, ?, ?, 'owner-1', ?)
            """,
            UUID.randomUUID(),
            tenant,
            planId,
            status.name(),
            idempotencyKey,
            requestHash,
            Timestamp.from(CREATED_AT),
            submittedBy,
            submittedAt == null ? null : Timestamp.from(submittedAt),
            approvedBy,
            approvedAt == null ? null : Timestamp.from(approvedAt),
            Timestamp.from(approvedAt == null ? CREATED_AT : approvedAt)
        );
    }

    private UUID seedImplementation(String tenant, UUID planId, UUID modelId) {
        return seedImplementation(
            tenant,
            planId,
            modelId,
            ImplementationMode.DESIGNER_GENERATED
        );
    }

    private UUID seedImplementation(
        String tenant,
        UUID planId,
        UUID modelId,
        ImplementationMode ownership
    ) {
        UUID implementationId = UUID.randomUUID();
        String modelChecksum = checksum(modelId);
        Integer inserted = jdbcTemplate.queryForObject(
            """
            with inserted_head as (
                insert into modeling_model_implementation (
                    id, tenant_id, model_spec_id, plan_id, model_revision, model_checksum,
                    ownership, project_key, dbt_unique_id, status, idempotency_key, created_by,
                    created_date, last_modified_date, implementation_revision,
                    current_implementation_checksum, input_mode, inputs_json, field_mappings_json,
                    settings_json, materialization
                ) values (
                    ?, ?, ?, ?, 1, ?, ?, 'release-it', ?, 'CLAIMED', ?,
                    'owner-1', current_timestamp, current_timestamp, 1, ?, 'GENERATED',
                    cast('[{"generatorType":"RELEASE_IT","config":{}}]' as jsonb),
                    cast('[]' as jsonb), cast('{}' as jsonb), 'table'
                )
                returning tenant_id, id, implementation_revision, current_implementation_checksum,
                          input_mode, inputs_json, field_mappings_json, settings_json, ownership, materialization
            ), inserted_revision as (
                insert into modeling_model_implementation_revision (
                    id, tenant_id, implementation_id, revision, content_checksum, input_mode,
                    inputs_json, field_mappings_json, settings_json, ownership, materialization,
                    created_by, created_date
                )
                select ?, tenant_id, id, implementation_revision, current_implementation_checksum,
                       input_mode, inputs_json, field_mappings_json, settings_json, ownership,
                       materialization, 'owner-1', current_timestamp
                  from inserted_head
                returning 1
            )
            select count(*)::int from inserted_revision
            """,
            Integer.class,
            implementationId,
            tenant,
            modelId,
            planId,
            modelChecksum,
            ownership.name(),
            "model.release_it." + modelId,
            "implementation-" + implementationId,
            hash('e'),
            UUID.randomUUID()
        );
        assertThat(inserted).isEqualTo(1);
        return implementationId;
    }

    private void assertCandidateRejected(String constraintName, Runnable operation) {
        assertThatThrownBy(() ->
            inNewTransaction(() -> {
                operation.run();
                return null;
            })
        )
            .isInstanceOf(DataIntegrityViolationException.class)
            .satisfies(error ->
                assertThat(((DataIntegrityViolationException) error).getMostSpecificCause().getMessage())
                    .contains(constraintName)
            );
    }

    private <T> T inNewTransaction(Supplier<T> work) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transaction.execute(status -> work.get());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, SECONDS)) throw new IllegalStateException("Concurrent update latch timed out");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent update interrupted", error);
        }
    }

    private static String tenant(String suffix) {
        return "release-candidate-" + suffix + "-" + UUID.randomUUID();
    }

    private static String checksum(UUID id) {
        String value = id.toString().replace("-", "");
        return value + value;
    }

    private static String hash(char value) {
        return String.valueOf(value).repeat(64);
    }

    private record Scope(String tenant, UUID planId, UUID modelId, UUID candidateId) {}
}
