package com.yuzhi.dts.platform.service.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.AirflowProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DbtDagServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void managedDagPurposesCannotOverwriteEachOther() throws Exception {
        AirflowProperties properties = new AirflowProperties();
        properties.setDagsDir(tempDir.toString());
        DbtDagService service = new DbtDagService(properties, new ObjectMapper());
        String releaseId = "dts_release_build_postgres_primary";
        String planId = "dts_plan_finance_prod_primary";
        service.ensureReleaseBuildDag(releaseId);
        service.ensurePlanDag(planId, UUID.randomUUID(), null, "UTC", "a".repeat(64));
        String releaseSource = Files.readString(tempDir.resolve(releaseId + ".py"));
        String planSource = Files.readString(tempDir.resolve(planId + ".py"));

        assertThatThrownBy(() -> service.ensurePlanDag(releaseId, UUID.randomUUID(), null, "UTC", "a".repeat(64)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.ensureReleaseBuildDag(planId))
            .isInstanceOf(IllegalArgumentException.class);

        assertThat(Files.readString(tempDir.resolve(releaseId + ".py"))).isEqualTo(releaseSource);
        assertThat(Files.readString(tempDir.resolve(planId + ".py"))).isEqualTo(planSource);
    }

    @Test
    void managedDagIsWorldReadableRegardlessOfUmaskEvenWhenUnchanged() throws Exception {
        AirflowProperties properties = new AirflowProperties();
        properties.setDagsDir(tempDir.toString());
        DbtDagService service = new DbtDagService(properties, new ObjectMapper());
        String releaseId = "dts_release_build_postgres_primary";
        Path dagFile = tempDir.resolve(releaseId + ".py");

        service.ensureReleaseBuildDag(releaseId);
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(dagFile))).isEqualTo("rw-r--r--");

        Files.setPosixFilePermissions(dagFile, PosixFilePermissions.fromString("rw-------"));
        service.ensureReleaseBuildDag(releaseId);

        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(dagFile))).isEqualTo("rw-r--r--");
    }

    @Test
    void shouldGenerateReleaseBuildAsAtomicThinManagedDag() throws Exception {
        AirflowProperties airflowProperties = new AirflowProperties();
        airflowProperties.setDagsDir(tempDir.toString());

        DbtDagService service = new DbtDagService(
            airflowProperties,
            new ObjectMapper()
        );

        DbtDagService.ManagedDagDeployment deployment = service.ensureReleaseBuildDag(
            "dts_release_build_postgres_primary"
        );
        Path dagFile = tempDir.resolve("dts_release_build_postgres_primary.py");

        assertThat(deployment.dagId()).isEqualTo(
            "dts_release_build_postgres_primary"
        );
        assertThat(deployment.templateVersion()).isEqualTo("sprint76-v1");
        assertThat(deployment.deploymentChecksum()).matches("[0-9a-f]{64}");
        assertThat(dagFile).exists();
        assertThat(Files.list(tempDir).map(Path::getFileName).map(Path::toString))
            .noneMatch(name -> name.contains(".tmp-"));

        String dagSource = Files.readString(dagFile);
        assertThat(dagSource)
            .contains("# airflow DAG")
            .contains(
                "from dts_runtime.dbt_task_factory import build_dbt_dag"
            )
            .contains("purpose=\"RELEASE_BUILD\"")
            .contains("schedule=None")
            .contains("template_version=\"sprint76-v1\"")
            .contains(
                "deployment_checksum=\"" +
                deployment.deploymentChecksum() +
                "\""
            )
            .doesNotContain("docker run")
            .doesNotContain("BashOperator")
            .doesNotContain("dag_run.conf")
            .doesNotContain("projectDir")
            .doesNotContain("profiles.yml")
            .doesNotContain("shell=True")
            .doesNotContain("|| true");
    }

    @Test
    void shouldGenerateStablePlanDagFromBindingMetadataOnly() throws Exception {
        AirflowProperties airflowProperties = new AirflowProperties();
        airflowProperties.setDagsDir(tempDir.toString());
        DbtDagService service = new DbtDagService(
            airflowProperties,
            new ObjectMapper()
        );
        UUID bindingId = UUID.fromString(
            "10000000-0000-0000-0000-000000000076"
        );

        DbtDagService.ManagedDagDeployment manual = service.ensurePlanDag(
            "dts_plan_finance_prod_primary",
            bindingId,
            null,
            "Asia/Shanghai",
            "a".repeat(64)
        );
        DbtDagService.ManagedDagDeployment cron = service.ensurePlanDag(
            "dts_plan_finance_prod_primary",
            bindingId,
            "0 2 * * *",
            "Asia/Shanghai",
            "b".repeat(64)
        );

        assertThat(manual.dagId()).isEqualTo(cron.dagId());
        assertThat(manual.deploymentChecksum())
            .isNotEqualTo(cron.deploymentChecksum());
        String dagSource = Files.readString(
            tempDir.resolve("dts_plan_finance_prod_primary.py")
        );
        assertThat(dagSource)
            .contains(
                "from dts_runtime.dbt_task_factory import build_dbt_dag"
            )
            .contains("purpose=\"OPERATIONAL_RUN\"")
            .contains("schedule=\"0 2 * * *\"")
            .contains("timezone=\"Asia/Shanghai\"")
            .contains("binding_id=\"" + bindingId + "\"")
            .contains("max_active_runs")
            .doesNotContain("docker run")
            .doesNotContain("BashOperator")
            .doesNotContain("projectDir")
            .doesNotContain("selector=");
    }
}
