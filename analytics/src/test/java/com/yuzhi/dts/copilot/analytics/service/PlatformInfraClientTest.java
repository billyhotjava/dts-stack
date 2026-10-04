package com.yuzhi.dts.copilot.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlatformInfraClientTest {
    @Mock private CopilotAiClient copilotAiClient;

    @Test
    void mapsDataSourceDetailsReturnedByTheApi() {
        when(copilotAiClient.getDataSource(15L, "")).thenReturn(Optional.of(Map.of(
                "id", 15L, "name", "test-source", "type", "postgres",
                "jdbcUrl", "jdbc:postgresql://example.invalid/test", "username", "reader",
                "props", Map.of("ssl", "true"), "secrets", Map.of("password", "test-only"))));
        PlatformInfraClient client = new PlatformInfraClient(copilotAiClient, new ObjectMapper());
        PlatformInfraClient.DataSourceDetail detail = client.fetchDataSourceDetail(15L);
        assertThat(detail.id()).isEqualTo("15");
        assertThat(detail.name()).isEqualTo("test-source");
        assertThat(detail.jdbcUrl()).isEqualTo("jdbc:postgresql://example.invalid/test");
        assertThat(detail.props()).containsEntry("ssl", "true");
        assertThat(detail.secrets()).containsEntry("password", "test-only");
    }

    @Test
    void failsClosedWhenTheApiHasNoDataSource() {
        when(copilotAiClient.getDataSource(15L, "")).thenReturn(Optional.empty());
        PlatformInfraClient client = new PlatformInfraClient(copilotAiClient, new ObjectMapper());
        assertThatThrownBy(() -> client.fetchDataSourceDetail(15L))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("API returned no details");
    }

    @Test
    void propagatesApiFailureInsteadOfReadingAnotherServiceDatabase() {
        when(copilotAiClient.getDataSource(15L, "")).thenThrow(new IllegalStateException("API unavailable"));
        PlatformInfraClient client = new PlatformInfraClient(copilotAiClient, new ObjectMapper());
        assertThatThrownBy(() -> client.fetchDataSourceDetail(15L))
                .isInstanceOf(IllegalStateException.class).hasMessage("API unavailable");
    }
}
