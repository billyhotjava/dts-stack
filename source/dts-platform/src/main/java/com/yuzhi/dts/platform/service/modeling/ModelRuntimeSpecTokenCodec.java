package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.config.ModelMaterializationProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Deterministic, restart-safe opaque token for one durable dispatch.
 *
 * <p>Only a SHA-256 digest is persisted. The raw token can be reproduced after a process restart
 * from the server-held signing key and immutable dispatch id, so retry never needs plaintext token
 * storage or a new DagRun id.
 */
@Component
public class ModelRuntimeSpecTokenCodec {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String TOKEN_VERSION = "v1";
    private final ModelMaterializationProperties properties;

    public ModelRuntimeSpecTokenCodec(
        ModelMaterializationProperties properties
    ) {
        this.properties = Objects.requireNonNull(
            properties,
            "properties is required"
        );
    }

    public IssuedToken issue(UUID dispatchId, Instant issuedAt) {
        Objects.requireNonNull(dispatchId, "dispatchId is required");
        Objects.requireNonNull(issuedAt, "issuedAt is required");
        Duration ttl = properties.getRuntimeSpecTokenTtl();
        if (
            ttl == null ||
            ttl.isNegative() ||
            ttl.isZero() ||
            ttl.compareTo(Duration.ofHours(1)) > 0
        ) {
            throw new IllegalStateException(
                "Runtime spec token TTL must be between 1 second and 1 hour"
            );
        }
        String token = token(dispatchId);
        return new IssuedToken(
            token,
            digest(token),
            issuedAt.plus(ttl)
        );
    }

    public IssuedToken restore(
        UUID dispatchId,
        Instant expiresAt
    ) {
        Objects.requireNonNull(dispatchId, "dispatchId is required");
        Objects.requireNonNull(expiresAt, "expiresAt is required");
        String token = token(dispatchId);
        return new IssuedToken(token, digest(token), expiresAt);
    }

    public boolean matches(
        UUID dispatchId,
        String suppliedToken,
        String expectedDigest
    ) {
        if (
            dispatchId == null ||
            suppliedToken == null ||
            expectedDigest == null
        ) {
            return false;
        }
        IssuedToken expected = restore(
            dispatchId,
            Instant.EPOCH
        );
        return (
            MessageDigest.isEqual(
                expected.token().getBytes(StandardCharsets.UTF_8),
                suppliedToken.trim().getBytes(StandardCharsets.UTF_8)
            ) &&
            MessageDigest.isEqual(
                expected.digest().getBytes(StandardCharsets.US_ASCII),
                expectedDigest.trim().getBytes(StandardCharsets.US_ASCII)
            )
        );
    }

    public String tokenDigest(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException(
                "Runtime spec token is required"
            );
        }
        return digest(token.trim());
    }

    private String token(UUID dispatchId) {
        String key = properties.getRuntimeSpecSigningKey();
        if (key == null || key.trim().length() < 32) {
            throw new IllegalStateException(
                "Runtime spec signing key must contain at least 32 characters"
            );
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(
                new SecretKeySpec(
                    key.trim().getBytes(StandardCharsets.UTF_8),
                    HMAC_ALGORITHM
                )
            );
            byte[] signature = mac.doFinal(
                (
                    "dts:model-runtime-spec:" +
                    TOKEN_VERSION +
                    ":" +
                    dispatchId
                ).getBytes(StandardCharsets.UTF_8)
            );
            return (
                TOKEN_VERSION +
                "." +
                Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(signature)
            );
        } catch (Exception failure) {
            throw new IllegalStateException(
                "Runtime spec token could not be generated",
                failure
            );
        }
    }

    private static String digest(String token) {
        try {
            return (
                "sha256:" +
                HexFormat.of()
                    .formatHex(
                        MessageDigest.getInstance("SHA-256")
                            .digest(
                                token.getBytes(StandardCharsets.UTF_8)
                            )
                    )
            );
        } catch (Exception failure) {
            throw new IllegalStateException(
                "Runtime spec token digest could not be generated",
                failure
            );
        }
    }

    public record IssuedToken(
        String token,
        String digest,
        Instant expiresAt
    ) {
        public IssuedToken {
            if (
                token == null ||
                token.isBlank() ||
                digest == null ||
                !digest.matches("^sha256:[0-9a-f]{64}$") ||
                expiresAt == null
            ) {
                throw new IllegalArgumentException(
                    "Issued runtime spec token is invalid"
                );
            }
        }
    }
}
