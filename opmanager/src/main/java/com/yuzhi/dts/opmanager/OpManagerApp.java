package com.yuzhi.dts.opmanager;

import com.yuzhi.dts.opmanager.config.OpManagerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(OpManagerProperties.class)
public class OpManagerApp {

    public static void main(String[] args) {
        SpringApplication.run(OpManagerApp.class, args);
    }
}
