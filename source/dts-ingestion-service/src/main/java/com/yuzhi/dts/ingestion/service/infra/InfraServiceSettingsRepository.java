package com.yuzhi.dts.ingestion.service.infra;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class InfraServiceSettingsRepository {

    private static final Logger LOG = LoggerFactory.getLogger(InfraServiceSettingsRepository.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final InfraSettingsCryptoService cryptoService;

    public InfraServiceSettingsRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, InfraSettingsCryptoService cryptoService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.cryptoService = cryptoService;
    }

    @PostConstruct
    public void ensureTable() {
        jdbcTemplate.execute(
            "create table if not exists infra_service_settings (" +
            "service varchar(64) primary key," +
            "settings_json text," +
            "secure_props bytea," +
            "secure_iv bytea," +
            "secure_key_version varchar(32)," +
            "updated_at timestamptz default now()," +
            "updated_by varchar(64)" +
            ")"
        );
    }

    public Optional<Map<String, Object>> findByService(String service) {
        if (!StringUtils.hasText(service)) {
            return Optional.empty();
        }
        String sql = "select settings_json, secure_props, secure_iv, secure_key_version from infra_service_settings where service = ?";
        return jdbcTemplate.query(sql, rs -> readSettings(rs, service), service.trim());
    }

    public void upsert(String service, Map<String, Object> settings, String updatedBy) {
        if (!StringUtils.hasText(service) || settings == null) {
            return;
        }
        String json = writeJson(settings);
        byte[] plain = json == null ? new byte[0] : json.getBytes(StandardCharsets.UTF_8);
        byte[] secureProps = null;
        byte[] secureIv = null;
        String keyVersion = null;
        String storeJson = json;
        if (cryptoService.isEncryptionReady()) {
            try {
                secureIv = cryptoService.randomIv();
                secureProps = cryptoService.encrypt(plain, secureIv);
                keyVersion = cryptoService.currentKeyVersion();
                storeJson = writeJson(maskSecrets(settings));
            } catch (Exception ex) {
                LOG.warn("Failed to encrypt settings for {}: {}", service, ex.getMessage());
            }
        } else {
            keyVersion = cryptoService.plaintextKeyVersion();
        }
        String sql =
            "insert into infra_service_settings (service, settings_json, secure_props, secure_iv, secure_key_version, updated_at, updated_by) " +
            "values (?, ?, ?, ?, ?, ?, ?) " +
            "on conflict (service) do update set settings_json = excluded.settings_json, secure_props = excluded.secure_props, " +
            "secure_iv = excluded.secure_iv, secure_key_version = excluded.secure_key_version, updated_at = excluded.updated_at, updated_by = excluded.updated_by";
        jdbcTemplate.update(sql, service.trim(), storeJson, secureProps, secureIv, keyVersion, Instant.now(), updatedBy);
    }

    private Optional<Map<String, Object>> readSettings(ResultSet rs, String service) {
        try {
            if (!rs.next()) {
                return Optional.empty();
            }
            byte[] secureProps = rs.getBytes("secure_props");
            byte[] secureIv = rs.getBytes("secure_iv");
            String keyVersion = rs.getString("secure_key_version");
            if (secureProps != null && secureProps.length > 0 && secureIv != null && secureIv.length > 0) {
                try {
                    byte[] plain = cryptoService.decrypt(secureProps, secureIv);
                    return Optional.of(objectMapper.readValue(new String(plain, StandardCharsets.UTF_8), MAP_TYPE));
                } catch (Exception ex) {
                    LOG.warn("Failed to decrypt settings for {}: {}", service, ex.getMessage());
                }
            } else if (StringUtils.hasText(keyVersion)) {
                LOG.debug("Settings for {} stored without encryption (keyVersion={})", service, keyVersion);
            }
            String json = rs.getString("settings_json");
            if (!StringUtils.hasText(json)) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, MAP_TYPE));
        } catch (Exception ex) {
            LOG.warn("Failed to read settings for {}: {}", service, ex.getMessage());
            return Optional.empty();
        }
    }

    private String writeJson(Map<String, Object> settings) {
        try {
            return objectMapper.writeValueAsString(settings);
        } catch (Exception ex) {
            return null;
        }
    }

    private Map<String, Object> maskSecrets(Map<String, Object> settings) {
        if (settings == null || settings.isEmpty()) {
            return settings;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        settings.forEach(
            (key, value) -> {
                if (key == null) {
                    return;
                }
                String lowered = key.toLowerCase();
                if (value instanceof Map<?, ?> nested) {
                    out.put(key, maskSecrets(new LinkedHashMap(nested)));
                } else if (lowered.contains("password") || lowered.contains("secret") || lowered.contains("token") || lowered.equals("key")) {
                    out.put(key, "******");
                } else {
                    out.put(key, value);
                }
            }
        );
        return out;
    }
}
