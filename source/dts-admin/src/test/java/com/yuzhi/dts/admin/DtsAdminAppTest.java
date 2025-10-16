package com.yuzhi.dts.admin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tech.jhipster.config.JHipsterConstants;

@ExtendWith(OutputCaptureExtension.class)
class DtsAdminAppTest {

    @Test
    void initApplicationLogsErrorWhenDevAndProdProfilesActive(CapturedOutput output) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(JHipsterConstants.SPRING_PROFILE_DEVELOPMENT, JHipsterConstants.SPRING_PROFILE_PRODUCTION);

        new DtsAdminApp(environment).initApplication();

        assertThat(output.getErr())
            .contains("You have misconfigured your application! It should not run with both the 'dev' and 'prod' profiles at the same time.");
    }

    @Test
    void initApplicationSucceedsWhenProfilesDoNotConflict(CapturedOutput output) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(JHipsterConstants.SPRING_PROFILE_PRODUCTION);

        new DtsAdminApp(environment).initApplication();

        assertThat(output.getErr()).doesNotContain("You have misconfigured your application!");
    }
}
