package com.yuzhi.dts.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import tech.jhipster.config.JHipsterConstants;

class ApplicationWebXmlTest {

    @Test
    void configureRegistersMainApplicationAndDefaultProfile() {
        ApplicationWebXml webXml = new ApplicationWebXml();
        SpringApplicationBuilder builder = new SpringApplicationBuilder();

        SpringApplicationBuilder configured = webXml.configure(builder);
        SpringApplication application = configured.application();

        assertThat(application.getAllSources()).contains(DtsAdminApp.class);

        Map<String, Object> defaultProperties = application.getDefaultProperties();
        assertThat(defaultProperties)
            .isNotNull()
            .containsEntry("spring.profiles.default", JHipsterConstants.SPRING_PROFILE_DEVELOPMENT);
    }
}
