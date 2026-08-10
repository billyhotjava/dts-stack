package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelLifecycleRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository.SourceBindingState;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetKey;
import com.yuzhi.dts.platform.service.catalog.CatalogClassificationBoundary;
import com.yuzhi.dts.platform.service.catalog.CatalogClassificationBoundary.ClassificationFact;
import com.yuzhi.dts.platform.service.catalog.JpaCatalogSourceReferenceReadAdapter;
import com.yuzhi.dts.platform.service.modeling.ModelClassificationPublishGate.Decision;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.PhysicalAssetInput;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModelClassificationPublishGateTest {

    @Test
    void resolvesCatalogTableLocatorToParentDatasetClassificationSubject() {
        String tenant = "tenant-a";
        UUID modelId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID bindingId = UUID.randomUUID();
        UUID tableId = UUID.randomUUID();
        UUID datasetId = UUID.randomUUID();
        UUID physicalSourceId = UUID.randomUUID();
        assertThat(tableId).isNotEqualTo(datasetId);

        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(datasetId);
        dataset.setName("orders");
        dataset.setSourceId(physicalSourceId);
        dataset.setHiveDatabase("sales");
        dataset.setHiveTable("orders");
        CatalogTableSchema table = new CatalogTableSchema();
        table.setId(tableId);
        table.setName("orders");
        table.setDataset(dataset);
        String expectedSubjectKey = CatalogAssetKey.dataset(dataset);

        CatalogTableSchemaRepository tables = mock(CatalogTableSchemaRepository.class);
        CatalogColumnSchemaRepository columns = mock(CatalogColumnSchemaRepository.class);
        CatalogDatasetRepository datasets = mock(CatalogDatasetRepository.class);
        lenient().when(tables.findById(tableId)).thenReturn(Optional.of(table));
        JpaCatalogSourceReferenceReadAdapter catalogSources = new JpaCatalogSourceReferenceReadAdapter(
            tables,
            columns,
            datasets,
            mock(AccessChecker.class)
        );

        ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
        ModelSpecRepository modelRepository = mock(ModelSpecRepository.class);
        ModelLifecycleRepository lifecycleRepository = mock(ModelLifecycleRepository.class);
        CatalogClassificationBoundary classifications = mock(CatalogClassificationBoundary.class);
        ModelSpecView model = mock(ModelSpecView.class);
        ImplementationView implementation = mock(ImplementationView.class);
        String checksum = "a".repeat(64);
        when(modelSpecs.get(tenant, modelId)).thenReturn(model);
        when(model.id()).thenReturn(modelId);
        when(model.planId()).thenReturn(planId);
        when(model.name()).thenReturn("orders_model");
        when(model.revision()).thenReturn(2);
        when(model.checksum()).thenReturn(checksum);
        when(model.fields()).thenReturn(List.of());
        when(lifecycleRepository.findImplementation(tenant, modelId)).thenReturn(Optional.of(implementation));
        when(implementation.revision()).thenReturn(2);
        when(implementation.modelChecksum()).thenReturn(checksum);
        when(implementation.dbtUniqueId()).thenReturn("model.dts.orders");
        when(implementation.inputs()).thenReturn(List.of(new PhysicalAssetInput(bindingId, "v1")));
        when(modelRepository.findSourceBinding(tenant, planId, bindingId))
            .thenReturn(
                Optional.of(
                    new SourceBindingState(
                        bindingId,
                        "CATALOG_TABLE",
                        tableId.toString(),
                        "v1",
                        "CONFIRMED",
                        "{\"assetId\":\"" + tableId + "\"}",
                        "owner-1",
                        "dept-1"
                    )
                )
            );
        lenient()
            .when(classifications.resolve("ASSET", expectedSubjectKey))
            .thenReturn(Optional.of(new ClassificationFact("ASSET", expectedSubjectKey, "DATA_INTERNAL", "PROPAGATED")));
        ModelClassificationPublishGate gate = new ModelClassificationPublishGate(
            modelSpecs,
            modelRepository,
            lifecycleRepository,
            catalogSources,
            classifications,
            new ObjectMapper()
        );

        Decision decision = gate.evaluate(tenant, modelId, 2, checksum);

        assertThat(decision.ready()).isTrue();
        assertThat(decision.effectiveLevel()).isEqualTo("DATA_INTERNAL");
        assertThat(decision.upstreamLevels()).containsEntry(expectedSubjectKey, "DATA_INTERNAL");
    }
}
