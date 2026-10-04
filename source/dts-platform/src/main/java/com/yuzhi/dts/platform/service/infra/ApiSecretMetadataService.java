package com.yuzhi.dts.platform.service.infra;

import com.yuzhi.dts.platform.service.infra.dto.ApiSecretSummary;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Computes and persists sidecar metadata for API data-source secrets.
 *
 * <p>Plaintext secrets are encrypted by {@link InfraSecretService} into the
 * {@code secure_props} blob; this service maintains a parallel {@code __apiSecretMeta}
 * entry inside {@code props} so the detail endpoint can render rotation state
 * (masked display / version / rotated_at / status) without ever decrypting
 * plaintext for callers.
 */
@Service
public class ApiSecretMetadataService {

    public static final String PROPS_METADATA_KEY = "__apiSecretMeta";

    private static final String PROVIDER_NONE = "none";
    private static final Map<String, List<String>> PROVIDER_SENSITIVE_FIELDS = Map.of(
        "none", List.of(),
        "apikey", List.of("value"),
        "bearertoken", List.of("token"),
        "basic", List.of("password"),
        "oauth2clientcredentials", List.of("clientSecret"),
        "jwtlogin", List.of("password", "secret", "clientSecret"),
        "customsignature", List.of("secret"),
        "mtls", List.of("certSecretRef", "keySecretRef")
    );

    private final Clock clock;

    public ApiSecretMetadataService() {
        this(Clock.systemUTC());
    }

    public ApiSecretMetadataService(Clock clock) {
        this.clock = clock;
    }

    /**
     * Compute the next metadata snapshot given an incoming provider, secrets map and
     * the previously persisted metadata (may be {@code null}).
     *
     * <ul>
     *   <li>Fields not present in the provider's sensitive-field list are ignored.</li>
     *   <li>Fields previously persisted but not provided in {@code incomingSecrets}
     *       are preserved with their existing version / rotatedAt / status.</li>
     *   <li>If the provider id changes, all field versions reset to {@code v1}.</li>
     *   <li>Newly provided fields start at {@code v1}; updated fields bump version.</li>
     * </ul>
     */
    public ApiSecretMetadata computeMetadata(
        String providerId,
        Map<String, Object> incomingSecrets,
        ApiSecretMetadata previous
    ) {
        String normalizedProvider = normalizeProviderId(providerId);
        List<String> sensitiveFields = resolveSensitiveFields(normalizedProvider, incomingSecrets, previous);
        if (sensitiveFields.isEmpty()) {
            return new ApiSecretMetadata(canonicalProviderId(providerId), List.of());
        }

        boolean providerChanged = previous != null
            && previous.providerId() != null
            && !normalizeProviderId(previous.providerId()).equals(normalizedProvider);

        Map<String, ApiSecretMetadata.FieldEntry> previousByField = new LinkedHashMap<>();
        if (!providerChanged && previous != null) {
            for (ApiSecretMetadata.FieldEntry entry : previous.fields()) {
                if (entry != null && StringUtils.hasText(entry.fieldName())) {
                    previousByField.put(entry.fieldName(), entry);
                }
            }
        }

        Instant now = Instant.now(clock);
        List<ApiSecretMetadata.FieldEntry> nextFields = new ArrayList<>();
        Set<String> handled = new LinkedHashSet<>();

        for (String fieldName : sensitiveFields) {
            handled.add(fieldName);
            String plain = extractPlaintext(incomingSecrets, fieldName);
            ApiSecretMetadata.FieldEntry prevEntry = previousByField.get(fieldName);

            if (StringUtils.hasText(plain)) {
                int nextVersionNumber = prevEntry == null ? 1 : parseVersion(prevEntry.secretVersion()) + 1;
                nextFields.add(new ApiSecretMetadata.FieldEntry(
                    fieldName,
                    maskedDisplay(plain),
                    "v" + nextVersionNumber,
                    now,
                    ApiSecretMetadata.STATUS_ACTIVE
                ));
            } else if (prevEntry != null) {
                // Field omitted in this update — keep existing entry intact
                nextFields.add(prevEntry);
            }
            // else: nothing previously stored and nothing supplied — skip
        }

        return new ApiSecretMetadata(canonicalProviderId(providerId), nextFields);
    }

    private static List<String> resolveSensitiveFields(
        String normalizedProvider,
        Map<String, Object> incomingSecrets,
        ApiSecretMetadata previous
    ) {
        List<String> configured = PROVIDER_SENSITIVE_FIELDS.getOrDefault(normalizedProvider, List.of());
        if (!"jwtlogin".equals(normalizedProvider)) {
            return configured;
        }
        Set<String> fields = new LinkedHashSet<>(configured);
        if (incomingSecrets != null) {
            incomingSecrets.forEach((key, value) -> {
                if (StringUtils.hasText(key) && StringUtils.hasText(asText(value))) {
                    fields.add(key);
                }
            });
        }
        if (previous != null) {
            previous.fields().forEach(entry -> {
                if (entry != null && StringUtils.hasText(entry.fieldName())) {
                    fields.add(entry.fieldName());
                }
            });
        }
        return List.copyOf(fields);
    }

    /**
     * Return per-field summaries safe to include in the detail response.
     */
    public List<ApiSecretSummary> toSummaries(ApiSecretMetadata metadata) {
        if (metadata == null || metadata.fields().isEmpty()) {
            return List.of();
        }
        List<ApiSecretSummary> summaries = new ArrayList<>(metadata.fields().size());
        for (ApiSecretMetadata.FieldEntry entry : metadata.fields()) {
            if (entry == null) {
                continue;
            }
            summaries.add(new ApiSecretSummary(
                metadata.providerId(),
                entry.fieldName(),
                entry.maskedDisplay(),
                entry.secretVersion(),
                entry.rotatedAt(),
                entry.status()
            ));
        }
        return List.copyOf(summaries);
    }

    /**
     * Write metadata into the props map under {@link #PROPS_METADATA_KEY}. Returns the
     * same map for chaining. When metadata is empty the key is removed so JDBC / file
     * data sources never carry the sidecar.
     */
    public Map<String, Object> writeIntoProps(Map<String, Object> props, ApiSecretMetadata metadata) {
        Objects.requireNonNull(props, "props");
        if (metadata == null || metadata.isEmpty()) {
            props.remove(PROPS_METADATA_KEY);
            return props;
        }
        Map<String, Object> serialized = new LinkedHashMap<>();
        serialized.put("providerId", metadata.providerId());
        List<Map<String, Object>> serializedFields = new ArrayList<>(metadata.fields().size());
        for (ApiSecretMetadata.FieldEntry entry : metadata.fields()) {
            Map<String, Object> entryMap = new LinkedHashMap<>();
            entryMap.put("fieldName", entry.fieldName());
            entryMap.put("maskedDisplay", entry.maskedDisplay());
            entryMap.put("secretVersion", entry.secretVersion());
            entryMap.put("rotatedAt", entry.rotatedAt() == null ? null : entry.rotatedAt().toString());
            entryMap.put("status", entry.status());
            serializedFields.add(entryMap);
        }
        serialized.put("fields", serializedFields);
        props.put(PROPS_METADATA_KEY, serialized);
        return props;
    }

    /**
     * Read previously persisted metadata back from props. Returns
     * {@link ApiSecretMetadata#empty()} when absent or malformed.
     */
    public ApiSecretMetadata readFromProps(Map<String, Object> props) {
        if (props == null) {
            return ApiSecretMetadata.empty();
        }
        Object raw = props.get(PROPS_METADATA_KEY);
        if (!(raw instanceof Map<?, ?> map)) {
            return ApiSecretMetadata.empty();
        }
        String providerId = asText(map.get("providerId"));
        Object rawFields = map.get("fields");
        List<ApiSecretMetadata.FieldEntry> entries = new ArrayList<>();
        if (rawFields instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (!(item instanceof Map<?, ?> entryMap)) {
                    continue;
                }
                String fieldName = asText(entryMap.get("fieldName"));
                if (!StringUtils.hasText(fieldName)) {
                    continue;
                }
                String maskedDisplay = asText(entryMap.get("maskedDisplay"));
                String secretVersion = asText(entryMap.get("secretVersion"));
                String status = asText(entryMap.get("status"));
                Instant rotatedAt = parseInstant(entryMap.get("rotatedAt"));
                entries.add(new ApiSecretMetadata.FieldEntry(
                    fieldName,
                    maskedDisplay,
                    secretVersion,
                    rotatedAt,
                    status == null ? ApiSecretMetadata.STATUS_ACTIVE : status
                ));
            }
        }
        return new ApiSecretMetadata(providerId, entries);
    }

    static String maskedDisplay(String plain) {
        if (plain == null) {
            return null;
        }
        int length = plain.length();
        if (length == 0) {
            return "";
        }
        if (length < 4) {
            return "***";
        }
        if (length < 8) {
            return plain.charAt(0) + "***";
        }
        return plain.substring(0, 2) + "***" + plain.substring(length - 4);
    }

    private static int parseVersion(String version) {
        if (!StringUtils.hasText(version)) {
            return 0;
        }
        String trimmed = version.trim();
        if (trimmed.startsWith("v") || trimmed.startsWith("V")) {
            trimmed = trimmed.substring(1);
        }
        try {
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static String normalizeProviderId(String providerId) {
        if (!StringUtils.hasText(providerId)) {
            return PROVIDER_NONE;
        }
        return providerId.trim().toLowerCase(Locale.ROOT);
    }

    private static String canonicalProviderId(String providerId) {
        if (!StringUtils.hasText(providerId)) {
            return PROVIDER_NONE;
        }
        return providerId.trim();
    }

    private static String extractPlaintext(Map<String, Object> secrets, String fieldName) {
        if (secrets == null || secrets.isEmpty() || !StringUtils.hasText(fieldName)) {
            return null;
        }
        Object value = secrets.get(fieldName);
        return value == null ? null : value.toString();
    }

    private static String asText(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return StringUtils.hasText(text) ? text : null;
    }

    private static Instant parseInstant(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value.toString());
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
