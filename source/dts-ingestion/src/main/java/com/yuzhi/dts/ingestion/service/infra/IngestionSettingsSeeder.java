package com.yuzhi.dts.ingestion.service.infra;

import com.yuzhi.dts.ingestion.config.AddaxProperties;
import com.yuzhi.dts.ingestion.config.AirflowProperties;
import com.yuzhi.dts.ingestion.config.OpenMetadataProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class IngestionSettingsSeeder implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(IngestionSettingsSeeder.class);

    private final InfraServiceSettingsRepository repository;
    private final AddaxProperties addaxProperties;
    private final AirflowProperties airflowProperties;
    private final OpenMetadataProperties openMetadataProperties;

    public IngestionSettingsSeeder(
        InfraServiceSettingsRepository repository,
        AddaxProperties addaxProperties,
        AirflowProperties airflowProperties,
        OpenMetadataProperties openMetadataProperties
    ) {
        this.repository = repository;
        this.addaxProperties = addaxProperties;
        this.airflowProperties = airflowProperties;
        this.openMetadataProperties = openMetadataProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedIfMissing(IngestionSettingsService.SERVICE_ADDAX, buildAddaxSettings());
        seedIfMissing(IngestionSettingsService.SERVICE_AIRFLOW, buildAirflowSettings());
        seedIfMissing(IngestionSettingsService.SERVICE_OPENMETADATA, buildOpenMetadataSettings());
    }

    private void seedIfMissing(String service, Map<String, Object> settings) {
        if (repository.findByService(service).isPresent()) {
            return;
        }
        if (settings == null || settings.isEmpty()) {
            return;
        }
        repository.upsert(service, settings, "system");
        LOG.info("[ingestion] Seeded settings for service={} keys={}", service, settings.keySet());
    }

    private Map<String, Object> buildAddaxSettings() {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("enabled", addaxProperties.isEnabled());
        putIfText(settings, "jobDir", addaxProperties.getJobDir());
        putIfText(settings, "image", addaxProperties.getImage());
        putIfText(settings, "dagId", addaxProperties.getDagId());
        putIfText(settings, "defaultWriterType", addaxProperties.getDefaultWriterType());
        putIfText(settings, "defaultWriterJdbcUrl", addaxProperties.getDefaultWriterJdbcUrl());
        putIfText(settings, "defaultWriterUsername", addaxProperties.getDefaultWriterUsername());
        putIfText(settings, "defaultWriterPassword", addaxProperties.getDefaultWriterPassword());
        putIfText(settings, "defaultWriterSchema", addaxProperties.getDefaultWriterSchema());
        return settings;
    }

    private Map<String, Object> buildAirflowSettings() {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("enabled", airflowProperties.isEnabled());
        putIfText(settings, "baseUrl", airflowProperties.getBaseUrl());
        putIfText(settings, "apiPath", airflowProperties.getApiPath());
        putIfText(settings, "username", airflowProperties.getUsername());
        putIfText(settings, "password", airflowProperties.getPassword());
        putIfText(settings, "dagId", airflowProperties.getDagId());
        putIfText(settings, "dagsDir", airflowProperties.getDagsDir());
        return settings;
    }

    private Map<String, Object> buildOpenMetadataSettings() {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("enabled", openMetadataProperties.isEnabled());
        putIfText(settings, "baseUrl", openMetadataProperties.getBaseUrl());
        putIfText(settings, "apiPath", openMetadataProperties.getApiPath());
        putIfText(settings, "authToken", openMetadataProperties.getAuthToken());
        putIfText(settings, "sourceServiceName", openMetadataProperties.getSourceServiceName());
        putIfText(settings, "sourceServiceType", openMetadataProperties.getSourceServiceType());
        putIfText(settings, "destinationServiceName", openMetadataProperties.getDestinationServiceName());
        putIfText(settings, "destinationServiceType", openMetadataProperties.getDestinationServiceType());
        putIfText(settings, "destinationDatabase", openMetadataProperties.getDestinationDatabase());
        putIfText(settings, "destinationSchema", openMetadataProperties.getDestinationSchema());
        putIfText(settings, "sourceDatabase", openMetadataProperties.getSourceDatabase());
        putIfText(settings, "sourceSchema", openMetadataProperties.getSourceSchema());
        settings.put("ingestionEnabled", openMetadataProperties.isIngestionEnabled());
        putIfText(settings, "ingestionPrefix", openMetadataProperties.getIngestionPipelinePrefix());
        putIfText(settings, "ingestionSchedule", openMetadataProperties.getIngestionDefaultSchedule());
        putIfText(settings, "tableFields", openMetadataProperties.getTableFields());
        return settings;
    }

    private void putIfText(Map<String, Object> settings, String key, String value) {
        if (StringUtils.hasText(value)) {
            settings.put(key, value.trim());
        }
    }
}
