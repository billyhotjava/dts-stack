package com.yuzhi.dts.admin.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration controlling HMAC verification of the audit dictionary files.
 * <p>
 * In a governance-triad deployment {@link #hmacKey} is held by the authorization
 * administrator (authadmin) — sysadmin should not be able to read it. Keeping
 * the key in Vault / HSM / dedicated secret-mount is recommended; falling back
 * to environment variables is only acceptable when the operating system itself
 * enforces the read separation between sysadmin and authadmin.
 */
@ConfigurationProperties(prefix = "auditing.dictionary")
public class AuditDictionaryProperties {

    /**
     * Whether to verify HMAC sidecar signatures at startup. Default {@code true}.
     * Disable only in local dev where signing tooling is not available.
     */
    private boolean verifySignatures = true;

    /**
     * Hex-encoded HMAC-SHA256 key (recommended 32 bytes / 64 hex chars).
     * When blank with {@link #verifySignatures} true, audit dictionaries fail
     * to load — this is intentional fail-secure behaviour.
     */
    private String hmacKey;

    /**
     * Hex-encoded legacy keys, accepted in addition to the active key during
     * rotation windows. Drain after rollout completes.
     */
    private List<String> legacyKeys = new ArrayList<>();

    /** Filename suffix for sidecar signature files. Default ".sig". */
    private String signatureSuffix = ".sig";

    public boolean isVerifySignatures() {
        return verifySignatures;
    }

    public void setVerifySignatures(boolean verifySignatures) {
        this.verifySignatures = verifySignatures;
    }

    public String getHmacKey() {
        return hmacKey;
    }

    public void setHmacKey(String hmacKey) {
        this.hmacKey = hmacKey;
    }

    public List<String> getLegacyKeys() {
        return legacyKeys;
    }

    public void setLegacyKeys(List<String> legacyKeys) {
        if (legacyKeys == null) {
            this.legacyKeys = new ArrayList<>();
            return;
        }
        List<String> filtered = new ArrayList<>();
        for (String key : legacyKeys) {
            if (key != null && !key.isBlank()) {
                filtered.add(key.trim());
            }
        }
        this.legacyKeys = filtered;
    }

    public String getSignatureSuffix() {
        return signatureSuffix;
    }

    public void setSignatureSuffix(String signatureSuffix) {
        if (signatureSuffix == null || signatureSuffix.isBlank()) {
            this.signatureSuffix = ".sig";
            return;
        }
        String trimmed = signatureSuffix.trim();
        this.signatureSuffix = trimmed.startsWith(".") ? trimmed : "." + trimmed;
    }

    public List<String> getLegacyKeysView() {
        return Collections.unmodifiableList(legacyKeys);
    }
}
