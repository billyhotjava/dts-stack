package com.yuzhi.dts.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.analytics.config.AnalyticsOutboundPlatformProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class PlatformPermissionClientTest {

    @Test
    void checkCacheSeparatesSameUserByRolesAndDept() {
        PlatformPermissionClient client = buildClient(props());
        MockRestServiceServer server = bindServer(client);
        server
            .expect(requestTo("http://platform.test/api/internal/asset-permission/check"))
            .andExpect(method(POST))
            .andExpect(header("X-DTS-Service", "dts-analytics"))
            .andExpect(header("X-DTS-Service-Token", "analytics-secret"))
            .andRespond(withSuccess(
                "{\"allowed\":true,\"permission\":\"READ\",\"reason\":\"explicit_grant\"}",
                MediaType.APPLICATION_JSON
            ));
        server
            .expect(requestTo("http://platform.test/api/internal/asset-permission/check"))
            .andExpect(method(POST))
            .andRespond(withSuccess(
                "{\"allowed\":false,\"permission\":null,\"reason\":\"denied\"}",
                MediaType.APPLICATION_JSON
            ));

        PlatformPermissionClient.PermissionResult first =
            client.check("ptrdemo", "ROLE_FLOWER", "D1", "DASHBOARD", "100");
        PlatformPermissionClient.PermissionResult second =
            client.check("ptrdemo", "ROLE_OTHER", "D2", "DASHBOARD", "100");

        assertThat(first.allowed()).isTrue();
        assertThat(second.allowed()).isFalse();
        server.verify();
    }

    @Test
    void listCacheSeparatesSameUserByRolesAndDept() {
        PlatformPermissionClient client = buildClient(props());
        MockRestServiceServer server = bindServer(client);
        server
            .expect(requestTo("http://platform.test/api/internal/asset-permission/accessible-ids"))
            .andExpect(method(POST))
            .andRespond(withSuccess(
                "{\"assetIds\":[\"1\"],\"total\":1,\"scope\":\"FILTERED\"}",
                MediaType.APPLICATION_JSON
            ));
        server
            .expect(requestTo("http://platform.test/api/internal/asset-permission/accessible-ids"))
            .andExpect(method(POST))
            .andRespond(withSuccess(
                "{\"assetIds\":[\"2\"],\"total\":1,\"scope\":\"FILTERED\"}",
                MediaType.APPLICATION_JSON
            ));

        PlatformPermissionClient.AccessibleAssetsResult first =
            client.listAccessibleAssetIds("ptrdemo", "ROLE_FLOWER", "D1", "DASHBOARD", 0, 100);
        PlatformPermissionClient.AccessibleAssetsResult second =
            client.listAccessibleAssetIds("ptrdemo", "ROLE_OTHER", "D2", "DASHBOARD", 0, 100);

        assertThat(first.assetIds()).containsExactly("1");
        assertThat(second.assetIds()).containsExactly("2");
        server.verify();
    }

    @Test
    void batchCheckPopulatesSingleCheckCacheUsingRoleScopedKey() {
        PlatformPermissionClient client = buildClient(props());
        MockRestServiceServer server = bindServer(client);
        server
            .expect(requestTo("http://platform.test/api/internal/asset-permission/batch-check"))
            .andExpect(method(POST))
            .andRespond(withSuccess(
                "{\"results\":{\"DASHBOARD:100\":{\"allowed\":true,\"permission\":\"READ\",\"reason\":\"explicit_grant\"}}}",
                MediaType.APPLICATION_JSON
            ));

        client.batchCheck(
            "ptrdemo",
            "ROLE_FLOWER",
            "D1",
            List.of(new PlatformPermissionClient.AssetRef("DASHBOARD", "100"))
        );
        PlatformPermissionClient.PermissionResult cached =
            client.check("ptrdemo", "ROLE_FLOWER", "D1", "DASHBOARD", "100");

        assertThat(cached.allowed()).isTrue();
        server.verify();
    }

    @Test
    void authorizeSeparatesAssetAdministrationFromClassificationEnforcement() {
        PlatformPermissionClient client = buildClient(props());
        MockRestServiceServer server = bindServer(client);
        server
            .expect(requestTo("http://platform.test/api/internal/asset-permission/check"))
            .andExpect(method(POST))
            .andExpect(content().json(
                """
                {
                  "username": "xiezm",
                  "userRoles": ["ROLE_INST_DATA_OWNER"],
                  "userDeptCode": "D1",
                  "action": "EDIT",
                  "authorizationOnly": true,
                  "asset": {"type": "CARD", "id": "42"}
                }
                """,
                false
            ))
            .andRespond(withSuccess(
                "{\"allowed\":true,\"permission\":\"MANAGE\",\"reason\":\"inst_manage\"}",
                MediaType.APPLICATION_JSON
            ));

        PlatformPermissionClient.PermissionResult result = client.authorize(
            "xiezm",
            "ROLE_INST_DATA_OWNER",
            "D1",
            "CARD",
            "42",
            "EDIT"
        );

        assertThat(result.allowed()).isTrue();
        server.verify();
    }

    @Test
    void registersAssetOwnershipAndCreatorGrantThroughOnePlatformCommand() {
        PlatformPermissionClient client = buildClient(props());
        MockRestServiceServer server = bindServer(client);
        server
            .expect(requestTo("http://platform.test/api/internal/asset-permission/ownership"))
            .andExpect(method(POST))
            .andExpect(content().json(
                """
                {
                  "assetType": "DASHBOARD",
                  "assetId": "7",
                  "ownerDeptCode": "D1",
                  "ownerUsername": "xiezm",
                  "assignedBy": "xiezm",
                  "sourceId": "dts-analytics"
                }
                """,
                true
            ))
            .andRespond(withSuccess(
                "{\"ownershipRegistered\":true,\"creatorGrantRegistered\":true}",
                MediaType.APPLICATION_JSON
            ));

        client.registerAssetAccess("DASHBOARD", "7", "D1", "xiezm");

        server.verify();
    }

    private PlatformPermissionClient buildClient(AnalyticsOutboundPlatformProperties props) {
        return new PlatformPermissionClient(
            new RestTemplateBuilder(),
            new ObjectMapper(),
            props,
            2_000,
            5_000,
            300,
            300,
            1_000
        );
    }

    private MockRestServiceServer bindServer(PlatformPermissionClient client) {
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
        return MockRestServiceServer.bindTo(restTemplate).build();
    }

    private AnalyticsOutboundPlatformProperties props() {
        AnalyticsOutboundPlatformProperties props = new AnalyticsOutboundPlatformProperties();
        props.setBaseUrl("http://platform.test");
        props.setServiceName("dts-analytics");
        props.setServiceToken("analytics-secret");
        return props;
    }
}
