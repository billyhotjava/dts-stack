package com.yuzhi.dts.copilot.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsSetting;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsSettingRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class EmbedTokenService {

    public static final String EMBEDDING_ENABLED_KEY = "enable-embedding";
    public static final String EMBEDDING_SECRET_KEY = "embedding-secret-key";
    public static final String EMBEDDING_PARAMS_KEY = "embedding-params";

    private final AnalyticsSettingRepository settingRepository;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public EmbedTokenService(AnalyticsSettingRepository settingRepository, ObjectMapper objectMapper) {
        this.settingRepository = settingRepository;
        this.objectMapper = objectMapper;
    }

    public boolean isEmbeddingEnabled() {
        return settingRepository
                .findById(EMBEDDING_ENABLED_KEY)
                .map(AnalyticsSetting::getSettingValue)
                .map(String::trim)
                .map(v -> "true".equalsIgnoreCase(v) || "1".equals(v))
                .orElse(false);
    }

    public void setEmbeddingEnabled(boolean enabled) {
        putSetting(EMBEDDING_ENABLED_KEY, enabled ? "true" : "false");
    }

    public Optional<String> getSecretKey() {
        return settingRepository
                .findById(EMBEDDING_SECRET_KEY)
                .map(AnalyticsSetting::getSettingValue)
                .map(String::trim)
                .filter(v -> !v.isEmpty());
    }

    public String getOrCreateSecretKey() {
        return getSecretKey().orElseGet(() -> {
            String generated = generateSecretKey();
            putSetting(EMBEDDING_SECRET_KEY, generated);
            return generated;
        });
    }

    public String rotateSecretKey() {
        String generated = generateSecretKey();
        putSetting(EMBEDDING_SECRET_KEY, generated);
        return generated;
    }

    public void setSecretKey(String secretKey) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalArgumentException("secret_key must be a non-blank string");
        }
        putSetting(EMBEDDING_SECRET_KEY, secretKey);
    }

    public Object embeddingParamsOrNull() {
        return settingRepository.findById(EMBEDDING_PARAMS_KEY).map(AnalyticsSetting::getSettingValue).flatMap(raw -> {
            if (raw == null || raw.isBlank()) {
                return Optional.empty();
            }
            try {
                JsonNode node = objectMapper.readTree(raw);
                return Optional.ofNullable(node == null || node.isNull() ? null : node);
            } catch (Exception e) {
                return Optional.empty();
            }
        }).orElse(null);
    }

    public void setEmbeddingParams(JsonNode params) {
        if (params == null || params.isNull()) {
            putSetting(EMBEDDING_PARAMS_KEY, "null");
            return;
        }
        putSetting(EMBEDDING_PARAMS_KEY, params.toString());
    }

    public String signJwtHs256(Map<String, Object> payload) {
        String secret = getSecretKey().orElseThrow(() -> new IllegalStateException("Embedding secret key is not configured"));
        return signJwtHs256(payload, secret);
    }

    public String signJwtHs256(Map<String, Object> payload, String secret) {
        try {
            Map<String, Object> header = new LinkedHashMap<>();
            header.put("alg", "HS256");
            header.put("typ", "JWT");

            String headerJson = objectMapper.writeValueAsString(header);
            String payloadJson = objectMapper.writeValueAsString(payload);

            String headerB64 = base64Url(headerJson.getBytes(StandardCharsets.UTF_8));
            String payloadB64 = base64Url(payloadJson.getBytes(StandardCharsets.UTF_8));
            String signingInput = headerB64 + "." + payloadB64;

            byte[] signature = hmacSha256(secret.getBytes(StandardCharsets.UTF_8), signingInput.getBytes(StandardCharsets.UTF_8));
            return signingInput + "." + base64Url(signature);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign JWT", e);
        }
    }

    public JsonNode verifyAndDecodeJwtHs256(String jwt) {
        String secret = getSecretKey().orElseThrow(() -> new IllegalStateException("Embedding secret key is not configured"));
        return verifyAndDecodeJwtHs256(jwt, secret);
    }

    public JsonNode verifyAndDecodeJwtHs256(String jwt, String secret) {
        try {
            String[] parts = jwt == null ? new String[0] : jwt.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("invalid jwt");
            }
            String headerB64 = parts[0];
            String payloadB64 = parts[1];
            String sigB64 = parts[2];
            String signingInput = headerB64 + "." + payloadB64;

            byte[] expected = hmacSha256(secret.getBytes(StandardCharsets.UTF_8), signingInput.getBytes(StandardCharsets.UTF_8));
            byte[] provided = Base64.getUrlDecoder().decode(sigB64);
            if (!MessageDigest.isEqual(expected, provided)) {
                throw new IllegalArgumentException("invalid jwt signature");
            }

            byte[] payloadBytes = Base64.getUrlDecoder().decode(payloadB64);
            JsonNode payload = objectMapper.readTree(payloadBytes);
            if (payload != null && payload.has("exp") && payload.get("exp").canConvertToLong()) {
                long exp = payload.get("exp").asLong();
                long now = Instant.now().getEpochSecond();
                if (now > exp) {
                    throw new IllegalArgumentException("jwt expired");
                }
            }
            return payload;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid jwt", e);
        }
    }

    public long defaultPreviewExpiryEpochSeconds() {
        return Instant.now().plusSeconds(3600).getEpochSecond();
    }

    private void putSetting(String key, String value) {
        AnalyticsSetting setting = settingRepository.findById(key).orElseGet(AnalyticsSetting::new);
        setting.setSettingKey(key);
        setting.setSettingValue(value == null ? "null" : value);
        settingRepository.save(setting);
    }

    private String generateSecretKey() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private static byte[] hmacSha256(byte[] secret, byte[] input) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return mac.doFinal(input);
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
