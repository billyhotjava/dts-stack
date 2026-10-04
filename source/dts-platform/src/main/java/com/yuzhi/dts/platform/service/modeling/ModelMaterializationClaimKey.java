package com.yuzhi.dts.platform.service.modeling;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** Stable cross-plan claim identity for one tenant, environment and ModelSpec. */
public final class ModelMaterializationClaimKey {

    private ModelMaterializationClaimKey() {}

    public static String derive(
        String tenantId,
        String environment,
        UUID modelSpecId
    ) {
        if (
            tenantId == null ||
            tenantId.isBlank() ||
            environment == null ||
            environment.isBlank() ||
            modelSpecId == null
        ) {
            throw new IllegalArgumentException(
                "tenantId, environment and modelSpecId are required"
            );
        }
        MessageDigest digest = sha256();
        update(digest, tenantId.trim());
        update(digest, environment.trim());
        update(digest, modelSpecId.toString());
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
