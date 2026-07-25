package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.yuzhi.dts.platform.service.catalog.CatalogExternalAssetIdentityRegistry.Registration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class CatalogExternalAssetIdentityRegistryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private CodeAssetGrantWriter grantWriter;

    private CatalogExternalAssetIdentityRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new CatalogExternalAssetIdentityRegistry(
            jdbcTemplate,
            grantWriter
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void registersExactMetricIdentitiesAndCreatesOwnershipWhenProvided() {
        String metricKey = CatalogAssetKey.metric(
            "default",
            "core",
            "revenue"
        );
        String packKey = CatalogAssetKey.metricPack(
            "default",
            "core",
            "v1"
        );

        CatalogExternalAssetIdentityRegistry.RegistrationResult result =
            registry.register(
                "dts-metrics",
                List.of(
                    new Registration(
                        "METRIC",
                        metricKey,
                        metricKey,
                        "D01"
                    ),
                    new Registration(
                        "METRIC_PACK",
                        packKey,
                        "core:v1",
                        null
                    )
                )
            );

        assertThat(result.registered()).isEqualTo(2);
        assertThat(result.canonicalAssetKeys())
            .containsExactly(metricKey, packKey);
        ArgumentCaptor<List<Object[]>> rows = ArgumentCaptor.forClass(
            (Class<List<Object[]>>) (Class<?>) List.class
        );
        verify(jdbcTemplate).batchUpdate(anyString(), rows.capture());
        assertThat(rows.getValue()).hasSize(2);
        assertThat(rows.getValue().getFirst()[1]).isEqualTo("METRIC");
        assertThat(rows.getValue().getFirst()[2]).isEqualTo(metricKey);
        assertThat(rows.getValue().getFirst()[3])
            .isEqualTo(metricKey);
        verify(grantWriter)
            .synchronizeExternalCodeAsset(
                new CatalogAssetIdentity(
                    CatalogAssetType.METRIC,
                    metricKey,
                    metricKey,
                    "dts-metrics:" + metricKey
                ),
                "D01",
                "dts-metrics",
                "INTERNAL",
                "ACTIVE"
            );
        verify(grantWriter)
            .synchronizeExternalCodeAsset(
                new CatalogAssetIdentity(
                    CatalogAssetType.METRIC_PACK,
                    packKey,
                    "core:v1",
                    "dts-metrics:core:v1"
                ),
                null,
                "dts-metrics",
                "INTERNAL",
                "ACTIVE"
            );
    }

    @Test
    void registersTenantScopedMetricWithoutCollidingWithLegacyMetricKeys() {
        String scopedMetricKey = CatalogAssetKey.metric(
            "tenant-a",
            "core",
            "revenue"
        );

        var result = registry.register(
            "dts-metrics",
            List.of(
                new Registration(
                    "METRIC",
                    scopedMetricKey,
                    scopedMetricKey,
                    "D01"
                )
            )
        );

        assertThat(result.canonicalAssetKeys())
            .containsExactly(scopedMetricKey);
        verify(grantWriter)
            .synchronizeExternalCodeAsset(
                new CatalogAssetIdentity(
                    CatalogAssetType.METRIC,
                    scopedMetricKey,
                    scopedMetricKey,
                    "dts-metrics:" + scopedMetricKey
                ),
                "D01",
                "dts-metrics",
                "INTERNAL",
                "ACTIVE"
            );
    }

    @Test
    void rejectsUnknownOrNonCanonicalAssetsBeforeWriting() {
        assertThatThrownBy(() ->
            registry.register(
                "dts-metrics",
                List.of(
                    new Registration(
                        "DATA_PRODUCT",
                        CatalogAssetKey.codeAsset(
                            CatalogAssetType.DATA_PRODUCT,
                            "default",
                            "orders"
                        ),
                        "orders",
                        null
                    )
                )
            )
        ).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
            registry.register(
                "dts-metrics",
                List.of(
                    new Registration(
                        "METRIC",
                        CatalogAssetKey.metric("core", "revenue"),
                        "legacy-metric-id",
                        null
                    )
                )
            )
        ).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
            registry.register(
                "dts-metrics",
                List.of(
                    new Registration(
                        "METRIC",
                        "metric:core/revenue/extra",
                        "revenue",
                        null
                    )
                )
            )
        ).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(jdbcTemplate);
        verify(grantWriter, never())
            .synchronizeExternalCodeAsset(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
            );
    }

    @Test
    void emptyRegistrationIsAnIdempotentNoOp() {
        assertThat(registry.register("dts-metrics", List.of()).registered())
            .isZero();

        verify(jdbcTemplate, never())
            .batchUpdate(anyString(), anyList());
        verifyNoInteractions(grantWriter);
    }

    @Test
    @SuppressWarnings("unchecked")
    void identicalDuplicatesConvergeButConflictingDuplicatesFailBeforeWriting() {
        String metricKey = CatalogAssetKey.metric(
            "default",
            "core",
            "revenue"
        );
        Registration registration = new Registration(
            "METRIC",
            metricKey,
            metricKey,
            null
        );

        var result = registry.register(
            "dts-metrics",
            List.of(registration, registration)
        );

        assertThat(result.registered()).isEqualTo(1);
        ArgumentCaptor<List<Object[]>> rows = ArgumentCaptor.forClass(
            (Class<List<Object[]>>) (Class<?>) List.class
        );
        verify(jdbcTemplate).batchUpdate(anyString(), rows.capture());
        assertThat(rows.getValue()).hasSize(1);

        assertThatThrownBy(() ->
            registry.register(
                "dts-metrics",
                List.of(
                    registration,
                    new Registration(
                        "METRIC",
                        metricKey,
                        "another-remote-id",
                        null
                    )
                )
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("conflicting duplicate");
    }

    @Test
    void rejectsRemotePermissionIdentityReuseAcrossCanonicalKeys() {
        assertThatThrownBy(() ->
            registry.register(
                "dts-metrics",
                List.of(
                    new Registration(
                        "METRIC",
                        CatalogAssetKey.metric(
                            "tenant-a",
                            "core",
                            "revenue"
                        ),
                        "shared-remote-id",
                        null
                    ),
                    new Registration(
                        "METRIC",
                        CatalogAssetKey.metric(
                            "tenant-b",
                            "core",
                            "revenue"
                        ),
                        "shared-remote-id",
                        null
                    )
                )
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("remote asset identity");

        verifyNoInteractions(jdbcTemplate, grantWriter);
    }
}
