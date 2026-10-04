package com.yuzhi.dts.copilot.analytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DtsAnalyticsApp {

    public static void main(String[] args) {
        SpringApplication.run(DtsAnalyticsApp.class, args);
    }
}
