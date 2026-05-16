package com.yuzhi.dts.metrics;

import com.yuzhi.dts.metrics.config.DtsMetricsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(DtsMetricsProperties.class)
public class DtsMetricsApp {

    public static void main(String[] args) {
        SpringApplication.run(DtsMetricsApp.class, args);
    }
}
