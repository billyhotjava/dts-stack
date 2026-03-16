package com.yuzhi.dts.platform.service.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.DbtProperties;
import com.yuzhi.dts.platform.service.ops.ExternalRunLogService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
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

    @Test
    void shouldUseManifestModelCountWhenFallbackEvidenceTargetsAllModels() throws Exception {
        Path targetDir = Files.createDirectories(tempDir.resolve("target"));
        Files.writeString(
            targetDir.resolve("manifest.json"),
            """
            {
              "nodes": {
                "model.demo.one": {
                  "name": "one",
                  "resource_type": "model",
                  "path": "models/one.sql"
                },
                "model.demo.two": {
                  "name": "two",
                  "resource_type": "model",
                  "path": "models/two.sql"
                },
                "test.demo.not_null_one": {
                  "name": "not_null_one",
                  "resource_type": "test",
                  "path": "target/compiled/tests.sql"
                }
              }
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

        ExternalRunLogService externalRunLogService = mock(ExternalRunLogService.class);
        when(externalRunLogService.findLatestDbtBuildEvidence()).thenReturn(
            Optional.of(
                new ExternalRunLogService.DbtBuildEvidenceSnapshot(
                    "dag-run-2",
                    "SUCCESS",
                    Instant.parse("2026-03-16T05:00:00Z"),
                    Instant.parse("2026-03-16T05:02:00Z"),
                    "dbt compile --target dev",
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
        assertThat(summary.command()).isEqualTo("dbt compile --target dev");
        assertThat(summary.total()).isEqualTo(2);
        assertThat(summary.success()).isEqualTo(2);
        assertThat(summary.failed()).isZero();
    }

    @Test
    void shouldUseManifestTagCountWhenFallbackEvidenceTargetsTagSelector() throws Exception {
        Path targetDir = Files.createDirectories(tempDir.resolve("target"));
        Files.writeString(
            targetDir.resolve("manifest.json"),
            """
            {
              "nodes": {
                "model.demo.alpha": {
                  "name": "alpha",
                  "resource_type": "model",
                  "path": "models/alpha.sql",
                  "tags": ["project-management", "biadmin"]
                },
                "model.demo.beta": {
                  "name": "beta",
                  "resource_type": "model",
                  "path": "models/beta.sql",
                  "tags": ["project-management"]
                },
                "model.demo.gamma": {
                  "name": "gamma",
                  "resource_type": "model",
                  "path": "models/gamma.sql",
                  "tags": ["finance"]
                }
              }
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

        ExternalRunLogService externalRunLogService = mock(ExternalRunLogService.class);
        when(externalRunLogService.findLatestDbtBuildEvidence()).thenReturn(
            Optional.of(
                new ExternalRunLogService.DbtBuildEvidenceSnapshot(
                    "dag-run-3",
                    "SUCCESS",
                    Instant.parse("2026-03-16T05:10:00Z"),
                    Instant.parse("2026-03-16T05:12:00Z"),
                    "dbt compile --select tag:project-management --target dev",
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
        assertThat(summary.command()).isEqualTo("dbt compile --select tag:project-management --target dev");
        assertThat(summary.total()).isEqualTo(2);
        assertThat(summary.success()).isEqualTo(2);
        assertThat(summary.failed()).isZero();
    }

    @Test
    void shouldPreferNewerExternalBuildEvidenceOverOlderSkippedLocalRunResults() throws Exception {
        Path targetDir = Files.createDirectories(tempDir.resolve("target"));
        Files.writeString(
            targetDir.resolve("run_results.json"),
            """
            {
              "metadata": {
                "generated_at": "2026-03-16T05:00:00Z",
                "invocation_id": "inv-old"
              },
              "args": {
                "invocation_command": "dbt compile --select tag:project-management --target dev",
                "which": "compile"
              },
              "results": []
            }
            """
        );
        Files.writeString(
            targetDir.resolve("manifest.json"),
            """
            {
              "nodes": {
                "model.demo.alpha": {
                  "name": "alpha",
                  "resource_type": "model",
                  "path": "models/alpha.sql",
                  "tags": ["project-management"]
                },
                "model.demo.beta": {
                  "name": "beta",
                  "resource_type": "model",
                  "path": "models/beta.sql",
                  "tags": ["project-management"]
                }
              }
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

        ExternalRunLogService externalRunLogService = mock(ExternalRunLogService.class);
        when(externalRunLogService.findLatestDbtBuildEvidence()).thenReturn(
            Optional.of(
                new ExternalRunLogService.DbtBuildEvidenceSnapshot(
                    "dag-run-4",
                    "SUCCESS",
                    Instant.parse("2026-03-16T05:09:00Z"),
                    Instant.parse("2026-03-16T05:10:00Z"),
                    "dbt compile --select tag:project-management --target dev",
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

        DbtRunResultService.DbtRunSummary summary = service.loadLatestBuildSummary(10);

        assertThat(summary.present()).isTrue();
        assertThat(summary.invocationId()).isEqualTo("dag-run-4");
        assertThat(summary.status()).isEqualTo("SUCCESS");
        assertThat(summary.total()).isEqualTo(2);
        assertThat(summary.success()).isEqualTo(2);
        assertThat(summary.skipped()).isZero();
    }
}
