package com.yuzhi.dts.ingestion;

import com.yuzhi.dts.ingestion.config.AddaxProperties;
import com.yuzhi.dts.ingestion.config.AirflowProperties;
import com.yuzhi.dts.ingestion.config.InfraSecurityProperties;
import com.yuzhi.dts.ingestion.config.IngestionOutboundPlatformProperties;
import com.yuzhi.dts.ingestion.config.IngestionProperties;
import com.yuzhi.dts.ingestion.config.OpenMetadataProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableConfigurationProperties({
    AddaxProperties.class,
    AirflowProperties.class,
    InfraSecurityProperties.class,
    IngestionOutboundPlatformProperties.class,
    IngestionProperties.class,
    OpenMetadataProperties.class
})
public class DtsIngestionApp {

    public static void main(String[] args) {
        SpringApplication.run(DtsIngestionApp.class, args);
    }
}
