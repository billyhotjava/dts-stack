package com.yuzhi.dts.metrics.config;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfiguration {

    /**
     * RestClient used for all dts-platform internal contract calls. Connect/read timeouts (Sprint-35b F4-T01)
     * keep a slow or hung platform from blocking metrics requests indefinitely; on timeout the call surfaces as
     * a {@code PlatformContractException} -> 503, preserving the existing fail-closed semantics.
     */
    @Bean
    RestClient restClient(RestClient.Builder builder, DtsMetricsProperties properties) {
        DtsMetricsProperties.Platform platform = properties.getPlatform();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(platform.getConnectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(platform.getReadTimeoutMs()));
        return builder.requestFactory(factory).build();
    }
}
