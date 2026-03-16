package com.yuzhi.dts.platform.service.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.AirflowProperties;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DbtDagServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldGenerateManualDagWithHostSideDockerRunAndSingleThreadDefault() throws Exception {
        AirflowProperties airflowProperties = new AirflowProperties();
        airflowProperties.setDagsDir(tempDir.toString());
        airflowProperties.setDockerNetwork("dts-core");
        airflowProperties.setDockerPrivileged(true);

        Path projectDir = Files.createDirectories(tempDir.resolve("dbt"));
        Path profilesDir = Files.createDirectories(tempDir.resolve("profiles"));

        DbtConfigService configService = mock(DbtConfigService.class);
        when(configService.loadConfig()).thenReturn(
            new DbtConfigService.DbtConfigView(
                true,
                new DbtConfigService.DbtWorkspaceConfig(
                    true,
                    projectDir.toString(),
                    profilesDir.toString(),
                    "dts",
                    "dev",
                    null,
                    null,
                    null,
                    java.util.Map.of()
                ),
                null,
                null,
                null
            )
        );

        InfraDataSourceRepository dataSourceRepository = mock(InfraDataSourceRepository.class);
        when(dataSourceRepository.findByStatusIgnoreCase("ACTIVE")).thenReturn(List.of());

        DbtDagService service = new DbtDagService(
            airflowProperties,
            configService,
            dataSourceRepository,
            new ObjectMapper()
        );

        String dagId = service.ensureDagForTag("biadmin", "dwh");
        Path dagFile = tempDir.resolve("dwh").resolve(dagId + ".py");

        assertThat(dagId).isEqualTo("dwh_biadmin_dbt_manual");
        assertThat(dagFile).exists();

        String dagSource = Files.readString(dagFile);
        assertThat(dagSource).contains("task_id=\"dbt_run\"");
        assertThat(dagSource).contains("docker run --rm");
        assertThat(dagSource).contains("DBT_THREADS");
        assertThat(dagSource).contains("DBT_THREADS_DEFAULT=");
        assertThat(dagSource).contains("docker_cmd+=(--threads");
        assertThat(dagSource).contains("dag_run.conf.get('vars', '') | tojson");
        assertThat(dagSource).contains("docker_cmd+=(--vars \\\"$vars_json\\\")");
        assertThat(dagSource).doesNotContain("env_var(");
        assertThat(dagSource).doesNotContain("DockerOperator(");
        assertThat(dagSource).doesNotContain("from airflow.providers.docker.operators.docker import DockerOperator");
    }
}
