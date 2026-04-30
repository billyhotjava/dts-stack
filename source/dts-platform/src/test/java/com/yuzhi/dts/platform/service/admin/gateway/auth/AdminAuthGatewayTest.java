package com.yuzhi.dts.platform.service.admin.gateway.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.yuzhi.dts.platform.config.DtsAdminProperties;
import com.yuzhi.dts.platform.service.admin.gateway.support.AdminGatewayHeaders;
import com.yuzhi.dts.platform.service.admin.gateway.support.AdminGatewayTransport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class AdminAuthGatewayTest {

    private AdminAuthGateway gateway;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        DtsAdminProperties properties = new DtsAdminProperties();
        properties.setBaseUrl("http://dts-admin.test:8081");
        properties.setApiPath("/api");
        properties.setAdminApiPath("/api/admin");
        properties.setServiceToken("svc-token");
        properties.setServiceName("dts-platform");

        AdminGatewayTransport transport = new AdminGatewayTransport(
            new RestTemplateBuilder(),
            properties,
            new AdminGatewayHeaders(properties)
        );
        gateway = new AdminAuthGateway(transport, properties);
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(transport, "restTemplate");
        server = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();
    }

    @Test
    void profileShouldUseAuthenticatedProfileEndpointWithoutPassword() {
        server
            .expect(requestTo("http://dts-admin.test:8081/api/keycloak/auth/platform/profile?auditSilent=true"))
            .andExpect(method(POST))
            .andExpect(request -> assertThat(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer kc-access"))
            .andExpect(content().string(not(containsString("secret"))))
            .andRespond(
                withSuccess(
                    """
                    {
                      "status":"SUCCESS",
                      "data":{
                        "user":{"username":"alice","roles":["ROLE_USER","ROLE_DEPT_DATA_OWNER"]}
                      }
                    }
                    """,
                    MediaType.APPLICATION_JSON
                )
            );

        AdminAuthGateway.ProfileResult result = gateway.profile(
            "alice",
            Map.of("username", "alice", "roles", List.of("ROLE_USER")),
            "kc-access"
        );

        assertThat(result.user()).containsEntry("username", "alice");
        assertThat(result.user().get("roles")).asList().contains("ROLE_DEPT_DATA_OWNER");
    }

    @Test
    void profileShouldExposeMissingEndpointForLegacyAdminFallback() {
        server
            .expect(requestTo("http://dts-admin.test:8081/api/keycloak/auth/platform/profile?auditSilent=true"))
            .andExpect(method(POST))
            .andRespond(
                withStatus(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(
                        """
                        {
                          "status":404,
                          "message":"error.http.404",
                          "detail":"No static resource api/keycloak/auth/platform/profile."
                        }
                        """
                    )
            );

        org.assertj.core.api.Assertions
            .assertThatThrownBy(() -> gateway.profile("alice", Map.of("username", "alice"), "kc-access"))
            .isInstanceOf(AdminAuthGateway.ProfileEndpointUnavailableException.class)
            .hasMessageContaining("error.http.404");
    }

    @Test
    void loginShouldUsePlatformLoginEndpointAndReturnAdminTokens() {
        server
            .expect(requestTo("http://dts-admin.test:8081/api/keycloak/auth/platform/login?auditSilent=true"))
            .andExpect(method(POST))
            .andExpect(request -> assertThat(request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)).isFalse())
            .andRespond(
                withSuccess(
                    """
                    {
                      "status":"SUCCESS",
                      "data":{
                        "user":{"username":"alice","roles":["ROLE_USER"]},
                        "accessToken":"access-1",
                        "refreshToken":"refresh-1",
                        "expiresIn":300,
                        "refreshExpiresIn":1800
                      }
                    }
                    """,
                    MediaType.APPLICATION_JSON
                )
            );

        AdminAuthGateway.LoginResult result = gateway.login("alice", "secret");

        assertThat(result.user()).containsEntry("username", "alice");
        assertThat(result.accessToken()).isEqualTo("access-1");
        assertThat(result.refreshToken()).isEqualTo("refresh-1");
        assertThat(result.accessTokenExpiresIn()).isEqualTo(300L);
        assertThat(result.refreshTokenExpiresIn()).isEqualTo(1800L);
    }

    @Test
    void getPkiChallengeShouldReturnChallengeView() {
        server
            .expect(requestTo("http://dts-admin.test:8081/api/keycloak/auth/pki-challenge"))
            .andExpect(method(GET))
            .andExpect(request -> assertThat(request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)).isFalse())
            .andRespond(
                withSuccess(
                    """
                    {
                      "status":"SUCCESS",
                      "data":{
                        "challengeId":"c-1",
                        "nonce":"nonce-1",
                        "aud":"dts-admin",
                        "ts":123,
                        "exp":456
                      }
                    }
                    """,
                    MediaType.APPLICATION_JSON
                )
            );

        AdminAuthGateway.PkiChallengeView challenge = gateway.getPkiChallenge();

        assertThat(challenge.challengeId()).isEqualTo("c-1");
        assertThat(challenge.nonce()).isEqualTo("nonce-1");
        assertThat(challenge.aud()).isEqualTo("dts-admin");
    }

    @Test
    void pkiLoginShouldReturnUpstreamPayloadData() {
        server
            .expect(requestTo("http://dts-admin.test:8081/api/keycloak/auth/pki-login"))
            .andExpect(method(POST))
            .andExpect(request -> assertThat(request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)).isFalse())
            .andRespond(
                withSuccess(
                    """
                    {
                      "status":"SUCCESS",
                      "data":{
                        "user":{
                          "username":"alice",
                          "fullName":"Alice"
                        }
                      }
                    }
                    """,
                    MediaType.APPLICATION_JSON
                )
            );

        Map<String, Object> response = gateway.pkiLogin(Map.of("challengeId", "c-1", "nonce", "n-1"));

        assertThat(response).containsKey("user");
    }

    @Test
    void refreshShouldNotSendAuthorizationHeaderToPermitAllEndpoint() {
        server
            .expect(requestTo("http://dts-admin.test:8081/api/keycloak/auth/refresh?auditSilent=true"))
            .andExpect(method(POST))
            .andExpect(request -> assertThat(request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)).isFalse())
            .andRespond(
                withSuccess(
                    """
                    {
                      "status":"SUCCESS",
                      "data":{
                        "accessToken":"access-2",
                        "refreshToken":"refresh-2",
                        "expiresIn":300,
                        "refreshExpiresIn":1800
                      }
                    }
                    """,
                    MediaType.APPLICATION_JSON
                )
            );

        AdminAuthGateway.RefreshResult result = gateway.refresh("refresh-1");

        assertThat(result.accessToken()).isEqualTo("access-2");
        assertThat(result.refreshToken()).isEqualTo("refresh-2");
    }

    @Test
    void logoutShouldNotSendAuthorizationHeaderToPermitAllEndpoint() {
        server
            .expect(requestTo("http://dts-admin.test:8081/api/keycloak/auth/logout?auditSilent=true"))
            .andExpect(method(POST))
            .andExpect(request -> assertThat(request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)).isFalse())
            .andRespond(
                withSuccess(
                    """
                    {
                      "status":"SUCCESS",
                      "data":null
                    }
                    """,
                    MediaType.APPLICATION_JSON
                )
            );

        gateway.logout("refresh-1");
    }
}
