package com.yuzhi.dts.platform.service.modeling.serving;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.modeling.CatalogModelServingProjectionRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelServingContract.ModelServingProjection;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelServingContract.ServingRef;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.PhysicalPreviewRequest;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.PreviewScope;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class DefaultPhysicalPreviewAccessAdapterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void candidateNeverReusesTheOldServingPhysicalAssetAsItsPolicyAnchor() {
        UUID modelId = UUID.fromString("30000000-0000-0000-0000-000000000001");
        UUID oldServingAssetId = UUID.fromString("40000000-0000-0000-0000-000000000001");
        CatalogModelServingProjectionRepository projections = mock(CatalogModelServingProjectionRepository.class);
        CatalogDatasetRepository datasets = mock(CatalogDatasetRepository.class);
        AccessChecker accessChecker = mock(AccessChecker.class);
        ModelSpecView model = mock(ModelSpecView.class);
        PhysicalPreviewRequest request = mock(PhysicalPreviewRequest.class);
        ModelServingProjection projection = mock(ModelServingProjection.class);
        ServingRef serving = mock(ServingRef.class);
        when(model.id()).thenReturn(modelId);
        when(request.scope()).thenReturn(PreviewScope.CANDIDATE);
        when(serving.physicalAssetId()).thenReturn(oldServingAssetId);
        when(projection.servingRef()).thenReturn(serving);
        when(projections.findProjection("tenant-a", modelId)).thenReturn(Optional.of(projection));
        SecurityContextHolder
            .getContext()
            .setAuthentication(new TestingAuthenticationToken("alice", "n/a", AuthoritiesConstants.OP_ADMIN));

        var access = new DefaultPhysicalPreviewAccessAdapter(projections, datasets, accessChecker)
            .authorize("tenant-a", "alice", model, request);

        assertThat(access.physicalAssetId()).isNull();
        verify(projections, never()).findProjection("tenant-a", modelId);
        verifyNoInteractions(datasets, accessChecker);
    }
}
