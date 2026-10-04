package com.yuzhi.dts.copilot.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PlatformIndicatorClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void returnsDegradedCatalogWhenBaseUrlIsNotConfigured() {
        PlatformIndicatorClient client = new PlatformIndicatorClient(
                new PlatformIndicatorProperties("", "", "", "", "", "", "", 2, 0),
                MAPPER);

        PlatformIndicatorClient.CatalogResponse response = client.listIndicators();

        assertThat(response.degraded()).isTrue();
        assertThat(response.items()).isEmpty();
        assertThat(response.degradedReason()).contains("未配置");
    }

    @Test
    void listsPublishedIndicatorsAndSendsBearerToken() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/governance/indicators", exchange -> {
            assertThat(exchange.getRequestURI().getRawQuery()).contains("status=PUBLISHED");
            assertThat(exchange.getRequestHeaders().getFirst("Authorization"))
                    .isEqualTo("Bearer service-token");
            byte[] body = """
                    {"data":{"items":[
                      {
                        "id":"cash-in",
                        "code":"cash_in",
                        "name":"回款金额",
                        "category":"财务",
                        "definition":"按月统计已确认回款。",
                        "expressionSql":"sum(received_amount)",
                        "status":"已发布",
                        "version":"v2",
                        "dimensionFields":["project","customer"],
                        "timeGrain":"month",
                        "owner":"finance",
                        "dataLevel":"L2"
                      }
                    ]}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });
        server.start();

        try {
            PlatformIndicatorClient client = new PlatformIndicatorClient(
                    new PlatformIndicatorProperties(
                            "http://127.0.0.1:" + server.getAddress().getPort(),
                            "",
                            "",
                            "",
                            "service-token",
                            "",
                            "",
                            2,
                            0),
                    MAPPER);

            PlatformIndicatorClient.CatalogResponse response = client.listIndicators();

            assertThat(response.degraded()).isFalse();
            assertThat(response.items()).hasSize(1);
            assertThat(response.items().getFirst()).satisfies(item -> {
                assertThat(item.id()).isEqualTo("cash-in");
                assertThat(item.name()).isEqualTo("回款金额");
                assertThat(item.definition()).isEqualTo("按月统计已确认回款。");
                assertThat(item.version()).isEqualTo("v2");
                assertThat(item.dimensionFields()).containsExactly("project", "customer");
            });
        } finally {
            server.stop(0);
        }
    }

    @Test
    void listsPublishedIndicatorsWithPlatformServiceHeaders() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/governance/indicators", exchange -> {
            assertThat(exchange.getRequestURI().getRawQuery()).contains("status=PUBLISHED");
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isNull();
            assertThat(exchange.getRequestHeaders().getFirst("X-DTS-Service"))
                    .isEqualTo("dts-copilot");
            assertThat(exchange.getRequestHeaders().getFirst("X-DTS-Service-Token"))
                    .isEqualTo("service-secret");
            byte[] body = """
                    {"data":{"items":[
                      {"id":"cash-in","code":"cash_in","name":"回款金额","status":"已发布"}
                    ]}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });
        server.start();

        try {
            PlatformIndicatorClient client = new PlatformIndicatorClient(
                    new PlatformIndicatorProperties(
                            "http://127.0.0.1:" + server.getAddress().getPort(),
                            "",
                            "",
                            "",
                            "ignored-bearer",
                            "dts-copilot",
                            "service-secret",
                            2,
                            0),
                    MAPPER);

            PlatformIndicatorClient.CatalogResponse response = client.listIndicators();

            assertThat(response.degraded()).isFalse();
            assertThat(response.items()).extracting(PlatformIndicatorClient.IndicatorItem::name)
                    .containsExactly("回款金额");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void returnsDegradedValueWhenPlatformValueEndpointFails() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/governance/indicators/cash-in/detail", exchange -> {
            byte[] body = "{\"message\":\"upstream down\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(503, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });
        server.start();

        try {
            PlatformIndicatorClient client = new PlatformIndicatorClient(
                    new PlatformIndicatorProperties(
                            "http://127.0.0.1:" + server.getAddress().getPort(),
                            "",
                            "",
                            "",
                            "",
                            "",
                            "",
                            2,
                            0),
                    MAPPER);

            PlatformIndicatorClient.ValueResponse response = client.getDetail("cash-in", 30);

            assertThat(response.indicatorId()).isEqualTo("cash-in");
            assertThat(response.mode()).isEqualTo("detail");
            assertThat(response.cols()).isEmpty();
            assertThat(response.rows()).isEmpty();
            assertThat(response.degraded()).isTrue();
            assertThat(response.degradedReason()).contains("暂不可达");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void cachesValueResponsesWithinConfiguredTtl() throws Exception {
        AtomicInteger detailCalls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/governance/indicators/cash-in/detail", exchange -> {
            int call = detailCalls.incrementAndGet();
            byte[] body = ("""
                    {"data":{
                      "indicatorId":"cash-in",
                      "mode":"detail",
                      "cols":["month","value"],
                      "rows":[["2026-05",%d]],
                      "timeGrain":"month",
                      "dimensionFields":["project","customer"]
                    }}
                    """.formatted(call * 100)).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });
        server.start();

        try {
            PlatformIndicatorClient client = new PlatformIndicatorClient(
                    new PlatformIndicatorProperties(
                            "http://127.0.0.1:" + server.getAddress().getPort(),
                            "",
                            "",
                            "",
                            "",
                            "",
                            "",
                            2,
                            60),
                    MAPPER);

            PlatformIndicatorClient.ValueResponse first = client.getDetail("cash-in", 30);
            PlatformIndicatorClient.ValueResponse second = client.getDetail("cash-in", 30);

            assertThat(detailCalls).hasValue(1);
            assertThat(first.rows()).isEqualTo(second.rows());
            assertThat(second.dimensionFields()).containsExactly("project", "customer");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void infersColumnsForObjectRowsReturnedByDrilldownEndpoint() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/governance/indicators/cash-in/drilldown", exchange -> {
            byte[] body = """
                    {"data":{
                      "rows":[
                        {"dimension":"项目A","metric_value":1200,"row_count":3},
                        {"dimension":"项目B","metric_value":900,"row_count":2}
                      ],
                      "dimensionFields":"project"
                    }}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });
        server.start();

        try {
            PlatformIndicatorClient client = new PlatformIndicatorClient(
                    new PlatformIndicatorProperties(
                            "http://127.0.0.1:" + server.getAddress().getPort(),
                            "",
                            "",
                            "",
                            "",
                            "",
                            "",
                            2,
                            0),
                    MAPPER);

            PlatformIndicatorClient.ValueResponse response = client.drilldown("cash-in", "project", null);

            assertThat(response.mode()).isEqualTo("drilldown");
            assertThat(response.cols())
                    .extracting(PlatformIndicatorClient.DatasetColumn::name)
                    .containsExactly("dimension", "metric_value", "row_count");
            assertThat(response.rows()).containsExactly(
                    java.util.List.of("项目A", 1200, 3),
                    java.util.List.of("项目B", 900, 2));
            assertThat(response.dimensionFields()).containsExactly("project");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void mapsPlatformDashboardIndicatorsToRows() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/governance/indicators/dashboard", exchange -> {
            byte[] body = """
                    {"data":{
                      "indicators":[
                        {
                          "id":"cash-in",
                          "code":"cash_in",
                          "name":"回款金额",
                          "currentValue":300,
                          "changeRate":0.1538,
                          "alertLevel":"GREEN",
                          "lastRunAt":"2026-05-30T20:06:58Z"
                        }
                      ],
                      "total":1
                    }}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });
        server.start();

        try {
            PlatformIndicatorClient client = new PlatformIndicatorClient(
                    new PlatformIndicatorProperties(
                            "http://127.0.0.1:" + server.getAddress().getPort(),
                            "",
                            "",
                            "",
                            "",
                            "",
                            "",
                            2,
                            0),
                    MAPPER);

            PlatformIndicatorClient.ValueResponse response = client.getDashboard(30);

            assertThat(response.mode()).isEqualTo("dashboard");
            assertThat(response.cols())
                    .extracting(PlatformIndicatorClient.DatasetColumn::name)
                    .contains("id", "code", "name", "currentValue", "alertLevel");
            assertThat(response.rows()).hasSize(1);
            assertThat(response.rows().getFirst()).contains("cash-in", "cash_in", "回款金额", 300, "GREEN");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void mapsPlatformDetailTrendToRowsAndNestedIndicatorMeta() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/governance/indicators/cash-in/detail", exchange -> {
            byte[] body = """
                    {"data":{
                      "indicator":{
                        "id":"cash-in",
                        "code":"cash_in",
                        "timeGrain":"MONTH",
                        "dimensionFields":"[\\"project\\",\\"customer\\"]"
                      },
                      "trend":[
                        {"date":"2026-05-29T20:06:58Z","value":260,"alertLevel":"GREEN"},
                        {"date":"2026-05-30T20:06:58Z","value":300,"alertLevel":"GREEN"}
                      ],
                      "history":[]
                    }}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });
        server.start();

        try {
            PlatformIndicatorClient client = new PlatformIndicatorClient(
                    new PlatformIndicatorProperties(
                            "http://127.0.0.1:" + server.getAddress().getPort(),
                            "",
                            "",
                            "",
                            "",
                            "",
                            "",
                            2,
                            0),
                    MAPPER);

            PlatformIndicatorClient.ValueResponse response = client.getDetail("cash-in", 30);

            assertThat(response.indicatorId()).isEqualTo("cash-in");
            assertThat(response.mode()).isEqualTo("detail");
            assertThat(response.timeGrain()).isEqualTo("MONTH");
            assertThat(response.dimensionFields()).containsExactly("project", "customer");
            assertThat(response.cols())
                    .extracting(PlatformIndicatorClient.DatasetColumn::name)
                    .containsExactly("date", "value", "alertLevel");
            assertThat(response.rows()).containsExactly(
                    java.util.List.of("2026-05-29T20:06:58Z", 260, "GREEN"),
                    java.util.List.of("2026-05-30T20:06:58Z", 300, "GREEN"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void mapsPlatformDrilldownDataArrayToRows() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/governance/indicators/cash-in/drilldown", exchange -> {
            byte[] body = """
                    {"data":[
                      {"dimension":"north","metric_value":200,"row_count":2},
                      {"dimension":"south","metric_value":60,"row_count":1}
                    ]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });
        server.start();

        try {
            PlatformIndicatorClient client = new PlatformIndicatorClient(
                    new PlatformIndicatorProperties(
                            "http://127.0.0.1:" + server.getAddress().getPort(),
                            "",
                            "",
                            "",
                            "",
                            "",
                            "",
                            2,
                            0),
                    MAPPER);

            PlatformIndicatorClient.ValueResponse response = client.drilldown("cash-in", "dept", null);

            assertThat(response.mode()).isEqualTo("drilldown");
            assertThat(response.cols())
                    .extracting(PlatformIndicatorClient.DatasetColumn::name)
                    .containsExactly("dimension", "metric_value", "row_count");
            assertThat(response.rows()).containsExactly(
                    java.util.List.of("north", 200, 2),
                    java.util.List.of("south", 60, 1));
        } finally {
            server.stop(0);
        }
    }
}
