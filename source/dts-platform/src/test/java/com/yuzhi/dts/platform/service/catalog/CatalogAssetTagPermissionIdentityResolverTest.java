package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetTagPermissionIdentityResolver.ResolvedPermissionIdentity;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetTagPermissionReadAdapter.BatchLookup;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetTagPermissionReadAdapter.BatchLookupRequest;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetTagPermissionReadAdapter.OpenMetadataDatasetIdentity;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetTagPermissionReadAdapter.PermissionIdentity;
import com.yuzhi.dts.platform.service.catalog.dto.AssetRef;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class CatalogAssetTagPermissionIdentityResolverTest {

    private static final UUID ENTITY_ID = UUID.fromString(
        "11111111-1111-1111-1111-111111111111"
    );

    @Mock
    private CatalogAssetTagPermissionReadAdapter readAdapter;

    private CatalogAssetTagPermissionIdentityResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new CatalogAssetTagPermissionIdentityResolver(readAdapter);
    }

    @Test
    void resolvesCatalogDomainOnlyWhenCodeIsUniqueAndCanonicalKeyRoundTrips() {
        when(readAdapter.loadBatch(any(BatchLookupRequest.class)))
            .thenReturn(
                lookup(
                    CatalogAssetType.CATALOG_DOMAIN,
                    identity(ENTITY_ID, "finance")
                )
            );

        String key = CatalogAssetKey.codeAsset(
            CatalogAssetType.CATALOG_DOMAIN,
            "default",
            "finance"
        );
        ResolvedPermissionIdentity identity = resolver.resolve(
            CatalogAssetType.CATALOG_DOMAIN,
            key
        );

        assertThat(identity.requestedType())
            .isEqualTo(CatalogAssetType.CATALOG_DOMAIN);
        assertThat(identity.canonicalAssetKey()).isEqualTo(key);
        assertThat(identity.grantAssetType()).isEqualTo("CATALOG_DOMAIN");
        assertThat(identity.grantAssetId()).isEqualTo(ENTITY_ID.toString());
        assertThat(identity.dataset()).isNull();
    }

    @Test
    void rejectsAmbiguousCatalogDomainInsteadOfTakingTheFirstMatch() {
        when(readAdapter.loadBatch(any(BatchLookupRequest.class)))
            .thenReturn(
                lookup(
                    CatalogAssetType.CATALOG_DOMAIN,
                    identity(ENTITY_ID, "finance"),
                    identity(
                        UUID.fromString(
                            "22222222-2222-2222-2222-222222222222"
                        ),
                        "FINANCE"
                    )
                )
            );
        String key = CatalogAssetKey.codeAsset(
            CatalogAssetType.CATALOG_DOMAIN,
            "default",
            "finance"
        );

        assertReason(
            () -> resolver.resolve(CatalogAssetType.CATALOG_DOMAIN, key),
            "AMBIGUOUS_PERMISSION_IDENTITY"
        );
    }

    @Test
    void resolvesLegacyDatasetAndRejectsLossyCanonicalCollisions() {
        CatalogDataset unique = dataset(
            ENTITY_ID,
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
            "DWD",
            "Order Detail"
        );
        String key = CatalogAssetKey.dataset(unique);
        when(readAdapter.loadBatch(any(BatchLookupRequest.class)))
            .thenReturn(new BatchLookup(List.of(unique), List.of(), Map.of()));

        ResolvedPermissionIdentity identity = resolver.resolve(
            CatalogAssetType.DATASET,
            key
        );

        assertThat(identity.grantAssetId()).isEqualTo(ENTITY_ID.toString());
        assertThat(identity.dataset()).isSameAs(unique);

        CatalogDataset collision = dataset(
            UUID.fromString("33333333-3333-3333-3333-333333333333"),
            unique.getSourceId(),
            "dwd",
            "order_detail"
        );
        when(readAdapter.loadBatch(any(BatchLookupRequest.class)))
            .thenReturn(
                new BatchLookup(
                    List.of(unique, collision),
                    List.of(),
                    Map.of()
                )
            );

        assertReason(
            () -> resolver.resolve(CatalogAssetType.DATASET, key),
            "AMBIGUOUS_PERMISSION_IDENTITY"
        );
    }

    @Test
    void mappedOpenMetadataKeyUsesTheLegacyDatasetWriteIdentityButOmOnlyFailsClosed() {
        CatalogDataset legacy = dataset(
            ENTITY_ID,
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
            "dwd",
            "orders"
        );
        UUID omId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        UUID mappingId = UUID.fromString(
            "55555555-5555-5555-5555-555555555555"
        );
        String fqn = "mysql.warehouse.dwd.orders";
        String omKey = CatalogAssetKey.openMetadataDataset(fqn);
        when(readAdapter.loadBatch(any(BatchLookupRequest.class)))
            .thenReturn(
                new BatchLookup(
                    List.of(),
                    List.of(
                        new OpenMetadataDatasetIdentity(
                            omId,
                            fqn,
                            mappingId,
                            legacy.getId(),
                            legacy
                        )
                    ),
                    Map.of()
                )
            );

        ResolvedPermissionIdentity identity = resolver.resolve(
            CatalogAssetType.DATASET,
            omKey
        );

        assertThat(identity.canonicalAssetKey()).isEqualTo(omKey);
        assertThat(identity.grantAssetId())
            .isEqualTo(legacy.getId().toString());
        assertThat(identity.dataset()).isSameAs(legacy);

        when(readAdapter.loadBatch(any(BatchLookupRequest.class)))
            .thenReturn(
                new BatchLookup(
                    List.of(),
                    List.of(
                        new OpenMetadataDatasetIdentity(
                            omId,
                            fqn,
                            null,
                            null,
                            null
                        )
                    ),
                    Map.of()
                )
            );
        assertReason(
            () -> resolver.resolve(CatalogAssetType.DATASET, omKey),
            "UNMAPPED_DATASET_PERMISSION_IDENTITY"
        );
    }

    @Test
    void duplicateOpenMetadataMappingsFailClosedAsAmbiguous() {
        CatalogDataset legacy = dataset(
            ENTITY_ID,
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
            "dwd",
            "orders"
        );
        String fqn = "mysql.warehouse.dwd.orders";
        OpenMetadataDatasetIdentity first = new OpenMetadataDatasetIdentity(
            UUID.randomUUID(),
            fqn,
            UUID.randomUUID(),
            legacy.getId(),
            legacy
        );
        OpenMetadataDatasetIdentity second = new OpenMetadataDatasetIdentity(
            first.openMetadataId(),
            fqn,
            UUID.randomUUID(),
            legacy.getId(),
            legacy
        );
        when(readAdapter.loadBatch(any(BatchLookupRequest.class)))
            .thenReturn(
                new BatchLookup(
                    List.of(),
                    List.of(first, second),
                    Map.of()
                )
            );

        assertReason(
            () ->
                resolver.resolve(
                    CatalogAssetType.DATASET,
                    CatalogAssetKey.openMetadataDataset(fqn)
                ),
            "AMBIGUOUS_PERMISSION_IDENTITY"
        );
    }

    @Test
    void rejectsScopedDatasetBecauseItHasNoVerifiedLegacyIdentity() {
        String scoped = CatalogAssetKey.scopedDataset(
            "default",
            "prod",
            "generic",
            "mysql",
            "dwd",
            "orders"
        );

        assertReason(
            () -> resolver.resolve(CatalogAssetType.DATASET, scoped),
            "UNSUPPORTED_PERMISSION_IDENTITY"
        );
        verifyNoInteractions(readAdapter);
    }

    @Test
    void resolvesScreenWithOriginalScreenIdRatherThanLinkIdOrCanonicalKey() {
        when(readAdapter.loadBatch(any(BatchLookupRequest.class)))
            .thenReturn(
                lookup(
                    CatalogAssetType.SCREEN,
                    new PermissionIdentity("114", "114")
                )
            );

        ResolvedPermissionIdentity identity = resolver.resolve(
            CatalogAssetType.SCREEN,
            "screen:114"
        );

        assertThat(identity.grantAssetType()).isEqualTo("SCREEN");
        assertThat(identity.grantAssetId()).isEqualTo("114");
        assertThat(identity.grantAssetId())
            .isNotEqualTo(identity.canonicalAssetKey());
    }

    @Test
    void resolvesTheFourUniqueCodeAssetsWithoutCrossTypeGuessing() {
        when(readAdapter.loadBatch(any(BatchLookupRequest.class)))
            .thenAnswer(invocation -> {
                BatchLookupRequest request = invocation.getArgument(0);
                Map<CatalogAssetType, List<PermissionIdentity>> matches = new java.util.EnumMap<>(
                    CatalogAssetType.class
                );
                request
                    .naturalKeys()
                    .forEach((type, keys) ->
                        matches.put(
                            type,
                            keys
                                .stream()
                                .map(key -> identity(ENTITY_ID, key))
                                .toList()
                        )
                    );
                return new BatchLookup(List.of(), List.of(), matches);
            });

        assertResolved(CatalogAssetType.DATA_STANDARD, "customer_id");
        assertResolved(CatalogAssetType.GLOSSARY_TERM, "customer");
        assertResolved(CatalogAssetType.GOV_INDICATOR, "revenue");
        assertResolved(CatalogAssetType.API_SERVICE, "customer_query");

        String wrongTypeKey = CatalogAssetKey.codeAsset(
            CatalogAssetType.GOV_INDICATOR,
            "default",
            "customer_query"
        );
        assertReason(
            () ->
                resolver.resolve(CatalogAssetType.API_SERVICE, wrongTypeKey),
            "INVALID_PERMISSION_IDENTITY"
        );
    }

    @Test
    void rejectsWrongTenantEvenWhenTheEntityCodeExists() {
        String wrongTenant = CatalogAssetKey.codeAsset(
            CatalogAssetType.API_SERVICE,
            "tenant-a",
            "customer_query"
        );

        assertReason(
            () -> resolver.resolve(CatalogAssetType.API_SERVICE, wrongTenant),
            "INVALID_PERMISSION_IDENTITY"
        );
        verifyNoInteractions(readAdapter);
    }

    @Test
    void fiveHundredAssetsUseOneBatchLookupInsteadOfPerAssetRepositoryCalls() {
        List<AssetRef> assets = IntStream
            .range(0, 500)
            .mapToObj(index ->
                new AssetRef(
                    CatalogAssetType.API_SERVICE.name(),
                    CatalogAssetKey.codeAsset(
                        CatalogAssetType.API_SERVICE,
                        "default",
                        "api_" + index
                    )
                )
            )
            .toList();
        List<PermissionIdentity> apiIdentities = IntStream
            .range(0, 500)
            .mapToObj(index ->
                new PermissionIdentity(
                    new UUID(0, index + 1L).toString(),
                    "api_" + index
                )
            )
            .toList();
        when(readAdapter.loadBatch(any(BatchLookupRequest.class)))
            .thenReturn(
                lookup(
                    CatalogAssetType.API_SERVICE,
                    apiIdentities.toArray(PermissionIdentity[]::new)
                )
            );

        List<ResolvedPermissionIdentity> resolved = resolver.resolveAll(assets);

        assertThat(resolved).hasSize(500);
        verify(readAdapter, times(1))
            .loadBatch(
                argThat(request ->
                    request
                        .naturalKeys()
                        .get(CatalogAssetType.API_SERVICE)
                        .size() ==
                    500
                )
            );
    }

    @Test
    void fiveHundredAndOneAssetsFailBeforeAnyLookup() {
        List<AssetRef> assets = IntStream
            .range(0, 501)
            .mapToObj(index ->
                new AssetRef(
                    CatalogAssetType.API_SERVICE.name(),
                    CatalogAssetKey.codeAsset(
                        CatalogAssetType.API_SERVICE,
                        "default",
                        "api_" + index
                    )
                )
            )
            .toList();

        assertThatThrownBy(() -> resolver.resolveAll(assets))
            .isInstanceOf(CatalogAssetTagPermissionException.class)
            .satisfies(failure -> {
                CatalogAssetTagPermissionException exception = (CatalogAssetTagPermissionException) failure;
                assertThat(exception.getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(exception.reasonCode())
                    .isEqualTo("BATCH_SIZE_EXCEEDED");
            });
        verifyNoInteractions(readAdapter);
    }

    @Test
    void resolvesEveryPreviouslyUnsupportedAssetTypeThroughAnExactIdentity() {
        UUID secondId = UUID.fromString(
            "22222222-2222-2222-2222-222222222222"
        );
        CatalogDataset securedDataset = dataset(
            secondId,
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
            "dwd",
            "secured_orders"
        );
        String metricKey = CatalogAssetKey.metric(
            "tenant-a",
            "core",
            "revenue"
        );
        String metricPackKey = CatalogAssetKey.metricPack(
            "default",
            "core",
            "v1"
        );
        Map<CatalogAssetType, List<PermissionIdentity>> matches = new java.util.EnumMap<>(
            CatalogAssetType.class
        );
        matches.put(
            CatalogAssetType.DBT_MODEL,
            List.of(identity(ENTITY_ID, "model.orders"))
        );
        matches.put(
            CatalogAssetType.BI_DATASET,
            List.of(identity(ENTITY_ID, ENTITY_ID.toString()))
        );
        matches.put(
            CatalogAssetType.METRIC,
            List.of(
                new PermissionIdentity(
                    ENTITY_ID.toString(),
                    "revenue",
                    metricKey,
                    null,
                    null
                )
            )
        );
        matches.put(
            CatalogAssetType.METRIC_PACK,
            List.of(
                new PermissionIdentity(
                    ENTITY_ID.toString(),
                    "core",
                    metricPackKey,
                    null,
                    null
                )
            )
        );
        matches.put(
            CatalogAssetType.SEMANTIC_MODEL,
            List.of(identity(ENTITY_ID, ENTITY_ID.toString()))
        );
        matches.put(
            CatalogAssetType.DATA_PRODUCT,
            List.of(identity(ENTITY_ID, "orders"))
        );
        matches.put(
            CatalogAssetType.METADATA_STANDARD,
            List.of(identity(ENTITY_ID, ENTITY_ID.toString()))
        );
        matches.put(
            CatalogAssetType.GOV_INDICATOR_TEMPLATE,
            List.of(identity(ENTITY_ID, "standard-revenue"))
        );
        matches.put(
            CatalogAssetType.QUALITY_RULE,
            List.of(identity(ENTITY_ID, "orders-not-null"))
        );
        matches.put(
            CatalogAssetType.SECURITY_POLICY,
            List.of(
                new PermissionIdentity(
                    secondId.toString(),
                    secondId.toString(),
                    CatalogAssetKey.codeAsset(
                        CatalogAssetType.SECURITY_POLICY,
                        "default",
                        secondId.toString()
                    ),
                    CatalogAssetType.DATASET.name(),
                    securedDataset
                )
            )
        );
        matches.put(
            CatalogAssetType.BACKFILL_REQUEST,
            List.of(identity(ENTITY_ID, ENTITY_ID.toString()))
        );
        when(readAdapter.loadBatch(any(BatchLookupRequest.class)))
            .thenReturn(new BatchLookup(List.of(), List.of(), matches));

        List<ResolvedPermissionIdentity> resolved = resolver.resolveAll(
            List.of(
                new AssetRef(
                    "DBT_MODEL",
                    CatalogAssetKey.dbtModel("model.orders", null)
                ),
                new AssetRef(
                    "BI_DATASET",
                    CatalogAssetKey.biDataset(ENTITY_ID)
                ),
                new AssetRef("METRIC", metricKey),
                new AssetRef("METRIC_PACK", metricPackKey),
                new AssetRef(
                    "SEMANTIC_MODEL",
                    CatalogAssetKey.semanticModel(ENTITY_ID.toString())
                ),
                codeRef(CatalogAssetType.DATA_PRODUCT, "orders"),
                codeRef(
                    CatalogAssetType.METADATA_STANDARD,
                    ENTITY_ID.toString()
                ),
                codeRef(
                    CatalogAssetType.GOV_INDICATOR_TEMPLATE,
                    "standard-revenue"
                ),
                codeRef(CatalogAssetType.QUALITY_RULE, "orders-not-null"),
                codeRef(
                    CatalogAssetType.SECURITY_POLICY,
                    secondId.toString()
                ),
                codeRef(
                    CatalogAssetType.BACKFILL_REQUEST,
                    ENTITY_ID.toString()
                )
            )
        );

        assertThat(resolved)
            .extracting(ResolvedPermissionIdentity::requestedType)
            .containsExactly(
                CatalogAssetType.DBT_MODEL,
                CatalogAssetType.BI_DATASET,
                CatalogAssetType.METRIC,
                CatalogAssetType.METRIC_PACK,
                CatalogAssetType.SEMANTIC_MODEL,
                CatalogAssetType.DATA_PRODUCT,
                CatalogAssetType.METADATA_STANDARD,
                CatalogAssetType.GOV_INDICATOR_TEMPLATE,
                CatalogAssetType.QUALITY_RULE,
                CatalogAssetType.SECURITY_POLICY,
                CatalogAssetType.BACKFILL_REQUEST
            );
        ResolvedPermissionIdentity securityPolicy = resolved.get(9);
        assertThat(securityPolicy.grantAssetType())
            .isEqualTo(CatalogAssetType.DATASET.name());
        assertThat(securityPolicy.dataset()).isSameAs(securedDataset);
    }

    private void assertResolved(
        CatalogAssetType type,
        String naturalKey
    ) {
        String key = CatalogAssetKey.codeAsset(type, "default", naturalKey);
        ResolvedPermissionIdentity identity = resolver.resolve(type, key);
        assertThat(identity.requestedType()).isEqualTo(type);
        assertThat(identity.canonicalAssetKey()).isEqualTo(key);
        assertThat(identity.grantAssetType()).isEqualTo(type.name());
        assertThat(identity.grantAssetId()).isEqualTo(ENTITY_ID.toString());
    }

    private void assertReason(Runnable action, String reasonCode) {
        assertThatThrownBy(action::run)
            .isInstanceOf(CatalogAssetTagPermissionException.class)
            .extracting("reasonCode")
            .isEqualTo(reasonCode);
    }

    private BatchLookup lookup(
        CatalogAssetType type,
        PermissionIdentity... identities
    ) {
        return new BatchLookup(
            List.of(),
            List.of(),
            Map.of(type, List.of(identities))
        );
    }

    private PermissionIdentity identity(UUID id, String naturalKey) {
        return new PermissionIdentity(id.toString(), naturalKey);
    }

    private AssetRef codeRef(CatalogAssetType type, String naturalKey) {
        return new AssetRef(
            type.name(),
            CatalogAssetKey.codeAsset(type, "default", naturalKey)
        );
    }

    private static CatalogDataset dataset(
        UUID id,
        UUID sourceId,
        String schema,
        String table
    ) {
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(id);
        dataset.setSourceId(sourceId);
        dataset.setName(table);
        dataset.setHiveDatabase(schema);
        dataset.setHiveTable(table);
        return dataset;
    }
}
