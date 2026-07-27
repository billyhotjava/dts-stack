package com.yuzhi.dts.admin.web.rest.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.admin.security.AdminInboundServiceAuthenticator;
import com.yuzhi.dts.admin.security.AdminInboundServiceAuthenticator.Decision;
import com.yuzhi.dts.admin.service.dto.keycloak.KeycloakUserDTO;
import com.yuzhi.dts.admin.service.keycloak.KeycloakAdminClient;
import com.yuzhi.dts.admin.service.keycloak.KeycloakAuthService;
import com.yuzhi.dts.admin.service.keycloak.KeycloakAuthService.TokenResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReleaseDutyInternalResourceTest {

    @Mock
    private AdminInboundServiceAuthenticator authenticator;

    @Mock
    private KeycloakAuthService keycloakAuthService;

    @Mock
    private KeycloakAdminClient keycloakAdminClient;

    private ReleaseDutyInternalResource resource;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        resource = new ReleaseDutyInternalResource(
            authenticator,
            keycloakAuthService,
            keycloakAdminClient
        );
        ReflectionTestUtils.setField(
            resource,
            "managementClientId",
            "admin-cli"
        );
        ReflectionTestUtils.setField(
            resource,
            "managementClientSecret",
            "secret"
        );
        request = new MockHttpServletRequest();
    }

    @Test
    void readsTheCurrentExactRealmRoleByKeycloakIdWithoutAUserToken() {
        when(authenticator.authenticate(request, "dts-platform"))
            .thenReturn(new Decision(true, "dts-platform", "accepted"));
        when(
            keycloakAuthService.obtainClientCredentialsToken(
                "admin-cli",
                "secret"
            )
        )
            .thenReturn(
                new TokenResponse(
                    "management-token",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
                )
            );
        KeycloakUserDTO user = new KeycloakUserDTO();
        user.setId("kc-alice");
        user.setUsername("alice");
        user.setEnabled(true);
        when(keycloakAdminClient.findById("kc-alice", "management-token"))
            .thenReturn(Optional.of(user));
        when(
            keycloakAdminClient.listUserRealmRoles(
                "kc-alice",
                "management-token"
            )
        )
            .thenReturn(
                List.of(
                    "ROLE_MODEL_MAINTAINER",
                    "ROLE_CATALOG_MAINTAINER"
                )
            );

        var response = resource.check(
            "kc-alice",
            "MODEL_MAINTAINER",
            request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().hasDuty()).isTrue();
    }

    @Test
    void failsClosedWhenServiceAuthenticationFails() {
        when(authenticator.authenticate(request, "dts-platform"))
            .thenReturn(new Decision(false, null, "token_mismatch"));

        var response = resource.check(
            "alice",
            "MODEL_MAINTAINER",
            request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void reportsUnavailableWhenKeycloakCannotBeQueried() {
        when(authenticator.authenticate(request, "dts-platform"))
            .thenReturn(new Decision(true, "dts-platform", "accepted"));
        when(
            keycloakAuthService.obtainClientCredentialsToken(
                "admin-cli",
                "secret"
            )
        )
            .thenThrow(new IllegalStateException("offline"));

        var response = resource.check(
            "alice",
            "MODEL_MAINTAINER",
            request
        );

        assertThat(response.getStatusCode())
            .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
