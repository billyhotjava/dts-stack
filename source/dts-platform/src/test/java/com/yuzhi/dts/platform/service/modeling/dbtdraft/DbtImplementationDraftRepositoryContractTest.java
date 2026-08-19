package com.yuzhi.dts.platform.service.modeling.dbtdraft;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DbtImplementationDraftRepositoryContractTest {

    @Test
    void repositoryKeepsActorTenantCasAndUsesBoundedSkipLockedExpiryPurge() throws Exception {
        String source = Files.readString(
            Path.of("src/main/java/com/yuzhi/dts/platform/repository/modeling/DbtImplementationDraftRepository.java")
        );

        assertThat(source).contains("tenant_id = ?", "model_spec_id = ?", "actor_id = ?", "etag = ?");
        assertThat(source).contains("on conflict (tenant_id, plan_id, model_spec_id, actor_id, idempotency_key)");
        assertThat(source).contains("expires_at <= ?", "for update skip locked", "limit ?");
        assertThat(source).contains("bundle_checksum", "bundle_manifest", "project_checksum");
        assertThat(source).contains("model_spec_snapshot", "projection_summary", "authoring_origin");
        assertThat(source).contains(
            "findOpenForActor",
            "replaceAuthoringContent",
            "listAuthoringMigrationCandidates",
            "backfillAuthoringMetadata",
            "authoringMetadataMatches",
            "rollbackAuthoringMetadata"
        );
        assertThat(source).contains(
            "model_spec_snapshot = cast(? as jsonb)",
            "projection_summary = cast(? as jsonb)",
            "last_modified_date = ?",
            "is not distinct from cast(? as jsonb)"
        );
    }
}
