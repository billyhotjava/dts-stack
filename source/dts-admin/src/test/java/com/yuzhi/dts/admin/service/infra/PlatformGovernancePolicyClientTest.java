package com.yuzhi.dts.admin.service.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.yuzhi.dts.admin.config.PlatformIntegrationProperties;
import com.yuzhi.dts.admin.service.infra.PlatformGovernancePolicyClient.PlatformPolicyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
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
        // Existing deployments do not need an admin-to-platform service credential.
        properties.setServiceToken(null);
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new PlatformGovernancePolicyClient(restTemplate, properties);
        login("first-user-token");
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    private void login(String token) {
        Jwt jwt = Jwt.withTokenValue(token).header("alg", "RS256").subject("sysadmin").build();
        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthenticationToken(jwt, AuthorityUtils.createAuthorityList("ROLE_SYS_ADMIN"), "sysadmin"));
    }

    @Test
    void forwardsTheAuthenticatedUserAndExpectedRevisionWithoutServiceCredentials() {
        server.expect(requestTo(POLICY_URL))
            .andExpect(method(HttpMethod.PUT))
            .andExpect(header("Authorization", "Bearer first-user-token"))
            .andExpect(headerDoesNotExist("X-DTS-Service"))
            .andExpect(headerDoesNotExist("X-DTS-Service-Token"))
            .andExpect(jsonPath("$.qualityGate").value("BLOCKING"))
            .andExpect(jsonPath("$.expectedRevision").value(1))
            .andExpect(jsonPath("$.actor").doesNotExist())
            .andRespond(withSuccess(POLICY_JSON, MediaType.APPLICATION_JSON));

        var view = client.update("BLOCKING", 1, "收紧");

        assertThat(view.qualityGate()).isEqualTo("BLOCKING");
        assertThat(view.revision()).isEqualTo(2);
        server.verify();
    }

    @Test
    void revisionConflictIsReportedAsConflict() {
        server.expect(requestTo(POLICY_URL)).andRespond(withStatus(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> client.update("ADVISORY", 1, "放宽"))
            .isInstanceOfSatisfying(PlatformPolicyException.class, failure -> {
                assertThat(failure.status()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(failure.code()).isEqualTo("GOVERNANCE_POLICY_REVISION_CONFLICT");
            });
    }

    @Test
    void rejectedUserTokenRequestsLoginAgain() {
        server.expect(requestTo(POLICY_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.current())
            .isInstanceOfSatisfying(PlatformPolicyException.class, failure -> {
                assertThat(failure.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
                assertThat(failure.code()).isEqualTo("GOVERNANCE_POLICY_LOGIN_REQUIRED");
            });
    }

    @Test
    void missingUserIdentityFailsEvenIfALegacyServiceTokenExists() {
        properties.setServiceToken("legacy-service-token");
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> client.update("BLOCKING", 1, "收紧"))
            .isInstanceOfSatisfying(PlatformPolicyException.class, failure ->
                assertThat(failure.code()).isEqualTo("GOVERNANCE_POLICY_LOGIN_REQUIRED"));
        server.verify();
    }

    @Test
    void readsUseEachRequestsIdentityRatherThanACachedToken() {
        server.expect(requestTo(POLICY_URL)).andExpect(header("Authorization", "Bearer first-user-token"))
            .andRespond(withSuccess(POLICY_JSON, MediaType.APPLICATION_JSON));
        server.expect(requestTo(POLICY_URL + "/impact")).andExpect(header("Authorization", "Bearer second-user-token"))
            .andRespond(withSuccess("{\"unfrozenCandidates\":3,\"frozenCandidates\":2}", MediaType.APPLICATION_JSON));
        client.current();
        login("second-user-token");
        assertThat(client.impact().unfrozenCandidates()).isEqualTo(3);
        server.verify();
    }

    @Test
    void authorizationFailureIsNotReportedAsAConfigurationFailure() {
        server.expect(requestTo(POLICY_URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> client.current()).isInstanceOfSatisfying(PlatformPolicyException.class, failure -> {
            assertThat(failure.status()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(failure.code()).isEqualTo("GOVERNANCE_POLICY_ACCESS_DENIED");
        });
    }

    @Test
    void springUsesTheBuilderConstructorWhenTwoConstructorsExist() throws Exception {
        // Two constructors without a marker made the admin context fail with "No default constructor found".
        assertThat(
            PlatformGovernancePolicyClient.class
                .getConstructor(RestTemplateBuilder.class, PlatformIntegrationProperties.class)
                .isAnnotationPresent(Autowired.class)
        ).isTrue();
    }
}
