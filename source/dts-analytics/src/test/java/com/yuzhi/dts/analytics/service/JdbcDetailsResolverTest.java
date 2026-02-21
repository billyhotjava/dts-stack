package com.yuzhi.dts.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;

class JdbcDetailsResolverTest {

    private static final UUID PLATFORM_ID = UUID.fromString("f07b674b-c6ee-4710-b13e-0b2b11272c2d");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void resolve_platformBiadminDockerIp_rewritesToConfiguredPgHost() throws Exception {
        PlatformInfraClient platformInfraClient = fakeClient("jdbc:postgresql://172.19.0.12:5432/biadmin");
        JdbcDetailsResolver resolver = new JdbcDetailsResolver(platformInfraClient, "dts-pg");

        JsonNode details = objectMapper.readTree("""
                {
                  "platformDataSourceId": "f07b674b-c6ee-4710-b13e-0b2b11272c2d"
                }
                """);

        JdbcDetailsResolver.JdbcDetails jdbc = resolver.resolve("jdbc", details);

        assertThat(jdbc.jdbcUrl()).isEqualTo("jdbc:postgresql://dts-pg:5432/biadmin");
        assertThat(jdbc.username()).isEqualTo("biadmin");
        assertThat(jdbc.password()).isEqualTo("secret");
    }

    @Test
    void resolve_platformNonBiadminDockerIp_keepsOriginalJdbcUrl() throws Exception {
        PlatformInfraClient platformInfraClient = fakeClient("jdbc:postgresql://172.19.0.12:5432/customdb");
        JdbcDetailsResolver resolver = new JdbcDetailsResolver(platformInfraClient, "dts-pg");

        JsonNode details = objectMapper.readTree("""
                {
                  "platformDataSourceId": "f07b674b-c6ee-4710-b13e-0b2b11272c2d"
                }
                """);

        JdbcDetailsResolver.JdbcDetails jdbc = resolver.resolve("jdbc", details);

        assertThat(jdbc.jdbcUrl()).isEqualTo("jdbc:postgresql://172.19.0.12:5432/customdb");
    }

    @Test
    void resolve_platformBiadminNonDockerIp_keepsOriginalJdbcUrl() throws Exception {
        PlatformInfraClient platformInfraClient = fakeClient("jdbc:postgresql://10.1.2.3:5432/biadmin");
        JdbcDetailsResolver resolver = new JdbcDetailsResolver(platformInfraClient, "dts-pg");

        JsonNode details = objectMapper.readTree("""
                {
                  "platformDataSourceId": "f07b674b-c6ee-4710-b13e-0b2b11272c2d"
                }
                """);

        JdbcDetailsResolver.JdbcDetails jdbc = resolver.resolve("jdbc", details);

        assertThat(jdbc.jdbcUrl()).isEqualTo("jdbc:postgresql://10.1.2.3:5432/biadmin");
    }

    private PlatformInfraClient.DataSourceDetail dataSourceDetail(String jdbcUrl) {
        return new PlatformInfraClient.DataSourceDetail(
                PLATFORM_ID.toString(),
                "pg-lake",
                "JDBC",
                jdbcUrl,
                "biadmin",
                "desc",
                null,
                Map.of(),
                Map.of("password", "secret"),
                "ACTIVE",
                null);
    }

    private PlatformInfraClient fakeClient(String jdbcUrl) {
        return new PlatformInfraClient(new RestTemplateBuilder(), new ObjectMapper(), "", "", "", 10) {
            @Override
            public DataSourceDetail fetchDataSourceDetail(UUID id) {
                assertThat(id).isEqualTo(PLATFORM_ID);
                return dataSourceDetail(jdbcUrl);
            }
        };
    }
}
