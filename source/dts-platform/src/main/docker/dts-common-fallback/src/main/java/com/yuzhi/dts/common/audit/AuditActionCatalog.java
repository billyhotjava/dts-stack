package com.yuzhi.dts.common.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
    private final Map<String, AuditActionDefinition> byCode = new ConcurrentHashMap<>();
    private final Map<String, AuditActionDefinition> byMenuKey = new ConcurrentHashMap<>();

    public AuditActionCatalog(
        ObjectMapper objectMapper,
        ResourceLoader resourceLoader,
        @Value("${dts.audit.catalog-path:classpath:/config/audit-action-catalog.json}") String catalogLocation
    ) {
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.catalogLocation = catalogLocation;
        reload();
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
        try (InputStream is = resource.getInputStream()) {
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

    private String text(JsonNode node, String fieldName, String fallback) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return fallback;
        }
        String text = value.asText();
        return StringUtils.hasText(text) ? text : fallback;
    }
}
