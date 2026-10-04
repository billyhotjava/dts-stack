package com.yuzhi.dts.ingestion.service.etl;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AddaxJdbcConfigNormalizerTest {

    private AddaxJdbcConfigNormalizer normalizer;

    @BeforeEach
    void setup() {
        normalizer = new AddaxJdbcConfigNormalizer(new ObjectMapper());
    }

    @Test
    void shouldNormalizeWriterDriverAndConnection() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("connection", Map.of("jdbcUrl", "[\"jdbc:postgresql://10.0.0.2:5432/biadmin\"]"));
        config.put("table", List.of("ods_demo"));

        Map<String, Object> result = normalizer.ensureDriver("rdbmswriter", config);
        normalizer.ensureWriterConnection("rdbmswriter", result);

        assertThat(result.get("driver")).isEqualTo("org.postgresql.Driver");
        assertThat(result.get("jdbcUrl")).isNull();
        assertThat(result.get("connection")).isInstanceOf(List.class);
        Map<?, ?> connection = (Map<?, ?>) ((List<?>) result.get("connection")).get(0);
        assertThat(connection.get("jdbcUrl")).isEqualTo("jdbc:postgresql://10.0.0.2:5432/biadmin?sslmode=disable");
        assertThat(connection.get("table")).isEqualTo(List.of("ods_demo"));
    }

    @Test
    void shouldFillReaderJdbcUrlFromHostParts() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("host", "10.0.0.1");
        config.put("port", "5236");
        config.put("database", "DMHR");

        Map<String, Object> result = normalizer.ensureDriver("dmreader", config);

        assertThat(result.get("driver")).isEqualTo("dm.jdbc.driver.DmDriver");
        assertThat(result.get("jdbcUrl")).isEqualTo(List.of("jdbc:dm://10.0.0.1:5236/DMHR"));
    }
}
