package com.yuzhi.dts.ingestion.service.infra;

import com.yuzhi.dts.ingestion.config.AirbyteProperties;
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
    private final AirbyteProperties airbyteProperties;
    private final AirflowProperties airflowProperties;
    private final OpenMetadataProperties openMetadataProperties;
    private final AirbyteDefaultDestinationRegistrar defaultDestinationRegistrar;

    public IngestionSettingsSeeder(
        InfraServiceSettingsRepository repository,
        AirbyteProperties airbyteProperties,
        AirflowProperties airflowProperties,
        OpenMetadataProperties openMetadataProperties,
        AirbyteDefaultDestinationRegistrar defaultDestinationRegistrar
    ) {
        this.repository = repository;
        this.airbyteProperties = airbyteProperties;
        this.airflowProperties = airflowProperties;
        this.openMetadataProperties = openMetadataProperties;
        this.defaultDestinationRegistrar = defaultDestinationRegistrar;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedIfMissing(IngestionSettingsService.SERVICE_AIRBYTE, buildAirbyteSettings());
        seedIfMissing(IngestionSettingsService.SERVICE_AIRFLOW, buildAirflowSettings());
        seedIfMissing(IngestionSettingsService.SERVICE_OPENMETADATA, buildOpenMetadataSettings());
        defaultDestinationRegistrar.registerIfMissing();
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

    private Map<String, Object> buildAirbyteSettings() {
        Map<String, Object> settings = new LinkedHashMap<>();
        putIfText(settings, "workspaceId", airbyteProperties.getWorkspaceId());
        putIfText(settings, "organizationId", airbyteProperties.getOrganizationId());
        putIfText(settings, "authUserId", airbyteProperties.getAuthUserId());
        putIfText(settings, "organizationName", airbyteProperties.getOrganizationName());
        putIfText(settings, "defaultDestinationId", airbyteProperties.getDefaultDestinationId());
        putIfText(settings, "defaultDestinationName", airbyteProperties.getDefaultDestinationName());
        putIfText(settings, "defaultDestinationDefinitionId", airbyteProperties.getDefaultDestinationDefinitionId());
        putIfText(settings, "defaultDestinationConfigJson", airbyteProperties.getDefaultDestinationConfigJson());
        putIfText(settings, "defaultDestinationImage", airbyteProperties.getDefaultDestinationImage());
        return settings;
    }

    private Map<String, Object> buildAirflowSettings() {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("enabled", airflowProperties.isEnabled());
        putIfText(settings, "dagId", airflowProperties.getDagId());
        return settings;
    }

    private Map<String, Object> buildOpenMetadataSettings() {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("enabled", openMetadataProperties.isEnabled());
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
