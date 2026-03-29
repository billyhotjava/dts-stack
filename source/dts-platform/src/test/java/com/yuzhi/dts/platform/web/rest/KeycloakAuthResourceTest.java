package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.security.session.PortalSessionRegistry;
import com.yuzhi.dts.platform.service.admin.gateway.auth.AdminAuthGateway;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.infra.InceptorDataSourceRegistry;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class KeycloakAuthResourceTest {

    @Test
    void pkiChallengeShouldProxyAdminChallengeThroughPlatformApi() {
        AdminAuthGateway gateway = mock(AdminAuthGateway.class);
        when(gateway.getPkiChallenge())
            .thenReturn(new AdminAuthGateway.PkiChallengeView("c-1", "nonce-1", "dts-admin", 123L, 456L));

        KeycloakAuthResource resource = newResource(gateway);

        ResponseEntity<ApiResponse<AdminAuthGateway.PkiChallengeView>> response = resource.pkiChallenge();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().challengeId()).isEqualTo("c-1");
    }

    @Test
    void pkiLoginShouldProxyPayloadThroughPlatformApi() {
        AdminAuthGateway gateway = mock(AdminAuthGateway.class);
        when(gateway.pkiLogin(anyMap())).thenReturn(Map.of("user", Map.of("username", "alice")));

        KeycloakAuthResource resource = newResource(gateway);

        ResponseEntity<ApiResponse<Map<String, Object>>> response = resource.pkiLogin(Map.of("challengeId", "c-1"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).containsKey("user");
    }

    private KeycloakAuthResource newResource(AdminAuthGateway gateway) {
        return new KeycloakAuthResource(
            mock(PortalSessionRegistry.class),
            gateway,
            mock(AuditService.class),
            mock(InceptorDataSourceRegistry.class),
            false,
            false
        );
    }
}
