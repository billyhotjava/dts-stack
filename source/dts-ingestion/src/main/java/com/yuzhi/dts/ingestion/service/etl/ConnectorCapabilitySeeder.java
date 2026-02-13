package com.yuzhi.dts.ingestion.service.etl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ConnectorCapabilitySeeder implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(ConnectorCapabilitySeeder.class);

    private final ConnectorCapabilityService connectorCapabilityService;

    public ConnectorCapabilitySeeder(ConnectorCapabilityService connectorCapabilityService) {
        this.connectorCapabilityService = connectorCapabilityService;
    }

    @Override
    public void run(ApplicationArguments args) {
        connectorCapabilityService.ensureDefaults();
        LOG.info("[ingestion] connector capabilities seeded");
    }
}
