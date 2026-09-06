package com.yuzhi.dts.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpServer;
import com.yuzhi.dts.analytics.config.PlatformAuthProperties;
import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.repository.AnalyticsUserRepository;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

class PlatformDisplayNameTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void decodesDisplayNamesThroughProxyAndDirectAuth(boolean direct) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String[] wireName = {""};
        server.createContext("/auth", exchange -> {
            exchange.getResponseHeaders().add("X-DTS-User", "screen-editor");
            exchange.getResponseHeaders().add("X-DTS-Display-Name", wireName[0]);
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        try {
            var repository = mock(AnalyticsUserRepository.class);
            var encoder = mock(PasswordEncoder.class);
            when(encoder.encode(anyString())).thenReturn("hash");
            when(repository.save(any(AnalyticsUser.class))).thenAnswer(call -> call.getArgument(0));
            var service = new PlatformTrustedUserService(new PlatformAuthProperties(true, true, "platform.local",
                List.of("ROLE_OP_ADMIN"), direct, "http://127.0.0.1:" + server.getAddress().getPort() + "/auth", 2000),
                repository, mock(GroupService.class), encoder);
            for (String name : List.of("测试管理员", "Ops + Admin", "旧版姓名")) {
                wireName[0] = name.equals("旧版姓名") && !direct ? name :
                    "=?UTF-8?B?" + Base64.getEncoder().encodeToString(name.getBytes(StandardCharsets.UTF_8)) + "?=";
                var request = new MockHttpServletRequest();
                if (direct) {
                    request.addHeader("Authorization", "Bearer test-token");
                } else {
                    request.addHeader("X-Forwarded-Proto", "https");
                    request.addHeader("X-DTS-User", "screen-editor");
                    request.addHeader("X-DTS-Display-Name", wireName[0]);
                }
                var user = service.resolveOrProvision(request).orElseThrow();
                assertThat(user.getFirstName()).isEqualTo(name);
                assertThat(user.getPlatformUsername()).isEqualTo("screen-editor");
                assertThat(user.isSuperuser()).isFalse();
            }
        } finally {
            server.stop(0);
        }
    }
}
