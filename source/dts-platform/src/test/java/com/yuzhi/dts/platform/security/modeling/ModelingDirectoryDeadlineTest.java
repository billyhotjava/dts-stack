package com.yuzhi.dts.platform.security.modeling;

import static org.assertj.core.api.Assertions.*;
import com.sun.net.httpserver.HttpServer;
import com.yuzhi.dts.platform.config.PlatformOutboundAdminProperties;
import com.yuzhi.dts.platform.service.admin.gateway.support.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;

class ModelingDirectoryDeadlineTest {
    @Test void directoryRequestTimesOutWithoutRetryOrCachedAllow() throws Exception {
        var requests = new AtomicInteger(); var release = new CountDownLatch(1);
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0); server.setExecutor(executor);
        server.createContext("/api/platform/directory/users/resolve", exchange -> {
            requests.incrementAndGet();
            try { release.await(5,TimeUnit.SECONDS); } catch(InterruptedException error) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
        try {
            var transport = transport(server); long start = System.nanoTime();
            assertThatThrownBy(() -> transport.currentDirectory("/platform/directory/users/resolve?purpose=modeling&principalKey=user",new ParameterizedTypeReference<AdminGatewayEnvelope<Map<String,Object>>>() {}))
                .isInstanceOfSatisfying(AdminGatewayException.class, error -> assertThat(error.getUpstreamStatus()).isEqualTo(503));
            assertThat(Duration.ofNanos(System.nanoTime()-start)).isBetween(Duration.ofMillis(1700),Duration.ofMillis(4000));
            assertThat(requests.get()).isEqualTo(1);
        } finally { release.countDown(); server.stop(0); executor.close(); }
    }
    @Test void successfulLookupDoesNotMaskTheNextDirectoryFailure() throws Exception {
        var requests = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/platform/directory/users/resolve", exchange -> {
            int call = requests.incrementAndGet(); byte[] body = (call==1 ? "{\"status\":\"200\",\"data\":{\"id\":\"stable-user\"}}" : "{\"status\":\"ERROR\",\"data\":null}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(call==1?200:503,body.length);
            exchange.getResponseBody().write(body);exchange.close();
        });
        server.start();
        try {
            var transport=transport(server); var type=new ParameterizedTypeReference<AdminGatewayEnvelope<Map<String,Object>>>() {};
            assertThat(transport.currentDirectory("/platform/directory/users/resolve",type)).containsEntry("id","stable-user");
            assertThatThrownBy(()->transport.currentDirectory("/platform/directory/users/resolve",type)).isInstanceOf(AdminGatewayException.class);
            assertThat(requests.get()).isEqualTo(2);
        } finally {server.stop(0);}
    }
    private AdminGatewayTransport transport(HttpServer server) {
        var properties=new PlatformOutboundAdminProperties();properties.setBaseUrl("http://127.0.0.1:"+server.getAddress().getPort());properties.setApiPath("/api");
        return new AdminGatewayTransport(new RestTemplateBuilder(),properties);
    }
}
