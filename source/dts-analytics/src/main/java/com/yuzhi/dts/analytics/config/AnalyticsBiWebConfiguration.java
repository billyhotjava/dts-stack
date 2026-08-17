package com.yuzhi.dts.analytics.config;

import com.yuzhi.dts.analytics.web.interceptor.BiObservabilityInterceptor;
import com.yuzhi.dts.analytics.web.interceptor.GovernedBiFeatureInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AnalyticsBiWebConfiguration implements WebMvcConfigurer {

    private final BiObservabilityInterceptor observability;
    private final GovernedBiFeatureInterceptor featureGate;

    public AnalyticsBiWebConfiguration(
        BiObservabilityInterceptor observability,
        GovernedBiFeatureInterceptor featureGate
    ) {
        this.observability = observability;
        this.featureGate = featureGate;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(observability).addPathPatterns(
            "/api/analysis", "/api/analysis/**", "/api/card", "/api/card/**", "/api/dataset", "/api/dataset/**",
            "/api/dashboard", "/api/dashboard/**", "/api/semantic", "/api/semantic/**"
        );
        registry.addInterceptor(featureGate).addPathPatterns(
            "/api/analysis", "/api/analysis/**", "/api/dashboard/**"
        );
    }
}
