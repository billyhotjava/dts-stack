package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogTag;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTagRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class CatalogTagMigrationServiceTest {

    private static final UUID DATASET_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SECOND_DATASET_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CATEGORY_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID TRUSTED_TAG_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID CORE_TAG_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @Mock
    private CatalogDatasetRepository datasetRepository;

    @Mock
    private CatalogTagRepository tagRepository;

    @Mock
    private CatalogTagMigrationStore store;

    private CatalogTagMigrationService service;

    @BeforeEach
    void setUp() {
        service = new CatalogTagMigrationService(
            datasetRepository,
            tagRepository,
            store,
            new ObjectMapper().findAndRegisterModules(),
            Clock.fixed(Instant.parse("2026-07-25T08:00:00Z"), ZoneOffset.UTC)
        );
        org.mockito.Mockito
            .lenient()
            .when(store.findExistingRelations(anyCollection()))
            .thenReturn(Set.of());
    }

    @Test
    void dryRunIsDeterministicAndParsesControlledMixedDelimiterFixtureWithoutWritingLedger() {
        CatalogDataset dataset = dataset(
            DATASET_ID,
            " 高可信，核心; 高可信  未命中；核心 "
        );
        stubDatasets(List.of(dataset));
        stubTags(
            List.of(tag(TRUSTED_TAG_ID, "QUALITY-TRUSTED", "高可信"), tag(CORE_TAG_ID, "DOMAIN-CORE", "核心"))
        );

        var first = service.dryRun();
        var second = service.dryRun();

        assertThat(first).isEqualTo(second);
        assertThat(first.batchId()).isEqualTo("catalog-tags-" + first.checksum().substring(0, 24));
        assertThat(first.checksum()).matches("[0-9a-f]{64}");
        assertThat(first.datasetCount()).isEqualTo(1);
        assertThat(first.nonEmptyDatasetCount()).isEqualTo(1);
        assertThat(first.tokenCount()).isEqualTo(3);
        assertThat(first.matchedTokenCount()).isEqualTo(2);
        assertThat(first.unmatchedTokens()).extracting(issue -> issue.token()).containsExactly("未命中");
        assertThat(first.ambiguousTokens()).isEmpty();
        assertThat(first.protectedEvidence()).isEmpty();
        assertThat(first.plannedRelationCount()).isEqualTo(2);
        assertThat(first.existingRelationCount()).isZero();
        assertThat(first.relations()).extracting(relation -> relation.tagId()).containsExactly(
            TRUSTED_TAG_ID,
            CORE_TAG_ID
        );
        verify(store, never()).insertBatch(org.mockito.ArgumentMatchers.any());
        verify(store, never()).insertRelation(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void exactMatchingIsCaseSensitiveAndAmbiguousNamesAndMachineEvidenceAreNeverMigrated() {
        CatalogDataset human = dataset(DATASET_ID, "高可, HIGH TRUST, 重名");
        CatalogDataset jsonEvidence = dataset(
            SECOND_DATASET_ID,
            "{\"materializedTruth\":true,\"runId\":\"r-1\"}"
        );
        CatalogDataset apiEvidence = dataset(
            UUID.fromString("66666666-6666-6666-6666-666666666666"),
            "origin=API;connectionId=c-1;taskId=t-1;landingStatus=SUCCESS"
        );
        stubDatasets(List.of(apiEvidence, jsonEvidence, human));
        stubTags(
            List.of(
                tag(TRUSTED_TAG_ID, "QUALITY-TRUSTED", "High Trust"),
                tag(CORE_TAG_ID, "DUPLICATE-A", "重名"),
                tag(UUID.fromString("77777777-7777-7777-7777-777777777777"), "DUPLICATE-B", "重名"),
                tag(UUID.fromString("88888888-8888-8888-8888-888888888888"), "JSON-COLLISION", jsonEvidence.getTags()),
                tag(UUID.fromString("99999999-9999-9999-9999-999999999999"), "API-COLLISION", apiEvidence.getTags())
            )
        );

        var report = service.dryRun();

        assertThat(report.matchedTokenCount()).isZero();
        assertThat(report.relations()).isEmpty();
        assertThat(report.unmatchedTokens()).extracting(issue -> issue.token()).containsExactly(
            "高可",
            "HIGH",
            "TRUST"
        );
        assertThat(report.ambiguousTokens()).singleElement().satisfies(issue -> {
            assertThat(issue.token()).isEqualTo("重名");
            assertThat(issue.candidateTagIds()).containsExactlyInAnyOrder(
                CORE_TAG_ID,
                UUID.fromString("77777777-7777-7777-7777-777777777777")
            );
        });
        assertThat(report.protectedEvidence()).extracting(evidence -> evidence.format()).containsExactly(
            "JSON_OBJECT",
            "KEY_VALUE_EVIDENCE"
        );
        assertThat(report.protectedEvidence()).extracting(evidence -> evidence.originalTags()).containsExactly(
            jsonEvidence.getTags(),
            apiEvidence.getTags()
        );
    }

    @Test
    void disabledExactNameIsReportedAsConflictEvidenceAndNeverCreatesARelation() {
        CatalogDataset dataset = dataset(DATASET_ID, "高可信");
        CatalogTag disabled = tag(TRUSTED_TAG_ID, "QUALITY-TRUSTED", "高可信");
        disabled.setEnabled(false);
        stubDatasets(List.of(dataset));
        stubTags(List.of(disabled));

        var report = service.dryRun();

        assertThat(report.matchedTokenCount()).isZero();
        assertThat(report.relations()).isEmpty();
        assertThat(report.unmatchedTokens()).singleElement().satisfies(issue -> {
            assertThat(issue.token()).isEqualTo("高可信");
            assertThat(issue.reason()).isEqualTo("DISABLED_NAME");
            assertThat(issue.candidateTagIds()).containsExactly(TRUSTED_TAG_ID);
        });
    }

    @Test
    void dryRunRejectsDatasetCatalogAboveTheStrictCapacityBeforeMaterializingIt() {
        when(datasetRepository.findAll(any(Pageable.class))).thenReturn(
            new PageImpl<>(
                List.of(),
                PageRequest.of(0, 1),
                CatalogTagMigrationService.MAX_PLAN_DATASETS + 1L
            )
        );
        stubTags(List.of());

        assertThatThrownBy(service::dryRun)
            .isInstanceOf(CatalogTagMigrationConflictException.class)
            .hasMessageContaining("MIGRATION_DATASET_CAPACITY_EXCEEDED");
    }

    @Test
    void dryRunRejectsTagCatalogAboveTheStrictCapacityBeforeScanningDatasets() {
        when(tagRepository.findAll(any(Pageable.class))).thenReturn(
            new PageImpl<>(
                List.of(),
                PageRequest.of(0, 1),
                CatalogTagMigrationService.MAX_PLAN_TAGS + 1L
            )
        );

        assertThatThrownBy(service::dryRun)
            .isInstanceOf(CatalogTagMigrationConflictException.class)
            .hasMessageContaining("MIGRATION_TAG_CAPACITY_EXCEEDED");
        verify(datasetRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void checksumCoversUntouchedLegacySourceCanonicalKeyAndTagDecision() {
        CatalogDataset original = dataset(DATASET_ID, "高可信");
        stubDatasets(List.of(original));
        stubTags(List.of(tag(TRUSTED_TAG_ID, "QUALITY-TRUSTED", "高可信")));
        String sourceChecksum = service.dryRun().checksum();

        CatalogDataset sourceDrift = dataset(DATASET_ID, " 高可信 ");
        stubDatasets(List.of(sourceDrift));
        assertThat(service.dryRun().checksum()).isNotEqualTo(sourceChecksum);

        CatalogDataset identityDrift = dataset(DATASET_ID, "高可信");
        identityDrift.setHiveTable("orders_v2");
        stubDatasets(List.of(identityDrift));
        assertThat(service.dryRun().checksum()).isNotEqualTo(sourceChecksum);

        stubDatasets(List.of(original));
        stubTags(
            List.of(tag(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), "QUALITY-TRUSTED-V2", "高可信"))
        );
        assertThat(service.dryRun().checksum()).isNotEqualTo(sourceChecksum);
    }

    @Test
    void executeRejectsAStaleDryRunBeforeAnyLedgerOrRelationWrite() {
        CatalogDataset original = dataset(DATASET_ID, "高可信");
        stubDatasets(List.of(original));
        stubTags(List.of(tag(TRUSTED_TAG_ID, "QUALITY-TRUSTED", "高可信")));
        var dryRun = service.dryRun();

        CatalogDataset changed = dataset(DATASET_ID, "高可信, 核心");
        stubDatasets(List.of(changed));

        assertThatThrownBy(() ->
                service.execute(dryRun.batchId(), dryRun.checksum(), "alice")
            )
            .isInstanceOf(CatalogTagMigrationConflictException.class)
            .hasMessageContaining("MIGRATION_PLAN_DRIFT");

        verify(store).acquireMigrationLock();
        verify(store, never()).insertBatch(org.mockito.ArgumentMatchers.any());
        verify(store, never()).insertRelation(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void executeLocksMigrationInputsBeforeRecomputingTheAcceptedPlan() {
        CatalogDataset original = dataset(DATASET_ID, "高可信");
        stubDatasets(List.of(original));
        stubTags(
            List.of(tag(TRUSTED_TAG_ID, "QUALITY-TRUSTED", "高可信"))
        );
        var dryRun = service.dryRun();
        org.mockito.Mockito.clearInvocations(datasetRepository, tagRepository, store);
        when(store.lockBatch(dryRun.batchId())).thenReturn(Optional.empty());
        when(store.insertBatch(org.mockito.ArgumentMatchers.any())).thenReturn(1);
        when(
            store.insertRelation(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(dryRun.batchId()),
                org.mockito.ArgumentMatchers.eq("alice"),
                org.mockito.ArgumentMatchers.any()
            )
        ).thenReturn(Optional.empty());
        when(
            store.markExecuted(
                org.mockito.ArgumentMatchers.eq(dryRun.batchId()),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("alice"),
                org.mockito.ArgumentMatchers.any()
            )
        ).thenReturn(1);

        service.execute(dryRun.batchId(), dryRun.checksum(), "alice");

        var ordered = org.mockito.Mockito.inOrder(
            store,
            datasetRepository,
            tagRepository
        );
        ordered.verify(store).acquireMigrationLock();
        ordered.verify(store).lockBatch(dryRun.batchId());
        ordered.verify(store).lockMigrationInputs();
        ordered.verify(tagRepository).findAll(any(Pageable.class));
        ordered.verify(datasetRepository).findAll(any(Pageable.class));
    }

    @Test
    void replayedExecutionReturnsFromLedgerWithoutLockingOrScanningInputs() {
        String checksum = "a".repeat(64);
        String batchId = "catalog-tags-" + "a".repeat(24);
        String executionJson =
            """
            {
              "batchId": "%s",
              "checksum": "%s",
              "matched": 0,
              "unmatched": 0,
              "ambiguous": 0,
              "protectedEvidence": 0,
              "created": 0,
              "skipped": 0,
              "createdRelations": []
            }
            """.formatted(batchId, checksum);
        when(store.lockBatch(batchId)).thenReturn(
            Optional.of(
                new CatalogTagMigrationStore.BatchRecord(
                    batchId,
                    checksum,
                    "EXECUTED",
                    "{}",
                    executionJson,
                    null,
                    "alice",
                    Instant.parse("2026-07-25T08:00:00Z")
                )
            )
        );

        var execution = service.execute(batchId, checksum, "alice");

        assertThat(execution.replayed()).isTrue();
        verify(store).acquireMigrationLock();
        verify(store, never()).lockMigrationInputs();
        verify(datasetRepository, never()).findAll(any(Pageable.class));
        verify(tagRepository, never()).findAll(any(Pageable.class));
    }

    private void stubDatasets(List<CatalogDataset> datasets) {
        List<CatalogDataset> sorted = datasets
            .stream()
            .sorted(
                java.util.Comparator.comparing(
                    dataset -> dataset.getId().toString()
                )
            )
            .toList();
        when(datasetRepository.findAll(any(Pageable.class))).thenReturn(
            new PageImpl<>(sorted)
        );
    }

    private void stubTags(List<CatalogTag> tags) {
        List<CatalogTag> sorted = tags
            .stream()
            .sorted(
                java.util.Comparator.comparing(tag -> tag.getId().toString())
            )
            .toList();
        when(tagRepository.findAll(any(Pageable.class))).thenReturn(
            new PageImpl<>(sorted)
        );
    }

    private CatalogDataset dataset(UUID id, String tags) {
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(id);
        dataset.setName("orders");
        dataset.setSourceId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
        dataset.setHiveDatabase("dwd");
        dataset.setHiveTable("orders");
        dataset.setTags(tags);
        return dataset;
    }

    private CatalogTag tag(UUID id, String code, String name) {
        CatalogTag tag = new CatalogTag();
        tag.setId(id);
        tag.setCategoryId(CATEGORY_ID);
        tag.setCode(code);
        tag.setName(name);
        tag.setEnabled(true);
        return tag;
    }
}
