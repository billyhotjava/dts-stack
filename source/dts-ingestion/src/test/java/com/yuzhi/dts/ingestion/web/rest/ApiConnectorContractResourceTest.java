package com.yuzhi.dts.ingestion.web.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.ingestion.service.etl.api.ApiAuthProviderRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ApiConnectorContractResourceTest {

    private final ApiConnectorContractResource resource = new ApiConnectorContractResource(new ApiAuthProviderRegistry());

    @Test
    void getContract_exposesApiSourceContractAndAuthProviders() {
        Map<String, Object> contract = resource.getContract().getBody();

        assertThat(contract)
            .containsEntry("contractVersion", "1.0.0")
            .containsEntry("connectorType", "api")
            .containsEntry("defaultReaderType", "httpreader");
        @SuppressWarnings("unchecked")
        List<String> syncModes = (List<String>) contract.get("syncModes");
        assertThat(syncModes).contains("full_refresh", "incremental");
        @SuppressWarnings("unchecked")
        List<String> sourceTypes = (List<String>) contract.get("sourceTypes");
        assertThat(sourceTypes).contains("api_http", "https", "httpreader");
        assertThat((List<?>) contract.get("authProviders")).hasSizeGreaterThanOrEqualTo(3);
    }
}
