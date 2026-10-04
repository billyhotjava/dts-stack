package com.yuzhi.dts.copilot.analytics.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(EltSyncProperties.class)
@ConditionalOnProperty(name = "dts.elt.enabled", havingValue = "true")
public class EltSyncConfig {
}
