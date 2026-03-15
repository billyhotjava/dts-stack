package com.yuzhi.dts.platform.service.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.DbtProperties;
import com.yuzhi.dts.platform.service.ops.ExternalRunLogService;
import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DbtRunResultServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldReadInvocationCommandFromTopLevelArgsMap() throws Exception {
        Path targetDir = Files.createDirectories(tempDir.resolve("target"));
        Files.writeString(
            targetDir.resolve("run_results.json"),
            """
            {
              "metadata": {
                "generated_at": "2026-03-15T15:46:32.897374Z",
                "invocation_id": "inv-1"
              },
              "args": {
                "invocation_command": "dbt test --select model:demo",
                "which": "test"
              },
              "results": [
                {
                  "status": "pass",
                  "unique_id": "test.demo",
                  "execution_time": 0.2
                }
              ]
            }
            """
        );

        DbtProperties properties = new DbtProperties();
        properties.setEnabled(true);
        properties.setProjectDir(tempDir.toString());

        DbtConfigService configService = mock(DbtConfigService.class);
        when(configService.loadConfig()).thenReturn(
            new DbtConfigService.DbtConfigView(
                true,
                new DbtConfigService.DbtWorkspaceConfig(true, tempDir.toString(), "/tmp/profiles", "dts", "dev", null, null, null, java.util.Map.of()),
                null,
                null,
                null
            )
        );

        DbtRunResultService service = new DbtRunResultService(
            new ObjectMapper(),
            properties,
            configService,
            mock(ExternalRunLogService.class)
        );

        DbtRunResultService.DbtRunSummary summary = service.loadLatestSummary(10);

        assertThat(summary.present()).isTrue();
        assertThat(summary.command()).isEqualTo("dbt test --select model:demo");
        assertThat(summary.status()).isEqualTo("SUCCESS");
    }

    @Test
    void shouldFallbackToExternalBuildEvidenceWhenRunResultsMissing() {
        DbtProperties properties = new DbtProperties();
        properties.setEnabled(true);
        properties.setProjectDir(tempDir.toString());

        DbtConfigService configService = mock(DbtConfigService.class);
        when(configService.loadConfig()).thenReturn(
            new DbtConfigService.DbtConfigView(
                true,
                new DbtConfigService.DbtWorkspaceConfig(true, tempDir.toString(), "/tmp/profiles", "dts", "dev", null, null, null, java.util.Map.of()),
                null,
                null,
                null
            )
        );

        ExternalRunLogService externalRunLogService = mock(ExternalRunLogService.class);
        when(externalRunLogService.findLatestDbtBuildEvidence()).thenReturn(
            Optional.of(
                new ExternalRunLogService.DbtBuildEvidenceSnapshot(
                    "dag-run-1",
                    "SUCCESS",
                    Instant.parse("2026-03-16T03:10:00Z"),
                    Instant.parse("2026-03-16T03:12:00Z"),
                    "dbt test --select model:demo",
                    1,
                    "external-run-log"
                )
            )
        );

        DbtRunResultService service = new DbtRunResultService(
            new ObjectMapper(),
            properties,
            configService,
            externalRunLogService
        );

        DbtRunResultService.DbtRunSummary summary = service.loadLatestSummary(10);

        assertThat(summary.present()).isTrue();
        assertThat(summary.command()).isEqualTo("dbt test --select model:demo");
        assertThat(summary.status()).isEqualTo("SUCCESS");
        assertThat(summary.total()).isEqualTo(1);
        assertThat(summary.success()).isEqualTo(1);
        assertThat(summary.invocationId()).isEqualTo("dag-run-1");
        assertThat(summary.generatedAt()).isEqualTo("2026-03-16T03:12:00Z");
    }
}
