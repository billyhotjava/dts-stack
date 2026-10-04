package com.yuzhi.dts.ingestion.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

class ApiPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
        .withUserConfiguration(Config.class);

    @Test
    void bindsDefaultsAndOverridesForApiRuntime() {
        contextRunner
            .withPropertyValues(
                "dts.ingestion.api.max-pages=25",
                "dts.ingestion.api.max-response-bytes=64MB",
                "dts.ingestion.api.connect-timeout=3s",
                "dts.ingestion.api.read-timeout=45s",
                "dts.ingestion.api.execution-timeout=90s",
                "dts.ingestion.api.retry.max-retries=5",
                "dts.ingestion.api.retry.backoff-cap=20s",
                "dts.ingestion.api.rate-limit.default-rps=8",
                "dts.ingestion.api.landing.batch-size=200",
                "dts.ingestion.api.table-prefix=ods_ext_",
                "dts.ingestion.api.allow-http=true",
                "dts.ingestion.api.allowed-hosts[0]=api.example.test"
            )
            .run(context -> {
                assertThat(context).hasSingleBean(ApiProperties.class);
                ApiProperties properties = context.getBean(ApiProperties.class);
                assertThat(properties.getMaxPages()).isEqualTo(25);
                assertThat(properties.getMaxResponseBytes()).isEqualTo(DataSize.ofMegabytes(64));
                assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(3));
                assertThat(properties.getReadTimeout()).isEqualTo(Duration.ofSeconds(45));
                assertThat(properties.getExecutionTimeout()).isEqualTo(Duration.ofSeconds(90));
                assertThat(properties.getRetry().getMaxRetries()).isEqualTo(5);
                assertThat(properties.getRetry().getBackoffCap()).isEqualTo(Duration.ofSeconds(20));
                assertThat(properties.getRateLimit().getDefaultRps()).isEqualTo(8);
                assertThat(properties.getLanding().getBatchSize()).isEqualTo(200);
                assertThat(properties.getTablePrefix()).isEqualTo("ods_ext_");
                assertThat(properties.isAllowHttp()).isTrue();
                assertThat(properties.getAllowedHosts()).containsExactly("api.example.test");
            });
    }

    @Test
    void rejectsInvalidNegativeRuntimeValues() {
        contextRunner
            .withPropertyValues("dts.ingestion.api.max-pages=-1")
            .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ApiProperties.class)
    static class Config {}
}
