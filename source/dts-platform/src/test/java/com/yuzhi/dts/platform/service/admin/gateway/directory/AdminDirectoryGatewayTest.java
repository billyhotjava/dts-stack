package com.yuzhi.dts.platform.service.admin.gateway.directory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.yuzhi.dts.platform.config.DtsAdminProperties;
import com.yuzhi.dts.platform.service.admin.gateway.support.AdminGatewayHeaders;
import com.yuzhi.dts.platform.service.admin.gateway.support.AdminGatewayTransport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class AdminDirectoryGatewayTest {

    private AdminDirectoryGateway gateway;
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
        gateway = new AdminDirectoryGateway(transport, properties);
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(transport, "restTemplate");
        server = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();
    }

    @Test
    void fetchOrgTreeShouldFallbackToSyncWhenAdminReturnsEmptyTree() {
        server
            .expect(requestTo("http://dts-admin.test:8081/api/admin/platform/orgs"))
            .andExpect(method(GET))
            .andRespond(
                withSuccess(
                    """
                    {"status":"SUCCESS","data":[]}
                    """,
                    MediaType.APPLICATION_JSON
                )
            );
        server
            .expect(requestTo("http://dts-admin.test:8081/api/admin/platform/orgs/sync"))
            .andExpect(method(POST))
            .andRespond(
                withSuccess(
                    """
                    {
                      "status":"SUCCESS",
                      "data":[{"id":1,"name":"研究所","deptCode":"1000","isRoot":true}]
                    }
                    """,
                    MediaType.APPLICATION_JSON
                )
            );

        assertThat(gateway.fetchOrgTree())
            .extracting(AdminDirectoryGateway.OrgNode::getName)
            .containsExactly("研究所");
    }

    @Test
    void searchUsersShouldUsePlatformDirectoryEndpointFirst() {
        server
            .expect(requestTo("http://dts-admin.test:8081/api/platform/directory/users?keyword=alice"))
            .andExpect(method(GET))
            .andRespond(
                withSuccess(
                    """
                    {
                      "status":"SUCCESS",
                      "data":[{"id":"u1","username":"alice","displayName":"Alice","deptCode":"1001"}]
                    }
                    """,
                    MediaType.APPLICATION_JSON
                )
            );

        assertThat(gateway.searchUsers("alice"))
            .containsExactly(new AdminDirectoryGateway.UserSummary("u1", "alice", "Alice", "1001"));
    }

    @Test
    void listRolesShouldFallbackToLegacyEndpointWhenPlatformDirectoryIsUnavailable() {
        server
            .expect(requestTo("http://dts-admin.test:8081/api/platform/directory/roles"))
            .andExpect(method(GET))
            .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server
            .expect(requestTo("http://dts-admin.test:8081/api/keycloak/platform/roles"))
            .andExpect(method(GET))
            .andRespond(
                withSuccess(
                    """
                    [
                      {"id":"r1","name":"data_admin","description":"管理员"}
                    ]
                    """,
                    MediaType.APPLICATION_JSON
                )
            );

        assertThat(gateway.listRoles())
            .containsExactly(
                new AdminDirectoryGateway.RoleSummary(
                    "r1",
                    "ROLE_DATA_ADMIN",
                    "管理员",
                    null,
                    java.util.List.of(),
                    "legacy"
                )
            );
    }
}
