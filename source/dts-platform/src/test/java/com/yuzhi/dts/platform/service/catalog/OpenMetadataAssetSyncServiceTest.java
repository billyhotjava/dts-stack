package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.OpenMetadataProperties;
import com.yuzhi.dts.platform.domain.catalog.CatalogAssetExtension;
import com.yuzhi.dts.platform.domain.catalog.CatalogAssetMapping;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.OpenMetadataAssetCache;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.catalog.CatalogAssetExtensionRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogAssetMappingRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.OpenMetadataAssetCacheRepository;
import com.yuzhi.dts.platform.repository.catalog.OpenMetadataColumnCacheRepository;
import com.yuzhi.dts.platform.repository.catalog.OpenMetadataLineageCacheRepository;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.service.openmetadata.OpenMetadataClient;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OpenMetadataAssetSyncServiceTest {

    @Mock
    private OpenMetadataClient client;

    @Mock
    private OpenMetadataAssetCacheRepository assetRepository;

    @Mock
    private OpenMetadataColumnCacheRepository columnRepository;

    @Mock
    private OpenMetadataLineageCacheRepository lineageRepository;

    @Mock
    private CatalogAssetMappingRepository mappingRepository;

    @Mock
    private CatalogAssetExtensionRepository extensionRepository;

    @Mock
    private CatalogDatasetRepository datasetRepository;

    @Mock
    private InfraDataSourceRepository dataSourceRepository;

    @Mock
    private EntityManager entityManager;

    private OpenMetadataAssetSyncService service;

    @BeforeEach
    void setUp() {
        OpenMetadataProperties properties = new OpenMetadataProperties();
        properties.setEnabled(true);
        service =
            new OpenMetadataAssetSyncService(
                client,
                properties,
                assetRepository,
                columnRepository,
                lineageRepository,
                mappingRepository,
                extensionRepository,
                datasetRepository,
                dataSourceRepository,
                entityManager,
                new ObjectMapper()
            );
        when(client.listTables(anyInt(), anyString())).thenReturn(Optional.of(Map.of("data", List.of(tablePayload()))));
        when(assetRepository.findFirstByFqnIgnoreCase("svc.db.public.orders")).thenReturn(Optional.empty());
        when(assetRepository.save(any(OpenMetadataAssetCache.class))).thenAnswer(invocation -> {
            OpenMetadataAssetCache asset = invocation.getArgument(0);
            asset.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
            return asset;
        });
        when(mappingRepository.save(any(CatalogAssetMapping.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(extensionRepository.save(any(CatalogAssetExtension.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void ambiguousLegacyCandidatesRequireManualReviewAndClearStaleLinks() {
        CatalogDataset first = dataset("22222222-2222-2222-2222-222222222222", "source-a");
        CatalogDataset second = dataset("33333333-3333-3333-3333-333333333333", "source-b");
        CatalogAssetMapping mapping = new CatalogAssetMapping();
        mapping.setLegacyDatasetId(first.getId());
        mapping.setSourceId(first.getSourceId());
        CatalogAssetExtension extension = new CatalogAssetExtension();
        extension.setLegacyDatasetId(first.getId());
        extension.setClassification("DATA_INTERNAL");

        when(mappingRepository.findFirstByFqnIgnoreCase("svc.db.public.orders")).thenReturn(Optional.of(mapping));
        when(datasetRepository.findByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase("public", "orders"))
            .thenReturn(List.of(first, second));
        when(datasetRepository.findByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase("db", "orders")).thenReturn(List.of());
        when(extensionRepository.findFirstByOmAsset(any(OpenMetadataAssetCache.class))).thenReturn(Optional.of(extension));

        OpenMetadataAssetSyncService.SyncResult result = service.syncTables(50);

        assertThat(result.mappedCount()).isZero();
        assertThat(mapping.getMatchStatus()).isEqualTo("MANUAL_REVIEW");
        assertThat(mapping.getMatchReason()).contains("2");
        assertThat(mapping.getLegacyDatasetId()).isNull();
        assertThat(mapping.getSourceId()).isNull();
        assertThat(extension.getLegacyDatasetId()).isNull();
        assertThat(extension.getClassification()).isEqualTo("DATA_INTERNAL");
    }

    @Test
    void unavailableOpenMetadataLeavesTheDtsCatalogCacheUntouched() {
        org.mockito.Mockito.reset(assetRepository, mappingRepository, extensionRepository);
        when(client.listTables(anyInt(), anyString())).thenReturn(Optional.empty());

        OpenMetadataAssetSyncService.SyncResult result = service.syncTables(50);

        assertThat(result.enabled()).isTrue();
        assertThat(result.assetCount()).isZero();
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.message()).contains("OpenMetadata").contains("重试");
        org.mockito.Mockito.verify(assetRepository, org.mockito.Mockito.never()).save(any());
        org.mockito.Mockito.verify(columnRepository, org.mockito.Mockito.never()).deleteByAsset(any());
        org.mockito.Mockito.verify(mappingRepository, org.mockito.Mockito.never()).save(any());
        org.mockito.Mockito.verify(extensionRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void matchedAssetSeedsOnlyMissingGovernanceFields() {
        CatalogDataset dataset = dataset("44444444-4444-4444-4444-444444444444", "source-a");
        dataset.setClassification("DATA_PUBLIC");
        dataset.setOwnerDept("DTS");
        dataset.setOwner("collector");
        dataset.setLifecycleStatus("PENDING_GOVERNANCE");
        CatalogAssetMapping mapping = new CatalogAssetMapping();
        CatalogAssetExtension extension = new CatalogAssetExtension();
        extension.setId(UUID.fromString("55555555-5555-5555-5555-555555555555"));
        extension.setClassification("DATA_SECRET");
        extension.setOwnerDept("GOV");
        extension.setBusinessOwner("manual-owner");
        extension.setLifecycleStatus("ACTIVE");
        extension.setEnabled(Boolean.FALSE);

        when(mappingRepository.findFirstByFqnIgnoreCase("svc.db.public.orders")).thenReturn(Optional.of(mapping));
        when(datasetRepository.findByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase("public", "orders"))
            .thenReturn(List.of(dataset));
        when(datasetRepository.findByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase("db", "orders"))
            .thenReturn(List.of(dataset));
        when(extensionRepository.findFirstByOmAsset(any(OpenMetadataAssetCache.class))).thenReturn(Optional.of(extension));
        mapServiceToSource(dataset);

        OpenMetadataAssetSyncService.SyncResult result = service.syncTables(50);

        assertThat(result.mappedCount()).isEqualTo(1);
        assertThat(mapping.getMatchStatus()).isEqualTo("MATCHED");
        assertThat(mapping.getLegacyDatasetId()).isEqualTo(dataset.getId());
        assertThat(extension.getLegacyDatasetId()).isEqualTo(dataset.getId());
        assertThat(extension.getClassification()).isEqualTo("DATA_SECRET");
        assertThat(extension.getOwnerDept()).isEqualTo("GOV");
        assertThat(extension.getBusinessOwner()).isEqualTo("manual-owner");
        assertThat(extension.getLifecycleStatus()).isEqualTo("ACTIVE");
        assertThat(extension.getEnabled()).isFalse();
    }

    @Test
    void uniquePhysicalCandidateWithoutExplicitServiceMappingRequiresManualReview() {
        CatalogDataset dataset = dataset("66666666-6666-6666-6666-666666666666", "source-a");
        CatalogAssetMapping mapping = new CatalogAssetMapping();

        when(mappingRepository.findFirstByFqnIgnoreCase("svc.db.public.orders")).thenReturn(Optional.of(mapping));
        when(datasetRepository.findByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase("public", "orders"))
            .thenReturn(List.of(dataset));
        when(datasetRepository.findByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase("db", "orders"))
            .thenReturn(List.of(dataset));

        OpenMetadataAssetSyncService.SyncResult result = service.syncTables(50);

        assertThat(result.mappedCount()).isZero();
        assertThat(mapping.getMatchStatus()).isEqualTo("MANUAL_REVIEW");
        assertThat(mapping.getMatchReason()).contains("no reliable OpenMetadata service-to-source mapping");
        assertThat(mapping.getLegacyDatasetId()).isNull();
        assertThat(mapping.getSourceId()).isNull();
    }

    @Test
    void genericSourceServiceNameDoesNotCountAsExplicitOpenMetadataMapping() {
        CatalogDataset dataset = dataset("67676767-6767-6767-6767-676767676767", "source-a");
        CatalogAssetMapping mapping = new CatalogAssetMapping();
        InfraDataSource source = new InfraDataSource();
        source.setId(dataset.getSourceId());
        source.setProps("{\"sourceServiceName\":\"svc\"}");

        when(mappingRepository.findFirstByFqnIgnoreCase("svc.db.public.orders")).thenReturn(Optional.of(mapping));
        when(datasetRepository.findByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase("public", "orders"))
            .thenReturn(List.of(dataset));
        when(datasetRepository.findByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase("db", "orders"))
            .thenReturn(List.of(dataset));
        when(dataSourceRepository.findById(dataset.getSourceId())).thenReturn(Optional.of(source));

        OpenMetadataAssetSyncService.SyncResult result = service.syncTables(50);

        assertThat(result.mappedCount()).isZero();
        assertThat(mapping.getMatchStatus()).isEqualTo("MANUAL_REVIEW");
        assertThat(mapping.getMatchReason()).contains("no reliable OpenMetadata service-to-source mapping");
        assertThat(mapping.getLegacyDatasetId()).isNull();
    }

    @Test
    void existingInternallyConsistentMatchIsRetainedDuringExplicitServiceConfigurationTransition() {
        CatalogDataset dataset = dataset("69696969-6969-6969-6969-696969696969", "source-a");
        CatalogAssetMapping mapping = new CatalogAssetMapping();
        mapping.setFqn("svc.db.public.orders");
        mapping.setMatchStatus("MATCHED");
        mapping.setLegacyDatasetId(dataset.getId());
        mapping.setSourceId(dataset.getSourceId());

        when(mappingRepository.findFirstByFqnIgnoreCase("svc.db.public.orders")).thenReturn(Optional.of(mapping));
        when(datasetRepository.findByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase("public", "orders"))
            .thenReturn(List.of(dataset));
        when(datasetRepository.findByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase("db", "orders"))
            .thenReturn(List.of(dataset));

        OpenMetadataAssetSyncService.SyncResult result = service.syncTables(50);

        assertThat(result.mappedCount()).isEqualTo(1);
        assertThat(mapping.getLegacyDatasetId()).isEqualTo(dataset.getId());
        assertThat(mapping.getSourceId()).isEqualTo(dataset.getSourceId());
        assertThat(mapping.getMatchStatus()).isEqualTo("MATCHED");
        assertThat(mapping.getMatchReason()).contains("retained");
        org.mockito.InOrder claimOrder = org.mockito.Mockito.inOrder(entityManager, mappingRepository);
        claimOrder.verify(entityManager).lock(dataset, LockModeType.PESSIMISTIC_WRITE);
        claimOrder.verify(mappingRepository).findFirstByLegacyDatasetId(dataset.getId());
    }

    @Test
    void existingMatchIsNotRetainedWhenExplicitServiceConfigurationMismatches() {
        CatalogDataset dataset = dataset("70696969-6969-6969-6969-696969696969", "source-a");
        CatalogAssetMapping mapping = new CatalogAssetMapping();
        mapping.setFqn("svc.db.public.orders");
        mapping.setMatchStatus("MATCHED");
        mapping.setLegacyDatasetId(dataset.getId());
        mapping.setSourceId(dataset.getSourceId());
        InfraDataSource source = new InfraDataSource();
        source.setId(dataset.getSourceId());
        source.setProps("{\"openmetadataServiceName\":\"different-svc\"}");

        when(mappingRepository.findFirstByFqnIgnoreCase("svc.db.public.orders")).thenReturn(Optional.of(mapping));
        when(datasetRepository.findByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase("public", "orders"))
            .thenReturn(List.of(dataset));
        when(datasetRepository.findByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase("db", "orders"))
            .thenReturn(List.of(dataset));
        when(dataSourceRepository.findById(dataset.getSourceId())).thenReturn(Optional.of(source));

        OpenMetadataAssetSyncService.SyncResult result = service.syncTables(50);

        assertThat(result.mappedCount()).isZero();
        assertThat(mapping.getMatchStatus()).isEqualTo("MANUAL_REVIEW");
        assertThat(mapping.getMatchReason()).contains("no reliable OpenMetadata service-to-source mapping");
        assertThat(mapping.getLegacyDatasetId()).isNull();
        assertThat(mapping.getSourceId()).isNull();
    }

    @Test
    void newExtensionInheritsDisabledLegacyGovernanceFlag() {
        CatalogDataset dataset = dataset("77777777-7777-7777-7777-777777777777", "source-a");
        dataset.setEnabled(Boolean.FALSE);
        CatalogAssetMapping mapping = new CatalogAssetMapping();

        when(mappingRepository.findFirstByFqnIgnoreCase("svc.db.public.orders")).thenReturn(Optional.of(mapping));
        when(datasetRepository.findByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase("public", "orders"))
            .thenReturn(List.of(dataset));
        when(datasetRepository.findByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase("db", "orders"))
            .thenReturn(List.of(dataset));
        mapServiceToSource(dataset);

        OpenMetadataAssetSyncService.SyncResult result = service.syncTables(50);

        assertThat(result.mappedCount()).isEqualTo(1);
        org.mockito.ArgumentCaptor<CatalogAssetExtension> savedExtension =
            org.mockito.ArgumentCaptor.forClass(CatalogAssetExtension.class);
        org.mockito.Mockito.verify(extensionRepository).save(savedExtension.capture());
        assertThat(savedExtension.getValue().getLegacyDatasetId()).isEqualTo(dataset.getId());
        assertThat(savedExtension.getValue().getEnabled()).isFalse();
    }

    @Test
    void existingLegacyMappingCannotBeStolenByAnotherOpenMetadataAsset() {
        CatalogDataset dataset = dataset("88888888-8888-8888-8888-888888888888", "source-a");
        CatalogAssetMapping current = new CatalogAssetMapping();
        CatalogAssetMapping claimed = new CatalogAssetMapping();
        claimed.setFqn("other.db.public.orders");
        claimed.setLegacyDatasetId(dataset.getId());

        when(mappingRepository.findFirstByFqnIgnoreCase("svc.db.public.orders")).thenReturn(Optional.of(current));
        when(mappingRepository.findFirstByLegacyDatasetId(dataset.getId())).thenReturn(Optional.of(claimed));
        when(datasetRepository.findByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase("public", "orders"))
            .thenReturn(List.of(dataset));
        when(datasetRepository.findByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase("db", "orders"))
            .thenReturn(List.of(dataset));
        mapServiceToSource(dataset);

        OpenMetadataAssetSyncService.SyncResult result = service.syncTables(50);

        assertThat(result.mappedCount()).isZero();
        assertThat(current.getMatchStatus()).isEqualTo("MANUAL_REVIEW");
        assertThat(current.getMatchReason()).contains("already claimed");
        assertThat(current.getLegacyDatasetId()).isNull();
        assertThat(claimed.getLegacyDatasetId()).isEqualTo(dataset.getId());
    }

    @Test
    void existingLegacyExtensionCannotBeReboundToAnotherOpenMetadataAsset() {
        CatalogDataset dataset = dataset("99999999-9999-9999-9999-999999999999", "source-a");
        CatalogAssetMapping mapping = new CatalogAssetMapping();
        OpenMetadataAssetCache previousAsset = new OpenMetadataAssetCache();
        previousAsset.setId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        previousAsset.setFqn("other.db.public.orders");
        CatalogAssetExtension claimed = new CatalogAssetExtension();
        claimed.setOmAsset(previousAsset);
        claimed.setLegacyDatasetId(dataset.getId());

        when(mappingRepository.findFirstByFqnIgnoreCase("svc.db.public.orders")).thenReturn(Optional.of(mapping));
        when(extensionRepository.findFirstByLegacyDatasetId(dataset.getId())).thenReturn(Optional.of(claimed));
        when(datasetRepository.findByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase("public", "orders"))
            .thenReturn(List.of(dataset));
        when(datasetRepository.findByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase("db", "orders"))
            .thenReturn(List.of(dataset));
        mapServiceToSource(dataset);

        OpenMetadataAssetSyncService.SyncResult result = service.syncTables(50);

        assertThat(result.mappedCount()).isZero();
        assertThat(mapping.getMatchStatus()).isEqualTo("MANUAL_REVIEW");
        assertThat(mapping.getMatchReason()).contains("extension is already claimed");
        assertThat(claimed.getOmAsset()).isSameAs(previousAsset);
        assertThat(claimed.getLegacyDatasetId()).isEqualTo(dataset.getId());
    }

    private CatalogDataset dataset(String id, String sourceName) {
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(UUID.fromString(id));
        dataset.setSourceId(UUID.nameUUIDFromBytes(sourceName.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        dataset.setName("orders");
        dataset.setHiveDatabase("public");
        dataset.setHiveTable("orders");
        dataset.setEnabled(Boolean.TRUE);
        return dataset;
    }

    private void mapServiceToSource(CatalogDataset dataset) {
        InfraDataSource source = new InfraDataSource();
        source.setId(dataset.getSourceId());
        source.setName("source-a");
        source.setProps("{\"openmetadataServiceName\":\"svc\"}");
        when(dataSourceRepository.findById(dataset.getSourceId())).thenReturn(Optional.of(source));
    }

    private Map<String, Object> tablePayload() {
        return Map.of(
            "id",
            "om-orders",
            "fullyQualifiedName",
            "svc.db.public.orders",
            "name",
            "orders",
            "service",
            Map.of("name", "svc"),
            "database",
            Map.of("name", "db"),
            "databaseSchema",
            Map.of("name", "public"),
            "columns",
            List.of()
        );
    }
}
