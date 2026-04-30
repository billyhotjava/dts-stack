package com.yuzhi.dts.platform.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration controlling HMAC verification of the audit dictionary files
 * loaded by the platform service (e.g. legacy-action-mappings.json).
 * <p>
 * Mirrors {@code com.yuzhi.dts.admin.config.AuditDictionaryProperties} so the
 * verification key story is symmetric across services. The HMAC key SHOULD be
 * held by authadmin and provisioned through Vault/secret-mount; the system
 * administrator on the platform host should not have read access.
 */
@ConfigurationProperties(prefix = "auditing.dictionary")
public class AuditDictionaryProperties {

    private boolean verifySignatures = true;
    private String hmacKey;
    private List<String> legacyKeys = new ArrayList<>();
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
