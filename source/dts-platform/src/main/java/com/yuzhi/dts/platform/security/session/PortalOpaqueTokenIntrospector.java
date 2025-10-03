package com.yuzhi.dts.platform.security.session;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenIntrospectionClaimNames;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.stereotype.Component;

@Component
public class PortalOpaqueTokenIntrospector implements OpaqueTokenIntrospector {

    private final PortalSessionRegistry sessionRegistry;

    public PortalOpaqueTokenIntrospector(PortalSessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public DefaultOAuth2AuthenticatedPrincipal introspect(String token) {
        var session = sessionRegistry.findByAccessToken(token).orElseThrow(this::invalidToken);

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(OAuth2TokenIntrospectionClaimNames.ACTIVE, Boolean.TRUE);
        attributes.put(OAuth2TokenIntrospectionClaimNames.USERNAME, session.username());
        attributes.put("sub", session.username());
        attributes.put("roles", session.roles());
        attributes.put("permissions", session.permissions());
        attributes.put("token_type", "demo");
        attributes.put(OAuth2TokenIntrospectionClaimNames.EXP, session.expiresAt());
        attributes.put(OAuth2TokenIntrospectionClaimNames.IAT, Instant.now());

        List<GrantedAuthority> authorities = session
            .roles()
            .stream()
            .map(SimpleGrantedAuthority::new)
            .collect(Collectors.toList());

        return new DefaultOAuth2AuthenticatedPrincipal(attributes, authorities);
    }

    private OAuth2AuthenticationException invalidToken() {
        return new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN));
    }
}
