package com.yuzhi.dts.admin.service.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.yuzhi.dts.admin.config.PlatformIntegrationProperties;
import com.yuzhi.dts.admin.service.infra.PlatformGovernancePolicyClient.PlatformPolicyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class PlatformGovernancePolicyClientTest {

    private static final String POLICY_URL = "http://dts-platform:8081/api/internal/modeling/governance-policy";
    private static final String POLICY_JSON = """
        {"qualityGate":"BLOCKING","standardCoverage":"KEY_AND_MEASURE","revision":2,
         "lastModifiedBy":"sysadmin","lastModifiedDate":"2026-09-24T09:00:00Z","systemDefault":false}
        """;

    private PlatformIntegrationProperties properties;
    private MockRestServiceServer server;
    private PlatformGovernancePolicyClient client;

    @BeforeEach
    void setUp() {
        properties = new PlatformIntegrationProperties();
        properties.setServiceToken("admin-to-platform-secret");
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new PlatformGovernancePolicyClient(restTemplate, properties);
    }

    @Test
    void sendsTheAdminServiceIdentityAndExpectedRevision() {
        server.expect(requestTo(POLICY_URL))
            .andExpect(method(HttpMethod.PUT))
            .andExpect(header("X-DTS-Service", "dts-admin"))
            .andExpect(header("X-DTS-Service-Token", "admin-to-platform-secret"))
            .andExpect(jsonPath("$.qualityGate").value("BLOCKING"))
            .andExpect(jsonPath("$.expectedRevision").value(1))
            .andExpect(jsonPath("$.actor").value("sysadmin"))
            .andRespond(withSuccess(POLICY_JSON, MediaType.APPLICATION_JSON));

        var view = client.update("BLOCKING", 1, "sysadmin", "收紧");

        assertThat(view.qualityGate()).isEqualTo("BLOCKING");
        assertThat(view.revision()).isEqualTo(2);
        server.verify();
    }

    @Test
    void revisionConflictIsReportedAsConflict() {
        server.expect(requestTo(POLICY_URL)).andRespond(withStatus(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> client.update("ADVISORY", 1, "sysadmin", "放宽"))
            .isInstanceOfSatisfying(PlatformPolicyException.class, failure -> {
                assertThat(failure.status()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(failure.code()).isEqualTo("GOVERNANCE_POLICY_REVISION_CONFLICT");
            });
    }

    @Test
    void rejectedServiceCredentialNamesTheMisconfiguration() {
        server.expect(requestTo(POLICY_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.current())
            .isInstanceOfSatisfying(PlatformPolicyException.class, failure -> {
                assertThat(failure.status()).isEqualTo(HttpStatus.BAD_GATEWAY);
                assertThat(failure.code()).isEqualTo("PLATFORM_SERVICE_AUTH_REJECTED");
            });
    }

    @Test
    void missingServiceTokenFailsWithoutCallingThePlatform() {
        properties.setServiceToken(" ");

        assertThatThrownBy(() -> client.update("BLOCKING", 1, "sysadmin", "收紧"))
            .isInstanceOfSatisfying(PlatformPolicyException.class, failure ->
                assertThat(failure.code()).isEqualTo("PLATFORM_SERVICE_TOKEN_MISSING"));
        server.verify();
    }
}
