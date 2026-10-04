package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PlatformModelGovernancePolicyLiquibaseTest {

    private static final Path SCHEMA_CHANGELOG = Path.of(
        "src/main/resources/config/liquibase/changelog/20260811_01_platform_model_governance_policy.xml"
    );
    private static final Path SEED_CHANGELOG = Path.of(
        "src/main/resources/config/liquibase/changelog/20260811_02_seed_platform_model_governance_policy.xml"
    );

    @Test
    void schemaMigrationCreatesOnePlatformGlobalPolicyOwnerAndIsReversible() throws Exception {
        String xml = Files.readString(SCHEMA_CHANGELOG);

        assertThat(xml)
            .contains("author=\"xiezm\"")
            .contains("modeling_platform_governance_policy")
            .contains("standard_coverage")
            .contains("quality_gate")
            .contains("ck_platform_model_governance_policy_key")
            .contains("ck_platform_model_governance_standard_coverage")
            .contains("ck_platform_model_governance_quality_gate")
            .contains("DROP TABLE modeling_platform_governance_policy")
            .doesNotContain("tenant_id", "plan_id", "modeling_warehouse_plan");
    }

    @Test
    void seedMigrationInstallsTheSafeDefaultAndCanRemoveOnlyThatSeed() throws Exception {
        String xml = Files.readString(SEED_CHANGELOG);

        assertThat(xml)
            .contains("author=\"xiezm\"")
            .contains("PLATFORM_DEFAULT")
            .contains("KEY_AND_MEASURE")
            .contains("BLOCKING")
            .contains("DELETE FROM modeling_platform_governance_policy")
            .doesNotContain("modeling_warehouse_plan");
    }

    @Test
    void platformMasterIncludesSchemaBeforeSeed() throws Exception {
        String master = Files.readString(Path.of("src/main/resources/config/liquibase/master.xml"));
        int schema = master.indexOf("20260811_01_platform_model_governance_policy.xml");
        int seed = master.indexOf("20260811_02_seed_platform_model_governance_policy.xml");

        assertThat(schema).isGreaterThanOrEqualTo(0);
        assertThat(seed).isGreaterThan(schema);
    }
}
