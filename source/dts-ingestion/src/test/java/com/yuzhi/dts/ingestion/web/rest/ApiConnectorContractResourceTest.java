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
            .containsEntry("contractVersion", "1.1.0")
            .containsEntry("connectorType", "api")
            .containsEntry("defaultReaderType", "httpreader");
        @SuppressWarnings("unchecked")
        List<String> syncModes = (List<String>) contract.get("syncModes");
        assertThat(syncModes).contains("full_refresh", "incremental");
        @SuppressWarnings("unchecked")
        List<String> sourceTypes = (List<String>) contract.get("sourceTypes");
        assertThat(sourceTypes).contains("api_http", "https", "httpreader");
        @SuppressWarnings("unchecked")
        Map<String, Object> odsLanding = (Map<String, Object>) contract.get("odsLanding");
        assertThat(odsLanding)
            .containsEntry("mode", "raw_record")
            .containsEntry("rawRecordColumn", "_dts_raw_record")
            .containsEntry("normalizationLayer", "stg");
        @SuppressWarnings("unchecked")
        List<String> technicalColumns = (List<String>) odsLanding.get("technicalColumns");
        assertThat(technicalColumns).contains("_dts_batch_id", "_dts_execution_id", "_dts_cursor_value");
        assertThat((List<?>) contract.get("authProviders")).hasSizeGreaterThanOrEqualTo(3);
    }
}
