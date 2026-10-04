package com.yuzhi.dts.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.yuzhi.dts.platform.service.admin.gateway.support.AdminGatewayException;
import com.yuzhi.dts.platform.service.admin.gateway.support.AdminGatewayRequestOptions;
import com.yuzhi.dts.platform.service.admin.gateway.support.AdminGatewayTarget;
import com.yuzhi.dts.platform.service.admin.gateway.support.AdminGatewayTransport;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;

class ModelGovernanceUserSessionVerifierTest {
    private final AdminGatewayTransport transport = mock(AdminGatewayTransport.class);
    private final ModelGovernanceUserSessionVerifier verifier = new ModelGovernanceUserSessionVerifier(transport);
    private final Jwt jwt = Jwt.withTokenValue("signed-user-token").header("alg", "RS256")
        .subject("user-id").claim("preferred_username", "sysadmin").build();

    @Test
    void checksTheExistingSessionUsingOnlyTheVerifiedUserToken() {
        when(transport.exchangeEnvelopeData(any(), any(), anyString(), isNull(), any(), any()))
            .thenReturn(Map.of("actor", "sysadmin"));
        verifier.requireActive(jwt);
        var options = ArgumentCaptor.forClass(AdminGatewayRequestOptions.class);
        verify(transport).exchangeEnvelopeData(eq(AdminGatewayTarget.ADMIN_API), eq(HttpMethod.GET),
            eq("/infra/model-governance-policy/identity"), isNull(), any(), options.capture());
        assertThat(options.getValue().includeServiceAuthorization()).isFalse();
        assertThat(options.getValue().extraHeaders()).containsExactlyEntriesOf(Map.of("Authorization", "Bearer signed-user-token"));
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403})
    void refusesRevokedOrUnauthorizedSessions(int status) {
        when(transport.exchangeEnvelopeData(any(), any(), anyString(), isNull(), any(), any()))
            .thenThrow(new AdminGatewayException("denied", status, "/identity"));
        assertThatThrownBy(() -> verifier.requireActive(jwt)).isInstanceOf(OAuth2AuthenticationException.class);
    }

    @Test
    void rejectsAnIdentityMismatchOrEmptyResponse() {
        when(transport.exchangeEnvelopeData(any(), any(), anyString(), isNull(), any(), any()))
            .thenReturn(Map.of("actor", "other-user"), null);
        assertThatThrownBy(() -> verifier.requireActive(jwt)).isInstanceOf(OAuth2AuthenticationException.class);
        assertThatThrownBy(() -> verifier.requireActive(jwt)).isInstanceOf(OAuth2AuthenticationException.class);
    }

    @Test
    void sessionServiceOutageFailsClosedWithoutDisclosingUpstreamDetails() {
        when(transport.exchangeEnvelopeData(any(), any(), anyString(), isNull(), any(), any()))
            .thenThrow(new AdminGatewayException("internal details", null, "/identity"));
        assertThatThrownBy(() -> verifier.requireActive(jwt)).isInstanceOf(AuthenticationServiceException.class)
            .hasMessage("管理员会话校验暂不可用");
    }
}
