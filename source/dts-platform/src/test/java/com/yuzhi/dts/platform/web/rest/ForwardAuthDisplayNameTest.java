package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class ForwardAuthDisplayNameTest {
    @Test
    void transportsChineseDisplayNameWithoutIllegalHttpHeaderBytes() {
        String name = "测试管理员";
        var jwt = Jwt.withTokenValue("test-token").header("alg", "none")
            .claim("sub", "user-1").claim("preferred_username", "screen-editor")
            .claim("name", name).build();
        var authentication = new JwtAuthenticationToken(jwt);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            var response = new ForwardAuthResource().forwardAuth(authentication, new MockHttpServletRequest());
            assertThat(response.getStatusCode().value()).isEqualTo(204);
            String header = response.getHeaders().getFirst("X-DTS-Display-Name");
            assertThat(header).startsWith("=?UTF-8?B?").endsWith("?=");
            assertThat(header.chars().allMatch(c -> c >= 32 && c < 127)).isTrue();
            assertThat(new String(Base64.getDecoder().decode(header.substring(10, header.length() - 2)),
                StandardCharsets.UTF_8)).isEqualTo(name);
            assertThat(response.getHeaders().getFirst("X-DTS-User")).isEqualTo("screen-editor");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
