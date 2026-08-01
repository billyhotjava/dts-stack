package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.modeling.CandidatePublicationEvidenceRepository;
import com.yuzhi.dts.platform.repository.modeling.CandidatePublicationEvidenceRepository.PublicationEntryEvidence;
import com.yuzhi.dts.platform.security.policy.AssetAction;
import com.yuzhi.dts.platform.service.catalog.CatalogPublicationPolicyAdapter;
import com.yuzhi.dts.platform.service.catalog.CatalogPublicationPolicyPort.Action;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryAuditView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.EntryView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.ExpectedRelationType;
import com.yuzhi.dts.platform.service.modeling.ModelExecutionTargetCatalogResolver.ResolvedCatalogTarget;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CandidatePublicationAdmissionServiceTest {

    private static final String TENANT = "tenant-a";
    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID CANDIDATE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID FIRST_MODEL_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID SECOND_MODEL_ID = UUID.fromString("30000000-0000-0000-0000-000000000002");
    private static final UUID SOURCE_ID = UUID.fromString("60000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-07-28T10:00:00Z");

    @Mock
    private CandidatePublicationEvidenceRepository evidence;

    @Mock
    private CatalogDatasetRepository catalogs;

    @Mock
    private AccessChecker accessChecker;

    @Mock
    private ModelExecutionTargetCatalogResolver targetResolver;

    private CandidatePublicationAdmissionService service;

    @BeforeEach
    void setUp() {
        service = new CandidatePublicationAdmissionService(
            evidence,
            new CatalogPublicationPolicyAdapter(catalogs, accessChecker),
            targetResolver
        );
    }

    @Test
    void requiresCreateForNewAssetAndUpdateForExistingAsset() {
        CandidateView candidate = candidate();
        List<PublicationEntryEvidence> observations = List.of(
            observation(FIRST_MODEL_ID, "fct_payment"),
            observation(SECOND_MODEL_ID, "dim_account")
        );
        UUID firstAssetId = CandidatePublicationAssetFactory.catalogDatasetId(
            SOURCE_ID,
            "finance",
            "fct_payment"
        );
        UUID secondAssetId = CandidatePublicationAssetFactory.catalogDatasetId(
            SOURCE_ID,
            "finance",
            "dim_account"
        );
        CatalogDataset existing = new CatalogDataset();
        existing.setId(secondAssetId);
        existing.setName("dim_account");
        existing.setSourceId(SOURCE_ID);
        when(targetResolver.resolve(candidate)).thenReturn(target());
        when(evidence.requireCurrent(candidate, false)).thenReturn(observations);
        when(
            catalogs.findByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(
                "finance",
                "fct_payment"
            )
        ).thenReturn(List.of());
        when(
            catalogs.findByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(
                "finance",
                "dim_account"
            )
        ).thenReturn(List.of(existing));
        when(accessChecker.canPerform(org.mockito.ArgumentMatchers.any(CatalogDataset.class), org.mockito.ArgumentMatchers.any()))
            .thenReturn(true);

        var admitted = service.requireAllowed(candidate);

        assertThat(admitted).hasSize(2);
        verify(accessChecker).canPerform(
            org.mockito.ArgumentMatchers.argThat(asset -> firstAssetId.equals(asset.getId())),
            org.mockito.ArgumentMatchers.eq(AssetAction.CREATE)
        );
        verify(accessChecker).canPerform(existing, AssetAction.UPDATE);
    }

    @Test
    void failsClosedBeforePublicationWhenAnyPhysicalAssetActionIsDenied() {
        CandidateView candidate = candidate();
        PublicationEntryEvidence current = observation(FIRST_MODEL_ID, "fct_payment");
        when(targetResolver.resolve(candidate)).thenReturn(target());
        when(evidence.requireCurrent(candidate, false)).thenReturn(List.of(current));
        when(
            catalogs.findByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(
                "finance",
                "fct_payment"
            )
        ).thenReturn(List.of());
        when(accessChecker.canPerform(org.mockito.ArgumentMatchers.any(CatalogDataset.class), org.mockito.ArgumentMatchers.eq(AssetAction.CREATE)))
            .thenReturn(false);

        assertThatThrownBy(() -> service.requireAllowed(candidate))
            .isInstanceOf(ModelReleaseCandidateException.class)
            .satisfies(error -> {
                ModelReleaseCandidateException failure = (ModelReleaseCandidateException) error;
                assertThat(failure.code()).isEqualTo("MODEL_RELEASE_ASSET_ACTION_FORBIDDEN");
                assertThat(failure.kind()).isEqualTo(ModelReleaseCandidateException.Kind.FORBIDDEN);
                java.util.Map<?, ?> details = (java.util.Map<?, ?>) failure.details();
                assertThat(details.get("candidateId")).isEqualTo(CANDIDATE_ID);
                assertThat(details.get("modelSpecId")).isEqualTo(FIRST_MODEL_ID);
                assertThat(details.get("assetAction")).isEqualTo(Action.CREATE);
            });
    }

    @Test
    void failsClosedWithStableBlockerWhenAssetPolicyCannotBeEvaluated() {
        CandidateView candidate = candidate();
        PublicationEntryEvidence current = observation(FIRST_MODEL_ID, "fct_payment");
        when(targetResolver.resolve(candidate)).thenReturn(target());
        when(evidence.requireCurrent(candidate, false)).thenReturn(List.of(current));
        when(
            catalogs.findByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(
                "finance",
                "fct_payment"
            )
        ).thenReturn(List.of());
        when(
            accessChecker.canPerform(
                org.mockito.ArgumentMatchers.any(CatalogDataset.class),
                org.mockito.ArgumentMatchers.eq(AssetAction.CREATE)
            )
        )
            .thenThrow(new IllegalStateException("policy repository unavailable"));

        assertThatThrownBy(() -> service.requireAllowed(candidate))
            .isInstanceOf(ModelReleaseCandidateException.class)
            .satisfies(error -> {
                ModelReleaseCandidateException failure = (ModelReleaseCandidateException) error;
                assertThat(failure.code()).isEqualTo("MODEL_RELEASE_ASSET_POLICY_UNAVAILABLE");
                assertThat(failure.kind()).isEqualTo(ModelReleaseCandidateException.Kind.FORBIDDEN);
                java.util.Map<?, ?> details = (java.util.Map<?, ?>) failure.details();
                assertThat(details.get("candidateId")).isEqualTo(CANDIDATE_ID);
                assertThat(details.get("modelSpecId")).isEqualTo(FIRST_MODEL_ID);
                assertThat(details.get("assetAction")).isEqualTo(Action.CREATE);
            });
    }

    @Test
    void requiresArchiveOnTheExactPublishedPhysicalAsset() {
        CandidateView candidate = candidate(DeliveryStatus.PUBLISHED);
        PublicationEntryEvidence current = observation(FIRST_MODEL_ID, "fct_payment");
        UUID assetId = CandidatePublicationAssetFactory.catalogDatasetId(
            SOURCE_ID,
            "finance",
            "fct_payment"
        );
        CatalogDataset asset = publishedAsset(assetId);
        when(evidence.requireCurrent(candidate, false)).thenReturn(List.of(current));
        when(evidence.requirePublishedAssetId(candidate, current)).thenReturn(assetId);
        when(catalogs.findById(assetId)).thenReturn(Optional.of(asset));
        when(accessChecker.canPerform(asset, AssetAction.ARCHIVE)).thenReturn(true);

        assertThat(service.requireArchiveAllowed(candidate)).containsExactly(current);

        verify(accessChecker).canPerform(asset, AssetAction.ARCHIVE);
    }

    @Test
    void failsClosedBeforeRollbackWhenArchiveIsDenied() {
        CandidateView candidate = candidate(DeliveryStatus.PUBLISHED);
        PublicationEntryEvidence current = observation(FIRST_MODEL_ID, "fct_payment");
        UUID assetId = CandidatePublicationAssetFactory.catalogDatasetId(
            SOURCE_ID,
            "finance",
            "fct_payment"
        );
        CatalogDataset asset = publishedAsset(assetId);
        when(evidence.requireCurrent(candidate, false)).thenReturn(List.of(current));
        when(evidence.requirePublishedAssetId(candidate, current)).thenReturn(assetId);
        when(catalogs.findById(assetId)).thenReturn(Optional.of(asset));
        when(accessChecker.canPerform(asset, AssetAction.ARCHIVE)).thenReturn(false);

        assertThatThrownBy(() -> service.requireArchiveAllowed(candidate))
            .isInstanceOf(ModelReleaseCandidateException.class)
            .satisfies(error -> {
                ModelReleaseCandidateException failure = (ModelReleaseCandidateException) error;
                assertThat(failure.code()).isEqualTo("MODEL_RELEASE_ASSET_ACTION_FORBIDDEN");
                assertThat(failure.kind()).isEqualTo(ModelReleaseCandidateException.Kind.FORBIDDEN);
                java.util.Map<?, ?> details = (java.util.Map<?, ?>) failure.details();
                assertThat(details.get("assetId")).isEqualTo(assetId);
                assertThat(details.get("assetAction")).isEqualTo(Action.ARCHIVE);
            });
    }

    @Test
    void failsClosedBeforeRollbackWhenArchivePolicyCannotBeEvaluated() {
        CandidateView candidate = candidate(DeliveryStatus.PUBLISHED);
        PublicationEntryEvidence current = observation(FIRST_MODEL_ID, "fct_payment");
        UUID assetId = CandidatePublicationAssetFactory.catalogDatasetId(
            SOURCE_ID,
            "finance",
            "fct_payment"
        );
        CatalogDataset asset = publishedAsset(assetId);
        when(evidence.requireCurrent(candidate, false)).thenReturn(List.of(current));
        when(evidence.requirePublishedAssetId(candidate, current)).thenReturn(assetId);
        when(catalogs.findById(assetId)).thenReturn(Optional.of(asset));
        when(accessChecker.canPerform(asset, AssetAction.ARCHIVE))
            .thenThrow(new IllegalStateException("policy repository unavailable"));

        assertThatThrownBy(() -> service.requireArchiveAllowed(candidate))
            .isInstanceOf(ModelReleaseCandidateException.class)
            .satisfies(error -> {
                ModelReleaseCandidateException failure = (ModelReleaseCandidateException) error;
                assertThat(failure.code()).isEqualTo("MODEL_RELEASE_ASSET_POLICY_UNAVAILABLE");
                assertThat(failure.kind()).isEqualTo(ModelReleaseCandidateException.Kind.FORBIDDEN);
                java.util.Map<?, ?> details = (java.util.Map<?, ?>) failure.details();
                assertThat(details.get("assetId")).isEqualTo(assetId);
                assertThat(details.get("assetAction")).isEqualTo(Action.ARCHIVE);
            });
    }

    private static CandidateView candidate() {
        return candidate(DeliveryStatus.APPROVED);
    }

    private static CandidateView candidate(DeliveryStatus status) {
        DeliveryAuditView audit = new DeliveryAuditView(
            "creator",
            NOW.minusSeconds(180),
            "submitter",
            NOW.minusSeconds(120),
            "reviewer",
            NOW.minusSeconds(60),
            status == DeliveryStatus.PUBLISHED ? "release-operator" : null,
            status == DeliveryStatus.PUBLISHED ? NOW.minusSeconds(60) : null
        );
        return new CandidateView(
            CANDIDATE_ID,
            TENANT,
            PLAN_ID,
            "prod",
            status,
            9,
            "candidate-key",
            "a".repeat(64),
            audit,
            "reviewer",
            NOW.minusSeconds(60),
            List.of(
                entry(FIRST_MODEL_ID, 0, status),
                entry(SECOND_MODEL_ID, 1, status)
            ),
            ModelReleaseCandidateContract.CandidateOrigin.BATCH_WORKBENCH,
            "postgres:warehouse/prod",
            "postgres",
            "warehouse",
            "prod"
        );
    }

    private static CatalogDataset publishedAsset(UUID assetId) {
        CatalogDataset asset = new CatalogDataset();
        asset.setId(assetId);
        asset.setEnabled(true);
        asset.setLifecycleStatus("PUBLISHED");
        return asset;
    }

    private static EntryView entry(UUID modelId, int sortOrder, DeliveryStatus status) {
        return new EntryView(
            UUID.nameUUIDFromBytes(("entry:" + modelId).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
            TENANT,
            CANDIDATE_ID,
            PLAN_ID,
            modelId,
            2,
            "b".repeat(64),
            UUID.nameUUIDFromBytes(("implementation:" + modelId).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
            ImplementationMode.DBT_MANAGED,
            status,
            sortOrder,
            "release"
        );
    }

    private static PublicationEntryEvidence observation(UUID modelId, String identifier) {
        return new PublicationEntryEvidence(
            UUID.nameUUIDFromBytes(("entry:" + modelId).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
            modelId,
            2,
            "b".repeat(64),
            3,
            "c".repeat(64),
            "model.finance." + identifier,
            identifier,
            "d".repeat(64),
            "e".repeat(64),
            UUID.fromString("40000000-0000-0000-0000-000000000001"),
            UUID.nameUUIDFromBytes(("run:" + modelId).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
            UUID.fromString("50000000-0000-0000-0000-000000000001"),
            "postgres",
            "warehouse",
            "finance",
            identifier,
            ExpectedRelationType.TABLE,
            "f".repeat(64),
            NOW
        );
    }

    private static ResolvedCatalogTarget target() {
        return new ResolvedCatalogTarget(
            "postgres:warehouse/prod",
            SOURCE_ID,
            "postgres"
        );
    }
}
