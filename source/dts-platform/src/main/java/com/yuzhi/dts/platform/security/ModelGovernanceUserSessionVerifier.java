package com.yuzhi.dts.platform.security;

import com.yuzhi.dts.platform.service.admin.gateway.support.AdminGatewayEnvelope;
import com.yuzhi.dts.platform.service.admin.gateway.support.AdminGatewayException;
import com.yuzhi.dts.platform.service.admin.gateway.support.AdminGatewayRequestOptions;
import com.yuzhi.dts.platform.service.admin.gateway.support.AdminGatewayTarget;
import com.yuzhi.dts.platform.service.admin.gateway.support.AdminGatewayTransport;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** Retains admin logout/inactivity enforcement when a signed user token reaches platform directly. */
@Component
public class ModelGovernanceUserSessionVerifier {

    private static final ParameterizedTypeReference<AdminGatewayEnvelope<Map<String, String>>> IDENTITY =
        new ParameterizedTypeReference<>() {};
    private final AdminGatewayTransport transport;

    public ModelGovernanceUserSessionVerifier(AdminGatewayTransport transport) {
        this.transport = transport;
    }

    public void requireActive(Jwt jwt) {
        Map<String, String> identity;
        try {
            identity = transport.exchangeEnvelopeData(
                AdminGatewayTarget.ADMIN_API, HttpMethod.GET, "/infra/model-governance-policy/identity", null, IDENTITY,
                AdminGatewayRequestOptions.builder().includeServiceAuthorization(false)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.getTokenValue()).build()
            );
        } catch (AdminGatewayException failure) {
            if (Integer.valueOf(401).equals(failure.getUpstreamStatus()) || Integer.valueOf(403).equals(failure.getUpstreamStatus())) {
                throw inactive();
            }
            throw new AuthenticationServiceException("管理员会话校验暂不可用");
        }
        if (identity == null || !jwt.getClaimAsString("preferred_username").equals(identity.get("actor"))) {
            throw inactive();
        }
    }

    private static OAuth2AuthenticationException inactive() {
        return new OAuth2AuthenticationException(new OAuth2Error("invalid_token", "Administrator session is inactive", null));
    }
}
