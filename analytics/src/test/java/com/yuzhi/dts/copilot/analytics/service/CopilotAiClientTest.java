package com.yuzhi.dts.copilot.analytics.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CopilotAiClientTest {

    @Test
    void preservesDatasourceCreateErrorMessageFromAiService() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/ai/copilot/datasources", exchange -> {
            byte[] body = "{\"success\":false,\"error\":\"MySQL data source requires host and database\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(400, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });
        server.start();

        try {
            CopilotAiClient client = new CopilotAiClient(
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    "change-me-in-production");

            assertThatThrownBy(() -> client.createDataSource(Map.of(
                    "name", "园林业务库",
                    "type", "mysql")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("MySQL data source requires host and database");
        } finally {
            server.stop(0);
        }
    }
}
