package com.yuzhi.dts.admin.service.audit;

import com.yuzhi.dts.admin.config.AuditDictionaryProperties;
import com.yuzhi.dts.common.audit.HmacDictionaryVerifier;
import com.yuzhi.dts.common.audit.HmacDictionaryVerifier.Decision;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * Drives HMAC verification of audit dictionary files at load time.
 * <p>
 * The guard reads a sidecar signature file ({@code <dictionary>.sig} by default),
 * delegates to {@link HmacDictionaryVerifier}, and either lets the load proceed
 * or throws — the latter is mandatory in production, so a tampered or unsigned
 * dictionary cannot bring the audit subsystem up with degraded semantics.
 */
@Component
public class AuditDictionarySignatureGuard {

    private static final Logger log = LoggerFactory.getLogger(AuditDictionarySignatureGuard.class);

    private final AuditDictionaryProperties properties;
    private final ResourceLoader resourceLoader;

    public AuditDictionarySignatureGuard(AuditDictionaryProperties properties, ResourceLoader resourceLoader) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
    }

    /**
     * Verifies the supplied dictionary content against its sidecar signature.
     *
     * @param dictionaryLocation the Spring resource location of the dictionary file
     * @param content            raw bytes of the dictionary just read from {@code dictionaryLocation}
     * @throws IllegalStateException when verification fails and the deployment requires it
     */
    public void verify(String dictionaryLocation, byte[] content) {
        if (!properties.isVerifySignatures()) {
            log.warn(
                "Audit dictionary signature verification is DISABLED for {} — only safe in local dev",
                dictionaryLocation
            );
            return;
        }
        String sigLocation = dictionaryLocation + properties.getSignatureSuffix();
        String signatureHex = readSidecar(sigLocation);
        Decision decision = HmacDictionaryVerifier.verify(
            content,
            signatureHex,
            properties.getHmacKey(),
            properties.getLegacyKeysView()
        );
        switch (decision) {
            case VERIFIED -> log.info("Audit dictionary signature OK for {}", dictionaryLocation);
            case SIGNATURE_MISSING -> throw new IllegalStateException(
                "Audit dictionary signature missing: expected sidecar at " + sigLocation
            );
            case NO_KEY_CONFIGURED -> throw new IllegalStateException(
                "auditing.dictionary.hmac-key is empty — refusing to load " + dictionaryLocation +
                " under fail-secure default. Either configure the key or set verify-signatures=false explicitly."
            );
            case SIGNATURE_INVALID -> throw new IllegalStateException(
                "Audit dictionary signature INVALID for " + dictionaryLocation +
                " — content may have been tampered with, or signed by a non-active key"
            );
        }
    }

    private String readSidecar(String sigLocation) {
        Resource resource = resourceLoader.getResource(sigLocation);
        if (!resource.exists()) {
            return null;
        }
        try (InputStream in = resource.getInputStream()) {
            byte[] bytes = in.readAllBytes();
            if (bytes.length == 0) {
                return null;
            }
            return new String(bytes, StandardCharsets.UTF_8).trim();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read audit dictionary sidecar: " + sigLocation, ex);
        }
    }
}
