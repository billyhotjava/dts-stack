package com.yuzhi.dts.ingestion.service.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.ingestion.config.AddaxProperties;
import com.yuzhi.dts.ingestion.config.AirflowProperties;
import com.yuzhi.dts.ingestion.service.infra.IngestionSettingsService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AirflowAdapterTest {

    @Mock
    private AirflowClient client;

    @Mock
    private IngestionSettingsService settingsService;

    private AirflowProperties airflowProperties;
    private AddaxProperties addaxProperties;
    private AirflowAdapter airflowAdapter;

    @BeforeEach
    void setUp() {
        airflowProperties = new AirflowProperties();
        addaxProperties = new AddaxProperties();
        airflowAdapter = new AirflowAdapter(client, airflowProperties, addaxProperties, settingsService);
    }

    @Test
    void shouldTriggerWhenDagIsReady() {
        IngestionSettingsService.SettingsSnapshot airflowSettings = snapshot(
            Map.of(
                "enabled", true,
                "dagId", "task_demo_manual",
                "dagReadyWaitSeconds", 0,
                "dagTriggerRetrySeconds", 0,
                "dagReadyPollSeconds", 1,
                "dagNotFoundRetryWaitSeconds", 0
            )
        );
        IngestionSettingsService.SettingsSnapshot addaxSettings = snapshot(Map.of("enabled", false));
        when(settingsService.getSettings(eq(IngestionSettingsService.SERVICE_AIRFLOW))).thenReturn(airflowSettings);
        when(settingsService.getSettings(eq(IngestionSettingsService.SERVICE_ADDAX))).thenReturn(addaxSettings);

        Map<String, Object> payload = Map.of("conf", Map.of("task_id", 1));
        when(client.triggerDag(eq("task_demo_manual"), eq(payload)))
            .thenReturn(new AirflowClient.TriggerResult(true, 200, null, Map.of("dag_run_id", "manual__1")));

        Map<String, Object> result = airflowAdapter.triggerIfRequested(
            new AirflowAdapter.AirflowRequest(true, null, null, null, null),
            Map.of("task_id", 1),
            true
        );

        assertThat(result.get("status")).isEqualTo("triggered");
        assertThat(result.get("dagId")).isEqualTo("task_demo_manual");
        verify(client, times(1)).triggerDag(eq("task_demo_manual"), eq(payload));
    }

    @Test
    void shouldRecoverFromDagNotFoundAfterRetryWait() {
        IngestionSettingsService.SettingsSnapshot airflowSettings = snapshot(
            Map.of(
                "enabled", true,
                "dagReadyWaitSeconds", 0,
                "dagTriggerRetrySeconds", 0,
                "dagReadyPollSeconds", 1,
                "dagNotFoundRetryWaitSeconds", 30
            )
        );
        IngestionSettingsService.SettingsSnapshot addaxSettings = snapshot(Map.of("enabled", false));
        when(settingsService.getSettings(eq(IngestionSettingsService.SERVICE_AIRFLOW))).thenReturn(airflowSettings);
        when(settingsService.getSettings(eq(IngestionSettingsService.SERVICE_ADDAX))).thenReturn(addaxSettings);

        Map<String, Object> payload = Map.of("conf", Map.of("task_id", 2));
        when(client.triggerDag(eq("task_task_filetest1_manual"), eq(payload)))
            .thenReturn(new AirflowClient.TriggerResult(false, 404, "DAG not found", null))
            .thenReturn(new AirflowClient.TriggerResult(true, 200, null, Map.of("dag_run_id", "manual__2")));
        when(client.waitForDag(eq("task_task_filetest1_manual"), any(), any())).thenReturn(true);

        Map<String, Object> result = airflowAdapter.triggerIfRequested(
            new AirflowAdapter.AirflowRequest(true, "task_task_filetest1_manual", null, null, null),
            Map.of("task_id", 2),
            true
        );

        assertThat(result.get("status")).isEqualTo("triggered");
        assertThat(result.get("dagId")).isEqualTo("task_task_filetest1_manual");
        verify(client, times(2)).triggerDag(eq("task_task_filetest1_manual"), eq(payload));
        verify(client, times(1)).waitForDag(eq("task_task_filetest1_manual"), any(), any());
    }

    @Test
    void shouldReturnReadableFailureWhenDagNotReadyTimeout() {
        IngestionSettingsService.SettingsSnapshot airflowSettings = snapshot(
            Map.of(
                "enabled", true,
                "dagReadyWaitSeconds", 0,
                "dagTriggerRetrySeconds", 0,
                "dagReadyPollSeconds", 1,
                "dagNotFoundRetryWaitSeconds", 10
            )
        );
        IngestionSettingsService.SettingsSnapshot addaxSettings = snapshot(Map.of("enabled", false));
        when(settingsService.getSettings(eq(IngestionSettingsService.SERVICE_AIRFLOW))).thenReturn(airflowSettings);
        when(settingsService.getSettings(eq(IngestionSettingsService.SERVICE_ADDAX))).thenReturn(addaxSettings);

        Map<String, Object> payload = Map.of("conf", Map.of("task_id", 3));
        when(client.triggerDag(eq("task_task_missing_manual"), eq(payload)))
            .thenReturn(new AirflowClient.TriggerResult(false, 404, "DAG not found", null));
        when(client.waitForDag(eq("task_task_missing_manual"), any(), any())).thenReturn(false);
        when(client.listImportErrors(100)).thenReturn(java.util.Optional.of(java.util.List.of()));

        Map<String, Object> result = airflowAdapter.triggerIfRequested(
            new AirflowAdapter.AirflowRequest(true, "task_task_missing_manual", null, null, null),
            Map.of("task_id", 3),
            true
        );

        assertThat(result.get("status")).isEqualTo("failed");
        assertThat(result.get("code")).isEqualTo("AIRFLOW_DAG_NOT_READY_TIMEOUT");
        assertThat(String.valueOf(result.get("message"))).contains("DAG 未就绪");
        verify(client, times(1)).triggerDag(eq("task_task_missing_manual"), eq(payload));
    }

    private IngestionSettingsService.SettingsSnapshot snapshot(Map<String, Object> raw) {
        return new IngestionSettingsService.SettingsSnapshot(raw);
    }
}
