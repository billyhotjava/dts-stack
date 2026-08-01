package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.config.AirflowProperties;
import com.yuzhi.dts.platform.service.etl.AirflowClient;
import com.yuzhi.dts.platform.service.etl.DbtArtifactSyncState;
import com.yuzhi.dts.platform.service.etl.DbtDagService;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EtlResourceRetainedDbtOperationsTest {

    @Test
    void dagReadyKeepsResolvingModelSelectorsThroughTheManagedDbtDag() {
        Fixture fixture = fixture();
        when(fixture.dbtDagService().ensureDagForSelector("tag:dbt model:finance")).thenReturn("finance-dag");

        ApiResponse<Map<String, Object>> response = fixture.resource().checkDagReady("model:finance");

        assertThat(response.getData()).containsEntry("dagId", "finance-dag");
    }

    @Test
    void runListingKeepsResolvingModelSelectorsThroughTheManagedDbtDag() {
        Fixture fixture = fixture();
        when(fixture.dbtDagService().ensureDagForSelector("tag:dbt model:finance")).thenReturn("finance-dag");
        when(fixture.airflowClient().listDagRuns("finance-dag", 20))
            .thenReturn(Optional.of(Map.of("dag_runs", List.of())));

        ApiResponse<Map<String, Object>> response = fixture.resource().listDbtRuns(20, null, "model:finance", "FIN");

        assertThat(response.getData()).containsEntry("dagId", "finance-dag");
    }

    @Test
    void syncStatusKeepsPollingTheManagedDbtDagForItsSelector() {
        Fixture fixture = fixture();
        when(fixture.dbtDagService().ensureDagForSelector("tag:dbt model:finance")).thenReturn("finance-dag");
        when(fixture.airflowClient().listDagRuns("finance-dag", 10))
            .thenReturn(Optional.of(Map.of("dag_runs", List.of())));

        fixture.resource().getDbtSyncStatus("model:finance", "FIN");

        verify(fixture.airflowClient()).listDagRuns("finance-dag", 10);
    }

    private Fixture fixture() {
        DbtDagService dbtDagService = mock(DbtDagService.class);
        AirflowClient airflowClient = mock(AirflowClient.class);
        AirflowProperties airflowProperties = new AirflowProperties();
        airflowProperties.setEnabled(true);
        airflowProperties.setDagId("fallback-dag");
        airflowProperties.setDagReadyWaitSeconds(0);
        airflowProperties.setDagReadyPollSeconds(0);

        Map<Class<?>, Object> dependencies = new LinkedHashMap<>();
        dependencies.put(DbtDagService.class, dbtDagService);
        dependencies.put(AirflowClient.class, airflowClient);
        dependencies.put(AirflowProperties.class, airflowProperties);
        dependencies.put(DbtArtifactSyncState.class, new DbtArtifactSyncState());
        try {
            Constructor<?> constructor = EtlResource.class.getDeclaredConstructors()[0];
            Object[] arguments = Arrays
                .stream(constructor.getParameterTypes())
                .map(type -> dependencies.computeIfAbsent(type, dependency -> mock(dependency)))
                .toArray();
            return new Fixture((EtlResource) constructor.newInstance(arguments), dbtDagService, airflowClient);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Unable to construct EtlResource", failure);
        }
    }

    private record Fixture(EtlResource resource, DbtDagService dbtDagService, AirflowClient airflowClient) {}
}
