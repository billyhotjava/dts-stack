package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class DbtModelingContractTest {

    @Test
    void validManifestImportRequestHasNoIssues() {
        DbtModelingContract.ManifestImportRequest request = new DbtModelingContract.ManifestImportRequest(
            "pjm-v4",
            "1.8",
            "model.pm_analytics_v3.biz_dwd_project_node_v2",
            Map.of("nodes", Map.of("model.pm_analytics_v3.biz_dwd_project_node_v2", Map.of("resource_type", "model"))),
            "select 1",
            "idem-dbt-1",
            "30000000-0000-0000-0000-000000000001",
            3,
            "a".repeat(64),
            2,
            "b".repeat(64)
        );

        assertThat(DbtModelingContract.validateManifestImport(request)).isEmpty();
    }

    @Test
    void malformedManifestUsesStableErrorCode() {
        DbtModelingContract.ManifestImportRequest request = new DbtModelingContract.ManifestImportRequest(
            "pjm-v4",
            "",
            "source.pm_analytics_v3.project_subject_domain_v2",
            Map.of(),
            null,
            "idem-dbt-2",
            null,
            0,
            null,
            0,
            null
        );

        assertThat(DbtModelingContract.validateManifestImport(request))
            .containsExactly(
                DbtModelingContract.ErrorCode.DBT_MANIFEST_INVALID.name(),
                DbtModelingContract.ErrorCode.DBT_MODEL_NOT_FOUND.name(),
                DbtModelingContract.ErrorCode.DBT_MODEL_OWNER_REQUIRED.name()
            );
    }
}
