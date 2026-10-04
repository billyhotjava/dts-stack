package com.yuzhi.dts.admin;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.test.util.ReflectionTestUtils;
import tech.jhipster.config.JHipsterConstants;

class ApplicationWebXmlTest {

    @Test
    void configureRegistersMainApplicationAndDefaultProfile() {
        ApplicationWebXml webXml = new ApplicationWebXml();
        SpringApplicationBuilder builder = new SpringApplicationBuilder();

        SpringApplicationBuilder configured = webXml.configure(builder);
        SpringApplication application = configured.build();

        Object primarySources = ReflectionTestUtils.getField(application, "primarySources");
        assertThat(primarySources)
            .isNotNull()
            .asInstanceOf(InstanceOfAssertFactories.COLLECTION)
            .contains(DtsAdminApp.class);

        Object defaultProperties = ReflectionTestUtils.getField(application, "defaultProperties");
        assertThat(defaultProperties)
            .isNotNull()
            .asInstanceOf(InstanceOfAssertFactories.MAP)
            .containsEntry("spring.profiles.default", JHipsterConstants.SPRING_PROFILE_DEVELOPMENT);
    }
}
