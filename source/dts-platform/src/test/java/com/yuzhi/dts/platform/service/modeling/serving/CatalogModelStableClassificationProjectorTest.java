package com.yuzhi.dts.platform.service.modeling.serving;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.service.catalog.CatalogAssetKey;
import com.yuzhi.dts.platform.service.catalog.CatalogClassificationBoundary;
import com.yuzhi.dts.platform.service.catalog.CatalogClassificationBoundary.ClassificationFact;
import com.yuzhi.dts.platform.service.catalog.CatalogClassificationBoundary.SealRequest;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelField;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CatalogModelStableClassificationProjectorTest {

    @Test
    void projectsStableModelClassificationAsUpstreamInheritance() {
        CatalogClassificationBoundary classifications = mock(CatalogClassificationBoundary.class);
        ModelSpecView model = mock(ModelSpecView.class);
        ImplementationView implementation = mock(ImplementationView.class);
        ModelField field = mock(ModelField.class);
        UUID modelId = UUID.randomUUID();
        String dbtUniqueId = "model.pm_analytics_v3.dim_change_category_v2";
        String sourceKey = CatalogAssetKey.dbtModel(dbtUniqueId, "dim_change_category_v2");

        when(model.id()).thenReturn(modelId);
        when(model.revision()).thenReturn(2);
        when(model.name()).thenReturn("dim_change_category_v2");
        when(model.fields()).thenReturn(List.of(field));
        when(field.name()).thenReturn("change_category_id");
        when(field.securityLevel()).thenReturn("INTERNAL");
        when(implementation.dbtUniqueId()).thenReturn(dbtUniqueId);
        when(classifications.resolve("ASSET", sourceKey))
            .thenReturn(Optional.of(new ClassificationFact("ASSET", sourceKey, "INTERNAL", "PROPAGATED")));
        when(classifications.resolve("COLUMN", sourceKey + "/column:change_category_id")).thenReturn(Optional.empty());
        when(classifications.sealOrRaise(any(SealRequest.class)))
            .thenAnswer(invocation -> {
                SealRequest request = invocation.getArgument(0);
                return new ClassificationFact(
                    request.subjectType(),
                    request.subjectKey(),
                    "INTERNAL",
                    "PROPAGATED"
                );
            });

        new CatalogModelStableClassificationProjector(classifications).project(model, implementation, "candidate-1");

        ArgumentCaptor<SealRequest> requests = ArgumentCaptor.forClass(SealRequest.class);
        verify(classifications, org.mockito.Mockito.times(2)).sealOrRaise(requests.capture());
        assertThat(requests.getAllValues())
            .extracting(SealRequest::originType)
            .containsOnly("UPSTREAM_INHERITANCE");
    }
}
