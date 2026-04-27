package com.yuzhi.dts.platform.service.infra;

import java.time.Instant;
import java.util.List;

/**
 * Sidecar metadata for API data-source secrets, persisted under
 * {@code props.__apiSecretMeta} as JSON. Plaintext secret values stay in the
 * existing {@code secure_props} encrypted blob via {@link InfraSecretService}.
 *
 * <p>Each {@link FieldEntry} records per-field rotation state (masked display,
 * version, rotation timestamp, lifecycle status) so the detail endpoint can
 * return a safe summary without ever decrypting and emitting plaintext secrets.
 */
public record ApiSecretMetadata(String providerId, List<FieldEntry> fields) {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_ROTATED = "ROTATED";
    public static final String STATUS_REVOKED = "REVOKED";

    public ApiSecretMetadata {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }

    public static ApiSecretMetadata empty() {
        return new ApiSecretMetadata(null, List.of());
    }

    public boolean isEmpty() {
        return fields.isEmpty();
    }

    public record FieldEntry(
        String fieldName,
        String maskedDisplay,
        String secretVersion,
        Instant rotatedAt,
        String status
    ) {}
}
