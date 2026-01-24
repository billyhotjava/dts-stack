package com.yuzhi.dts.ingestion.service.infra;

import com.yuzhi.dts.ingestion.service.etl.AirbyteClient;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AirbyteDefaultDestinationRegistrar {

    private static final Logger LOG = LoggerFactory.getLogger(AirbyteDefaultDestinationRegistrar.class);

    private final AirbyteClient airbyteClient;
    private final IngestionSettingsService settingsService;
    private final com.yuzhi.dts.ingestion.config.AirbyteProperties airbyteProperties;

    public AirbyteDefaultDestinationRegistrar(
        AirbyteClient airbyteClient,
        IngestionSettingsService settingsService,
        com.yuzhi.dts.ingestion.config.AirbyteProperties airbyteProperties
    ) {
        this.airbyteClient = airbyteClient;
        this.settingsService = settingsService;
        this.airbyteProperties = airbyteProperties;
    }

    public void registerIfMissing() {
        try {
            IngestionSettingsService.SettingsSnapshot settings = settingsService.getSettings(IngestionSettingsService.SERVICE_AIRBYTE);
            String image = settings.getString("defaultDestinationImage", airbyteProperties.getDefaultDestinationImage());
            if (!StringUtils.hasText(image)) {
                LOG.debug("[ingestion] default destination image not provided; skip auto-register");
                return;
            }
            String repo = image;
            String tag = "latest";
            int idx = image.lastIndexOf(':');
            if (idx > 0 && idx < image.length() - 1) {
                repo = image.substring(0, idx);
                tag = image.substring(idx + 1);
            }
            String name = settings.getString("defaultDestinationName", "dts-default-destination");
            String desiredId = settings.getString("defaultDestinationDefinitionId", null);
            List<Map<String, Object>> defs = airbyteClient
                .listDestinationDefinitions()
                .map(payload -> (List<Map<String, Object>>) payload.getOrDefault("destinationDefinitions", List.of()))
                .orElse(List.of());
            if (!defs.isEmpty()) {
                String found = findDefinitionId(defs, desiredId, name);
                if (StringUtils.hasText(found)) {
                    LOG.debug("[ingestion] default destination definition already exists: {}", found);
                    return;
                }
            }
            final String imageRef = image;
            final String nameRef = name;
            final String repoRef = repo;
            final String tagRef = tag;
            final IngestionSettingsService.SettingsSnapshot settingsSnapshot = settings;
            airbyteClient
                .createCustomDestinationDefinition(name, repo, tag)
                .ifPresent(def -> {
                    Object defIdObj = def.get("destinationDefinitionId");
                    String defId = defIdObj == null ? null : defIdObj.toString();
                    LOG.info("[ingestion] registered default destination definition id={} image={} tag={}", defId, repoRef, tagRef);
                    if (StringUtils.hasText(defId)) {
                        Map<String, Object> merged = new java.util.LinkedHashMap<>(settingsSnapshot.raw());
                        merged.put("defaultDestinationDefinitionId", defId);
                        merged.putIfAbsent("defaultDestinationName", nameRef);
                        merged.putIfAbsent("defaultDestinationImage", imageRef);
                        settingsService.upsertSettings(IngestionSettingsService.SERVICE_AIRBYTE, merged, "system");
                    }
                });
        } catch (Exception ex) {
            LOG.warn("[ingestion] failed to auto-register default destination: {}", ex.getMessage());
            LOG.debug("[ingestion] default destination registration failed", ex);
        }
    }

    private String findDefinitionId(List<Map<String, Object>> defs, String desiredId, String desiredName) {
        String normalizedName = StringUtils.hasText(desiredName) ? desiredName.trim().toLowerCase(Locale.ROOT) : null;
        for (Map<String, Object> def : defs) {
            Object id = def.get("destinationDefinitionId");
            String defId = id == null ? null : id.toString();
            if (StringUtils.hasText(desiredId) && desiredId.equals(defId)) {
                return defId;
            }
            Object name = def.get("name");
            String defName = name == null ? null : name.toString().trim().toLowerCase(Locale.ROOT);
            if (StringUtils.hasText(normalizedName) && normalizedName.equals(defName)) {
                return defId;
            }
        }
        return null;
    }
}
