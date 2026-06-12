package com.yuzhi.dts.ingestion.service.etl.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import com.yuzhi.dts.ingestion.config.ApiProperties;
import com.yuzhi.dts.ingestion.service.etl.connector.ExecutionPlan;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

class ApiHttpEngineTest {

    private static final String TEST_KEYSTORE_BASE64 =
        "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";
    private static final char[] TEST_KEYSTORE_PASSWORD = "changeit".toCharArray();

    private HttpServer server;
    private ExecutorService serverExecutor;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
            serverExecutor = null;
        }
    }

    @Test
    void execute_shouldUseConfiguredDefaultMaxResponseBytes() throws Exception {
        startServer(exchange -> write(exchange, 200, "12345"));
        ApiProperties properties = testProperties();
        properties.setMaxResponseBytes(DataSize.ofBytes(4));
        ApiHttpEngine engine = new ApiHttpEngine(duration -> {}, properties);

        assertThatThrownBy(() -> engine.execute(plan(Map.of(), Map.of(), Map.of("path", "/orders"))))
            .isInstanceOf(ApiHttpException.class)
            .extracting("code")
            .isEqualTo("API_RUNTIME_RESPONSE_TOO_LARGE");
    }

    @Test
    void execute_shouldBlockHttpWhenGlobalPolicyDisallowsIt() throws Exception {
        startServer(exchange -> write(exchange, 200, "ok"));
        ApiHttpEngine engine = new ApiHttpEngine(duration -> {}, new ApiProperties());

        assertThatThrownBy(() -> engine.execute(plan(Map.of(), Map.of(), Map.of("path", "/orders"))))
            .isInstanceOf(ApiHttpException.class)
            .extracting("code")
            .isEqualTo("API_RUNTIME_BLOCKED_URL");
    }

    @Test
    void execute_shouldRejectLoopbackIpBaseUrlUnlessHostIsExplicitlyAllowed() {
        ApiProperties properties = new ApiProperties();
        properties.setAllowHttp(true);
        ApiHttpEngine engine = new ApiHttpEngine(duration -> {}, properties);

        assertThatThrownBy(() ->
            engine.execute(
                planWithBaseUrl("http://127.0.0.1:1", Map.of(), Map.of("maxRetries", 0), Map.of(), Map.of("path", "/orders"))
            )
        )
            .isInstanceOf(ApiHttpException.class)
            .extracting("code")
            .isEqualTo("API_RUNTIME_BLOCKED_URL");
    }

    @Test
    void execute_shouldRejectHostnameResolvingToLoopbackUnlessHostIsExplicitlyAllowed() {
        ApiProperties properties = new ApiProperties();
        properties.setAllowHttp(true);
        ApiHttpEngine engine = new ApiHttpEngine(duration -> {}, properties);

        assertThatThrownBy(() ->
            engine.execute(
                planWithBaseUrl("http://localhost:1", Map.of(), Map.of("maxRetries", 0), Map.of(), Map.of("path", "/orders"))
            )
        )
            .isInstanceOf(ApiHttpException.class)
            .extracting("code")
            .isEqualTo("API_RUNTIME_BLOCKED_URL");
    }

    @Test
    void execute_shouldAllowSelfSignedHttpsWhenVerifyTlsIsFalse() throws Exception {
        startHttpsServer(exchange -> write(exchange, 200, "{\"data\":{\"items\":[{\"id\":1}]}}"));
        ApiHttpEngine engine = engine();

        List<ApiHttpEngine.ApiHttpResult> results = engine.execute(
            planWithBaseUrl(
                "https://127.0.0.1:" + server.getAddress().getPort(),
                Map.of(),
                Map.of("maxRetries", 0),
                Map.of("tls", Map.of("verifyTls", false)),
                Map.of("path", "/orders", "recordPath", "$.data.items")
            )
        );

        assertThat(results).hasSize(1);
        assertThat(results.get(0).records()).hasSize(1);
    }

    @Test
    void execute_shouldRejectSelfSignedHttpsByDefault() throws Exception {
        startHttpsServer(exchange -> write(exchange, 200, "{\"data\":{\"items\":[{\"id\":1}]}}"));
        ApiHttpEngine engine = engine();

        assertThatThrownBy(() ->
            engine.execute(
                planWithBaseUrl(
                    "https://127.0.0.1:" + server.getAddress().getPort(),
                    Map.of(),
                    Map.of("maxRetries", 0),
                    Map.of(),
                    Map.of("path", "/orders", "recordPath", "$.data.items")
                )
            )
        )
            .isInstanceOf(ApiHttpException.class)
            .extracting("code")
            .isEqualTo("API_RUNTIME_NETWORK");
    }

    @Test
    void execute_shouldTrustCustomCaPemFromSecrets() throws Exception {
        startHttpsServer(exchange -> write(exchange, 200, "{\"data\":{\"items\":[{\"id\":1}]}}"));
        ApiHttpEngine engine = engine();

        List<ApiHttpEngine.ApiHttpResult> results = engine.execute(
            planWithBaseUrl(
                "https://127.0.0.1:" + server.getAddress().getPort(),
                Map.of(),
                Map.of("maxRetries", 0),
                Map.of("tls", Map.of("caSecretRef", "serverCaPem"), "secrets", Map.of("serverCaPem", testServerCertificatePem())),
                Map.of("path", "/orders", "recordPath", "$.data.items")
            )
        );

        assertThat(results).hasSize(1);
        assertThat(results.get(0).records()).hasSize(1);
    }

    @Test
    void execute_shouldRejectResponseLargerThanRequestPolicy() throws Exception {
        startServer(exchange -> write(exchange, 200, "12345"));
        ApiHttpEngine engine = engine();

        assertThatThrownBy(() -> engine.execute(plan(Map.of("maxResponseBytes", 4), Map.of(), Map.of("path", "/orders"))))
            .isInstanceOf(ApiHttpException.class)
            .extracting("code")
            .isEqualTo("API_RUNTIME_RESPONSE_TOO_LARGE");
    }

    @Test
    void execute_shouldKeepRedirectsDisabledByDefault() throws Exception {
        startServer(exchange -> {
            exchange.getResponseHeaders().add("Location", "/orders");
            write(exchange, 302, "");
        });
        ApiHttpEngine engine = engine();

        assertThatThrownBy(() -> engine.execute(plan(Map.of(), Map.of(), Map.of("path", "/redirect"))))
            .isInstanceOf(ApiHttpException.class)
            .extracting("code")
            .isEqualTo("API_RUNTIME_REDIRECT");
    }

    @Test
    void execute_shouldRetry429UsingRetryAfterHeader() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        startServer(exchange -> {
            if (attempts.incrementAndGet() == 1) {
                exchange.getResponseHeaders().add("Retry-After", "0");
                write(exchange, 429, "later");
                return;
            }
            write(exchange, 200, "ok");
        });
        List<Duration> sleeps = new ArrayList<>();
        ApiHttpEngine engine = engine(sleeps::add);

        List<ApiHttpEngine.ApiHttpResult> results = engine.execute(
            plan(Map.of(), Map.of("maxRetries", 1), Map.of("path", "/orders"))
        );

        assertThat(results).hasSize(1);
        assertThat(results.get(0).statusCode()).isEqualTo(200);
        assertThat(results.get(0).attempts()).isEqualTo(2);
        assertThat(results.get(0).bodyText()).isEqualTo("ok");
        assertThat(sleeps).contains(Duration.ZERO);
    }

    @Test
    void execute_shouldRetryServerErrorsUsingConfiguredBackoff() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        startServer(exchange -> {
            if (attempts.incrementAndGet() == 1) {
                write(exchange, 500, "server-error");
                return;
            }
            write(exchange, 200, "ok");
        });
        List<Duration> sleeps = new ArrayList<>();
        ApiHttpEngine engine = engine(sleeps::add);

        List<ApiHttpEngine.ApiHttpResult> results = engine.execute(
            plan(
                Map.of(),
                Map.of("maxRetries", 1, "baseBackoffMillis", 25, "maxBackoffMillis", 25),
                Map.of("path", "/orders")
            )
        );

        assertThat(results).hasSize(1);
        assertThat(results.get(0).statusCode()).isEqualTo(200);
        assertThat(results.get(0).attempts()).isEqualTo(2);
        assertThat(sleeps).containsExactly(Duration.ofMillis(25));
    }

    @Test
    void execute_shouldClassifyReadTimeoutAsNetworkFailure() throws Exception {
        startServer(exchange -> {
            try {
                Thread.sleep(250);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            write(exchange, 200, "late");
        });
        ApiHttpEngine engine = engine();

        assertThatThrownBy(() ->
            engine.execute(
                plan(Map.of("readTimeoutMillis", 50), Map.of("maxRetries", 0), Map.of("path", "/orders"))
            )
        )
            .isInstanceOf(ApiHttpException.class)
            .extracting("code")
            .isEqualTo("API_RUNTIME_NETWORK");
    }

    @Test
    void execute_shouldClassifyConnectTimeoutAsNetworkFailure() {
        ApiHttpEngine engine = engine();

        assertThatThrownBy(() ->
            engine.execute(
                planWithBaseUrl(
                    "http://10.255.255.1:81",
                    Map.of("connectTimeoutMillis", 50, "allowedHosts", List.of("10.255.255.1")),
                    Map.of("maxRetries", 0),
                    Map.of(),
                    Map.of("path", "/orders")
                )
            )
        )
            .isInstanceOf(ApiHttpException.class)
            .extracting("code")
            .isEqualTo("API_RUNTIME_NETWORK");
    }

    @Test
    void execute_shouldApplyRateLimitAcrossRequestsForSameResource() throws Exception {
        startServer(exchange -> write(exchange, 200, "ok"));
        List<Duration> sleeps = new ArrayList<>();
        ApiHttpEngine engine = engine(sleeps::add);
        ExecutionPlan plan = plan(
            Map.of(),
            Map.of(),
            Map.of("path", "/orders", "rateLimit", Map.of("requestsPerSecond", 1, "burst", 1))
        );

        engine.execute(plan);
        engine.execute(plan);

        assertThat(sleeps).anySatisfy(duration -> assertThat(duration).isGreaterThanOrEqualTo(Duration.ofMillis(900)));
    }

    @Test
    void execute_shouldLimitConcurrentRequestsForSameResource() throws Exception {
        AtomicInteger activeRequests = new AtomicInteger();
        AtomicInteger peakRequests = new AtomicInteger();
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        startServer(exchange -> {
            int active = activeRequests.incrementAndGet();
            peakRequests.accumulateAndGet(active, Math::max);
            firstEntered.countDown();
            try {
                release.await(2, TimeUnit.SECONDS);
                write(exchange, 200, "ok");
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                write(exchange, 500, "interrupted");
            } finally {
                activeRequests.decrementAndGet();
            }
        });
        ApiHttpEngine engine = engine();
        ExecutionPlan plan = plan(
            Map.of(),
            Map.of(),
            Map.of("path", "/orders", "rateLimit", Map.of("maxConcurrency", 1))
        );

        CompletableFuture<List<ApiHttpEngine.ApiHttpResult>> first = CompletableFuture.supplyAsync(() -> engine.execute(plan));
        CompletableFuture<List<ApiHttpEngine.ApiHttpResult>> second = null;
        try {
            assertThat(firstEntered.await(1, TimeUnit.SECONDS)).isTrue();
            second = CompletableFuture.supplyAsync(() -> engine.execute(plan));
            Thread.sleep(100);
            assertThat(activeRequests).hasValue(1);
            assertThat(peakRequests).hasValue(1);
        } finally {
            release.countDown();
        }

        assertThat(first.join()).hasSize(1);
        assertThat(second).isNotNull();
        assertThat(second.join()).hasSize(1);
        assertThat(peakRequests).hasValue(1);
    }

    @Test
    void execute_shouldInjectBearerTokenFromResolvedSecrets() throws Exception {
        startServer(exchange -> write(exchange, 200, String.valueOf(exchange.getRequestHeaders().getFirst("Authorization"))));
        ApiHttpEngine engine = engine();

        List<ApiHttpEngine.ApiHttpResult> results = engine.execute(
            plan(
                Map.of(),
                Map.of(),
                Map.of(
                    "auth",
                    Map.of("provider", "bearerToken", "tokenRef", "accessToken"),
                    "secrets",
                    Map.of("accessToken", "token-123")
                ),
                Map.of("path", "/orders")
            )
        );

        assertThat(results).hasSize(1);
        assertThat(results.get(0).bodyText()).isEqualTo("Bearer token-123");
    }

    @Test
    void execute_shouldInjectApiKeyIntoHeaderOrQueryFromResolvedSecrets() throws Exception {
        startServer(exchange -> {
            String header = String.valueOf(exchange.getRequestHeaders().getFirst("X-API-Key"));
            String query = String.valueOf(exchange.getRequestURI().getRawQuery());
            write(exchange, 200, header + "|" + query);
        });
        ApiHttpEngine engine = engine();

        List<ApiHttpEngine.ApiHttpResult> headerResults = engine.execute(
            plan(
                Map.of(),
                Map.of(),
                Map.of(
                    "auth",
                    Map.of("provider", "apiKey", "name", "X-API-Key", "location", "header", "valueRef", "apiKey"),
                    "secrets",
                    Map.of("apiKey", "key-123")
                ),
                Map.of("path", "/orders")
            )
        );
        List<ApiHttpEngine.ApiHttpResult> queryResults = engine.execute(
            plan(
                Map.of(),
                Map.of(),
                Map.of(
                    "auth",
                    Map.of("provider", "apiKey", "name", "api_key", "location", "query", "valueRef", "apiKey"),
                    "secrets",
                    Map.of("apiKey", "key-456")
                ),
                Map.of("path", "/orders")
            )
        );

        assertThat(headerResults.get(0).bodyText()).isEqualTo("key-123|null");
        assertThat(queryResults.get(0).bodyText()).isEqualTo("null|api_key=key-456");
    }

    @Test
    void execute_shouldInjectBasicAuthorizationFromResolvedSecrets() throws Exception {
        startServer(exchange -> write(exchange, 200, String.valueOf(exchange.getRequestHeaders().getFirst("Authorization"))));
        ApiHttpEngine engine = engine();

        List<ApiHttpEngine.ApiHttpResult> results = engine.execute(
            plan(
                Map.of(),
                Map.of(),
                Map.of(
                    "auth",
                    Map.of("provider", "basic", "username", "api-user", "passwordRef", "password"),
                    "secrets",
                    Map.of("password", "api-pass")
                ),
                Map.of("path", "/orders")
            )
        );

        String expected = "Basic " + Base64.getEncoder().encodeToString("api-user:api-pass".getBytes(StandardCharsets.UTF_8));
        assertThat(results.get(0).bodyText()).isEqualTo(expected);
    }

    @Test
    void execute_shouldLoginOnceAndCacheJwtTokenForBusinessRequests() throws Exception {
        AtomicInteger loginAttempts = new AtomicInteger();
        startServer(exchange -> {
            if ("/login".equals(exchange.getRequestURI().getPath())) {
                loginAttempts.incrementAndGet();
                write(exchange, 200, "{\"data\":{\"token\":\"jwt-token-123\"},\"expiresIn\":3600}");
                return;
            }
            write(exchange, 200, String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
        });
        ApiHttpEngine engine = engine();
        ExecutionPlan plan = plan(
            Map.of(),
            Map.of(),
            Map.of(
                "auth",
                Map.of(
                    "provider",
                    "jwtLogin",
                    "loginUrl",
                    "/login",
                    "loginBodyTemplate",
                    "{\"username\":\"{{secretRefs.username}}\",\"password\":\"{{secretRefs.password}}\"}",
                    "tokenPath",
                    "$.data.token",
                    "expiresInPath",
                    "$.expiresIn"
                ),
                "secrets",
                Map.of("username", "api-user", "password", "api-pass")
            ),
            Map.of("path", "/orders")
        );

        List<ApiHttpEngine.ApiHttpResult> first = engine.execute(plan);
        List<ApiHttpEngine.ApiHttpResult> second = engine.execute(plan);

        assertThat(first.get(0).bodyText()).isEqualTo("Bearer jwt-token-123");
        assertThat(second.get(0).bodyText()).isEqualTo("Bearer jwt-token-123");
        assertThat(loginAttempts).hasValue(1);
    }

    @Test
    void execute_shouldUseJwtExpClaimWhenExpiresInPathIsMissing() throws Exception {
        AtomicInteger loginAttempts = new AtomicInteger();
        startServer(exchange -> {
            if ("/login".equals(exchange.getRequestURI().getPath())) {
                int attempt = loginAttempts.incrementAndGet();
                String token = unsignedJwtWithExp(Instant.now().plusSeconds(30), "token-" + attempt);
                write(exchange, 200, "{\"data\":{\"token\":\"" + token + "\"}}");
                return;
            }
            write(exchange, 200, String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
        });
        ApiHttpEngine engine = engine();
        ExecutionPlan plan = plan(
            Map.of(),
            Map.of(),
            Map.of(
                "auth",
                Map.of(
                    "provider",
                    "jwtLogin",
                    "loginUrl",
                    "/login",
                    "loginBodyTemplate",
                    "{\"username\":\"{{secretRefs.username}}\",\"password\":\"{{secretRefs.password}}\"}",
                    "tokenPath",
                    "$.data.token"
                ),
                "secrets",
                Map.of("username", "api-user", "password", "api-pass")
            ),
            Map.of("path", "/orders")
        );

        List<ApiHttpEngine.ApiHttpResult> first = engine.execute(plan);
        List<ApiHttpEngine.ApiHttpResult> second = engine.execute(plan);

        assertThat(first.get(0).bodyText()).startsWith("Bearer ");
        assertThat(second.get(0).bodyText()).startsWith("Bearer ");
        assertThat(second.get(0).bodyText()).isNotEqualTo(first.get(0).bodyText());
        assertThat(loginAttempts).hasValue(2);
    }

    @Test
    void execute_shouldKeepJwtDefaultTtlWhenNoExpiresInfoExists() throws Exception {
        AtomicInteger loginAttempts = new AtomicInteger();
        startServer(exchange -> {
            if ("/login".equals(exchange.getRequestURI().getPath())) {
                loginAttempts.incrementAndGet();
                write(exchange, 200, "{\"data\":{\"token\":\"opaque-jwt-token\"}}");
                return;
            }
            write(exchange, 200, String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
        });
        ApiHttpEngine engine = engine();
        ExecutionPlan plan = plan(
            Map.of(),
            Map.of(),
            Map.of(
                "auth",
                Map.of(
                    "provider",
                    "jwtLogin",
                    "loginUrl",
                    "/login",
                    "loginBodyTemplate",
                    "{\"username\":\"{{secretRefs.username}}\",\"password\":\"{{secretRefs.password}}\"}",
                    "tokenPath",
                    "$.data.token"
                ),
                "secrets",
                Map.of("username", "api-user", "password", "api-pass")
            ),
            Map.of("path", "/orders")
        );

        List<ApiHttpEngine.ApiHttpResult> first = engine.execute(plan);
        List<ApiHttpEngine.ApiHttpResult> second = engine.execute(plan);

        assertThat(first.get(0).bodyText()).isEqualTo("Bearer opaque-jwt-token");
        assertThat(second.get(0).bodyText()).isEqualTo("Bearer opaque-jwt-token");
        assertThat(loginAttempts).hasValue(1);
    }

    @Test
    void execute_shouldRefreshJwtTokenAndRetryOnceWhenBusinessRequestReturns401() throws Exception {
        AtomicInteger loginAttempts = new AtomicInteger();
        startServer(exchange -> {
            if ("/login".equals(exchange.getRequestURI().getPath())) {
                int attempt = loginAttempts.incrementAndGet();
                String token = attempt == 1 ? "expired-token" : "fresh-token";
                write(exchange, 200, "{\"data\":{\"token\":\"" + token + "\"},\"expiresIn\":3600}");
                return;
            }
            String authorization = String.valueOf(exchange.getRequestHeaders().getFirst("Authorization"));
            if ("Bearer expired-token".equals(authorization)) {
                write(exchange, 401, "expired");
                return;
            }
            write(exchange, 200, authorization);
        });
        ApiHttpEngine engine = engine();

        List<ApiHttpEngine.ApiHttpResult> results = engine.execute(jwtPlan());

        assertThat(results.get(0).bodyText()).isEqualTo("Bearer fresh-token");
        assertThat(results.get(0).attempts()).isEqualTo(2);
        assertThat(loginAttempts).hasValue(2);
    }

    @Test
    void execute_shouldUseOAuth2ClientCredentialsTokenEndpointAndCacheAccessToken() throws Exception {
        AtomicInteger tokenAttempts = new AtomicInteger();
        startServer(exchange -> {
            if ("/oauth/token".equals(exchange.getRequestURI().getPath())) {
                tokenAttempts.incrementAndGet();
                write(exchange, 200, "{\"access_token\":\"oauth-token-123\",\"expires_in\":3600}");
                return;
            }
            write(exchange, 200, String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
        });
        ApiHttpEngine engine = engine();
        ExecutionPlan plan = plan(
            Map.of(),
            Map.of(),
            Map.of(
                "auth",
                Map.of(
                    "provider",
                    "oauth2ClientCredentials",
                    "tokenUrl",
                    "/oauth/token",
                    "clientId",
                    "client-1",
                    "clientSecretRef",
                    "clientSecret",
                    "scope",
                    "orders.read"
                ),
                "secrets",
                Map.of("clientSecret", "secret-1")
            ),
            Map.of("path", "/orders")
        );

        List<ApiHttpEngine.ApiHttpResult> first = engine.execute(plan);
        List<ApiHttpEngine.ApiHttpResult> second = engine.execute(plan);

        assertThat(first.get(0).bodyText()).isEqualTo("Bearer oauth-token-123");
        assertThat(second.get(0).bodyText()).isEqualTo("Bearer oauth-token-123");
        assertThat(tokenAttempts).hasValue(1);
    }

    @Test
    void execute_shouldRejectUnsupportedAuthProvider() throws Exception {
        startServer(exchange -> write(exchange, 200, "should-not-call"));
        ApiHttpEngine engine = engine();

        assertThatThrownBy(() ->
            engine.execute(
                plan(
                    Map.of(),
                    Map.of(),
                    Map.of("auth", Map.of("provider", "customSignature"), "secrets", Map.of("secret", "super-secret")),
                    Map.of("path", "/orders")
                )
            )
        )
            .isInstanceOf(ApiHttpException.class)
            .satisfies(error -> assertThat(((ApiHttpException) error).getCode()).isEqualTo("API_RUNTIME_AUTH_UNSUPPORTED"))
            .hasMessageContaining("customSignature")
            .satisfies(error -> assertThat(error.getMessage()).doesNotContain("super-secret"));
    }

    @Test
    void execute_shouldNotExposeJwtSecretsWhenLoginFails() throws Exception {
        startServer(exchange -> {
            if ("/login".equals(exchange.getRequestURI().getPath())) {
                write(exchange, 401, "{\"error\":\"api-pass super-secret-token\"}");
                return;
            }
            write(exchange, 200, "should-not-call");
        });
        ApiHttpEngine engine = engine();

        assertThatThrownBy(() ->
            engine.execute(
                plan(
                    Map.of(),
                    Map.of(),
                    Map.of(
                        "auth",
                        Map.of(
                            "provider",
                            "jwtLogin",
                            "loginUrl",
                            "/login",
                            "loginBodyTemplate",
                            "{\"username\":\"{{secretRefs.username}}\",\"password\":\"{{secretRefs.password}}\"}",
                            "tokenPath",
                            "$.data.token"
                        ),
                        "secrets",
                        Map.of("username", "api-user", "password", "api-pass", "token", "super-secret-token")
                    ),
                    Map.of("path", "/orders")
                )
            )
        )
            .isInstanceOf(ApiHttpException.class)
            .satisfies(error -> assertThat(((ApiHttpException) error).getCode()).isEqualTo("API_RUNTIME_AUTH"))
            .hasMessageContaining("JWT 登录失败: HTTP 401")
            .satisfies(error -> assertThat(error.getMessage()).doesNotContain("api-pass", "super-secret-token"));
    }

    @Test
    void execute_shouldFollowTokenPaginationUntilNoNextToken() throws Exception {
        startServer(exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            if (query != null && query.contains("pageToken=next-1")) {
                write(exchange, 200, "{\"data\":{\"items\":[{\"id\":2}]}}");
                return;
            }
            write(exchange, 200, "{\"data\":{\"items\":[{\"id\":1}],\"next\":\"next-1\"}}");
        });
        ApiHttpEngine engine = engine();

        List<ApiHttpEngine.ApiHttpResult> results = engine.execute(
            plan(
                Map.of(),
                Map.of(),
                Map.of(
                    "auth",
                    Map.of("provider", "apiKey", "name", "api_key", "location", "query", "valueRef", "apiKey"),
                    "secrets",
                    Map.of("apiKey", "key-123")
                ),
                Map.of(
                    "path",
                    "/orders",
                    "recordPath",
                    "$.data.items",
                    "pagination",
                    Map.of("type", "token", "tokenParam", "pageToken", "nextTokenPath", "$.data.next")
                )
            )
        );

        assertThat(results).hasSize(2);
        assertThat(results.get(0).pageNo()).isEqualTo(1);
        assertThat(results.get(1).pageNo()).isEqualTo(2);
        assertThat(results.get(1).uri().getRawQuery()).contains("pageToken=next-1", "api_key=key-123");
        assertThat(results).allSatisfy(result -> assertThat(result.records()).hasSize(1));
    }

    @Test
    void execute_shouldFollowLinkHeaderEvenWhenFirstPageHasFewerRecordsThanPageSize() throws Exception {
        startServer(exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            if ("page=2".equals(query)) {
                write(exchange, 200, "{\"data\":{\"items\":[{\"id\":2}]}}");
                return;
            }
            String next = "http://127.0.0.1:" + server.getAddress().getPort() + "/orders?page=2";
            exchange.getResponseHeaders().add("Link", "<" + next + ">; rel=\"next\"");
            write(exchange, 200, "{\"data\":{\"items\":[{\"id\":1}]}}");
        });
        ApiHttpEngine engine = engine();

        List<ApiHttpEngine.ApiHttpResult> results = engine.execute(
            plan(
                Map.of(),
                Map.of(),
                Map.of(
                    "path",
                    "/orders",
                    "recordPath",
                    "$.data.items",
                    "pagination",
                    Map.of("type", "page", "pageParam", "page", "pageSize", 100, "maxPages", 5)
                )
            )
        );

        assertThat(results).hasSize(2);
        assertThat(results.get(1).uri().getRawQuery()).isEqualTo("page=2");
    }

    @Test
    void execute_shouldFollowNextUrlPathFromResponseBody() throws Exception {
        startServer(exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            if (query != null && query.contains("page=2")) {
                write(exchange, 200, "{\"data\":{\"items\":[{\"id\":2}]}}");
                return;
            }
            write(exchange, 200, "{\"data\":{\"items\":[{\"id\":1}],\"nextUrl\":\"/orders?page=2\"}}");
        });
        ApiHttpEngine engine = engine();

        List<ApiHttpEngine.ApiHttpResult> results = engine.execute(
            plan(
                Map.of(),
                Map.of(),
                Map.of(
                    "auth",
                    Map.of("provider", "apiKey", "name", "api_key", "location", "query", "valueRef", "apiKey"),
                    "secrets",
                    Map.of("apiKey", "key-123")
                ),
                Map.of(
                    "path",
                    "/orders",
                    "recordPath",
                    "$.data.items",
                    "pagination",
                    Map.of("type", "nextUrl", "nextUrlPath", "$.data.nextUrl")
                )
            )
        );

        assertThat(results).hasSize(2);
        assertThat(results.get(1).uri().getRawQuery()).contains("page=2", "api_key=key-123");
    }

    @Test
    void execute_shouldRejectCrossOriginNextUrlFromResponseBodyWhenHostIsNotAllowed() throws Exception {
        startServer(exchange ->
            write(exchange, 200, "{\"data\":{\"items\":[{\"id\":1}],\"nextUrl\":\"http://example.test/orders?page=2\"}}")
        );
        ApiHttpEngine engine = engine();

        assertThatThrownBy(() ->
            engine.execute(
                plan(
                    Map.of(),
                    Map.of(),
                    Map.of(
                        "path",
                        "/orders",
                        "recordPath",
                        "$.data.items",
                        "pagination",
                        Map.of("type", "nextUrl", "nextUrlPath", "$.data.nextUrl")
                    )
                )
            )
        )
            .isInstanceOf(ApiHttpException.class)
            .extracting("code")
            .isEqualTo("API_RUNTIME_BLOCKED_URL");
    }

    @Test
    void execute_shouldRejectCrossOriginLinkHeaderWhenHostIsNotAllowed() throws Exception {
        startServer(exchange -> {
            exchange.getResponseHeaders().add("Link", "<http://example.test/orders?page=2>; rel=\"next\"");
            write(exchange, 200, "{\"data\":{\"items\":[{\"id\":1}]}}");
        });
        ApiHttpEngine engine = engine();

        assertThatThrownBy(() ->
            engine.execute(
                plan(
                    Map.of(),
                    Map.of(),
                    Map.of(
                        "path",
                        "/orders",
                        "recordPath",
                        "$.data.items",
                        "pagination",
                        Map.of("type", "page", "pageParam", "page", "pageSize", 100, "maxPages", 5)
                    )
                )
            )
        )
            .isInstanceOf(ApiHttpException.class)
            .extracting("code")
            .isEqualTo("API_RUNTIME_BLOCKED_URL");
    }

    @Test
    void execute_shouldRejectCrossOriginJwtLoginUrlWhenHostIsNotAllowed() throws Exception {
        startServer(exchange -> write(exchange, 200, "should-not-call"));
        ApiHttpEngine engine = engine();

        assertThatThrownBy(() ->
            engine.execute(
                plan(
                    Map.of(),
                    Map.of(),
                    Map.of(
                        "auth",
                        Map.of(
                            "provider",
                            "jwtLogin",
                            "loginUrl",
                            "http://example.test/login",
                            "loginBodyTemplate",
                            "{\"username\":\"{{secretRefs.username}}\",\"password\":\"{{secretRefs.password}}\"}",
                            "tokenPath",
                            "$.data.token"
                        ),
                        "secrets",
                        Map.of("username", "api-user", "password", "api-pass")
                    ),
                    Map.of("path", "/orders")
                )
            )
        )
            .isInstanceOf(ApiHttpException.class)
            .extracting("code")
            .isEqualTo("API_RUNTIME_BLOCKED_URL");
    }

    @Test
    void execute_shouldContinuePageModeUntilEmptyPageInsteadOfStoppingOnShortPage() throws Exception {
        startServer(exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            if (query != null && query.contains("page=2")) {
                write(exchange, 200, "{\"data\":{\"items\":[]}}");
                return;
            }
            write(exchange, 200, "{\"data\":{\"items\":[{\"id\":1}]}}");
        });
        ApiHttpEngine engine = engine();

        List<ApiHttpEngine.ApiHttpResult> results = engine.execute(
            plan(
                Map.of(),
                Map.of(),
                Map.of(
                    "path",
                    "/orders",
                    "recordPath",
                    "$.data.items",
                    "pagination",
                    Map.of("type", "page", "pageParam", "page", "pageSize", 100, "maxPages", 5)
                )
            )
        );

        assertThat(results).hasSize(2);
        assertThat(results.get(0).records()).hasSize(1);
        assertThat(results.get(1).records()).isEmpty();
        assertThat(results.get(1).uri().getRawQuery()).contains("page=2", "size=100");
    }

    @Test
    void execute_shouldAdvanceOffsetModeByPageSizeUntilEmptyPage() throws Exception {
        startServer(exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            if (query != null && query.contains("offset=100")) {
                write(exchange, 200, "{\"data\":{\"items\":[]}}");
                return;
            }
            write(exchange, 200, "{\"data\":{\"items\":[{\"id\":1}]}}");
        });
        ApiHttpEngine engine = engine();

        List<ApiHttpEngine.ApiHttpResult> results = engine.execute(
            plan(
                Map.of(),
                Map.of(),
                Map.of(
                    "path",
                    "/orders",
                    "recordPath",
                    "$.data.items",
                    "pagination",
                    Map.of("type", "offset", "offsetParam", "offset", "pageSize", 100, "maxPages", 5)
                )
            )
        );

        assertThat(results).hasSize(2);
        assertThat(results.get(1).uri().getRawQuery()).contains("offset=100", "size=100");
    }

    @Test
    void execute_shouldInjectCursorInitialValueWithLookbackIntoQuery() throws Exception {
        startServer(exchange -> write(exchange, 200, String.valueOf(exchange.getRequestURI().getRawQuery())));
        ApiHttpEngine engine = engine();

        List<ApiHttpEngine.ApiHttpResult> results = engine.execute(
            plan(
                Map.of(),
                Map.of(),
                Map.of(
                    "path",
                    "/orders",
                    "cursor",
                    Map.of(
                        "type",
                        "datetime",
                        "field",
                        "updatedAt",
                        "injectInto",
                        "query",
                        "parameterName",
                        "updatedAfter",
                        "initialValue",
                        "2026-01-01T00:00:00Z",
                        "lookbackSeconds",
                        300
                    )
                )
            )
        );

        assertThat(results.get(0).bodyText()).contains("updatedAfter=2025-12-31T23%3A55%3A00Z");
    }

    private void startServer(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", handler);
        serverExecutor = Executors.newCachedThreadPool();
        server.setExecutor(serverExecutor);
        server.start();
    }

    private void startHttpsServer(com.sun.net.httpserver.HttpHandler handler) throws Exception {
        HttpsServer httpsServer = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpsServer.setHttpsConfigurator(new HttpsConfigurator(testServerSslContext()));
        server = httpsServer;
        server.createContext("/", handler);
        serverExecutor = Executors.newCachedThreadPool();
        server.setExecutor(serverExecutor);
        server.start();
    }

    private SSLContext testServerSslContext() throws Exception {
        KeyStore keyStore = testKeyStore();
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, TEST_KEYSTORE_PASSWORD);
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
        return sslContext;
    }

    private String testServerCertificatePem() throws Exception {
        Certificate certificate = testKeyStore().getCertificate("api-test");
        String encoded = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(certificate.getEncoded());
        return "-----BEGIN CERTIFICATE-----\n" + encoded + "\n-----END CERTIFICATE-----";
    }

    private KeyStore testKeyStore() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(new ByteArrayInputStream(Base64.getDecoder().decode(TEST_KEYSTORE_BASE64)), TEST_KEYSTORE_PASSWORD);
        return keyStore;
    }

    private ApiHttpEngine engine() {
        return engine(duration -> {});
    }

    private ApiHttpEngine engine(ApiHttpEngine.Sleeper sleeper) {
        return new ApiHttpEngine(sleeper, testProperties());
    }

    private ApiProperties testProperties() {
        ApiProperties properties = new ApiProperties();
        properties.setAllowHttp(true);
        properties.setAllowedHosts(List.of("127.0.0.1"));
        return properties;
    }

    private ExecutionPlan plan(
        Map<String, Object> requestPolicy,
        Map<String, Object> retryPolicy,
        Map<String, Object> resourceOverrides
    ) {
        return plan(requestPolicy, retryPolicy, Map.of(), resourceOverrides);
    }

    private ExecutionPlan plan(
        Map<String, Object> requestPolicy,
        Map<String, Object> retryPolicy,
        Map<String, Object> sourceOverrides,
        Map<String, Object> resourceOverrides
    ) {
        return planWithBaseUrl(
            "http://127.0.0.1:" + server.getAddress().getPort(),
            requestPolicy,
            retryPolicy,
            sourceOverrides,
            resourceOverrides
        );
    }

    private ExecutionPlan planWithBaseUrl(
        String baseUrl,
        Map<String, Object> requestPolicy,
        Map<String, Object> retryPolicy,
        Map<String, Object> sourceOverrides,
        Map<String, Object> resourceOverrides
    ) {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("resourceId", "orders");
        resource.put("path", "/orders");
        resource.putAll(resourceOverrides);

        Map<String, Object> sourceConfig = new LinkedHashMap<>();
        sourceConfig.put("baseUrl", baseUrl);
        sourceConfig.putAll(sourceOverrides);
        if (!requestPolicy.isEmpty()) {
            sourceConfig.put("requestPolicy", requestPolicy);
        }
        if (!retryPolicy.isEmpty()) {
            sourceConfig.put("retryPolicy", retryPolicy);
        }
        sourceConfig.put("resources", List.of(resource));

        return new ExecutionPlan(
            "api-http",
            "api",
            "1.2.0",
            null,
            Map.of("sourceConfig", sourceConfig),
            List.of(),
            new ExecutionPlan.CheckpointPolicy("none", null, "task_success"),
            Map.of("engine", "api-http")
        );
    }

    private ExecutionPlan jwtPlan() {
        return plan(
            Map.of(),
            Map.of(),
            Map.of(
                "auth",
                Map.of(
                    "provider",
                    "jwtLogin",
                    "loginUrl",
                    "/login",
                    "loginBodyTemplate",
                    "{\"username\":\"{{secretRefs.username}}\",\"password\":\"{{secretRefs.password}}\"}",
                    "tokenPath",
                    "$.data.token",
                    "expiresInPath",
                    "$.expiresIn"
                ),
                "secrets",
                Map.of("username", "api-user", "password", "api-pass")
            ),
            Map.of("path", "/orders")
        );
    }

    private String unsignedJwtWithExp(Instant expiresAt, String marker) {
        String header = base64Url("{\"alg\":\"none\",\"typ\":\"JWT\"}");
        String payload = base64Url("{\"exp\":" + expiresAt.getEpochSecond() + ",\"jti\":\"" + marker + "\"}");
        return header + "." + payload + ".";
    }

    private String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private void write(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
