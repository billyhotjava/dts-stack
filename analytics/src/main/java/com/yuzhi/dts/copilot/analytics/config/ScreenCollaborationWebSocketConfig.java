package com.yuzhi.dts.copilot.analytics.config;

import com.yuzhi.dts.copilot.analytics.web.socket.ScreenCollaborationHandshakeInterceptor;
import com.yuzhi.dts.copilot.analytics.web.socket.ScreenCollaborationWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class ScreenCollaborationWebSocketConfig implements WebSocketConfigurer {

    private final ScreenCollaborationWebSocketHandler webSocketHandler;
    private final ScreenCollaborationHandshakeInterceptor handshakeInterceptor;

    public ScreenCollaborationWebSocketConfig(
            ScreenCollaborationWebSocketHandler webSocketHandler,
            ScreenCollaborationHandshakeInterceptor handshakeInterceptor) {
        this.webSocketHandler = webSocketHandler;
        this.handshakeInterceptor = handshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webSocketHandler, "/api/screens/*/collaboration/ws")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
