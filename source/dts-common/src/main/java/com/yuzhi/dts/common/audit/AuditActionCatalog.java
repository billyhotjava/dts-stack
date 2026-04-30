package com.yuzhi.dts.common.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.audit.HmacDictionaryVerifier.Decision;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AuditActionCatalog {

    private static final Logger log = LoggerFactory.getLogger(AuditActionCatalog.class);

    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final String catalogLocation;
    private final boolean verifySignatures;
    private final String hmacKey;
    private final List<String> legacyKeys;
    private final String signatureSuffix;
    private final Map<String, AuditActionDefinition> byCode = new ConcurrentHashMap<>();
    private final Map<String, AuditActionDefinition> byMenuKey = new ConcurrentHashMap<>();

    public AuditActionCatalog(
        ObjectMapper objectMapper,
        ResourceLoader resourceLoader,
        @Value("${dts.audit.catalog-path:classpath:/config/audit-action-catalog.json}") String catalogLocation,
        @Value("${auditing.dictionary.verify-signatures:false}") boolean verifySignatures,
        @Value("${auditing.dictionary.hmac-key:}") String hmacKey,
        @Value("${auditing.dictionary.legacy-keys:}") String legacyKeysCsv,
        @Value("${auditing.dictionary.signature-suffix:.sig}") String signatureSuffix
    ) {
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.catalogLocation = catalogLocation;
        this.verifySignatures = verifySignatures;
        this.hmacKey = hmacKey;
        this.legacyKeys = parseLegacyKeys(legacyKeysCsv);
        this.signatureSuffix = StringUtils.hasText(signatureSuffix) ? signatureSuffix.trim() : ".sig";
        reload();
    }

    private static List<String> parseLegacyKeys(String csv) {
        if (!StringUtils.hasText(csv)) {
            return Collections.emptyList();
        }
        return Arrays
            .stream(csv.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toUnmodifiableList());
    }

    public Optional<AuditActionDefinition> findByCode(String code) {
        if (!StringUtils.hasText(code)) {
            return Optional.empty();
        }
        return Optional.ofNullable(byCode.get(code.trim().toUpperCase(Locale.ROOT)));
    }

    public Optional<AuditActionDefinition> findByMenuKey(String menuKey) {
        if (!StringUtils.hasText(menuKey)) {
            return Optional.empty();
        }
        return Optional.ofNullable(byMenuKey.get(menuKey.trim().toLowerCase(Locale.ROOT)));
    }

    public List<AuditActionDefinition> listAll() {
        return Collections.unmodifiableList(new ArrayList<>(byCode.values()));
    }

    public synchronized void reload() {
        Resource resource = resolveResource(catalogLocation);
        if (resource == null || !resource.exists()) {
            log.warn("Audit action catalog resource {} not found, falling back to classpath default", catalogLocation);
            resource = resourceLoader.getResource("classpath:/config/audit-action-catalog.json");
        }
        Map<String, AuditActionDefinition> newByCode = new HashMap<>();
        Map<String, AuditActionDefinition> newByMenu = new HashMap<>();
        if (resource == null || !resource.exists()) {
            log.error("Audit action catalog not available; audit enrichment will be skipped");
            byCode.clear();
            byMenuKey.clear();
            return;
        }
        byte[] content;
        try (InputStream is = resource.getInputStream()) {
            content = is.readAllBytes();
        } catch (IOException ex) {
            log.error("Failed to read audit action catalog from {}", resource.getDescription(), ex);
            return;
        }
        verifyCatalogSignature(resource, content);
        try (InputStream is = new ByteArrayInputStream(content)) {
            JsonNode root = objectMapper.readTree(is);
            JsonNode sections = root.path("sections");
            if (!sections.isArray()) {
                log.warn("Audit action catalog sections missing or not an array");
                return;
            }
            for (JsonNode section : sections) {
                String moduleKey = text(section, "module", "general");
                String moduleTitle = text(section, "title", moduleKey);
                JsonNode entries = section.path("entries");
                if (!entries.isArray()) {
                    continue;
                }
                for (JsonNode entry : entries) {
                    String entryKey = text(entry, "key", moduleKey);
                    String entryTitle = text(entry, "title", entryKey);
                    JsonNode actions = entry.path("actions");
                    if (!actions.isArray() || actions.isEmpty()) {
                        continue;
                    }
                    for (JsonNode action : actions) {
                        String code = text(action, "code", null);
                        String display = text(action, "display", code);
                        boolean supportsFlow = action.path("supportsFlow").asBoolean(false);
                        Set<AuditStage> phases = parsePhases(action.path("phases"));
                        if (!StringUtils.hasText(code)) {
                            log.debug("Skip audit action without code for menu key {}", entryKey);
                            continue;
                        }
                        String normalizedCode = code.trim().toUpperCase(Locale.ROOT);
                        AuditActionDefinition definition = new AuditActionDefinition(
                            normalizedCode,
                            display,
                            moduleKey,
                            moduleTitle,
                            entryKey,
                            entryTitle,
                            supportsFlow,
                            phases
                        );
                        if (newByCode.put(normalizedCode, definition) != null) {
                            log.warn("Duplicate audit action code encountered: {}", normalizedCode);
                        }
                        newByMenu.putIfAbsent(entryKey.trim().toLowerCase(Locale.ROOT), definition);
                    }
                }
            }
            byCode.clear();
            byCode.putAll(newByCode);
            byMenuKey.clear();
            byMenuKey.putAll(newByMenu);
            log.info("Loaded {} audit action definitions from {}", byCode.size(), resource.getDescription());
        } catch (IOException ex) {
            log.error("Failed to read audit action catalog from {}", resource.getDescription(), ex);
        }
    }

    private Resource resolveResource(String location) {
        if (!StringUtils.hasText(location)) {
            return null;
        }
        if (location.startsWith("classpath:")) {
            return resourceLoader.getResource(location);
        }
        if (location.startsWith("file:")) {
            return resourceLoader.getResource(location);
        }
        return resourceLoader.getResource("file:" + location);
    }

    private Set<AuditStage> parsePhases(JsonNode node) {
        if (node == null || !node.isArray() || node.size() == 0) {
            return EnumSet.of(AuditStage.SUCCESS);
        }
        EnumSet<AuditStage> phases = EnumSet.noneOf(AuditStage.class);
        node.forEach(element -> phases.add(AuditStage.fromString(element.asText())));
        if (phases.isEmpty()) {
            phases.add(AuditStage.SUCCESS);
        }
        return phases;
    }

    private String text(JsonNode node, String field, String defaultValue) {
        if (node == null) {
            return defaultValue;
        }
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) {
            return defaultValue;
        }
        String value = child.asText();
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    /**
     * Verifies the catalog's HMAC sidecar (e.g. {@code audit-action-catalog.json.sig}). Behaviour:
     * <ul>
     *   <li>{@code verifySignatures=false} — log only; legitimate dev mode.</li>
     *   <li>sidecar missing — fail-secure (throw) when verification is on.</li>
     *   <li>signature/key mismatch — fail-secure (throw).</li>
     * </ul>
     * <p>The catalog backs the entire audit enrichment pipeline, so a tampered catalog can
     * silently rewrite operation semantics — strictly fail-closed when the operator opted in.
     */
    private void verifyCatalogSignature(Resource catalogResource, byte[] content) {
        if (!verifySignatures) {
            log.debug("Audit catalog signature verification disabled");
            return;
        }
        String description = catalogResource.getDescription();
        Resource sigResource = resolveSidecar(catalogResource);
        String signatureHex = readSidecarText(sigResource);
        Decision decision = HmacDictionaryVerifier.verify(content, signatureHex, hmacKey, legacyKeys);
        switch (decision) {
            case VERIFIED -> log.info("Audit catalog signature OK for {}", description);
            case SIGNATURE_MISSING -> throw new IllegalStateException(
                "Audit catalog signature missing: expected sidecar at " + description + signatureSuffix
            );
            case NO_KEY_CONFIGURED -> throw new IllegalStateException(
                "auditing.dictionary.hmac-key is empty — refusing to load " + description +
                " under fail-secure default. Either configure the key or set auditing.dictionary.verify-signatures=false explicitly."
            );
            case SIGNATURE_INVALID -> throw new IllegalStateException(
                "Audit catalog signature INVALID for " + description +
                " — content may have been tampered with, or signed by a non-active key"
            );
        }
    }

    private Resource resolveSidecar(Resource catalogResource) {
        String description = catalogResource.getDescription();
        // Best-effort — Resource subclasses expose a URI-ish description, so try to derive a
        // sibling sidecar location. Fallback to "<catalogLocation>.sig" via resourceLoader.
        if (StringUtils.hasText(catalogLocation)) {
            return resolveResource(catalogLocation + signatureSuffix);
        }
        if (description != null) {
            return resourceLoader.getResource(description + signatureSuffix);
        }
        return null;
    }

    private String readSidecarText(Resource sigResource) {
        if (sigResource == null || !sigResource.exists()) {
            return null;
        }
        try (InputStream is = sigResource.getInputStream()) {
            byte[] bytes = is.readAllBytes();
            if (bytes.length == 0) {
                return null;
            }
            return new String(bytes, StandardCharsets.UTF_8).trim();
        } catch (IOException ex) {
            log.warn("Unable to read audit catalog sidecar {}: {}", sigResource.getDescription(), ex.getMessage());
            return null;
        }
    }
}
