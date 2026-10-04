package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ModelReleaseCandidateStableModelScopeLiquibaseTest {

    private static final String CHANGELOG =
        "/config/liquibase/changelog/20260904_01_model_release_candidate_stable_model_scope.xml";

    @Test
    void keepsHistoricalModeButBindsCandidateToStableModelIdentity() throws Exception {
        String xml = read(CHANGELOG);

        assertThat(xml)
            .contains("constraintName=\"uk_model_spec_release_identity_scope\"")
            .contains("columnNames=\"tenant_id, plan_id, id\"")
            .contains("constraintName=\"fk_model_release_candidate_entry_model_scope\"")
            .contains("baseColumnNames=\"tenant_id, plan_id, model_spec_id\"")
            .contains("referencedColumnNames=\"tenant_id, plan_id, id\"")
            .contains("onDelete=\"RESTRICT\"")
            .doesNotContain("dropColumn tableName=\"modeling_model_release_candidate_entry\" columnName=\"implementation_mode\"");
    }

    @Test
    void registersTheForwardChangeAfterExistingReleaseCandidateMigrations() throws Exception {
        String master = read("/config/liquibase/master.xml");

        assertThat(master).containsSubsequence(
            "config/liquibase/changelog/20260817_01_model_release_candidate_published_claim_release.xml",
            "config/liquibase/changelog/20260904_01_model_release_candidate_stable_model_scope.xml"
        );
    }

    private static String read(String path) throws Exception {
        try (var stream = ModelReleaseCandidateStableModelScopeLiquibaseTest.class.getResourceAsStream(path)) {
            assertThat(stream).as(path).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
