package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.RegistrationStep;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ArtifactView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Layer;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class CanonicalModelReleaseRegistrationAdapterTest {

    @Test
    void dbtPublishRegistrationDoesNotCreateAPhysicalCatalogAsset() {
        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        ModelSpecView model = model(ImplementationMode.DBT_MANAGED);

        ModelReleaseRegistrationPort.RegistrationResult result = new CanonicalModelReleaseRegistrationAdapter(jdbcTemplate, new ObjectMapper())
            .register(
                RegistrationStep.CATALOG_ASSET,
                "tenant-a",
                "alice",
                UUID.randomUUID(),
                model,
                3,
                "c".repeat(64),
                List.of(),
                null
            );

        assertThat(result.externalRef()).startsWith("catalog-sync-required:");
        verify(jdbcTemplate, never()).update(startsWith("insert into catalog_dataset"), any(Object[].class));
    }

    @Test
    void dbtPublishRegistrationSucceedsOnlyWithCurrentMaterializedCatalogEvidence() {
        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        UUID catalogAssetId = UUID.fromString("30000000-0000-0000-0000-000000000001");
        when(jdbcTemplate.queryForList(anyString(), org.mockito.ArgumentMatchers.eq(UUID.class), any(Object[].class)))
            .thenReturn(List.of(catalogAssetId));
        ModelSpecView model = model(ImplementationMode.DBT_MANAGED);

        ModelReleaseRegistrationPort.RegistrationResult result = new CanonicalModelReleaseRegistrationAdapter(jdbcTemplate, new ObjectMapper())
            .register(
                RegistrationStep.CATALOG_ASSET,
                "tenant-a",
                "alice",
                UUID.randomUUID(),
                model,
                3,
                "c".repeat(64),
                List.of(artifact(model, 3)),
                null
            );

        assertThat(result.externalRef()).isEqualTo("catalog-dataset:" + catalogAssetId);
        verify(jdbcTemplate, never()).update(startsWith("insert into catalog_dataset"), any(Object[].class));
    }

    @Test
    void legacyNonDbtPublishRegistrationKeepsItsCatalogRegistration() {
        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        when(jdbcTemplate.update(org.mockito.ArgumentMatchers.anyString(), any(Object[].class))).thenReturn(1);
        ModelSpecView model = model(null);

        ModelReleaseRegistrationPort.RegistrationResult result = new CanonicalModelReleaseRegistrationAdapter(jdbcTemplate, new ObjectMapper())
            .register(
                RegistrationStep.CATALOG_ASSET,
                "tenant-a",
                "alice",
                UUID.randomUUID(),
                model,
                3,
                "c".repeat(64),
                List.of(),
                null
            );

        assertThat(result.externalRef()).startsWith("catalog-dataset:");
        verify(jdbcTemplate).update(startsWith("insert into catalog_dataset"), any(Object[].class));
    }

    private static ModelSpecView model(ImplementationMode implementationMode) {
        ModelSpecView model = org.mockito.Mockito.mock(ModelSpecView.class);
        when(model.id()).thenReturn(UUID.fromString("10000000-0000-0000-0000-000000000001"));
        when(model.revision()).thenReturn(7);
        when(model.implementationMode()).thenReturn(implementationMode);
        when(model.name()).thenReturn("订单明细");
        when(model.domainId()).thenReturn(UUID.fromString("20000000-0000-0000-0000-000000000001"));
        when(model.layer()).thenReturn(Layer.DWD);
        when(model.checksum()).thenReturn("a".repeat(64));
        return model;
    }

    private static ArtifactView artifact(ModelSpecView model, int implementationRevision) {
        return new ArtifactView(
            UUID.randomUUID(),
            model.id(),
            UUID.randomUUID(),
            model.revision(),
            model.checksum(),
            ImplementationMode.DBT_MANAGED,
            "SQL",
            "models/dwd/orders.sql",
            "b".repeat(64),
            "COMPILED",
            implementationRevision,
            "MODEL",
            "table",
            null
        );
    }
}
