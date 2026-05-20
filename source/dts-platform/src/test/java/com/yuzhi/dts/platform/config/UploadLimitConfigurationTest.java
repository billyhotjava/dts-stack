package com.yuzhi.dts.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class UploadLimitConfigurationTest {

    private static final Path APPLICATION_YML = Path.of("src/main/resources/config/application.yml");

    @Test
    void undertowPostLimitUsesSpringBoot34PropertyName() throws IOException {
        String source = Files.readString(APPLICATION_YML);

        assertThat(source).contains("max-http-post-size: ${DATA_STANDARD_MAX_FILE_SIZE:209715200}");
        assertThat(source).doesNotContain("max-http-form-post-size");
    }
}
