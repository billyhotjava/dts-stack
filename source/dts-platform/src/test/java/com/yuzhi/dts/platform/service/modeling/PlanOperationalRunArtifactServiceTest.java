package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.ModelMaterializationProperties;
import com.yuzhi.dts.platform.repository.modeling.PhysicalRelationObservationRepository;
import com.yuzhi.dts.platform.repository.modeling.PlanOperationalRunRepository;
import com.yuzhi.dts.platform.repository.modeling.PlanOperationalRunRepository.EvidenceEntry;
import com.yuzhi.dts.platform.repository.modeling.PlanOperationalRunRepository.EvidenceScope;
import com.yuzhi.dts.platform.service.etl.DbtRuntimeProfileLeaseService;
import com.yuzhi.dts.platform.service.etl.DbtScopedProjectService;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationRunArtifactService.SyncProbeCommand;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.ExpectedRelationType;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.PhysicalColumn;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.PhysicalRelationObservation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlanOperationalRunArtifactServiceTest {

    private static final UUID GROUP_ID =
        UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID BINDING_ID =
        UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID MODEL_ID =
        UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final UUID PIPELINE_RUN_ID =
        UUID.fromString("40000000-0000-0000-0000-000000000004");
    private static final UUID LEASE_ID =
        UUID.fromString("50000000-0000-0000-0000-000000000005");
    private static final UUID INVOCATION_ID =
        UUID.fromString("60000000-0000-0000-0000-000000000006");
    private static final String MODEL_CHECKSUM = "a".repeat(64);
    private static final String IMPLEMENTATION_CHECKSUM = "b".repeat(64);
    private static final String BUNDLE_CHECKSUM = "c".repeat(64);
    private static final String IMPORTED_UNIQUE_ID =
        "model.pm_analytics_v3.dim_change_category_v2";
    private static final String RUNTIME_UNIQUE_ID =
        "model.dts.dim_change_category_v2";
    private static final Instant NOW =
        Instant.parse("2026-08-15T08:00:00Z");

    @TempDir
    Path project;

    private PlanOperationalRunRepository runs;
    private DbtScopedProjectService scopedProjects;
    private PhysicalRelationInspector inspector;
    private PhysicalRelationObservationRepository observations;
    private PlanOperationalRunArtifactService service;

    @BeforeEach
    void setUp() {
        runs = mock(PlanOperationalRunRepository.class);
        scopedProjects = mock(DbtScopedProjectService.class);
        inspector = mock(PhysicalRelationInspector.class);
        PhysicalRelationInspectorRegistry inspectors = mock(
            PhysicalRelationInspectorRegistry.class
        );
        observations = mock(
            PhysicalRelationObservationRepository.class
        );
        ModelMaterializationProperties properties =
            new ModelMaterializationProperties();
        properties.setAdapter("postgres");
        service = new PlanOperationalRunArtifactService(
            runs,
            scopedProjects,
            inspectors,
            observations,
            mock(DbtRuntimeProfileLeaseService.class),
            properties,
            new ObjectMapper(),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
        when(runs.loadEvidenceScope(GROUP_ID)).thenReturn(scope());
        when(scopedProjects.verifyCandidateProject(BUNDLE_CHECKSUM))
            .thenReturn(project);
        when(inspectors.require("postgres")).thenReturn(inspector);
        when(inspector.adapter()).thenReturn("postgres");
        when(inspector.observe(any(), any())).thenReturn(
            new PhysicalRelationObservation(
                true,
                ExpectedRelationType.TABLE,
                List.of(
                    new PhysicalColumn(1, "change_category_id", "text", false),
                    new PhysicalColumn(2, "code", "text", false)
                ),
                "d".repeat(64),
                NOW,
                null
            )
        );
        when(runs.markDbtSucceeded(GROUP_ID, INVOCATION_ID, NOW))
            .thenReturn(1);
        when(runs.markRelationsVerified(GROUP_ID, NOW)).thenReturn(1);
    }

    @Test
    void acceptsCanonicalRuntimeUniqueIdWhenImmutableModelIdentityMatches()
        throws Exception {
        writeRuntimeArtifacts();

        var result = service.syncAndProbe(
            GROUP_ID,
            new SyncProbeCommand("OPERATIONAL_RUN", BUNDLE_CHECKSUM)
        );

        assertThat(result.status()).isEqualTo("BUILT");
        verify(observations).appendAll(any());
    }

    @Test
    void physicalColumnOrderDoesNotBlockOperationalEvidence()
        throws Exception {
        writeRuntimeArtifacts();
        when(inspector.observe(any(), any())).thenReturn(
            new PhysicalRelationObservation(
                true,
                ExpectedRelationType.TABLE,
                List.of(
                    new PhysicalColumn(1, "code", "text", false),
                    new PhysicalColumn(2, "change_category_id", "text", false)
                ),
                "d".repeat(64),
                NOW,
                null
            )
        );

        var result = service.syncAndProbe(
            GROUP_ID,
            new SyncProbeCommand("OPERATIONAL_RUN", BUNDLE_CHECKSUM)
        );

        assertThat(result.status()).isEqualTo("BUILT");
        verify(observations).appendAll(any());
    }

    private EvidenceScope scope() {
        return new EvidenceScope(
            GROUP_ID,
            "default",
            BINDING_ID,
            1,
            "postgres-primary",
            BUNDLE_CHECKSUM,
            LEASE_ID,
            "sha256:" + "e".repeat(64),
            List.of(
                new EvidenceEntry(
                    PIPELINE_RUN_ID,
                    MODEL_ID,
                    2,
                    MODEL_CHECKSUM,
                    2,
                    IMPLEMENTATION_CHECKSUM,
                    "DBT_MANAGED",
                    IMPORTED_UNIQUE_ID,
                    "dim_change_category_v2",
                    List.of("change_category_id", "code")
                )
            )
        );
    }

    private void writeRuntimeArtifacts() throws Exception {
        Path target = Files.createDirectories(project.resolve("target"));
        Files.writeString(
            target.resolve("manifest.json"),
            """
            {
              "metadata": {"invocation_id": "%s"},
              "nodes": {
                "%s": {
                  "unique_id": "%s",
                  "resource_type": "model",
                  "database": "biadmin",
                  "schema": "public",
                  "alias": "dim_change_category_v2",
                  "config": {
                    "materialized": "table",
                    "meta": {
                      "modelSpecId": "%s",
                      "modelRevision": 2,
                      "modelChecksum": "%s",
                      "implementationRevision": 2,
                      "implementationChecksum": "%s"
                    }
                  },
                  "columns": {}
                }
              }
            }
            """.formatted(
                INVOCATION_ID,
                RUNTIME_UNIQUE_ID,
                RUNTIME_UNIQUE_ID,
                MODEL_ID,
                MODEL_CHECKSUM,
                IMPLEMENTATION_CHECKSUM
            )
        );
        Files.writeString(
            target.resolve("run_results.json"),
            """
            {
              "metadata": {"invocation_id": "%s"},
              "results": [
                {"unique_id": "%s", "status": "success"}
              ]
            }
            """.formatted(INVOCATION_ID, RUNTIME_UNIQUE_ID)
        );
    }
}
