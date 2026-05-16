package com.yuzhi.dts.platform.config.metrics;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DtsMetricsCapabilityProperties.class)
public class DtsMetricsCapabilityConfiguration {}
