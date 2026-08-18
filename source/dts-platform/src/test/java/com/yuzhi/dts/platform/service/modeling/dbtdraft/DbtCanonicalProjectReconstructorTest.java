package com.yuzhi.dts.platform.service.modeling.dbtdraft;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencyService.Resolution;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencySnapshotResolver.DependencyRole;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencySnapshotResolver.ModelInput;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencySnapshotResolver.PhysicalSource;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencySnapshotResolver.PhysicalSourceFact;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencySnapshotResolver.Snapshot;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.imports.checksum.ModelPackageChecksum;
import com.yuzhi.dts.platform.service.modeling.imports.converter.AdvancedDbtDraftStaticValidator;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationEvidencePort.ArtifactEvidence;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationEvidencePort.ImplementationSnapshot;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DbtCanonicalProjectReconstructorTest {

    private static final UUID MODEL_ID = UUID.fromString("ec1975b9-41d2-32d3-9bc6-370a7a0679cd");
    private static final UUID PLAN_ID = UUID.fromString("b1d7bcdc-887e-495e-8b93-8ae0d0ad5870");
    private static final UUID IMPLEMENTATION_ID = UUID.fromString("60512858-f7a4-4625-a9ae-003f10a0b559");
    private static final String MODEL_CHECKSUM = "a".repeat(64);
    private static final String IMPLEMENTATION_CHECKSUM = "b".repeat(64);
    private static final String PROJECT_KEY = "pm_analytics_v3";
    private static final String TARGET_PATH = "models/ads/biz_ads_progress_kpi_v2.sql";

    @Test
    void initializesManagedSourcesAndModelProxiesWithoutCountingThemAsOwnedTargets() {
        ObjectMapper objectMapper = new ObjectMapper();
        DbtCanonicalProjectReconstructor reconstructor = new DbtCanonicalProjectReconstructor(objectMapper);
        UUID sourceId = UUID.fromString("50000000-0000-0000-0000-000000000001");
        UUID dimensionId = UUID.fromString("30000000-0000-0000-0000-000000000001");
        String projectKey = "dts_model_ec1975b941d2";
        String sourceUniqueId = "source." + projectKey + ".dts_src_500000000000.src_500000000000";
        Snapshot snapshot = new Snapshot(
            MODEL_ID,
            1,
            MODEL_CHECKSUM,
            1,
            "0".repeat(64),
            List.of(new PhysicalSource(sourceId, "source-v1", sourceUniqueId)),
            List.of(
                new ModelInput(
                    dimensionId,
                    2,
                    "c".repeat(64),
                    3,
                    "d".repeat(64),
                    "model.pjm.dim_project",
                    DependencyRole.DIMENSION
                )
            ),
            "e".repeat(64)
        );
        Resolution dependencies = new Resolution(
            snapshot,
            Map.of(
                sourceId,
                new PhysicalSourceFact(sourceId, "source-v1", true, "CONNECTION_TABLE", "public.ods_project")
            )
        );

        var canonical = reconstructor.initialize(MODEL_ID, "table", "dwd_project_fact", dependencies);
        var validated = new AdvancedDbtDraftStaticValidator().validate(canonical.files());

        assertThat(canonical.files()).containsKeys(
            "models/.dts_dependencies/sources.yml",
            "models/.dts_dependencies/dts_ref_300000000000.sql",
            "models/dwd_project_fact.sql"
        );
        assertThat(canonical.files().get("models/dwd_project_fact.sql"))
            .contains("source('dts_src_500000000000', 'src_500000000000')")
            .contains("ref('dts_ref_300000000000')");
        assertThat(canonical.dependencyAliases())
            .containsEntry("model." + projectKey + ".dts_ref_300000000000", "model.pjm.dim_project");
        assertThat(canonical.dependencySnapshot()).isEqualTo(snapshot);
        assertThat(validated.nodes().stream().filter(node -> "MODEL".equals(node.nodeKind())))
            .extracting(node -> node.resourcePath())
            .containsExactly("models/dwd_project_fact.sql");
        assertThat(validated.nodes().getFirst().dependencies())
            .containsExactlyInAnyOrder(
                sourceUniqueId,
                "model." + projectKey + ".dts_ref_300000000000"
            );
    }

    @Test
    void restoresSameProjectUpstreamRefsAsEphemeralEvidencePlaceholders() {
        ObjectMapper objectMapper = new ObjectMapper();
        DbtCanonicalProjectReconstructor reconstructor = new DbtCanonicalProjectReconstructor(objectMapper);
        String sql =
            "{{ config(materialized='table') }}\n" +
            "select * from {{ ref('biz_dws_progress_monthly_v2') }}\n";
        String config = "{\"enabled\":true,\"materialized\":\"table\"}";
        String schema = "{\"columns\":[{\"name\":\"project_total_cnt\",\"dataType\":\"numeric\",\"role\":\"MEASURE\",\"tests\":[]}],\"tests\":[]}";
        String dependency = "[\"model.pm_analytics_v3.biz_dws_progress_monthly_v2\"]";

        var canonical = reconstructor.reconstruct(
            implementation(objectMapper),
            List.of(
                artifact("SQL", TARGET_PATH, sql),
                artifact("CONFIG", TARGET_PATH + "#config", config),
                artifact("SCHEMA", TARGET_PATH + "#schema", schema),
                artifact("DEPENDENCY", TARGET_PATH + "#dependency", dependency)
            ),
            MODEL_ID,
            1,
            MODEL_CHECKSUM,
            2,
            IMPLEMENTATION_CHECKSUM
        );

        assertThat(canonical.files())
            .containsKey("models/dts_dependencies/biz_dws_progress_monthly_v2.sql");
        assertThat(canonical.files().get("models/dts_dependencies/biz_dws_progress_monthly_v2.sql"))
            .contains("materialized='ephemeral'")
            .contains("Static dependency placeholder");

        var validated = new AdvancedDbtDraftStaticValidator().validate(canonical.files());
        var target = validated
            .nodes()
            .stream()
            .filter(node -> "model.pm_analytics_v3.biz_ads_progress_kpi_v2".equals(node.dbtUniqueId()))
            .findFirst()
            .orElseThrow();
        assertThat(target.dependencies())
            .containsExactly("model.pm_analytics_v3.biz_dws_progress_monthly_v2");
    }

    private static ImplementationSnapshot implementation(ObjectMapper objectMapper) {
        return new ImplementationSnapshot(
            IMPLEMENTATION_ID,
            MODEL_ID,
            PLAN_ID,
            1,
            MODEL_CHECKSUM,
            2,
            IMPLEMENTATION_CHECKSUM,
            ImplementationMode.DBT_MANAGED,
            PROJECT_KEY,
            "model.pm_analytics_v3.biz_ads_progress_kpi_v2",
            "GENERATED",
            objectMapper.createArrayNode(),
            objectMapper.createArrayNode(),
            objectMapper.createObjectNode(),
            "table"
        );
    }

    private static ArtifactEvidence artifact(String type, String path, String content) {
        return new ArtifactEvidence(
            type,
            path,
            ModelPackageChecksum.sha256Text(content),
            "COMPILED",
            content,
            MODEL_ID,
            1,
            MODEL_CHECKSUM,
            2,
            IMPLEMENTATION_CHECKSUM
        );
    }
}
