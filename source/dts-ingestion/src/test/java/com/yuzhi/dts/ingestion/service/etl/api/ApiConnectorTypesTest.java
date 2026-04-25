package com.yuzhi.dts.ingestion.service.etl.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiConnectorTypesTest {

    @Test
    void isApiSourceType_acceptsHttpAliases() {
        assertThat(ApiConnectorTypes.isApiSourceType("api")).isTrue();
        assertThat(ApiConnectorTypes.isApiSourceType("HTTP_API")).isTrue();
        assertThat(ApiConnectorTypes.isApiSourceType("httpreader")).isTrue();
    }

    @Test
    void normalizeConnectorType_mapsHttpAliasesToApi() {
        assertThat(ApiConnectorTypes.normalizeConnectorType("http")).isEqualTo("api");
        assertThat(ApiConnectorTypes.normalizeConnectorType("rest_api")).isEqualTo("api");
        assertThat(ApiConnectorTypes.normalizeConnectorType("addax")).isEqualTo("addax");
    }
}

