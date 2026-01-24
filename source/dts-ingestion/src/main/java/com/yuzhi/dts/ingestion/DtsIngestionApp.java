package com.yuzhi.dts.ingestion;

import com.yuzhi.dts.ingestion.config.AirbyteProperties;
import com.yuzhi.dts.ingestion.config.AirflowProperties;
import com.yuzhi.dts.ingestion.config.InfraSecurityProperties;
import com.yuzhi.dts.ingestion.config.IngestionProperties;
import com.yuzhi.dts.ingestion.config.OpenMetadataProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
    AirbyteProperties.class,
    AirflowProperties.class,
    InfraSecurityProperties.class,
    IngestionProperties.class,
    OpenMetadataProperties.class
})
public class DtsIngestionApp {

    public static void main(String[] args) {
        SpringApplication.run(DtsIngestionApp.class, args);
    }
}
