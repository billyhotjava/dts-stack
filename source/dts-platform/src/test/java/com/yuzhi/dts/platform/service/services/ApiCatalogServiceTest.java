package com.yuzhi.dts.platform.service.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.service.SvcApi;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.service.SvcApiMetricHourlyRepository;
import com.yuzhi.dts.platform.repository.service.SvcApiRepository;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetIdentity;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetType;
import com.yuzhi.dts.platform.service.catalog.CodeAssetGrantWriter;
import com.yuzhi.dts.platform.service.services.dto.ApiServiceUpsertRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApiCatalogServiceTest {

    @Mock
    private SvcApiRepository apiRepository;

    @Mock
    private SvcApiMetricHourlyRepository metricRepository;

    @Mock
    private CatalogDatasetRepository datasetRepository;

    @Mock
    private CodeAssetGrantWriter codeAssetGrantWriter;

    @Test
    void createSyncsApiServiceAsCodeAsset() {
        ApiCatalogService service = new ApiCatalogService(apiRepository, metricRepository, datasetRepository, new ObjectMapper());
        service.setCodeAssetGrantWriter(codeAssetGrantWriter);
        when(apiRepository.findByCode("contract_summary_api")).thenReturn(Optional.empty());
        when(apiRepository.save(any(SvcApi.class))).thenAnswer(invocation -> {
            SvcApi api = invocation.getArgument(0);
            api.setId(UUID.fromString("11111111-2222-3333-4444-555555555555"));
            return api;
        });
        when(metricRepository.sumCallsSince(any(), any())).thenReturn(0L);
        when(metricRepository.sumMaskedSince(any(), any())).thenReturn(0L);
        when(metricRepository.sumDeniesSince(any(), any())).thenReturn(0L);

        service.create(
            new ApiServiceUpsertRequest(
                "contract_summary_api",
                "Contract Summary API",
                null,
                "POST",
                "/openapi/contract-summary",
                "INTERNAL",
                10,
                1000,
                null,
                List.of(),
                List.of(),
                null,
                null
            ),
            "sysadmin"
        );

        ArgumentCaptor<CatalogAssetIdentity> identityCaptor = ArgumentCaptor.forClass(CatalogAssetIdentity.class);
        verify(codeAssetGrantWriter).upsertCodeAsset(identityCaptor.capture(), any(), any(), any(), any());
        CatalogAssetIdentity identity = identityCaptor.getValue();
        org.assertj.core.api.Assertions.assertThat(identity.type()).isEqualTo(CatalogAssetType.API_SERVICE);
        org.assertj.core.api.Assertions.assertThat(identity.assetId()).isEqualTo("11111111-2222-3333-4444-555555555555");
    }
}
