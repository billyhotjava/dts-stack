package com.yuzhi.dts.analytics.web.interceptor;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.analytics.config.AnalyticsBiFeatureProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class GovernedBiFeatureInterceptorTest {

    @Test
    void emergencySwitchDisablesOnlyTheGovernedSurfaceAndKeepsLegacyWritesRegisteredOn() throws Exception {
        AnalyticsBiFeatureProperties features = new AnalyticsBiFeatureProperties();
        features.setGovernedBiEnabled(false);
        GovernedBiFeatureInterceptor interceptor = new GovernedBiFeatureInterceptor(features, new ObjectMapper());

        MockHttpServletRequest governed = new MockHttpServletRequest("POST", "/api/analysis/12/publish");
        governed.setRequestURI("/api/analysis/12/publish");
        MockHttpServletResponse governedResponse = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(governed, governedResponse, new Object())).isFalse();
        assertThat(governedResponse.getStatus()).isEqualTo(503);
        assertThat(governedResponse.getContentAsString()).contains("GOVERNED_BI_DISABLED");

        MockHttpServletRequest legacy = new MockHttpServletRequest("POST", "/api/card");
        legacy.setRequestURI("/api/card");
        assertThat(interceptor.preHandle(legacy, new MockHttpServletResponse(), new Object())).isTrue();
        assertThat(features.isLegacyCardWriteEnabled()).isTrue();
    }

    @Test
    void enabledSwitchAllowsGovernedRequests() throws Exception {
        AnalyticsBiFeatureProperties features = new AnalyticsBiFeatureProperties();
        GovernedBiFeatureInterceptor interceptor = new GovernedBiFeatureInterceptor(features, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/dashboard/22/publish");
        request.setRequestURI("/api/dashboard/22/publish");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
    }
}
