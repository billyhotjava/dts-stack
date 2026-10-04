package com.yuzhi.dts.analytics.service.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.analytics.config.DtsAdminProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class AdminAuditHttpHeadersFactoryTest {

    @Test
    void buildsServiceTokenHeaderWithoutOAuthAuthorization() {
        DtsAdminProperties properties = new DtsAdminProperties();
        properties.setServiceName(" dts-analytics ");
        properties.setServiceToken("analytics-admin-secret");

        HttpHeaders headers = AdminAuditHttpHeadersFactory.build(properties);

        assertThat(headers.getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(headers.getFirst("X-DTS-Service")).isEqualTo("dts-analytics");
        assertThat(headers.getFirst("X-DTS-Service-Token")).isEqualTo("analytics-admin-secret");
        assertThat(headers.containsKey(HttpHeaders.AUTHORIZATION)).isFalse();
    }

    @Test
    void stripsBearerPrefixFromLegacyServiceTokenValues() {
        DtsAdminProperties properties = new DtsAdminProperties();
        properties.setServiceToken(" Bearer legacy-secret ");

        HttpHeaders headers = AdminAuditHttpHeadersFactory.build(properties);

        assertThat(headers.getFirst("X-DTS-Service-Token")).isEqualTo("legacy-secret");
        assertThat(headers.containsKey(HttpHeaders.AUTHORIZATION)).isFalse();
    }
}
