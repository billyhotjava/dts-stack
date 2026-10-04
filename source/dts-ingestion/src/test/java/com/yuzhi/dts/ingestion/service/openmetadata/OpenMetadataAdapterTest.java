package com.yuzhi.dts.ingestion.service.openmetadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.ingestion.config.OpenMetadataProperties;
import com.yuzhi.dts.ingestion.service.infra.IngestionSettingsService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OpenMetadataAdapterTest {

    @Mock
    private OpenMetadataClient client;

    @Mock
    private IngestionSettingsService settingsService;

    private OpenMetadataProperties properties;
    private OpenMetadataAdapter adapter;

    @BeforeEach
    void setUp() {
        properties = new OpenMetadataProperties();
        properties.setEnabled(true);
        properties.setDestinationServiceName("warehouse");
        properties.setDestinationServiceType("Postgres");
        properties.setIngestionPipelinePrefix("dts_ingest");
        properties.setIngestionDefaultSchedule("0 * * * *");
        adapter = new OpenMetadataAdapter(client, properties, settingsService);
    }

    @Test
    void ensureMetadataIngestionParsesNestedJdbcConnection() {
        when(settingsService.getSettings(IngestionSettingsService.SERVICE_OPENMETADATA))
            .thenReturn(new IngestionSettingsService.SettingsSnapshot(Map.of("enabled", true, "ingestionEnabled", true)));
        when(client.getDatabaseServiceByName("warehouse")).thenReturn(Optional.empty());
        when(client.createDatabaseService(eq("warehouse"), eq("Postgres"), anyMap()))
            .thenReturn(Optional.of(Map.of("id", "svc-1")));
        when(client.getIngestionPipelineByName("dts_ingest_warehouse")).thenReturn(Optional.of(Map.of("id", "pipe-1")));

        Map<String, Object> destinationConfig = Map.of(
            "writerType",
            "postgresqlwriter",
            "username",
            "etl_user",
            "password",
            "secret",
            "connection",
            List.of(Map.of("jdbcUrl", "jdbc:postgresql://10.0.0.2:15432/biadmin?sslmode=disable", "table", List.of("ods.orders")))
        );

        Map<String, Object> result = adapter.ensureMetadataIngestion(
            new OpenMetadataAdapter.IngestionContext("orders-sync", destinationConfig, null, false)
        );

        assertThat(result).containsEntry("status", "ready").containsEntry("serviceName", "warehouse");

        ArgumentCaptor<Map<String, Object>> configCaptor = ArgumentCaptor.forClass(Map.class);
        verify(client).createDatabaseService(eq("warehouse"), eq("Postgres"), configCaptor.capture());
        assertThat(configCaptor.getValue())
            .containsEntry("hostPort", "10.0.0.2:15432")
            .containsEntry("database", "biadmin")
            .containsEntry("username", "etl_user");
        assertThat(configCaptor.getValue().get("authType")).isEqualTo(Map.of("password", "secret"));
    }

    @Test
    void registerLineageUsesExplicitTargetMapping() {
        when(settingsService.getSettings(IngestionSettingsService.SERVICE_OPENMETADATA))
            .thenReturn(
                new IngestionSettingsService.SettingsSnapshot(
                    Map.of(
                        "enabled",
                        true,
                        "sourceServiceName",
                        "source_pg",
                        "destinationServiceName",
                        "warehouse",
                        "sourceDatabase",
                        "source_db",
                        "destinationDatabase",
                        "warehouse_db"
                    )
                )
            );
        when(client.getTableByFqn("source_pg.source_db.public.orders", properties.getTableFields()))
            .thenReturn(Optional.of(Map.of("id", "source-table")));
        when(client.getTableByFqn("warehouse.warehouse_db.ods.ods_orders", properties.getTableFields()))
            .thenReturn(Optional.of(Map.of("id", "target-table")));
        when(client.upsertLineage("source-table", "target-table", "orders-sync 入湖血缘"))
            .thenReturn(Optional.of(Map.of("status", "ok")));

        Map<String, Object> result = adapter.registerLineage(
            new OpenMetadataAdapter.LineageRequest(true, "finance", List.of("ods", "daily"), "alice"),
            new OpenMetadataAdapter.LineageContext(
                "orders-sync",
                "postgresqlreader",
                null,
                null,
                Map.of(),
                Map.of(),
                List.of(new OpenMetadataAdapter.StreamRef("orders", "public", "ods_orders", "ods"))
            )
        );

        assertThat(result).containsEntry("status", "success").containsEntry("created", 1);
        @SuppressWarnings("unchecked")
        List<Map<String, String>> edges = (List<Map<String, String>>) result.get("edges");
        assertThat(edges).containsExactly(Map.of("from", "source_pg.source_db.public.orders", "to", "warehouse.warehouse_db.ods.ods_orders"));
        Map<?, ?> governance = (Map<?, ?>) result.get("governance");
        assertThat(governance.get("status")).isEqualTo("not_supported");
        assertThat(governance.get("owner")).isEqualTo("alice");
        assertThat(governance.get("domain")).isEqualTo("finance");
        assertThat(governance.get("tags")).isEqualTo(List.of("ods", "daily"));
    }
}
