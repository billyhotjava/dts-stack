package com.yuzhi.dts.platform.service.admin.gateway.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.yuzhi.dts.platform.config.DtsAdminProperties;
import com.yuzhi.dts.platform.service.admin.gateway.support.AdminGatewayHeaders;
import com.yuzhi.dts.platform.service.admin.gateway.support.AdminGatewayTransport;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
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
    void loginShouldUsePlatformLoginEndpointAndReturnAdminTokens() {
        server
            .expect(requestTo("http://dts-admin.test:8081/api/keycloak/auth/platform/login?auditSilent=true"))
            .andExpect(method(POST))
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
}
