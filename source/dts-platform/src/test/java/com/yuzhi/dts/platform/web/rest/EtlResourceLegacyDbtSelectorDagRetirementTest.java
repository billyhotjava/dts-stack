package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.config.AirflowProperties;
import com.yuzhi.dts.platform.service.etl.AirflowClient;
import com.yuzhi.dts.platform.service.etl.DbtArtifactSyncState;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EtlResourceLegacyDbtSelectorDagRetirementTest {

    @Test
    void sharedMutableDbtFileResourceIsPhysicallyAbsent() {
        assertThatThrownBy(() -> Class.forName("com.yuzhi.dts.platform.web.rest.DbtFileResource"))
            .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void selectorDagReadinessRouteIsPhysicallyAbsent() {
        assertThat(Arrays.stream(EtlResource.class.getDeclaredMethods()).map(java.lang.reflect.Method::getName))
            .doesNotContain("checkDagReady");
    }

    @Test
    void runListingUsesOnlyAnExplicitOrConfiguredManagedDag() {
        Fixture fixture = fixture();
        when(fixture.airflowClient().listDagRuns("fallback-dag", 20))
            .thenReturn(Optional.of(Map.of("dag_runs", List.of())));

        ApiResponse<Map<String, Object>> response = fixture.resource().listDbtRuns(20, null, "FIN");

        assertThat(response.getData()).containsEntry("dagId", "fallback-dag");
    }

    @Test
    void syncStatusPollsTheConfiguredManagedDagWithoutGeneratingASelectorDag() {
        Fixture fixture = fixture();
        when(fixture.airflowClient().listDagRuns("fallback-dag", 10))
            .thenReturn(Optional.of(Map.of("dag_runs", List.of())));

        fixture.resource().getDbtSyncStatus("FIN");

        verify(fixture.airflowClient()).listDagRuns("fallback-dag", 10);
    }

    private Fixture fixture() {
        AirflowClient airflowClient = mock(AirflowClient.class);
        AirflowProperties airflowProperties = new AirflowProperties();
        airflowProperties.setEnabled(true);
        airflowProperties.setDagId("fallback-dag");

        Map<Class<?>, Object> dependencies = new LinkedHashMap<>();
        dependencies.put(AirflowClient.class, airflowClient);
        dependencies.put(AirflowProperties.class, airflowProperties);
        dependencies.put(DbtArtifactSyncState.class, new DbtArtifactSyncState());
        try {
            Constructor<?> constructor = EtlResource.class.getDeclaredConstructors()[0];
            Object[] arguments = Arrays
                .stream(constructor.getParameterTypes())
                .map(type -> dependencies.computeIfAbsent(type, dependency -> mock(dependency)))
                .toArray();
            return new Fixture((EtlResource) constructor.newInstance(arguments), airflowClient);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Unable to construct EtlResource", failure);
        }
    }

    private record Fixture(EtlResource resource, AirflowClient airflowClient) {}
}
