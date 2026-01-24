package com.yuzhi.dts.ingestion.service.infra;

import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class IngestionSettingsService {

    public static final String SERVICE_AIRBYTE = "airbyte";
    public static final String SERVICE_OPENMETADATA = "openmetadata";
    public static final String SERVICE_AIRFLOW = "airflow";
    public static final String SERVICE_DBT = "dbt";

    private final InfraServiceSettingsRepository repository;

    public IngestionSettingsService(InfraServiceSettingsRepository repository) {
        this.repository = repository;
    }

    public SettingsSnapshot getSettings(String service) {
        return new SettingsSnapshot(repository.findByService(service).orElse(Map.of()));
    }

    public void upsertSettings(String service, Map<String, Object> settings, String updatedBy) {
        if (!StringUtils.hasText(service) || settings == null) {
            return;
        }
        repository.upsert(service, settings, StringUtils.hasText(updatedBy) ? updatedBy : "system");
    }

    public record SettingsSnapshot(Map<String, Object> raw) {
        public String getString(String key, String fallback) {
            String value = stringValue(key);
            return StringUtils.hasText(value) ? value : fallback;
        }

        public boolean getBoolean(String key, boolean fallback) {
            Object value = lookup(key);
            if (value == null) {
                return fallback;
            }
            if (value instanceof Boolean bool) {
                return bool;
            }
            String text = value.toString().trim();
            if (text.isEmpty()) {
                return fallback;
            }
            return Boolean.parseBoolean(text);
        }

        public Integer getInteger(String key, Integer fallback) {
            Object value = lookup(key);
            if (value == null) {
                return fallback;
            }
            if (value instanceof Number num) {
                return num.intValue();
            }
            try {
                return Integer.parseInt(value.toString().trim());
            } catch (Exception ex) {
                return fallback;
            }
        }

        public Map<String, Object> getMap(String key) {
            Object value = lookup(key);
            if (!(value instanceof Map<?, ?> map)) {
                return Map.of();
            }
            return new java.util.LinkedHashMap(map);
        }

        private String stringValue(String key) {
            Object value = lookup(key);
            if (value == null) {
                return null;
            }
            String text = value.toString().trim();
            return StringUtils.hasText(text) ? text : null;
        }

        private Object lookup(String key) {
            if (raw == null || raw.isEmpty() || key == null) {
                return null;
            }
            Object direct = raw.get(key);
            if (direct != null) {
                return direct;
            }
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                    return entry.getValue();
                }
            }
            return null;
        }
    }
}
